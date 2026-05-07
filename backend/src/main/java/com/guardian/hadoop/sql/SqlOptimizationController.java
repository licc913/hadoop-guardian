package com.guardian.hadoop.sql;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql-optimization")
public class SqlOptimizationController {

    private final SqlOptimizationService optimizationService;

    public SqlOptimizationController(SqlOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @GetMapping("/history")
    public List<SqlOptimizationResult> listHistory() {
        return optimizationService.listHistory();
    }

    @GetMapping("/{recordId}")
    public SqlOptimizationResult getResult(@PathVariable long recordId) {
        return optimizationService.getResult(recordId);
    }

    @PostMapping("/analyze")
    public SqlOptimizationResult analyze(@RequestBody SqlOptimizationRequest request) {
        return optimizationService.analyze(request);
    }
}
