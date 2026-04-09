package com.guardian.hadoop.integration.cm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiAlertItem {

    private String id;
    private String content;
    private String severity;
    private String category;
    private boolean alert;
    private String timeOccurred;
    private JsonNode attributes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isAlert() {
        return alert;
    }

    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    public String getTimeOccurred() {
        return timeOccurred;
    }

    public void setTimeOccurred(String timeOccurred) {
        this.timeOccurred = timeOccurred;
    }

    public JsonNode getAttributes() {
        return attributes;
    }

    public void setAttributes(JsonNode attributes) {
        this.attributes = attributes;
    }

    @JsonProperty("timeOccurred")
    public void setTimeOccurredFromJson(String timeOccurred) {
        this.timeOccurred = timeOccurred;
    }

    public String getSummary() {
        return content;
    }

    public String getEventTime() {
        return timeOccurred;
    }

    public String getServiceName() {
        return firstNonBlank(
            findAttributeValue("serviceName", "serviceType", "serviceDisplayName", "service"),
            category
        );
    }

    public String getHostName() {
        return findAttributeValue("hostName", "host", "hostname", "roleHost");
    }

    public String getRoleName() {
        return firstNonBlank(
            findAttributeValue("roleName", "role", "roleDisplayName", "roleType"),
            findAttributeValue("testName", "healthTestName")
        );
    }

    public String getEntityName() {
        return firstNonBlank(
            findAttributeValue("entityName", "entityDisplayName", "displayName"),
            findAttributeValue("impalaPool", "poolName", "queueName")
        );
    }

    public String getName() {
        String service = getServiceName();
        String summary = firstNonBlank(content, category, "Cloudera Manager 告警");
        if (summary.length() > 72) {
            summary = summary.substring(0, 72) + "...";
        }
        return isBlank(service) ? summary : service + " 告警";
    }

    public List<String> getAttributeHighlights() {
        List<String> highlights = new ArrayList<String>();
        appendHighlight(highlights, "健康项", findAttributeValue("healthTestName", "testName"));
        appendHighlight(highlights, "角色类型", findAttributeValue("roleType"));
        appendHighlight(highlights, "角色实例", findAttributeValue("roleName", "roleDisplayName"));
        appendHighlight(highlights, "主机", findAttributeValue("hostName", "host", "hostname"));
        appendHighlight(highlights, "服务", findAttributeValue("serviceName", "serviceType", "serviceDisplayName"));
        appendHighlight(highlights, "队列", findAttributeValue("queueName", "poolName", "impalaPool"));
        appendHighlight(highlights, "当前值", findAttributeValue("actualValue", "currentValue", "value"));
        appendHighlight(highlights, "阈值", findAttributeValue("threshold", "criticalThreshold", "warningThreshold"));
        appendHighlight(highlights, "实体", findAttributeValue("entityName", "entityDisplayName", "displayName"));
        return highlights;
    }

    public String getSemanticFingerprint() {
        return String.join("|",
            safe(getServiceName()).toUpperCase(),
            safe(getCategory()).toUpperCase(),
            safe(getRoleName()).toUpperCase(),
            safe(getEntityName()).toUpperCase(),
            normalizeContent(getContent()).toUpperCase()
        );
    }

    private String findAttributeValue(String... keys) {
        if (attributes == null || attributes.isNull()) {
            return "";
        }

        if (attributes.isObject()) {
            for (String key : keys) {
                String value = textValue(attributes.get(key));
                if (!isBlank(value)) {
                    return value;
                }
            }
        }

        if (attributes.isArray()) {
            for (JsonNode item : attributes) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String itemName = firstNonBlank(
                    textValue(item.get("name")),
                    textValue(item.get("key")),
                    textValue(item.get("attributeName")),
                    textValue(item.get("attribute"))
                );
                if (isBlank(itemName) || !matchesAny(itemName, keys)) {
                    continue;
                }
                String value = firstNonBlank(
                    textValue(item.get("value")),
                    textValue(item.get("displayValue")),
                    textValue(item.get("values"))
                );
                if (!isBlank(value)) {
                    return value;
                }
            }
        }

        return "";
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                String text = textValue(item);
                if (isBlank(text)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(text);
            }
            return builder.toString();
        }
        return "";
    }

    private void appendHighlight(List<String> highlights, String label, String value) {
        if (!isBlank(value)) {
            highlights.add(label + "：" + value.trim());
        }
    }

    private boolean matchesAny(String value, String... keys) {
        for (String key : keys) {
            if (key.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeContent(String value) {
        return safe(value)
            .replaceAll("\\s+", " ")
            .replaceAll("[0-9]+(?:\\.[0-9]+)?", "#")
            .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
