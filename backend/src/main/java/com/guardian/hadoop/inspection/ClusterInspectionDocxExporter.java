package com.guardian.hadoop.inspection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class ClusterInspectionDocxExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    public byte[] export(ClusterInspectionReport report) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, report.getReportTitle());
            addMetadata(document, report);
            addSummary(document, report.getSummary());
            addMarkdown(document, report.getMarkdownContent());
            document.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("DOCX 导出失败", ex);
        }
    }

    private void addTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(240);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(22);
        run.setText(title);

        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        subtitle.setSpacingAfter(200);
        XWPFRun subRun = subtitle.createRun();
        subRun.setColor("666666");
        subRun.setFontSize(10);
        subRun.setText("Hadoop Guardian 集群巡检输出");
    }

    private void addMetadata(XWPFDocument document, ClusterInspectionReport report) {
        XWPFTable table = document.createTable(4, 2);
        table.setWidth("100%");
        table.getRow(0).getCell(0).setText("集群");
        table.getRow(0).getCell(1).setText(defaultIfBlank(report.getClusterName(), "UNKNOWN"));
        table.getRow(1).getCell(0).setText("总体风险");
        table.getRow(1).getCell(1).setText(defaultIfBlank(report.getOverallRisk(), "MEDIUM"));
        table.getRow(2).getCell(0).setText("大模型");
        table.getRow(2).getCell(1).setText(defaultIfBlank(report.getLlmModel(), "未记录"));
        table.getRow(3).getCell(0).setText("生成时间");
        table.getRow(3).getCell(1).setText(report.getCreatedAt() == null ? "-" : DATE_TIME_FORMATTER.format(report.getCreatedAt()));
        for (int index = 0; index < 4; index++) {
            styleMetadataRow(table.getRow(index));
        }
    }

    private void addSummary(XWPFDocument document, String summary) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        heading.setSpacingAfter(80);
        XWPFRun headingRun = heading.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(14);
        headingRun.setText("巡检结论");

        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(defaultIfBlank(summary, "无"));
    }

    private void addMarkdown(XWPFDocument document, String markdown) {
        String[] lines = markdown == null ? new String[0] : markdown.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                document.createParagraph();
                continue;
            }
            if (line.startsWith("# ")) {
                addHeading(document, line.substring(2).trim(), 18);
            } else if (line.startsWith("## ")) {
                addHeading(document, line.substring(3).trim(), 15);
            } else if (line.startsWith("### ")) {
                addHeading(document, line.substring(4).trim(), 13);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                addBullet(document, line.substring(2).trim());
            } else {
                addParagraph(document, line);
            }
        }
    }

    private void addHeading(XWPFDocument document, String text, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(fontSize);
        run.setText(text);
    }

    private void addBullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(360);
        paragraph.setSpacingAfter(40);
        XWPFRun run = paragraph.createRun();
        run.setText("• " + text);
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    private void styleMetadataRow(XWPFTableRow row) {
        if (row == null || row.getTableCells().size() < 2) {
            return;
        }
        row.getCell(0).setColor("EFF3F8");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
