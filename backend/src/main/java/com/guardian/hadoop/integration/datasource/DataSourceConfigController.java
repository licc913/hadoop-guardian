package com.guardian.hadoop.integration.datasource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/datasources")
public class DataSourceConfigController {

    private final DataSourceConfigService dataSourceConfigService;
    private final JmxProbeService jmxProbeService;
    private final LogSourceConnectionTestService logSourceConnectionTestService;
    private final LlmConnectionTestService llmConnectionTestService;
    private final LlmPromptService llmPromptService;
    private final DiagnosticScriptValidationService diagnosticScriptValidationService;

    public DataSourceConfigController(DataSourceConfigService dataSourceConfigService,
                                      JmxProbeService jmxProbeService,
                                      LogSourceConnectionTestService logSourceConnectionTestService,
                                      LlmConnectionTestService llmConnectionTestService,
                                      LlmPromptService llmPromptService,
                                      DiagnosticScriptValidationService diagnosticScriptValidationService) {
        this.dataSourceConfigService = dataSourceConfigService;
        this.jmxProbeService = jmxProbeService;
        this.logSourceConnectionTestService = logSourceConnectionTestService;
        this.llmConnectionTestService = llmConnectionTestService;
        this.llmPromptService = llmPromptService;
        this.diagnosticScriptValidationService = diagnosticScriptValidationService;
    }

    @GetMapping
    public DataSourceConfigResponse getConfig() {
        return dataSourceConfigService.getConfig();
    }

    @PutMapping
    public DataSourceConfigResponse saveConfig(@RequestBody DataSourceConfigRequest request) {
        return dataSourceConfigService.saveConfig(request);
    }

    @PostMapping("/jmx/test")
    public JmxProbeResponse testJmxEndpoints() {
        return jmxProbeService.testAll();
    }

    @PostMapping("/log-source/test")
    public IntegrationTestResponse testLogSource() {
        return logSourceConnectionTestService.testConnection();
    }

    @PostMapping("/llm/test")
    public IntegrationTestResponse testLlm() {
        return llmConnectionTestService.testConnection();
    }

    @PostMapping("/llm/chat")
    public LlmPromptResponse chatWithLlm(@RequestBody LlmPromptRequest request) {
        return llmPromptService.ask(request.getQuestion());
    }

    @PostMapping("/scripts/test")
    public IntegrationTestResponse testDiagnosticScripts() {
        return diagnosticScriptValidationService.validateScripts();
    }
}
