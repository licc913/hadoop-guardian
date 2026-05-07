package com.guardian.hadoop.tuning;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParameterOptimizationService {

    private final ParameterOptimizationRepository repository;
    private final ClusterConfigSnapshotService configSnapshotService;
    private final ParameterOptimizationRuleService ruleService;
    private final ParameterOptimizationLlmService llmService;
    private final JdbcTemplate jdbcTemplate;

    public ParameterOptimizationService(ParameterOptimizationRepository repository,
                                        ClusterConfigSnapshotService configSnapshotService,
                                        ParameterOptimizationRuleService ruleService,
                                        ParameterOptimizationLlmService llmService,
                                        JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.configSnapshotService = configSnapshotService;
        this.ruleService = ruleService;
        this.llmService = llmService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists parameter_optimization_record ("
                + "id bigserial primary key,"
                + "cluster_name varchar(128) not null,"
                + "service_name varchar(128) not null,"
                + "service_type varchar(64) not null,"
                + "component_version text,"
                + "current_symptoms text,"
                + "optimization_goal text,"
                + "config_snapshot_text text not null,"
                + "source_code_hints text,"
                + "problem_summary text not null,"
                + "recommended_parameters text not null,"
                + "source_evidence text not null,"
                + "expected_benefits text not null,"
                + "risk_notes text not null,"
                + "validation_steps text not null,"
                + "rule_findings text not null,"
                + "llm_model text,"
                + "analysis_source varchar(32) not null,"
                + "created_by varchar(128) not null,"
                + "created_at timestamp not null"
                + ")"
        );
        jdbcTemplate.execute("create index if not exists idx_parameter_optimization_record_created_at on parameter_optimization_record(created_at desc)");
        jdbcTemplate.execute("create index if not exists idx_parameter_optimization_record_service on parameter_optimization_record(service_type, created_at desc)");
        jdbcTemplate.execute("alter table parameter_optimization_record alter column component_version type text");
        jdbcTemplate.execute("alter table parameter_optimization_record alter column optimization_goal type text");
        jdbcTemplate.execute("alter table parameter_optimization_record alter column llm_model type text");
    }

    public List<ParameterOptimizationResult> listHistory() {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(ParameterOptimizationResult::fromEntity)
            .collect(Collectors.toList());
    }

    public ParameterOptimizationResult getResult(long recordId) {
        ParameterOptimizationEntity entity = repository.findById(recordId).orElse(null);
        return entity == null ? null : ParameterOptimizationResult.fromEntity(entity);
    }

    public ParameterOptimizationContextPreview getCurrentContext(String serviceType) {
        return configSnapshotService.collect(serviceType);
    }

    @Transactional
    public ParameterOptimizationResult analyze(ParameterOptimizationRequest request) {
        validateRequest(request);
        ParameterOptimizationContextPreview context = request.isUseCurrentClusterConfig()
            ? configSnapshotService.collect(request.getServiceType())
            : new ParameterOptimizationContextPreview(
                false,
                false,
                "本次未使用当前集群配置，仅依据手工输入和知识库做参数分析。",
                "",
                "",
                normalizeServiceType(request.getServiceType()),
                "",
                "",
                "",
                java.util.Collections.<String, String>emptyMap(),
                java.util.Collections.<ParameterConfigEntryRecord>emptyList(),
                java.util.Collections.<String>emptyList()
            );
        ParameterOptimizationRuleAnalysis ruleAnalysis = ruleService.analyze(request, context);
        ParameterOptimizationLlmOutcome llmOutcome = llmService.analyze(request, context, ruleAnalysis);

        ParameterOptimizationEntity entity = new ParameterOptimizationEntity();
        entity.setClusterName(defaultIfBlank(context.getClusterName(), "UNKNOWN_CLUSTER"));
        entity.setServiceName(defaultIfBlank(context.getServiceName(), normalizeServiceType(request.getServiceType())));
        entity.setServiceType(normalizeServiceType(request.getServiceType()));
        entity.setComponentVersion(trimToNull(context.getComponentVersion()));
        entity.setCurrentSymptoms(trimToNull(request.getCurrentSymptoms()));
        entity.setOptimizationGoal(trimToNull(request.getOptimizationGoal()));
        entity.setConfigSnapshotText(buildConfigSnapshotText(context, request));
        entity.setSourceCodeHints(trimToNull(request.getSourceCodeHints()));
        entity.setProblemSummary(llmOutcome.getProblemSummary());
        entity.setRecommendedParameters(joinRecommendations(llmOutcome.getRecommendations()));
        entity.setSourceEvidence(joinLines(llmOutcome.getSourceEvidence()));
        entity.setExpectedBenefits(joinLines(llmOutcome.getExpectedBenefits()));
        entity.setRiskNotes(joinLines(llmOutcome.getRiskNotes()));
        entity.setValidationSteps(joinLines(llmOutcome.getValidationSteps()));
        entity.setRuleFindings(joinLines(ruleAnalysis.getFindings()));
        entity.setLlmModel(trimToNull(llmOutcome.getLlmModel()));
        entity.setAnalysisSource(llmOutcome.getAnalysisSource());
        entity.setCreatedBy(defaultIfBlank(request.getCreatedBy(), "frontend-operator"));
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
        return ParameterOptimizationResult.fromEntity(entity);
    }

    private void validateRequest(ParameterOptimizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("参数优化请求不能为空。");
        }
        request.setServiceType(normalizeServiceType(request.getServiceType()));
        if (!hasText(request.getCurrentSymptoms())
            && !hasText(request.getManualConfigNote())
            && !hasText(request.getSourceCodeHints())
            && !request.isUseCurrentClusterConfig()) {
            throw new IllegalArgumentException("请至少提供当前症状、手工配置说明、源码线索，或启用当前集群配置采集。");
        }
    }

    private String buildConfigSnapshotText(ParameterOptimizationContextPreview context, ParameterOptimizationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("message=").append(defaultIfBlank(context.getMessage(), "")).append('\n');
        if (hasText(context.getComponentVersion())) {
            builder.append("version=").append(context.getComponentVersion()).append('\n');
        }
        if (context.getScopedConfigEntries() != null && !context.getScopedConfigEntries().isEmpty()) {
            for (ParameterConfigEntryRecord entry : context.getScopedConfigEntries()) {
                builder.append(renderScope(entry))
                    .append(" | ")
                    .append(entry.getConfigKey())
                    .append('=')
                    .append(entry.getConfigValue())
                    .append(" | source=")
                    .append(defaultIfBlank(entry.getValueSource(), "UNKNOWN"))
                    .append('\n');
            }
        } else if (context.getConfigEntries() != null && !context.getConfigEntries().isEmpty()) {
            context.getConfigEntries().forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        }
        if (hasText(request.getManualConfigNote())) {
            builder.append("manual-note=").append(request.getManualConfigNote().trim()).append('\n');
        }
        return builder.toString().trim();
    }

    private String joinRecommendations(List<ParameterRecommendationRecord> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "";
        }
        return recommendations.stream()
            .map(item -> defaultIfBlank(item.getConfigKey(), "未知参数")
                + " | " + defaultIfBlank(item.getCurrentValue(), "未采集")
                + " | " + defaultIfBlank(item.getRecommendedValue(), "建议进一步评估")
                + " | " + defaultIfBlank(item.getReason(), "无"))
            .collect(Collectors.joining("\n"));
    }

    private String joinLines(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("\n", values);
    }

    private String renderScope(ParameterConfigEntryRecord entry) {
        if (entry == null) {
            return "UNKNOWN";
        }
        if ("ROLE_CONFIG_GROUP".equalsIgnoreCase(entry.getScopeType())) {
            return defaultIfBlank(entry.getRoleType(), "ROLE") + "@" + defaultIfBlank(entry.getScopeName(), "未知配置组");
        }
        return "SERVICE@" + defaultIfBlank(entry.getScopeName(), "未知服务");
    }

    private String normalizeServiceType(String serviceType) {
        if (serviceType == null) {
            return "HDFS";
        }
        String normalized = serviceType.trim().toUpperCase(Locale.ROOT);
        if ("HIVE".equals(normalized) || "HIVE_ON_TEZ".equals(normalized)) {
            return "HIVE_ON_TEZ";
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
