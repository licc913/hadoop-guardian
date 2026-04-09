package com.guardian.hadoop.integration.datasource;

import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsResponse;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsPayload;
import java.util.List;

public class DataSourceConfigResponse {

    private final ClouderaManagerSettingsResponse clouderaManager;
    private final LogSourceSettingsPayload logSource;
    private final DiagnosisLlmSettingsPayload llm;
    private final List<JmxEndpointPayload> jmxEndpoints;
    private final List<DiagnosticScriptPayload> diagnosticScripts;

    public DataSourceConfigResponse(ClouderaManagerSettingsResponse clouderaManager,
                                    LogSourceSettingsPayload logSource,
                                    DiagnosisLlmSettingsPayload llm,
                                    List<JmxEndpointPayload> jmxEndpoints,
                                    List<DiagnosticScriptPayload> diagnosticScripts) {
        this.clouderaManager = clouderaManager;
        this.logSource = logSource;
        this.llm = llm;
        this.jmxEndpoints = jmxEndpoints;
        this.diagnosticScripts = diagnosticScripts;
    }

    public ClouderaManagerSettingsResponse getClouderaManager() {
        return clouderaManager;
    }

    public LogSourceSettingsPayload getLogSource() {
        return logSource;
    }

    public DiagnosisLlmSettingsPayload getLlm() {
        return llm;
    }

    public List<JmxEndpointPayload> getJmxEndpoints() {
        return jmxEndpoints;
    }

    public List<DiagnosticScriptPayload> getDiagnosticScripts() {
        return diagnosticScripts;
    }
}
