package com.guardian.hadoop.workflow;

import java.time.Instant;

public class ApprovalRecord {

    private final Long id;
    private final Long incidentId;
    private final Long actionRecommendationId;
    private final String approvalStatus;
    private final String requestedBy;
    private final String approver;
    private final String comment;
    private final Instant requestedAt;
    private final Instant decidedAt;

    public ApprovalRecord(Long id, Long incidentId, Long actionRecommendationId, String approvalStatus,
                          String requestedBy, String approver, String comment, Instant requestedAt, Instant decidedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.actionRecommendationId = actionRecommendationId;
        this.approvalStatus = approvalStatus;
        this.requestedBy = requestedBy;
        this.approver = approver;
        this.comment = comment;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
    }

    public static ApprovalRecord fromEntity(ApprovalRecordEntity entity) {
        return new ApprovalRecord(
            entity.getId(),
            entity.getIncident().getId(),
            entity.getActionRecommendation().getId(),
            entity.getApprovalStatus(),
            entity.getRequestedBy(),
            entity.getApprover(),
            entity.getComment(),
            entity.getRequestedAt(),
            entity.getDecidedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getActionRecommendationId() {
        return actionRecommendationId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getApprover() {
        return approver;
    }

    public String getComment() {
        return comment;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
