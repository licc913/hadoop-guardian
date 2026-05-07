package com.guardian.hadoop.incident;

public class IncidentGovernanceRequest {

    private String operator;
    private String note;
    private Integer suppressMinutes;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getSuppressMinutes() {
        return suppressMinutes;
    }

    public void setSuppressMinutes(Integer suppressMinutes) {
        this.suppressMinutes = suppressMinutes;
    }
}
