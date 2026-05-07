package com.guardian.hadoop.tuning;

public class ParameterRecommendationRecord {

    private final String configKey;
    private final String currentValue;
    private final String recommendedValue;
    private final String reason;

    public ParameterRecommendationRecord(String configKey,
                                         String currentValue,
                                         String recommendedValue,
                                         String reason) {
        this.configKey = configKey;
        this.currentValue = currentValue;
        this.recommendedValue = recommendedValue;
        this.reason = reason;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public String getRecommendedValue() {
        return recommendedValue;
    }

    public String getReason() {
        return reason;
    }
}
