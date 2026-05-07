package com.guardian.hadoop.tuning;

public class ParameterConfigEntryRecord {

    private final String scopeType;
    private final String scopeName;
    private final String roleType;
    private final String configKey;
    private final String configValue;
    private final String valueSource;

    public ParameterConfigEntryRecord(String scopeType,
                                      String scopeName,
                                      String roleType,
                                      String configKey,
                                      String configValue,
                                      String valueSource) {
        this.scopeType = scopeType;
        this.scopeName = scopeName;
        this.roleType = roleType;
        this.configKey = configKey;
        this.configValue = configValue;
        this.valueSource = valueSource;
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeName() {
        return scopeName;
    }

    public String getRoleType() {
        return roleType;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public String getValueSource() {
        return valueSource;
    }
}
