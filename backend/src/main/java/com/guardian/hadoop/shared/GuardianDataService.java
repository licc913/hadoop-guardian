package com.guardian.hadoop.shared;

import com.guardian.hadoop.action.ActionRecommendationEntity;
import com.guardian.hadoop.action.ActionRecommendationRecord;
import com.guardian.hadoop.action.ActionRecommendationRepository;
import com.guardian.hadoop.diagnosis.DiagnosisBlueprint;
import com.guardian.hadoop.diagnosis.DiagnosisEntity;
import com.guardian.hadoop.diagnosis.DiagnosisRecord;
import com.guardian.hadoop.diagnosis.DiagnosisRepository;
import com.guardian.hadoop.diagnosis.DiagnosisTaskEntity;
import com.guardian.hadoop.diagnosis.DiagnosisTaskRepository;
import com.guardian.hadoop.diagnosis.DiagnosisTaskResponse;
import com.guardian.hadoop.diagnosis.LlmDiagnosisResult;
import com.guardian.hadoop.diagnosis.LlmDiagnosisService;
import com.guardian.hadoop.incident.CrossComponentAnalysisRecord;
import com.guardian.hadoop.incident.CrossComponentAnalysisService;
import com.guardian.hadoop.incident.IncidentCloseResponse;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentGovernanceResponse;
import com.guardian.hadoop.incident.IncidentRecord;
import com.guardian.hadoop.incident.IncidentRepository;
import com.guardian.hadoop.inspection.ClusterInspectionReportRepository;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotService;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsService;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import com.guardian.hadoop.workflow.ApprovalRecord;
import com.guardian.hadoop.workflow.ApprovalRecordCreateRequest;
import com.guardian.hadoop.workflow.ApprovalRecordEntity;
import com.guardian.hadoop.workflow.ApprovalRecordRepository;
import com.guardian.hadoop.workflow.ExecutionRecord;
import com.guardian.hadoop.workflow.ExecutionRecordCreateRequest;
import com.guardian.hadoop.workflow.ExecutionRecordEntity;
import com.guardian.hadoop.workflow.ExecutionRecordRepository;
import com.guardian.hadoop.workflow.PostmortemRecord;
import com.guardian.hadoop.workflow.PostmortemRecordEntity;
import com.guardian.hadoop.workflow.PostmortemRecordRepository;
import com.guardian.hadoop.workflow.PostmortemUpsertRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardianDataService {

    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_LLM_ONLY = "LLM_ONLY";
    private static final String MODE_KNOWLEDGE_ONLY = "KNOWLEDGE_ONLY";

    private final IncidentRepository incidentRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final DiagnosisTaskRepository diagnosisTaskRepository;
    private final ActionRecommendationRepository actionRecommendationRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final ExecutionRecordRepository executionRecordRepository;
    private final PostmortemRecordRepository postmortemRecordRepository;
    private final ClouderaManagerSettingsService settingsService;
    private final CmServiceLogSnapshotService logSnapshotService;
    private final KnowledgeSuggestionService knowledgeSuggestionService;
    private final LlmDiagnosisService llmDiagnosisService;
    private final ClusterInspectionReportRepository inspectionReportRepository;
    private final PlatformRuntimeStatusService runtimeStatusService;
    private final CrossComponentAnalysisService crossComponentAnalysisService;
    private final String datasourceUrl;

    public GuardianDataService(IncidentRepository incidentRepository,
                               DiagnosisRepository diagnosisRepository,
                               DiagnosisTaskRepository diagnosisTaskRepository,
                               ActionRecommendationRepository actionRecommendationRepository,
                               ApprovalRecordRepository approvalRecordRepository,
                               ExecutionRecordRepository executionRecordRepository,
                               PostmortemRecordRepository postmortemRecordRepository,
                               ClouderaManagerSettingsService settingsService,
                               CmServiceLogSnapshotService logSnapshotService,
                               KnowledgeSuggestionService knowledgeSuggestionService,
                               LlmDiagnosisService llmDiagnosisService,
                               ClusterInspectionReportRepository inspectionReportRepository,
                               PlatformRuntimeStatusService runtimeStatusService,
                               CrossComponentAnalysisService crossComponentAnalysisService,
                               @Value("${spring.datasource.url}") String datasourceUrl) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.diagnosisTaskRepository = diagnosisTaskRepository;
        this.actionRecommendationRepository = actionRecommendationRepository;
        this.approvalRecordRepository = approvalRecordRepository;
        this.executionRecordRepository = executionRecordRepository;
        this.postmortemRecordRepository = postmortemRecordRepository;
        this.settingsService = settingsService;
        this.logSnapshotService = logSnapshotService;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.llmDiagnosisService = llmDiagnosisService;
        this.inspectionReportRepository = inspectionReportRepository;
        this.runtimeStatusService = runtimeStatusService;
        this.crossComponentAnalysisService = crossComponentAnalysisService;
        this.datasourceUrl = datasourceUrl;
    }

    public List<IncidentRecord> getIncidents() {
        return getDistinctActiveRealtimeIncidents().stream()
            .sorted((left, right) -> compareInstant(right.getOccurredAt(), left.getOccurredAt()))
            .map(IncidentRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public IncidentRecord getIncident(long incidentId) {
        IncidentEntity entity = incidentRepository.findById(incidentId).orElse(null);
        return entity == null ? null : IncidentRecord.fromEntity(entity);
    }

    public List<DiagnosisRecord> getDiagnoses(long incidentId) {
        return diagnosisRepository.findByIncident_IdOrderByCreatedAtDesc(incidentId).stream()
            .map(DiagnosisRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public List<ActionRecommendationRecord> getActionRecommendations(long incidentId) {
        return actionRecommendationRepository.findByIncident_IdOrderByCreatedAtDesc(incidentId).stream()
            .map(ActionRecommendationRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public List<ApprovalRecord> getApprovals(long incidentId) {
        return approvalRecordRepository.findByIncident_IdOrderByRequestedAtAsc(incidentId).stream()
            .map(ApprovalRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public List<ExecutionRecord> getExecutions(long incidentId) {
        return executionRecordRepository.findByIncident_IdOrderByStartedAtAsc(incidentId).stream()
            .map(ExecutionRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public PostmortemRecord getPostmortem(long incidentId) {
        return postmortemRecordRepository.findByIncident_Id(incidentId)
            .map(PostmortemRecord::fromEntity)
            .orElse(null);
    }

    @Transactional
    public IncidentGovernanceResponse suppressIncident(long incidentId, String operator, String note, Integer suppressMinutes) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        Instant now = Instant.now();
        int minutes = suppressMinutes == null || suppressMinutes.intValue() <= 0 ? 120 : suppressMinutes.intValue();
        incident.setGovernanceStatus("SUPPRESSED");
        incident.setSuppressedUntil(now.plusSeconds(minutes * 60L));
        incident.setGovernanceNote(buildGovernanceNote(operator, note, "suppressed for " + minutes + " minutes"));
        incidentRepository.save(incident);
        return new IncidentGovernanceResponse(
            true,
            "Incident suppressed and removed from active queue until the suppression window expires.",
            IncidentRecord.fromEntity(incident),
            now
        );
    }

    @Transactional
    public IncidentGovernanceResponse resumeIncident(long incidentId, String operator, String note) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        Instant now = Instant.now();
        incident.setGovernanceStatus("ACTIVE");
        incident.setSuppressedUntil(null);
        incident.setGovernanceNote(buildGovernanceNote(operator, note, "suppression cleared"));
        incidentRepository.save(incident);
        return new IncidentGovernanceResponse(
            true,
            "Incident restored to the active queue.",
            IncidentRecord.fromEntity(incident),
            now
        );
    }

    @Transactional
    public ApprovalRecord createApprovalRecord(long incidentId, ApprovalRecordCreateRequest request) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }
        ActionRecommendationEntity action = resolveActionRecommendation(incidentId, request == null ? null : request.getActionRecommendationId());
        if (action == null) {
            return null;
        }

        ApprovalRecordEntity entity = new ApprovalRecordEntity();
        entity.setIncident(incident);
        entity.setActionRecommendation(action);
        entity.setApprovalStatus(defaultIfBlank(request.getApprovalStatus(), "PENDING"));
        entity.setRequestedBy(defaultIfBlank(request.getRequestedBy(), "frontend-operator"));
        entity.setApprover(trimToNull(request.getApprover()));
        entity.setComment(trimToNull(request.getComment()));
        entity.setRequestedAt(Instant.now());
        if (!"PENDING".equalsIgnoreCase(entity.getApprovalStatus())) {
            entity.setDecidedAt(Instant.now());
            if ("APPROVED".equalsIgnoreCase(entity.getApprovalStatus())) {
                action.setStatus("APPROVED");
            } else if ("REJECTED".equalsIgnoreCase(entity.getApprovalStatus())) {
                action.setStatus("REJECTED");
            }
        } else {
            action.setStatus("AWAITING_APPROVAL");
        }
        approvalRecordRepository.save(entity);
        actionRecommendationRepository.save(action);
        return ApprovalRecord.fromEntity(entity);
    }

    @Transactional
    public ExecutionRecord createExecutionRecord(long incidentId, ExecutionRecordCreateRequest request) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }
        ActionRecommendationEntity action = resolveActionRecommendation(incidentId, request == null ? null : request.getActionRecommendationId());
        if (action == null) {
            return null;
        }

        ExecutionRecordEntity entity = new ExecutionRecordEntity();
        entity.setIncident(incident);
        entity.setActionRecommendation(action);
        entity.setExecutionStatus(defaultIfBlank(request.getExecutionStatus(), "RUNNING"));
        entity.setExecutor(defaultIfBlank(request.getExecutor(), "frontend-operator"));
        entity.setExecutionSummary(defaultIfBlank(request.getExecutionSummary(), "Execution record created from incident workflow."));
        entity.setStartedAt(Instant.now());
        if (!"RUNNING".equalsIgnoreCase(entity.getExecutionStatus())) {
            entity.setFinishedAt(Instant.now());
        }
        executionRecordRepository.save(entity);

        if ("SUCCESS".equalsIgnoreCase(entity.getExecutionStatus())) {
            action.setStatus("DONE");
        } else if ("FAILED".equalsIgnoreCase(entity.getExecutionStatus())) {
            action.setStatus("FAILED");
        } else {
            action.setStatus("EXECUTING");
        }
        actionRecommendationRepository.save(action);
        return ExecutionRecord.fromEntity(entity);
    }

    @Transactional
    public PostmortemRecord upsertPostmortem(long incidentId, PostmortemUpsertRequest request) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null || request == null) {
            return null;
        }

        PostmortemRecordEntity entity = postmortemRecordRepository.findByIncident_Id(incidentId)
            .orElseGet(PostmortemRecordEntity::new);
        entity.setIncident(incident);
        entity.setSummary(defaultIfBlank(request.getSummary(), "No summary provided."));
        entity.setRootCause(defaultIfBlank(request.getRootCause(), "Root cause is under review."));
        entity.setImpactStatement(defaultIfBlank(request.getImpactStatement(), "Impact statement is pending."));
        entity.setTimeline(normalizeLines(request.getTimeline()));
        entity.setPreventionItems(normalizeLines(request.getPreventionItems()));
        entity.setUpdatedAt(Instant.now());
        postmortemRecordRepository.save(entity);
        return PostmortemRecord.fromEntity(entity);
    }

    public List<CmServiceLogSnapshotRecord> getIncidentServiceLogs(long incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return Collections.emptyList();
        }
        return logSnapshotService.getLogsForIncident(incident);
    }

    public CrossComponentAnalysisRecord getCrossComponentAnalysis(long incidentId) {
        return crossComponentAnalysisService.analyze(incidentId);
    }

    @Transactional
    public DiagnosisTaskResponse createDiagnosis(long incidentId, String triggerBy, String triggerReason, String diagnosisMode) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        String requestedMode = normalizeDiagnosisMode(diagnosisMode);
        DiagnosisEntity latestDiagnosis = diagnosisRepository.findTopByIncident_IdOrderByCreatedAtDesc(incidentId);

        DiagnosisTaskEntity task = new DiagnosisTaskEntity();
        task.setIncident(incident);
        task.setTaskType("MANUAL_DIAGNOSIS");
        task.setTriggerBy(defaultIfBlank(triggerBy, "frontend-operator"));
        task.setTriggerReason(defaultIfBlank(triggerReason, "由事件详情页人工发起诊断"));
        task.setStartedAt(Instant.now());
        task.setCreatedAt(Instant.now());

        DiagnosisBlueprintSelection selection = buildDiagnosisBlueprint(incident, triggerReason, triggerBy, requestedMode);
        if (selection.getBlueprint() == null) {
            task.setStatus("FAILED");
            task.setFinishedAt(Instant.now());
            task.setErrorMessage(selection.getMessage());
            diagnosisTaskRepository.save(task);
            return new DiagnosisTaskResponse(
                false,
                selection.getMessage(),
                selection.getReasonCodes(),
                null,
                selection.getDiagnosisSource(),
                requestedMode,
                false,
                selection.getDetails()
            );
        }

        DiagnosisBlueprint blueprint = selection.getBlueprint();
        if (isEquivalentDiagnosis(latestDiagnosis, blueprint)) {
            DiagnosisRecord reusedDiagnosis = latestDiagnosis == null ? null : DiagnosisRecord.fromEntity(latestDiagnosis);
            if (MODE_KNOWLEDGE_ONLY.equals(requestedMode) || MODE_LLM_ONLY.equals(requestedMode)) {
                reusedDiagnosis = null;
            }
            task.setStatus("SKIPPED");
            task.setFinishedAt(Instant.now());
            task.setErrorMessage("当前没有发现新的有效证据，本次复用最近一次有效诊断。");
            diagnosisTaskRepository.save(task);
            return new DiagnosisTaskResponse(
                false,
                "当前没有发现新的有效证据，本次复用最近一次有效诊断。",
                Arrays.asList("NO_NEW_EVIDENCE", "REUSED_LATEST_DIAGNOSIS"),
                reusedDiagnosis,
                selection.getDiagnosisSource(),
                requestedMode,
                selection.isUsedFallback(),
                selection.getDetails()
            );
        }

        incident.setStatus("DIAGNOSING");
        task.setStatus("SUCCESS");
        task.setFinishedAt(Instant.now());
        diagnosisTaskRepository.save(task);

        DiagnosisEntity diagnosis = new DiagnosisEntity();
        diagnosis.setIncident(incident);
        diagnosis.setSubsystem(incident.getServiceType());
        diagnosis.setRootCause(blueprint.getRootCause());
        diagnosis.setConfidence(blueprint.getConfidence());
        diagnosis.setImpactLevel(blueprint.getImpactLevel());
        diagnosis.setCrossComponentPath(blueprint.getCrossComponentPath());
        diagnosis.setRecommendations(blueprint.getRecommendations());
        diagnosis.setFollowUps(blueprint.getFollowUps());
        diagnosis.setCreatedAt(Instant.now());
        diagnosisRepository.save(diagnosis);

        ActionRecommendationEntity action = new ActionRecommendationEntity();
        action.setIncident(incident);
        action.setDiagnosis(diagnosis);
        action.setActionName(blueprint.getActionName());
        action.setActionType(blueprint.getActionType());
        action.setRiskLevel(blueprint.getActionRiskLevel());
        action.setRequiresApproval(blueprint.isActionRequiresApproval());
        action.setRecommendationText(blueprint.getActionRecommendationText());
        action.setStatus("PENDING");
        action.setCreatedAt(Instant.now());
        actionRecommendationRepository.save(action);

        List<String> reasonCodes = new ArrayList<String>();
        reasonCodes.add("NEW_DIAGNOSIS_CREATED");
        reasonCodes.add(blueprint.getGenerationSource());
        reasonCodes.add("REQUEST_MODE_" + requestedMode);
        if (selection.isUsedFallback()) {
            reasonCodes.add("MODE_FALLBACK_APPLIED");
        }
        if (hasText(triggerReason)) {
            reasonCodes.add("TRIGGERED_BY_OPERATOR");
        }

        String successMessage = "EXTERNAL_LLM".equals(blueprint.getGenerationSource())
            ? "本次诊断由 AI 大模型生成，并同步产出动作建议。"
            : "本次诊断由知识库或规则链路生成，并同步产出动作建议。";

        return new DiagnosisTaskResponse(
            true,
            successMessage,
            reasonCodes,
            DiagnosisRecord.fromEntity(diagnosis),
            blueprint.getGenerationSource(),
            requestedMode,
            selection.isUsedFallback(),
            selection.getDetails()
        );
    }

    @Transactional
    public IncidentCloseResponse closeIncident(long incidentId, String closedBy, String closeReason) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        if ("CLOSED".equalsIgnoreCase(incident.getStatus())) {
            return new IncidentCloseResponse(true, "事件已处于关闭状态。", IncidentRecord.fromEntity(incident), Instant.now());
        }

        incident.setStatus("CLOSED");
        if (hasText(closedBy)) {
            incident.setOwner(closedBy.trim());
        }
        List<String> evidence = new ArrayList<String>(incident.getEvidence() == null ? Collections.<String>emptyList() : incident.getEvidence());
        evidence.add("处置闭环：" + defaultIfBlank(closeReason, "已人工确认处置完成，事件关闭。"));
        evidence.add("关闭时间：" + Instant.now().toString());
        incident.setEvidence(evidence);
        incidentRepository.save(incident);

        List<ActionRecommendationEntity> actions = actionRecommendationRepository.findByIncident_IdOrderByCreatedAtDesc(incidentId);
        for (ActionRecommendationEntity action : actions) {
            if (!"CLOSED".equalsIgnoreCase(action.getStatus())) {
                action.setStatus("CLOSED");
            }
        }
        actionRecommendationRepository.saveAll(actions);

        return new IncidentCloseResponse(true, "事件已关闭，并从活动事件队列移除。", IncidentRecord.fromEntity(incident), Instant.now());
    }

    public DashboardSummary getSummary() {
        List<IncidentEntity> incidents = getDistinctActiveRealtimeIncidents();
        long openIncidents = incidents.stream().filter(incident -> "OPEN".equalsIgnoreCase(incident.getStatus())).count();
        long diagnosingIncidents = incidents.stream().filter(incident -> "DIAGNOSING".equalsIgnoreCase(incident.getStatus())).count();
        long criticalIncidents = incidents.stream().filter(incident -> "CRITICAL".equalsIgnoreCase(incident.getSeverity())).count();
        long suppressedIncidents = incidentRepository.countByGovernanceStatus("SUPPRESSED");
        long actionRequired = actionRecommendationRepository.findAll().stream()
            .filter(record -> !"CLOSED".equalsIgnoreCase(record.getStatus()))
            .count();
        return new DashboardSummary((int) openIncidents, (int) diagnosingIncidents, (int) criticalIncidents, (int) actionRequired, (int) suppressedIncidents);
    }

    public SystemStatusResponse getSystemStatus() {
        String databaseMode = datasourceUrl.contains("postgresql") ? "PostgreSQL" : "H2";
        long inspectionRunningCount = inspectionReportRepository.countByStatus("RUNNING") + inspectionReportRepository.countByStatus("PENDING");
        long inspectionFailedCount = inspectionReportRepository.countByStatus("FAILED");
        return new SystemStatusResponse(
            true,
            settingsService.getEffectiveSettings().isEnabled(),
            databaseMode,
            getDistinctActiveRealtimeIncidents().size(),
            (int) incidentRepository.countByGovernanceStatus("SUPPRESSED"),
            runtimeStatusService.getLastCmCollectionAt(),
            runtimeStatusService.getLastCmCollectionSuccess(),
            runtimeStatusService.getLastCmCollectionMessage(),
            runtimeStatusService.getLastCmRecentLogCount(),
            (int) inspectionRunningCount,
            (int) inspectionFailedCount,
            runtimeStatusService.getLastInspectionStartedAt(),
            runtimeStatusService.getLastInspectionCompletedAt(),
            runtimeStatusService.getLastInspectionStatus(),
            runtimeStatusService.getLastInspectionMessage()
        );
    }

    public void seedIfEmpty() {
        // Use imported CM alerts and explicit knowledge articles instead of seeded runtime data.
    }

    private List<IncidentEntity> getDistinctActiveIncidents() {
        return getDistinctIncidents().stream()
            .filter(incident -> !"CLOSED".equalsIgnoreCase(incident.getStatus()))
            .filter(this::isIncidentVisible)
            .collect(Collectors.toList());
    }

    private List<IncidentEntity> getDistinctActiveRealtimeIncidents() {
        return getDistinctIncidents().stream()
            .filter(incident -> "CM_CURRENT".equalsIgnoreCase(incident.getSourceType()))
            .filter(incident -> !"CLOSED".equalsIgnoreCase(incident.getStatus()))
            .filter(this::isIncidentVisible)
            .collect(Collectors.toList());
    }

    private List<IncidentEntity> getDistinctIncidents() {
        List<IncidentEntity> sorted = incidentRepository.findAll().stream()
            .sorted((left, right) -> compareInstant(right.getOccurredAt(), left.getOccurredAt()))
            .collect(Collectors.toList());
        Map<String, IncidentEntity> deduped = new LinkedHashMap<String, IncidentEntity>();
        for (IncidentEntity incident : sorted) {
            deduped.putIfAbsent(buildIncidentDedupKey(incident), incident);
        }
        return new ArrayList<IncidentEntity>(deduped.values());
    }

    private String buildIncidentDedupKey(IncidentEntity incident) {
        if ("CM_ALERT".equalsIgnoreCase(incident.getSourceType())) {
            return String.join("|",
                safe(incident.getClusterName()).toUpperCase(Locale.ROOT),
                safe(incident.getServiceType()).toUpperCase(Locale.ROOT),
                normalizeAlertText(incident.getTitle()),
                normalizeAlertText(incident.getSummary())
            );
        }
        if (hasText(incident.getIncidentNo())) {
            return "NO:" + safe(incident.getIncidentNo()).toUpperCase(Locale.ROOT);
        }
        return String.join("|",
            safe(incident.getClusterName()).toUpperCase(Locale.ROOT),
            safe(incident.getServiceType()).toUpperCase(Locale.ROOT),
            safe(incident.getTitle()).toUpperCase(Locale.ROOT),
            safe(incident.getOccurredAt() == null ? null : incident.getOccurredAt().toString()));
    }

    private boolean isIncidentVisible(IncidentEntity incident) {
        if (incident == null) {
            return false;
        }
        if (!"SUPPRESSED".equalsIgnoreCase(incident.getGovernanceStatus())) {
            return true;
        }
        Instant suppressedUntil = incident.getSuppressedUntil();
        return suppressedUntil == null || !suppressedUntil.isAfter(Instant.now());
    }

    private String buildGovernanceNote(String operator, String note, String action) {
        StringBuilder builder = new StringBuilder();
        builder.append(defaultIfBlank(operator, "frontend-operator"))
            .append(" @ ")
            .append(Instant.now())
            .append(" - ")
            .append(defaultIfBlank(action, "updated governance state"));
        if (hasText(note)) {
            builder.append(" | ").append(note.trim());
        }
        return builder.toString();
    }

    private ActionRecommendationEntity resolveActionRecommendation(long incidentId, Long actionRecommendationId) {
        if (actionRecommendationId != null) {
            ActionRecommendationEntity entity = actionRecommendationRepository.findById(actionRecommendationId).orElse(null);
            if (entity != null && entity.getIncident() != null && entity.getIncident().getId() != null
                && entity.getIncident().getId().longValue() == incidentId) {
                return entity;
            }
        }
        return actionRecommendationRepository.findTopByIncident_IdOrderByCreatedAtDesc(incidentId);
    }

    private List<String> normalizeLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .map(this::trimToNull)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeAlertText(String value) {
        return safe(value)
            .replaceAll("\\s+", " ")
            .replaceAll("[0-9]+(?:\\.[0-9]+)?", "#")
            .replaceAll("[A-Fa-f0-9]{8,}(?:-[A-Fa-f0-9]{4,})*", "#")
            .replaceAll("[A-Za-z_]+-[A-Za-z0-9]{8,}", "#")
            .trim()
            .toUpperCase(Locale.ROOT);
    }

    private DiagnosisBlueprintSelection buildDiagnosisBlueprint(IncidentEntity incident,
                                                               String triggerReason,
                                                               String triggerBy,
                                                               String requestedMode) {
        if (MODE_KNOWLEDGE_ONLY.equals(requestedMode)) {
            DiagnosisBlueprint knowledgeBlueprint = buildKnowledgeDiagnosisBlueprint(incident);
            if (knowledgeBlueprint != null) {
                return DiagnosisBlueprintSelection.success(knowledgeBlueprint, "KNOWLEDGE_BASE", false);
            }
            return DiagnosisBlueprintSelection.success(
                buildRuleDiagnosisBlueprint(incident, triggerReason, triggerBy, "RULE_FALLBACK"),
                "RULE_FALLBACK",
                false
            );
        }

        LlmDiagnosisResult llmResult = llmDiagnosisService.generate(incident, triggerReason, triggerBy);
        if (llmResult.getBlueprint() != null) {
            return DiagnosisBlueprintSelection.success(llmResult.getBlueprint(), llmResult.getBlueprint().getGenerationSource(), false);
        }

        if (MODE_LLM_ONLY.equals(requestedMode)) {
            return DiagnosisBlueprintSelection.failure(
                "AI 大模型没有返回可用诊断结果，请检查模型配置或接口状态。",
                Arrays.asList("LLM_REQUESTED", "LLM_UNAVAILABLE"),
                "NONE",
                llmResult.getFailureDetails()
            );
        }

        DiagnosisBlueprint knowledgeBlueprint = buildKnowledgeDiagnosisBlueprint(incident);
        if (knowledgeBlueprint != null) {
            return DiagnosisBlueprintSelection.success(knowledgeBlueprint, "KNOWLEDGE_BASE", true);
        }

        return DiagnosisBlueprintSelection.success(
            buildRuleDiagnosisBlueprint(incident, triggerReason, triggerBy, "RULE_FALLBACK"),
            "RULE_FALLBACK",
            true
        );
    }

    private DiagnosisBlueprint buildKnowledgeDiagnosisBlueprint(IncidentEntity incident) {
        if (incident.getId() == null) {
            return null;
        }
        List<KnowledgeSuggestionRecord> suggestions = knowledgeSuggestionService.getSuggestions(incident.getId());
        if (suggestions.isEmpty()) {
            return null;
        }

        KnowledgeSuggestionRecord primary = suggestions.get(0);
        List<String> recommendations = selectLines(primary.getSteps(), primary.getMatchReasons(), primary.getValidationChecks());
        List<String> followUps = selectLines(primary.getValidationChecks(), primary.getCautionItems(), primary.getMatchReasons());
        String actionText = firstText(primary.getSteps(), primary.getValidationChecks(), primary.getSummary());

        return new DiagnosisBlueprint(
            hasText(primary.getSummary()) ? primary.getSummary() : primary.getTitle(),
            Math.min(0.88d, 0.45d + (primary.getScore() * 0.04d)),
            "CRITICAL".equalsIgnoreCase(incident.getSeverity()) ? "SEV1" : "SEV2",
            safe(incident.getServiceType()),
            recommendations,
            followUps,
            "执行知识库建议核查",
            "DIAGNOSTIC_COLLECTION",
            hasText(primary.getRiskLevel()) ? primary.getRiskLevel() : "LOW",
            primary.isRequiresApproval(),
            actionText,
            "KNOWLEDGE_BASE"
        );
    }

    private List<String> selectLines(List<String> primary, List<String> secondary, List<String> tertiary) {
        List<String> values = takeNonBlank(primary, 3);
        if (!values.isEmpty()) {
            return values;
        }
        values = takeNonBlank(secondary, 3);
        if (!values.isEmpty()) {
            return values;
        }
        values = takeNonBlank(tertiary, 3);
        if (!values.isEmpty()) {
            return values;
        }
        return Collections.singletonList("请先核对当前事件证据，再决定是否继续扩大处置范围。");
    }

    private List<String> takeNonBlank(List<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .filter(this::hasText)
            .limit(maxItems)
            .collect(Collectors.toList());
    }

    private String firstText(List<String> primary, List<String> secondary, String fallback) {
        List<String> values = takeNonBlank(primary, 1);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        values = takeNonBlank(secondary, 1);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        return hasText(fallback) ? fallback : "请先核对当前事件证据，再决定是否执行后续动作。";
    }

    private DiagnosisBlueprint buildRuleDiagnosisBlueprint(IncidentEntity incident,
                                                           String triggerReason,
                                                           String triggerBy,
                                                           String generationSource) {
        String serviceType = safe(incident.getServiceType()).toUpperCase(Locale.ROOT);
        String triggerReasonText = defaultIfBlank(triggerReason, "人工发起诊断");
        String triggerByText = defaultIfBlank(triggerBy, "未知操作人");

        if ("HDFS".equals(serviceType)) {
            return new DiagnosisBlueprint(
                "当前证据更像 HDFS 容量压力、副本恢复积压或热目录导致的元数据与存储压力。触发原因：" + triggerReasonText,
                0.68d,
                "SEV2",
                "HDFS",
                Arrays.asList(
                    "先检查 HDFS 容量趋势、坏块数量和欠副本数量是否持续升高。",
                    "补采 NameNode 与受影响 DataNode 日志，确认是否由小文件压力、磁盘异常或副本恢复积压导致。",
                    "在执行 balancer、目录清理或节点隔离前，先确认业务窗口和审批边界。"
                ),
                Arrays.asList(
                    "触发人：" + triggerByText,
                    "结合 fsck 与副本状态结果，刷新最终诊断结论。"
                ),
                "补采 HDFS 关键证据",
                "EVIDENCE_COLLECTION",
                "LOW",
                false,
                "优先补采 NameNode 指标、副本状态和热点目录证据，再判断是否需要扩展处置。",
                generationSource
            );
        }

        if ("YARN".equals(serviceType)) {
            return new DiagnosisBlueprint(
                "当前证据更像 YARN 队列拥塞或资源分配变慢。触发原因：" + triggerReasonText,
                0.69d,
                "SEV2",
                "YARN",
                Arrays.asList(
                    "检查 pending 资源、队列占用率和调度延迟，确认是否由资源竞争导致。",
                    "补采 ResourceManager 与异常 NodeManager 日志，确认是否存在节点抖动或 Container 启动失败。",
                    "在调整队列容量或重启服务前，先确认租户影响和审批要求。"
                ),
                Arrays.asList(
                    "触发人：" + triggerByText,
                    "结合队列状态和节点健康结果刷新建议。"
                ),
                "补采 YARN 队列与节点证据",
                "EVIDENCE_COLLECTION",
                "LOW",
                false,
                "优先补采队列积压、NodeManager 健康和 ResourceManager 调度证据，再决定是否限流或平衡资源。",
                generationSource
            );
        }

        if ("IMPALA".equals(serviceType)) {
            return new DiagnosisBlueprint(
                "当前证据更像 Impala admission control 排队、连接池耗尽或元数据滞后。触发原因：" + triggerReasonText,
                0.67d,
                "SEV2",
                "Impala -> HDFS",
                Arrays.asList(
                    "确认 admission control 是否持续排队，并判断是否有少量查询长期占用资源。",
                    "补采 impalad、catalogd 日志，确认是否存在连接池耗尽、metadata refresh 延迟或热点节点。",
                    "在执行 refresh、限流或受控降载前，先确认受影响库表和业务窗口。"
                ),
                Arrays.asList(
                    "触发人：" + triggerByText,
                    "补齐查询类型、热点表和 admission 指标后再刷新建议。"
                ),
                "补采 Impala 运行证据",
                "EVIDENCE_COLLECTION",
                "LOW",
                false,
                "优先补采 admission 队列、连接池、catalogd 日志和热点表状态，再决定是否 refresh 或限流。",
                generationSource
            );
        }

        if ("HIVE_ON_TEZ".equals(serviceType)) {
            return new DiagnosisBlueprint(
                "当前证据更像 Hive on Tez 依赖 YARN 或 Metastore 的资源波动。触发原因：" + triggerReasonText,
                0.70d,
                "SEV2",
                "Hive on Tez -> YARN -> HDFS",
                Arrays.asList(
                    "确认 Tez AM 获取资源是否变慢，并检查 HiveServer2 与 Metastore 是否有连接异常。",
                    "补采 HiveServer2、Metastore 与 Tez 日志，判断问题在 Hive 侧还是依赖链。",
                    "在重启会话、切换执行路径或扩展处置范围前，先完成依赖核验和审批确认。"
                ),
                Arrays.asList(
                    "触发人：" + triggerByText,
                    "结合 Metastore、Tez DAG 与 YARN 证据刷新建议。"
                ),
                "补采 Hive on Tez 依赖链证据",
                "EVIDENCE_COLLECTION",
                "LOW",
                false,
                "优先补采 HiveServer2、Metastore、Tez AM 与 YARN 资源等待证据，再判断是否需要会话恢复或重试。",
                generationSource
            );
        }

        return new DiagnosisBlueprint(
            "已创建诊断任务，但在形成最终根因前仍需补充更多组件上下文和关键证据。触发原因：" + triggerReasonText,
            0.55d,
            "SEV2",
            safe(incident.getServiceType()),
            Arrays.asList(
                "补采最新的 Cloudera Manager 告警上下文和关键角色日志。",
                "确认当前影响范围、跨组件依赖关系，以及是否存在并发变更。",
                "在执行任何动作前，先确认控制边界和审批要求。"
            ),
            Arrays.asList(
                "触发人：" + triggerByText,
                "证据采集完成后再补充最终诊断结论。"
            ),
            "补充诊断证据",
            "EVIDENCE_COLLECTION",
            "LOW",
            false,
            "优先补采告警上下文、角色日志和核心运行指标，再决定是否进入受控处置。",
            generationSource
        );
    }

    private boolean isEquivalentDiagnosis(DiagnosisEntity diagnosis, DiagnosisBlueprint blueprint) {
        if (diagnosis == null) {
            return false;
        }
        return Objects.equals(safe(diagnosis.getRootCause()), safe(blueprint.getRootCause()))
            && Objects.equals(safe(diagnosis.getImpactLevel()), safe(blueprint.getImpactLevel()))
            && Objects.equals(safe(diagnosis.getCrossComponentPath()), safe(blueprint.getCrossComponentPath()))
            && normalizeList(diagnosis.getRecommendations()).equals(normalizeList(blueprint.getRecommendations()))
            && normalizeList(diagnosis.getFollowUps()).equals(normalizeList(blueprint.getFollowUps()));
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
            .map(this::safe)
            .filter(this::hasText)
            .collect(Collectors.toList());
    }

    private String normalizeDiagnosisMode(String diagnosisMode) {
        String normalized = safe(diagnosisMode).toUpperCase(Locale.ROOT);
        if (MODE_LLM_ONLY.equals(normalized) || MODE_KNOWLEDGE_ONLY.equals(normalized)) {
            return normalized;
        }
        return MODE_AUTO;
    }

    private int compareInstant(Instant left, Instant right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class DiagnosisBlueprintSelection {

        private final DiagnosisBlueprint blueprint;
        private final String diagnosisSource;
        private final boolean usedFallback;
        private final String message;
        private final List<String> reasonCodes;
        private final String details;

        private DiagnosisBlueprintSelection(DiagnosisBlueprint blueprint,
                                            String diagnosisSource,
                                            boolean usedFallback,
                                            String message,
                                            List<String> reasonCodes,
                                            String details) {
            this.blueprint = blueprint;
            this.diagnosisSource = diagnosisSource;
            this.usedFallback = usedFallback;
            this.message = message;
            this.reasonCodes = reasonCodes == null ? Collections.<String>emptyList() : reasonCodes;
            this.details = details;
        }

        static DiagnosisBlueprintSelection success(DiagnosisBlueprint blueprint, String diagnosisSource, boolean usedFallback) {
            return new DiagnosisBlueprintSelection(blueprint, diagnosisSource, usedFallback, null, Collections.<String>emptyList(), null);
        }

        static DiagnosisBlueprintSelection failure(String message, List<String> reasonCodes, String diagnosisSource, String details) {
            return new DiagnosisBlueprintSelection(null, diagnosisSource, false, message, reasonCodes, details);
        }

        public DiagnosisBlueprint getBlueprint() {
            return blueprint;
        }

        public String getDiagnosisSource() {
            return diagnosisSource;
        }

        public boolean isUsedFallback() {
            return usedFallback;
        }

        public String getMessage() {
            if (hasText(details)) {
                return hasText(message) ? message + " " + details : details;
            }
            return message;
        }

        public List<String> getReasonCodes() {
            return reasonCodes;
        }

        public String getDetails() {
            return details;
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }
}
