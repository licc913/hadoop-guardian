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
@Table(name = "execution_record")
public class ExecutionRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "incident_id")
    private IncidentEntity incident;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action_recommendation_id")
    private ActionRecommendationEntity actionRecommendation;

    @Column(name = "execution_status", nullable = false, length = 32)
    private String executionStatus;

    @Column(nullable = false, length = 128)
    private String executor;

    @Column(name = "execution_summary", nullable = false, columnDefinition = "TEXT")
    private String executionSummary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getExecutionSummary() {
        return executionSummary;
    }

    public void setExecutionSummary(String executionSummary) {
        this.executionSummary = executionSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
