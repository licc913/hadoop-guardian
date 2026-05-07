package com.guardian.hadoop.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SqlOptimizationRuleService {

    private static final Pattern JOIN_PATTERN = Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE);

    public SqlOptimizationRuleAnalysis analyze(SqlOptimizationRequest request) {
        String sql = normalizeSql(request == null ? null : request.getOriginalSql());
        String lower = sql.toLowerCase(Locale.ROOT);
        String engineType = normalizeEngineType(request == null ? null : request.getEngineType());

        List<String> findings = new ArrayList<String>();
        List<String> hints = new ArrayList<String>();
        int riskScore = 0;

        if (lower.contains("select *")) {
            findings.add("存在 select *，会放大扫描、网络传输和元数据变更带来的影响。");
            hints.add("改成显式字段列表，只保留业务真正需要的列。");
            riskScore += 2;
        }
        if (lower.contains("order by") && !lower.contains("limit")) {
            findings.add("存在全局 order by 且没有 limit，容易触发大规模排序和长时间内存占用。");
            hints.add("优先确认是否可以改成 sort by，或者补充明确的 limit。");
            riskScore += 2;
        }
        if (lower.contains("count(distinct")) {
            findings.add("count(distinct ...) 属于高代价聚合，数据量大时容易放大 shuffle 和内存压力。");
            hints.add("评估是否可以先去重再聚合，或改为近似去重方案。");
            riskScore += 2;
        }
        if (lower.contains(" like '%")) {
            findings.add("前置通配 like 通常无法利用分区或过滤裁剪，容易退化为全表扫描。");
            hints.add("尽量改成前缀匹配、等值过滤，或者增加预处理字段。");
            riskScore += 2;
        }
        if (countMatches(JOIN_PATTERN, lower) >= 3) {
            findings.add("SQL 包含较多 join，需要重点检查大表 join 顺序、过滤下推和广播风险。");
            hints.add("优先把高选择性过滤提前，让低基数结果先收敛后再参与后续 join。");
            riskScore += 2;
        }
        if (containsFromClause(lower) && !lower.contains(" where ")) {
            findings.add("SQL 没有明显 where 过滤条件，存在全表扫描风险。");
            hints.add("补充时间、分区或业务主键过滤条件，先缩小扫描范围。");
            riskScore += 1;
        }
        if (hasText(request == null ? null : request.getPartitionInfo())
            && !mentionsPartitionFields(lower, request.getPartitionInfo())) {
            findings.add("已提供分区字段信息，但 SQL 中没有明显使用这些字段，分区裁剪可能失效。");
            hints.add("把分区字段写入 where 条件，避免扫描全部分区。");
            riskScore += 2;
        }
        if ("IMPALA".equals(engineType) && lower.contains("insert overwrite")) {
            findings.add("Impala 执行 insert overwrite 时要特别注意目标分区范围和输出文件数量。");
            hints.add("确认目标分区边界，必要时先写入中间表，再覆盖目标表。");
            riskScore += 1;
        }
        if ("IMPALA".equals(engineType) && countMatches(JOIN_PATTERN, lower) > 0 && !lower.contains("[shuffle]") && !lower.contains("/*+shuffle*/")) {
            findings.add("Impala SQL 存在 join，但没有显式给出 shuffle 策略，若表大小判断失误，可能触发大表 broadcast 导致单节点内存峰值过高。");
            hints.add("如果表大小不确定，优先评估在 left/right join 后显式增加 [shuffle]，避免广播到单节点。");
            riskScore += 1;
        }
        if ("IMPALA".equals(engineType) && lower.contains("inner join") && !lower.contains("[straight_join]") && !lower.contains("/*+straight_join*/")) {
            findings.add("Impala SQL 存在 inner join，若依赖固定的大表在前、小表在后策略，优化器可能重排 join 顺序。");
            hints.add("如需严格控制 join 顺序，可评估在 select 关键字后增加 [straight_join]。");
            riskScore += 1;
        }
        if ("IMPALA".equals(engineType) && lower.contains("insert overwrite") && lower.contains("partition(") && !lower.contains("[noclustered]") && !lower.contains("/*+noclustered*/")) {
            findings.add("Impala 动态分区插入可能在落盘前触发额外排序，造成明显内存消耗。");
            hints.add("如果必须动态分区插入，评估在 insert 子句中增加 [noclustered] 以避免排序步骤。");
            riskScore += 1;
        }
        if ("HIVE".equals(engineType) && lower.contains("distribute by") && !lower.contains("sort by")) {
            findings.add("Hive 使用 distribute by 但没有 sort by，Reducer 负载和输出顺序可能不可控。");
            hints.add("结合业务要求评估是否同时使用 sort by 或 cluster by。");
            riskScore += 1;
        }
        if ("HIVE".equals(engineType) && lower.contains("row_number() over")) {
            findings.add("Hive SQL 使用了 row_number() over(...)，这类窗口排序在大数据量场景下容易放大排序与 Shuffle 代价。");
            hints.add("如果业务不要求严格去重排序，优先评估是否可用先聚合、半连接或更轻量的去重方式替代。");
            riskScore += 2;
        }
        if ("HIVE".equals(engineType) && countNestedFunctionSignals(lower) >= 2) {
            findings.add("Hive SQL 中存在较多函数嵌套或复杂表达式，容易拉高 CPU 开销并影响 Tez 任务稳定性。");
            hints.add("尽量拆平多重函数嵌套、复杂 case when 或重复表达式，必要时先落中间层再做后续计算。");
            riskScore += 1;
        }
        if ("HIVE".equals(engineType) && countMatches(JOIN_PATTERN, lower) > 0 && referencesFunctionAroundJoin(lower)) {
            findings.add("Hive SQL 的 join 条件附近疑似存在函数处理，可能导致无法高效利用关联键并放大 Shuffle。");
            hints.add("关联键尽量保持原始字段直接匹配，避免在 join 条件中对键做 cast、substr、concat、nvl 等处理。");
            riskScore += 2;
        }
        if (hasText(request == null ? null : request.getErrorText())) {
            findings.add("已提供执行报错信息，优化时需要同时考虑语义错误、类型不兼容或资源限制。");
            hints.add("优化后先在测试环境回放报错场景，确认语义和执行计划都已收敛。");
            riskScore += 1;
        }

        if (findings.isEmpty()) {
            findings.add("规则扫描未发现明显反模式，建议结合 EXPLAIN、表结构和业务目标做精细优化。");
            hints.add("补充 EXPLAIN 和表结构后再做二轮优化。");
        }

        return new SqlOptimizationRuleAnalysis(sql, normalizeRiskLevel(riskScore), findings, hints);
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private boolean containsFromClause(String lower) {
        return lower.contains(" from ");
    }

    private boolean mentionsPartitionFields(String lowerSql, String partitionInfo) {
        String[] parts = partitionInfo.split("[,\\n;|]");
        for (String part : parts) {
            String normalized = part.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2 && lowerSql.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countNestedFunctionSignals(String lowerSql) {
        int count = 0;
        count += occurrences(lowerSql, "case when");
        count += occurrences(lowerSql, "coalesce(");
        count += occurrences(lowerSql, "nvl(");
        count += occurrences(lowerSql, "substr(");
        count += occurrences(lowerSql, "regexp_");
        return count;
    }

    private boolean referencesFunctionAroundJoin(String lowerSql) {
        int joinIndex = lowerSql.indexOf(" join ");
        int onIndex = lowerSql.indexOf(" on ");
        if (joinIndex < 0 || onIndex < 0 || onIndex <= joinIndex) {
            return false;
        }
        int end = Math.min(lowerSql.length(), onIndex + 240);
        String joinClause = lowerSql.substring(onIndex, end);
        return joinClause.contains("cast(")
            || joinClause.contains("substr(")
            || joinClause.contains("concat(")
            || joinClause.contains("coalesce(")
            || joinClause.contains("nvl(")
            || joinClause.contains("trim(");
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String normalizeRiskLevel(int riskScore) {
        if (riskScore >= 7) {
            return "HIGH";
        }
        if (riskScore >= 4) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalizeEngineType(String engineType) {
        if (engineType == null) {
            return "IMPALA";
        }
        String normalized = engineType.trim().toUpperCase(Locale.ROOT);
        return "HIVE".equals(normalized) ? "HIVE" : "IMPALA";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
