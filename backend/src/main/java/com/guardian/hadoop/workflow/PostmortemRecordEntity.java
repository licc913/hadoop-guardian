package com.guardian.hadoop.workflow;

import com.guardian.hadoop.incident.IncidentEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

@Entity
@Table(name = "postmortem_record")
public class PostmortemRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "incident_id")
    private IncidentEntity incident;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "impact_statement", nullable = false, columnDefinition = "TEXT")
    private String impactStatement;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "postmortem_timeline_item", joinColumns = @JoinColumn(name = "postmortem_id"))
    @Column(name = "timeline_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> timeline = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "postmortem_prevention_item", joinColumns = @JoinColumn(name = "postmortem_id"))
    @Column(name = "prevention_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> preventionItems = new ArrayList<String>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public IncidentEntity getIncident() {
        return incident;
    }

    public void setIncident(IncidentEntity incident) {
        this.incident = incident;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getImpactStatement() {
        return impactStatement;
    }

    public void setImpactStatement(String impactStatement) {
        this.impactStatement = impactStatement;
    }

    public List<String> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<String> timeline) {
        this.timeline = timeline;
    }

    public List<String> getPreventionItems() {
        return preventionItems;
    }

    public void setPreventionItems(List<String> preventionItems) {
        this.preventionItems = preventionItems;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
