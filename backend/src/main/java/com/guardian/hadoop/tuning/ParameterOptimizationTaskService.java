package com.guardian.hadoop.tuning;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParameterOptimizationTaskService {

    private final ParameterOptimizationService optimizationService;
    private final ParameterOptimizationTaskRepository taskRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public ParameterOptimizationTaskService(ParameterOptimizationService optimizationService,
                                            ParameterOptimizationTaskRepository taskRepository,
                                            JdbcTemplate jdbcTemplate) {
        this.optimizationService = optimizationService;
        this.taskRepository = taskRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists parameter_optimization_task ("
                + "task_id varchar(64) primary key,"
                + "status varchar(32) not null,"
                + "message text,"
                + "result_id bigint,"
                + "error_message text,"
                + "request_summary text,"
                + "created_at timestamp not null,"
                + "updated_at timestamp not null"
                + ")"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_parameter_optimization_task_updated_at "
                + "on parameter_optimization_task(updated_at desc)"
        );
    }

    @Transactional
    public ParameterOptimizationTaskResponse start(ParameterOptimizationRequest request) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ParameterOptimizationTaskEntity task = new ParameterOptimizationTaskEntity();
        task.setTaskId(taskId);
        task.setStatus(ParameterOptimizationTaskStatus.RUNNING.name());
        task.setMessage("任务已创建，正在等待后台执行。");
        task.setRequestSummary(buildRequestSummary(request));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);
        CompletableFuture.runAsync(() -> runTask(taskId, request), executor);
        return toResponse(task, null);
    }

    @Transactional(readOnly = true)
    public ParameterOptimizationTaskResponse get(String taskId) {
        ParameterOptimizationTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            Instant now = Instant.now();
            return new ParameterOptimizationTaskResponse(
                taskId,
                ParameterOptimizationTaskStatus.FAILED,
                "任务不存在，可能尚未创建或已被清理。",
                null,
                null,
                "task not found",
                now,
                now
            );
        }
        ParameterOptimizationResult result = task.getResultId() == null ? null : optimizationService.getResult(task.getResultId());
        return toResponse(task, result);
    }

    private void runTask(String taskId, ParameterOptimizationRequest request) {
        updateTask(taskId, ParameterOptimizationTaskStatus.RUNNING, "正在采集 CM 配置并调用大模型分析。", null, null);
        try {
            ParameterOptimizationResult result = optimizationService.analyze(request);
            updateTask(taskId, ParameterOptimizationTaskStatus.COMPLETED, "参数优化分析完成。", result.getId(), null);
        } catch (Exception exception) {
            updateTask(
                taskId,
                ParameterOptimizationTaskStatus.FAILED,
                "参数优化分析失败。",
                null,
                exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error")
            );
        }
    }

    @Transactional
    public void updateTask(String taskId,
                           ParameterOptimizationTaskStatus status,
                           String message,
                           Long resultId,
                           String errorMessage) {
        ParameterOptimizationTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(status.name());
        task.setMessage(message);
        task.setResultId(resultId);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
    }

    private ParameterOptimizationTaskResponse toResponse(ParameterOptimizationTaskEntity task,
                                                         ParameterOptimizationResult result) {
        return new ParameterOptimizationTaskResponse(
            task.getTaskId(),
            parseStatus(task.getStatus()),
            defaultIfBlank(task.getMessage(), ""),
            task.getResultId(),
            result,
            task.getErrorMessage(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }

    private ParameterOptimizationTaskStatus parseStatus(String status) {
        try {
            return ParameterOptimizationTaskStatus.valueOf(defaultIfBlank(status, "FAILED"));
        } catch (Exception exception) {
            return ParameterOptimizationTaskStatus.FAILED;
        }
    }

    private String buildRequestSummary(ParameterOptimizationRequest request) {
        if (request == null) {
            return "";
        }
        return "serviceType=" + defaultIfBlank(request.getServiceType(), "")
            + "\nuseCurrentClusterConfig=" + request.isUseCurrentClusterConfig()
            + "\noptimizationGoal=" + defaultIfBlank(request.getOptimizationGoal(), "")
            + "\ncurrentSymptoms=" + defaultIfBlank(request.getCurrentSymptoms(), "");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
