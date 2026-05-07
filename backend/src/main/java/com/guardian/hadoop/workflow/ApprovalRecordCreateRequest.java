package com.guardian.hadoop.workflow;

public class ApprovalRecordCreateRequest {

    private Long actionRecommendationId;
    private String approvalStatus;
    private String requestedBy;
    private String approver;
    private String comment;

    public Long getActionRecommendationId() {
        return actionRecommendationId;
    }

    public void setActionRecommendationId(Long actionRecommendationId) {
        this.actionRecommendationId = actionRecommendationId;
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
}
