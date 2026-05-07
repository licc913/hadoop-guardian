package com.guardian.hadoop.sql;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlOptimizationService {

    private final SqlOptimizationRepository repository;
    private final SqlOptimizationRuleService ruleService;
    private final SqlOptimizationLlmService llmService;
    private final JdbcTemplate jdbcTemplate;

    public SqlOptimizationService(SqlOptimizationRepository repository,
                                  SqlOptimizationRuleService ruleService,
                                  SqlOptimizationLlmService llmService,
                                  JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.ruleService = ruleService;
        this.llmService = llmService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists sql_optimization_record ("
                + "id bigserial primary key,"
                + "engine_type varchar(32) not null,"
                + "original_sql text not null,"
                + "table_schema_note text,"
                + "partition_info text,"
                + "explain_text text,"
                + "error_text text,"
                + "optimization_goal text,"
                + "problem_summary text not null,"
                + "optimized_sql text not null,"
                + "optimization_points text not null,"
                + "risk_notes text not null,"
                + "validation_steps text not null,"
                + "rule_findings text not null,"
                + "llm_model text,"
                + "analysis_source varchar(32) not null,"
                + "created_by varchar(128) not null,"
                + "created_at timestamp not null"
                + ")"
        );
        jdbcTemplate.execute("create index if not exists idx_sql_optimization_record_created_at on sql_optimization_record(created_at desc)");
        jdbcTemplate.execute("create index if not exists idx_sql_optimization_record_engine on sql_optimization_record(engine_type, created_at desc)");
        jdbcTemplate.execute("alter table sql_optimization_record alter column optimization_goal type text");
        jdbcTemplate.execute("alter table sql_optimization_record alter column llm_model type text");
    }

    public List<SqlOptimizationResult> listHistory() {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(SqlOptimizationResult::fromEntity)
            .collect(Collectors.toList());
    }

    public SqlOptimizationResult getResult(long recordId) {
        SqlOptimizationEntity entity = repository.findById(recordId).orElse(null);
        return entity == null ? null : SqlOptimizationResult.fromEntity(entity);
    }

    @Transactional
    public SqlOptimizationResult analyze(SqlOptimizationRequest request) {
        validateRequest(request);
        SqlOptimizationRuleAnalysis ruleAnalysis = ruleService.analyze(request);
        SqlOptimizationLlmOutcome llmOutcome = llmService.optimize(request, ruleAnalysis);

        SqlOptimizationEntity entity = new SqlOptimizationEntity();
        entity.setEngineType(normalizeEngineType(request.getEngineType()));
        entity.setOriginalSql(defaultIfBlank(request.getOriginalSql(), ""));
        entity.setTableSchemaNote(trimToNull(request.getTableSchemaNote()));
        entity.setPartitionInfo(trimToNull(request.getPartitionInfo()));
        entity.setExplainText(trimToNull(request.getExplainText()));
        entity.setErrorText(trimToNull(request.getErrorText()));
        entity.setOptimizationGoal(trimToNull(request.getOptimizationGoal()));
        entity.setProblemSummary(llmOutcome.getProblemSummary());
        entity.setOptimizedSql(llmOutcome.getOptimizedSql());
        entity.setOptimizationPoints(joinLines(llmOutcome.getOptimizationPoints()));
        entity.setRiskNotes(joinLines(llmOutcome.getRiskNotes()));
        entity.setValidationSteps(joinLines(llmOutcome.getValidationSteps()));
        entity.setRuleFindings(joinLines(ruleAnalysis.getFindings()));
        entity.setLlmModel(trimToNull(llmOutcome.getLlmModel()));
        entity.setAnalysisSource(llmOutcome.getAnalysisSource());
        entity.setCreatedBy(defaultIfBlank(request.getCreatedBy(), "frontend-operator"));
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
        return SqlOptimizationResult.fromEntity(entity);
    }

    private void validateRequest(SqlOptimizationRequest request) {
        if (request == null || !hasText(request.getOriginalSql())) {
            throw new IllegalArgumentException("SQL 内容不能为空。");
        }
        request.setEngineType(normalizeEngineType(request.getEngineType()));
    }

    private String normalizeEngineType(String engineType) {
        if (engineType == null) {
            return "IMPALA";
        }
        String normalized = engineType.trim().toUpperCase();
        return "HIVE".equals(normalized) ? "HIVE" : "IMPALA";
    }

    private String joinLines(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("\n", values);
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
