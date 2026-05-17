package com.guardian.hadoop.shared;

import com.guardian.hadoop.inspection.ClusterInspectionReportEntity;
import com.guardian.hadoop.inspection.ClusterInspectionReportRepository;
import com.guardian.hadoop.integration.cm.CmLogCollectionStatusResponse;
import com.guardian.hadoop.integration.cm.CmServiceLogCollectionScheduler;
import com.guardian.hadoop.tuning.ParameterOptimizationTaskEntity;
import com.guardian.hadoop.tuning.ParameterOptimizationTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationTaskStatusService {

    private final CmServiceLogCollectionScheduler logCollectionScheduler;
    private final ClusterInspectionReportRepository inspectionRepository;
    private final ParameterOptimizationTaskRepository parameterTaskRepository;

    public OperationTaskStatusService(CmServiceLogCollectionScheduler logCollectionScheduler,
                                      ClusterInspectionReportRepository inspectionRepository,
                                      ParameterOptimizationTaskRepository parameterTaskRepository) {
        this.logCollectionScheduler = logCollectionScheduler;
        this.inspectionRepository = inspectionRepository;
        this.parameterTaskRepository = parameterTaskRepository;
    }

    public OperationTaskStatusResponse getStatus() {
        List<OperationTaskStatusItem> tasks = new ArrayList<OperationTaskStatusItem>();
        tasks.add(toLogCollectionTask(logCollectionScheduler.getStatus()));
        for (ClusterInspectionReportEntity report : inspectionRepository.findTop10ByOrderByCreatedAtDescIdDesc()) {
            tasks.add(toInspectionTask(report));
        }
        for (ParameterOptimizationTaskEntity task : parameterTaskRepository.findTop10ByOrderByUpdatedAtDesc()) {
            tasks.add(toParameterTask(task));
        }
        tasks.sort(Comparator.comparing(OperationTaskStatusItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int runningCount = (int) tasks.stream().filter(this::isRunning).count();
        int failedCount = (int) tasks.stream().filter(this::isFailed).count();
        return new OperationTaskStatusResponse(
            Instant.now(),
            runningCount,
            failedCount,
            tasks.size(),
            tasks.subList(0, Math.min(tasks.size(), 20))
        );
    }

    private OperationTaskStatusItem toLogCollectionTask(CmLogCollectionStatusResponse status) {
        String taskStatus = status.isRunning() ? "RUNNING" : (status.isLastSuccess() ? "COMPLETED" : "FAILED");
        return new OperationTaskStatusItem(
            "CM_LOG_COLLECTION",
            "cm-log-collection",
            taskStatus,
            "CM 服务日志采集",
            defaultIfBlank(status.getLastMessage(), "尚未产生采集结果。"),
            status.getLastStartedAt(),
            status.getLastFinishedAt() == null ? status.getLastStartedAt() : status.getLastFinishedAt(),
            status.getLastDurationMs()
        );
    }

    private OperationTaskStatusItem toInspectionTask(ClusterInspectionReportEntity report) {
        return new OperationTaskStatusItem(
            "CLUSTER_INSPECTION",
            String.valueOf(report.getId()),
            report.getStatus(),
            report.getReportTitle(),
            defaultIfBlank(report.getErrorMessage(), report.getSummary()),
            report.getCreatedAt(),
            report.getCompletedAt() == null ? report.getCreatedAt() : report.getCompletedAt(),
            durationMs(report.getCreatedAt(), report.getCompletedAt())
        );
    }

    private OperationTaskStatusItem toParameterTask(ParameterOptimizationTaskEntity task) {
        return new OperationTaskStatusItem(
            "PARAMETER_OPTIMIZATION",
            task.getTaskId(),
            task.getStatus(),
            "参数优化任务",
            defaultIfBlank(task.getErrorMessage(), task.getMessage()),
            task.getCreatedAt(),
            task.getUpdatedAt(),
            durationMs(task.getCreatedAt(), task.getUpdatedAt())
        );
    }

    private boolean isRunning(OperationTaskStatusItem task) {
        String status = task.getStatus();
        return "RUNNING".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status);
    }

    private boolean isFailed(OperationTaskStatusItem task) {
        return "FAILED".equalsIgnoreCase(task.getStatus());
    }

    private Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
