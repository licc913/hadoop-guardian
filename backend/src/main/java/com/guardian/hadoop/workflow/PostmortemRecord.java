package com.guardian.hadoop.workflow;

import java.time.Instant;
import java.util.List;

public class PostmortemRecord {

    private final Long id;
    private final Long incidentId;
    private final String summary;
    private final String rootCause;
    private final String impactStatement;
    private final List<String> timeline;
    private final List<String> preventionItems;
    private final Instant updatedAt;

    public PostmortemRecord(Long id, Long incidentId, String summary, String rootCause, String impactStatement,
                            List<String> timeline, List<String> preventionItems, Instant updatedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.summary = summary;
        this.rootCause = rootCause;
        this.impactStatement = impactStatement;
        this.timeline = timeline;
        this.preventionItems = preventionItems;
        this.updatedAt = updatedAt;
    }

    public static PostmortemRecord fromEntity(PostmortemRecordEntity entity) {
        return new PostmortemRecord(
            entity.getId(),
            entity.getIncident().getId(),
            entity.getSummary(),
            entity.getRootCause(),
            entity.getImpactStatement(),
            entity.getTimeline(),
            entity.getPreventionItems(),
            entity.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getImpactStatement() {
        return impactStatement;
    }

    public List<String> getTimeline() {
        return timeline;
    }

    public List<String> getPreventionItems() {
        return preventionItems;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
