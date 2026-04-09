package com.guardian.hadoop.integration.datasource;

import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsRequest;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsPayload;
import java.util.ArrayList;
import java.util.List;

public class DataSourceConfigRequest {

    private ClouderaManagerSettingsRequest clouderaManager;
    private LogSourceSettingsPayload logSource;
    private DiagnosisLlmSettingsPayload llm;
    private List<JmxEndpointPayload> jmxEndpoints = new ArrayList<JmxEndpointPayload>();
    private List<DiagnosticScriptPayload> diagnosticScripts = new ArrayList<DiagnosticScriptPayload>();

    public ClouderaManagerSettingsRequest getClouderaManager() {
        return clouderaManager;
    }

    public void setClouderaManager(ClouderaManagerSettingsRequest clouderaManager) {
        this.clouderaManager = clouderaManager;
    }

    public LogSourceSettingsPayload getLogSource() {
        return logSource;
    }

    public void setLogSource(LogSourceSettingsPayload logSource) {
        this.logSource = logSource;
    }

    public DiagnosisLlmSettingsPayload getLlm() {
        return llm;
    }

    public void setLlm(DiagnosisLlmSettingsPayload llm) {
        this.llm = llm;
    }

    public List<JmxEndpointPayload> getJmxEndpoints() {
        return jmxEndpoints;
    }

    public void setJmxEndpoints(List<JmxEndpointPayload> jmxEndpoints) {
        this.jmxEndpoints = jmxEndpoints;
    }

    public List<DiagnosticScriptPayload> getDiagnosticScripts() {
        return diagnosticScripts;
    }

    public void setDiagnosticScripts(List<DiagnosticScriptPayload> diagnosticScripts) {
        this.diagnosticScripts = diagnosticScripts;
    }
}
