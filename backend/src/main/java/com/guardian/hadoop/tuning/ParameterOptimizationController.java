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

    public ParameterOptimizationController(ParameterOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
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
    public ParameterOptimizationContextPreview getCurrentContext(@RequestParam String serviceType) {
        return optimizationService.getCurrentContext(serviceType);
    }

    @PostMapping("/analyze")
    public ParameterOptimizationResult analyze(@RequestBody ParameterOptimizationRequest request) {
        return optimizationService.analyze(request);
    }
}
