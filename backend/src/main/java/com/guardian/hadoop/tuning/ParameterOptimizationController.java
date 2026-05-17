package com.guardian.hadoop.tuning;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parameter-optimization")
public class ParameterOptimizationController {

    private final ParameterOptimizationService optimizationService;
    private final ParameterOptimizationTaskService taskService;
    private final CmConfigCheckService cmConfigCheckService;

    public ParameterOptimizationController(ParameterOptimizationService optimizationService,
                                           ParameterOptimizationTaskService taskService,
                                           CmConfigCheckService cmConfigCheckService) {
        this.optimizationService = optimizationService;
        this.taskService = taskService;
        this.cmConfigCheckService = cmConfigCheckService;
    }

    @GetMapping("/history")
    public List<ParameterOptimizationResult> listHistory() {
        return optimizationService.listHistory();
    }

    @GetMapping("/{recordId}")
    public ParameterOptimizationResult getResult(@PathVariable long recordId) {
        return optimizationService.getResult(recordId);
    }

    @GetMapping("/context")
    public ParameterOptimizationContextPreview getCurrentContext(@RequestParam String serviceType,
                                                                 @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return optimizationService.getCurrentContext(serviceType, forceRefresh);
    }

    @GetMapping("/cm-config-check")
    public CmConfigCheckResponse checkCmConfig(@RequestParam String serviceType) {
        return cmConfigCheckService.check(serviceType);
    }

    @PostMapping("/analyze")
    public ParameterOptimizationResult analyze(@RequestBody ParameterOptimizationRequest request) {
        return optimizationService.analyze(request);
    }

    @PostMapping("/tasks")
    public ParameterOptimizationTaskResponse startAnalyzeTask(@RequestBody ParameterOptimizationRequest request) {
        return taskService.start(request);
    }

    @GetMapping("/tasks/{taskId}")
    public ParameterOptimizationTaskResponse getAnalyzeTask(@PathVariable String taskId) {
        return taskService.get(taskId);
    }
}
