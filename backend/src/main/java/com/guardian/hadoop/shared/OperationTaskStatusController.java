package com.guardian.hadoop.shared;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OperationTaskStatusController {

    private final OperationTaskStatusService taskStatusService;

    public OperationTaskStatusController(OperationTaskStatusService taskStatusService) {
        this.taskStatusService = taskStatusService;
    }

    @GetMapping("/tasks")
    public OperationTaskStatusResponse getTasks() {
        return taskStatusService.getStatus();
    }
}
