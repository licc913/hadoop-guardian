package com.guardian.hadoop.workflow;

import com.guardian.hadoop.action.ActionRecommendationEntity;
import com.guardian.hadoop.incident.IncidentEntity;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "approval_record")
public class ApprovalRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "incident_id")
    private IncidentEntity incident;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action_recommendation_id")
    private ActionRecommendationEntity actionRecommendation;

    @Column(name = "approval_status", nullable = false, length = 32)
    private String approvalStatus;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(length = 128)
    private String approver;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public Long getId() {
        return id;
    }

    public IncidentEntity getIncident() {
        return incident;
    }

    public void setIncident(IncidentEntity incident) {
        this.incident = incident;
    }

    public ActionRecommendationEntity getActionRecommendation() {
        return actionRecommendation;
    }

    public void setActionRecommendation(ActionRecommendationEntity actionRecommendation) {
        this.actionRecommendation = actionRecommendation;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
