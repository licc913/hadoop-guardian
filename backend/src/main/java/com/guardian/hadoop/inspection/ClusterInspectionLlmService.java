package com.guardian.hadoop.inspection;

import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClusterInspectionLlmService {

    private static final String REPORT_TITLE = "# 集群巡检报告";

    private final DiagnosisLlmSettingsService settingsService;

    public ClusterInspectionLlmService(DiagnosisLlmSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public ClusterInspectionLlmResult generateReport(String clusterName, String inspectionContext) {
        return generateReport(clusterName, inspectionContext, null);
    }

    public ClusterInspectionLlmResult generateReport(String clusterName,
                                                     String inspectionContext,
                                                     SectionProgressListener progressListener) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        if (!settings.isEnabled() || !hasText(settings.getEndpoint()) || !hasText(settings.getApiKey()) || !hasText(settings.getModel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大模型配置不完整，无法生成巡检报告");
        }

        try {
            List<InspectionChunk> chunks = buildChunks(inspectionContext);
            StringBuilder markdown = new StringBuilder();
            markdown.append(REPORT_TITLE).append("\n\n");
            int completed = 0;
            int total = chunks.size();
            for (InspectionChunk chunk : chunks) {
                String sectionBody = generateSection(settings, clusterName, chunk);
                if (!hasText(sectionBody)) {
                    continue;
                }
                if (markdown.length() > REPORT_TITLE.length() + 2) {
                    markdown.append("\n\n");
                }
                markdown.append(sectionBody.trim());
                completed++;
                if (progressListener != null) {
                    progressListener.onSectionGenerated(completed, total, chunk.sectionTitle, markdown.toString().trim());
                }
            }

            SummaryEnvelope envelope = generateSummary(settings, clusterName, markdown.toString());
            return new ClusterInspectionLlmResult(
                envelope.overallRisk,
                envelope.summary,
                markdown.toString().trim(),
                settings.getModel()
            );
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "巡检报告生成失败，HTTP " + ex.getRawStatusCode() + " " + ex.getStatusText()
            );
        } catch (ResourceAccessException ex) {
            Throwable root = ex.getMostSpecificCause();
            if (root instanceof SocketTimeoutException) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "巡检报告分段生成超时，请提高大模型读取超时或缩小巡检范围");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, defaultIfBlank(ex.getMessage(), "巡检报告生成失败"));
        }
    }

    private String generateSection(DiagnosisLlmSettingsEntity settings, String clusterName, InspectionChunk chunk) {
        String prompt = "目标集群: " + clusterName
            + "\n当前只生成以下章节，不要输出其他章节，不要输出封面或总结。"
            + "\n章节标题: " + chunk.sectionTitle
            + "\n输出要求:"
            + "\n1. 只输出 Markdown 正文，且第一行必须是 " + chunk.sectionTitle
            + "\n2. 只能基于输入证据撰写，不能虚构节点、日志、指标或风险"
            + "\n3. 结论必须绑定节点、角色、日志、状态或指标证据"
            + "\n4. 如果本章节没有明显风险，明确写出“未发现明显风险”及依据"
            + "\n5. 建议必须可执行，避免空泛表述"
            + "\n\n章节上下文:\n" + chunk.context;
        return invokeForMarkdown(settings, buildSectionMessages(prompt));
    }

    private SummaryEnvelope generateSummary(DiagnosisLlmSettingsEntity settings, String clusterName, String markdown) {
        String prompt = "目标集群: " + clusterName
            + "\n请基于以下巡检 Markdown 正文，输出两行纯文本，不要输出其他内容："
            + "\n巡检结论: <一句话总结>"
            + "\n总体风险: <LOW|MEDIUM|HIGH|CRITICAL>"
            + "\n要求：必须使用简体中文；结论必须概括最核心的服务或节点风险。\n\n"
            + markdown;
        String answer = invokeForMarkdown(settings, buildSummaryMessages(prompt));
        return parseSummaryEnvelope(answer, markdown);
    }

    private String invokeForMarkdown(DiagnosisLlmSettingsEntity settings, List<Map<String, String>> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(settings.getApiKey().trim());

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", settings.getModel().trim());
        payload.put("stream", Boolean.FALSE);
        payload.put("temperature", Math.max(0.1d, settings.getTemperature()));
        int maxTokens = effectiveMaxTokens(settings);
        if (maxTokens > 0) {
            payload.put("max_tokens", maxTokens);
        }
        payload.put("messages", messages);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = createRestTemplate(settings).postForObject(
            settings.getEndpoint().trim(),
            new HttpEntity<Map<String, Object>>(payload, headers),
            Map.class
        );
        String answer = extractContent(response);
        if (!hasText(answer)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "大模型已响应，但没有返回可用的巡检报告内容");
        }
        return answer.trim();
    }

    private List<Map<String, String>> buildSectionMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message(
            "system",
            "你是一名专注于 CDP Hadoop 平台的高级巡检专家，熟悉 HDFS、YARN、Hive、Impala、Spark、Kafka、HBase、Ranger 与 ZooKeeper。"
                + "你必须始终使用简体中文，只能依据输入证据做判断，不允许虚构日志、节点、指标或风险。"
                + "当前任务是分章节生成巡检报告，请只输出要求的章节 Markdown 正文。"
        ));
        messages.add(message("user", prompt));
        return messages;
    }

    private List<Map<String, String>> buildSummaryMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message(
            "system",
            "你是一名 CDP/Hadoop 巡检报告审校专家。请严格按要求输出两行纯文本，不要添加任何解释、Markdown 或代码块。"
        ));
        messages.add(message("user", prompt));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private SummaryEnvelope parseSummaryEnvelope(String answer, String fallbackMarkdown) {
        String summary = "";
        String overallRisk = "MEDIUM";
        String[] lines = safe(answer).split("\\r?\\n");
        for (String line : lines) {
            String normalized = line.trim();
            if (normalized.startsWith("巡检结论:")) {
                summary = normalized.substring("巡检结论:".length()).trim();
            } else if (normalized.startsWith("总体风险:")) {
                overallRisk = normalizeRisk(normalized.substring("总体风险:".length()).trim());
            }
        }
        if (!hasText(summary)) {
            summary = extractFirstParagraph(fallbackMarkdown);
        }
        return new SummaryEnvelope(defaultIfBlank(summary, "本次巡检已完成，请查看详细报告。"), overallRisk);
    }

    private String extractFirstParagraph(String markdown) {
        String[] lines = markdown == null ? new String[0] : markdown.split("\\r?\\n");
        for (String line : lines) {
            String safe = line == null ? "" : line.trim();
            if (safe.isEmpty() || safe.startsWith("#")) {
                continue;
            }
            return safe;
        }
        return "";
    }

    private List<InspectionChunk> buildChunks(String inspectionContext) {
        List<InspectionChunk> chunks = new ArrayList<InspectionChunk>();
        addChunk(chunks, "## 巡检概览", inspectionContext, "=== 巡检基础信息 ===", "=== 当前未关闭事件 ===");
        addChunk(chunks, "## HDFS 专项巡检", inspectionContext, "=== HDFS 专项巡检上下文 ===");
        addChunk(chunks, "## YARN 专项巡检", inspectionContext, "=== YARN 专项巡检上下文 ===");
        addChunk(chunks, "## Hive 与 Impala 专项巡检", inspectionContext, "=== Hive 与 Impala 专项巡检上下文 ===");
        addChunk(chunks, "## 其他组件专项巡检", inspectionContext, "=== 其他组件专项巡检上下文 ===");
        addChunk(chunks, "## 容量风险", inspectionContext, "=== 容量风险专题证据 ===");
        addChunk(chunks, "## 性能风险", inspectionContext, "=== 性能风险专题证据 ===");
        addChunk(chunks, "## 稳定性风险", inspectionContext, "=== 稳定性风险专题证据 ===");
        addChunk(chunks, "## 安全与审计风险", inspectionContext, "=== 安全与审计风险专题证据 ===");
        addChunk(chunks, "## 高风险节点与异常角色", inspectionContext, "=== 最近服务日志快照 ===");
        addChunk(chunks, "## 关键日志与指标证据", inspectionContext, "=== 最近服务日志快照 ===");
        addChunk(chunks, "## 立即处理建议", inspectionContext, "=== 最近服务日志快照 ===", "=== 当前未关闭事件 ===");
        addChunk(chunks, "## 中长期治理建议", inspectionContext, "=== 最近服务日志快照 ===", "=== 当前未关闭事件 ===");
        return chunks;
    }

    private void addChunk(List<InspectionChunk> chunks, String sectionTitle, String inspectionContext, String... headers) {
        String context = extractSections(inspectionContext, headers);
        if (hasText(context)) {
            chunks.add(new InspectionChunk(sectionTitle, context));
        }
    }

    private String extractSections(String inspectionContext, String... headers) {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            String section = extractSection(inspectionContext, header);
            if (!hasText(section)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(section.trim());
        }
        return builder.toString();
    }

    private String extractSection(String inspectionContext, String header) {
        String source = safe(inspectionContext);
        int start = source.indexOf(header);
        if (start < 0) {
            return "";
        }
        int next = source.indexOf("\n===", start + header.length());
        if (next < 0) {
            return source.substring(start);
        }
        return source.substring(start, next).trim();
    }

    private String normalizeRisk(String risk) {
        String upper = defaultIfBlank(risk, "MEDIUM").toUpperCase(Locale.ROOT);
        if ("LOW".equals(upper) || "MEDIUM".equals(upper) || "HIGH".equals(upper) || "CRITICAL".equals(upper)) {
            return upper;
        }
        return "MEDIUM";
    }

    private RestTemplate createRestTemplate(DiagnosisLlmSettingsEntity settings) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(settings.getConnectTimeoutMs(), 10000));
        factory.setReadTimeout(Math.max(settings.getReadTimeoutMs(), 180000));
        return new RestTemplate(factory);
    }

    private int effectiveMaxTokens(DiagnosisLlmSettingsEntity settings) {
        int configured = settings == null ? 0 : settings.getMaxTokens();
        if (configured <= 0 || configured == 2048) {
            return 0;
        }
        return configured;
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List) || ((List<?>) choicesObject).isEmpty()) {
            return null;
        }
        Object firstChoice = ((List<?>) choicesObject).get(0);
        if (!(firstChoice instanceof Map)) {
            return null;
        }
        Object messageObject = ((Map<?, ?>) firstChoice).get("message");
        if (!(messageObject instanceof Map)) {
            return null;
        }
        Map<?, ?> message = (Map<?, ?>) messageObject;
        String content = extractTextValue(message.get("content"));
        if (hasText(content)) {
            return content;
        }
        content = extractTextValue(message.get("reasoning_content"));
        if (hasText(content)) {
            return content;
        }
        content = extractTextValue(message.get("text"));
        if (hasText(content)) {
            return content;
        }
        return extractTextValue(message.get("reasoning"));
    }

    private String extractTextValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (List<?>) value) {
                String text = extractTextValue(item);
                if (hasText(text)) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text.trim());
                }
            }
            return builder.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            String text = extractTextValue(map.get("text"));
            if (hasText(text)) {
                return text;
            }
            text = extractTextValue(map.get("content"));
            if (hasText(text)) {
                return text;
            }
            return extractTextValue(map.get("output_text"));
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class InspectionChunk {
        private final String sectionTitle;
        private final String context;

        private InspectionChunk(String sectionTitle, String context) {
            this.sectionTitle = sectionTitle;
            this.context = context;
        }
    }

    private static final class SummaryEnvelope {
        private final String summary;
        private final String overallRisk;

        private SummaryEnvelope(String summary, String overallRisk) {
            this.summary = summary;
            this.overallRisk = overallRisk;
        }
    }

    @FunctionalInterface
    public interface SectionProgressListener {
        void onSectionGenerated(int completedSections, int totalSections, String sectionTitle, String currentMarkdown);
    }
}
