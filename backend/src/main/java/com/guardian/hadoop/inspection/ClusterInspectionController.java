package com.guardian.hadoop.inspection;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspections")
public class ClusterInspectionController {

    private final ClusterInspectionService inspectionService;

    public ClusterInspectionController(ClusterInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    public List<ClusterInspectionReport> listReports() {
        return inspectionService.listReports();
    }

    @GetMapping("/{reportId}")
    public ClusterInspectionReport getReport(@PathVariable long reportId) {
        return inspectionService.getReport(reportId);
    }

    @PostMapping
    public ClusterInspectionReport createReport(@RequestBody(required = false) ClusterInspectionRequest request) {
        return inspectionService.createReport(request == null ? null : request.getTriggeredBy());
    }

    @GetMapping("/{reportId}/export/docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable long reportId) {
        ClusterInspectionReport report = inspectionService.getReport(reportId);
        byte[] bytes = inspectionService.exportDocx(reportId);
        String filename = sanitizeFileName(report.getReportTitle()) + ".docx";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFileName(filename))
            .body(bytes);
    }

    private String sanitizeFileName(String value) {
        String safe = value == null ? "cluster-inspection-report" : value.trim();
        return safe.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String encodeFileName(String fileName) {
        byte[] bytes = fileName.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z')
                || (unsigned >= 'A' && unsigned <= 'Z')
                || (unsigned >= '0' && unsigned <= '9')
                || unsigned == '.'
                || unsigned == '_'
                || unsigned == '-') {
                builder.append((char) unsigned);
            } else {
                builder.append('%');
                String hex = Integer.toHexString(unsigned).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
        }
        return builder.toString();
    }
}
