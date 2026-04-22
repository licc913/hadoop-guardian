package com.guardian.hadoop.inspection;

import java.time.Instant;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusterInspectionJobService {

    private final ClusterInspectionReportRepository repository;
    private final ClusterInspectionService inspectionService;

    public ClusterInspectionJobService(ClusterInspectionReportRepository repository,
                                       @Lazy ClusterInspectionService inspectionService) {
        this.repository = repository;
        this.inspectionService = inspectionService;
    }

    @Async
    public void generateReportAsync(long reportId) {
        ClusterInspectionReportEntity entity = repository.findById(reportId).orElse(null);
        if (entity == null) {
            return;
        }
        try {
            markReportRunning(reportId);
            inspectionService.generateReportContent(entity);
        } catch (Exception exception) {
            failReport(reportId, exception);
        }
    }

    @Transactional
    protected void markReportRunning(long reportId) {
        ClusterInspectionReportEntity entity = repository.findById(reportId).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setStatus("RUNNING");
        entity.setSummary("巡检报告正在生成，系统正在分章节调用大模型并汇总结果。");
        repository.save(entity);
    }

    @Transactional
    protected void failReport(long reportId, Exception exception) {
        ClusterInspectionReportEntity entity = repository.findById(reportId).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setStatus("FAILED");
        entity.setCompletedAt(Instant.now());
        String message = exception.getMessage();
        if (message != null && message.length() > 2000) {
            message = message.substring(0, 2000);
        }
        entity.setErrorMessage(message);
        if (entity.getMarkdownContent() == null || entity.getMarkdownContent().trim().isEmpty()) {
            entity.setMarkdownContent("巡检报告生成失败，请检查大模型配置、CM 采集链路和后端日志。");
        }
        repository.save(entity);
    }
}
