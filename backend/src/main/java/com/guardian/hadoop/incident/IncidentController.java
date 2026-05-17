package com.guardian.hadoop.incident;

import com.guardian.hadoop.action.ActionRecommendationRecord;
import com.guardian.hadoop.diagnosis.DiagnosisRecord;
import com.guardian.hadoop.diagnosis.DiagnosisTaskRequest;
import com.guardian.hadoop.diagnosis.DiagnosisTaskResponse;
import com.guardian.hadoop.integration.cm.ClouderaManagerSyncResponse;
import com.guardian.hadoop.integration.cm.ClouderaManagerSyncService;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import com.guardian.hadoop.shared.DashboardSummary;
import com.guardian.hadoop.shared.GuardianDataService;
import com.guardian.hadoop.shared.SystemStatusResponse;
import com.guardian.hadoop.workflow.ApprovalRecord;
import com.guardian.hadoop.workflow.ApprovalRecordCreateRequest;
import com.guardian.hadoop.workflow.ExecutionRecord;
import com.guardian.hadoop.workflow.ExecutionRecordCreateRequest;
import com.guardian.hadoop.workflow.PostmortemRecord;
import com.guardian.hadoop.workflow.PostmortemUpsertRequest;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class IncidentController {

    private final GuardianDataService guardianDataService;
    private final ClouderaManagerSyncService clouderaManagerSyncService;
    private final KnowledgeSuggestionService knowledgeSuggestionService;

    public IncidentController(GuardianDataService guardianDataService,
                              ClouderaManagerSyncService clouderaManagerSyncService,
                              KnowledgeSuggestionService knowledgeSuggestionService) {
        this.guardianDataService = guardianDataService;
        this.clouderaManagerSyncService = clouderaManagerSyncService;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary getDashboardSummary() {
        return guardianDataService.getSummary();
    }

    @GetMapping("/system/status")
    public SystemStatusResponse getSystemStatus() {
        return guardianDataService.getSystemStatus();
    }

    @GetMapping("/incidents")
    public List<IncidentRecord> getIncidents() {
        return guardianDataService.getIncidents();
    }

    @GetMapping("/incidents/{incidentId}")
    public IncidentRecord getIncident(@PathVariable long incidentId) {
        IncidentRecord incident = guardianDataService.getIncident(incidentId);
        if (incident == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return incident;
    }

    @GetMapping("/incidents/{incidentId}/diagnoses")
    public List<DiagnosisRecord> getIncidentDiagnoses(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return guardianDataService.getDiagnoses(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/actions")
    public List<ActionRecommendationRecord> getIncidentActions(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return guardianDataService.getActionRecommendations(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/knowledge-suggestions")
    public List<KnowledgeSuggestionRecord> getIncidentKnowledgeSuggestions(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return knowledgeSuggestionService.getSuggestions(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/service-logs")
    public List<CmServiceLogSnapshotRecord> getIncidentServiceLogs(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return guardianDataService.getIncidentServiceLogs(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/approvals")
    public List<ApprovalRecord> getIncidentApprovals(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return guardianDataService.getApprovals(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/executions")
    public List<ExecutionRecord> getIncidentExecutions(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        return guardianDataService.getExecutions(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/postmortem")
    public PostmortemRecord getIncidentPostmortem(@PathVariable long incidentId) {
        ensureIncidentExists(incidentId);
        PostmortemRecord record = guardianDataService.getPostmortem(incidentId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postmortem not found");
        }
        return record;
    }

    @PostMapping("/incidents/{incidentId}/diagnosis-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosisTaskResponse createDiagnosisTask(@PathVariable long incidentId,
                                                     @Valid @RequestBody DiagnosisTaskRequest request) {
        DiagnosisTaskResponse record = guardianDataService.createDiagnosis(
            incidentId,
            request.getTriggerBy(),
            request.getTriggerReason(),
            request.getDiagnosisMode()
        );
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return record;
    }

    @PostMapping("/incidents/{incidentId}/close")
    public IncidentCloseResponse closeIncident(@PathVariable long incidentId,
                                               @RequestBody(required = false) IncidentCloseRequest request) {
        IncidentCloseResponse response = guardianDataService.closeIncident(
            incidentId,
            request == null ? null : request.getClosedBy(),
            request == null ? null : request.getCloseReason()
        );
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return response;
    }

    @PostMapping("/incidents/{incidentId}/suppress")
    public IncidentGovernanceResponse suppressIncident(@PathVariable long incidentId,
                                                       @RequestBody(required = false) IncidentGovernanceRequest request) {
        IncidentGovernanceResponse response = guardianDataService.suppressIncident(
            incidentId,
            request == null ? null : request.getOperator(),
            request == null ? null : request.getNote(),
            request == null ? null : request.getSuppressMinutes()
        );
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return response;
    }

    @PostMapping("/incidents/{incidentId}/resume")
    public IncidentGovernanceResponse resumeIncident(@PathVariable long incidentId,
                                                     @RequestBody(required = false) IncidentGovernanceRequest request) {
        IncidentGovernanceResponse response = guardianDataService.resumeIncident(
            incidentId,
            request == null ? null : request.getOperator(),
            request == null ? null : request.getNote()
        );
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return response;
    }

    @PostMapping("/incidents/{incidentId}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalRecord createApprovalRecord(@PathVariable long incidentId,
                                               @RequestBody ApprovalRecordCreateRequest request) {
        ApprovalRecord record = guardianDataService.createApprovalRecord(incidentId, request);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident or action recommendation not found");
        }
        return record;
    }

    @PostMapping("/incidents/{incidentId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionRecord createExecutionRecord(@PathVariable long incidentId,
                                                 @RequestBody ExecutionRecordCreateRequest request) {
        ExecutionRecord record = guardianDataService.createExecutionRecord(incidentId, request);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident or action recommendation not found");
        }
        return record;
    }

    @PostMapping("/incidents/{incidentId}/postmortem")
    public PostmortemRecord upsertPostmortem(@PathVariable long incidentId,
                                             @RequestBody PostmortemUpsertRequest request) {
        PostmortemRecord record = guardianDataService.upsertPostmortem(incidentId, request);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return record;
    }

    @PostMapping("/integrations/cloudera-manager/sync-alerts")
    public ClouderaManagerSyncResponse syncClouderaManagerAlerts() {
        try {
            return clouderaManagerSyncService.syncAlerts();
        } catch (Exception exception) {
            return new ClouderaManagerSyncResponse(
                false,
                true,
                0,
                0,
                0,
                "Cloudera Manager 告警同步失败。",
                exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error"),
                java.time.Instant.now(),
                "",
                java.util.Collections.emptyList()
            );
        }
    }

    private void ensureIncidentExists(long incidentId) {
        if (guardianDataService.getIncident(incidentId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
