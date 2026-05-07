package com.guardian.hadoop.tuning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ParameterOptimizationRuleService {

    public ParameterOptimizationRuleAnalysis analyze(ParameterOptimizationRequest request,
                                                     ParameterOptimizationContextPreview context) {
        String serviceType = normalizeServiceType(request == null ? null : request.getServiceType());
        List<String> findings = new ArrayList<String>();
        List<String> focusParameters = new ArrayList<String>();
        Map<String, String> proposedValues = new LinkedHashMap<String, String>();

        if (context == null || !context.isAvailable()) {
            findings.add("当前没有可用的 Cloudera Manager 参数快照，只能基于手工输入的症状和源码线索给出保守建议。");
        } else {
            findings.add("已采集当前 " + serviceType + " 组件配置快照，可结合当前版本与日志信号做参数建议。");
        }

        if ("HDFS".equals(serviceType)) {
            add(focusParameters, "dfs.datanode.max.transfer.threads", "dfs.client.socket-timeout", "dfs.namenode.handler.count");
            add(findings,
                "重点核查 NameNode handler、DataNode 传输线程、客户端超时与副本链路相关参数是否协调。",
                "如果最近日志出现 slow block receiver、packet mirror timeout 或 version mismatch，需要先区分网络链路问题和参数上限问题。");
            proposedValues.put("dfs.datanode.max.transfer.threads", "结合并发量、CPU 与网络情况适度上调");
        } else if ("YARN".equals(serviceType)) {
            add(focusParameters, "yarn.scheduler.maximum-allocation-mb", "yarn.nodemanager.resource.memory-mb", "yarn.scheduler.capacity.maximum-am-resource-percent");
            add(findings,
                "重点核查队列资源上限、NodeManager 总资源、AM 资源占比与 pending application 挤压现象。",
                "如果现象更像 scheduler 拥塞，应先区分队列规划问题和参数上限问题。");
            proposedValues.put("yarn.scheduler.maximum-allocation-mb", "按单任务真实内存需求与集群碎片情况重新评估");
        } else if ("HIVE_ON_TEZ".equals(serviceType)) {
            add(focusParameters, "hive.auto.convert.join", "tez.task.resource.memory.mb", "hive.exec.reducers.bytes.per.reducer");
            add(findings,
                "重点核查 Tez 容器大小、Reducer 划分、Join 自动转换与并行度参数是否匹配当前数据量。",
                "如果 SQL 本身存在 row_number、复杂函数嵌套或重复扫描，应优先考虑 SQL 改写，而不是只调参数。");
            proposedValues.put("tez.task.resource.memory.mb", "结合 Tez container OOM、GC 与并发度重新评估");
        } else if ("IMPALA".equals(serviceType)) {
            add(focusParameters, "default_query_options", "admission_control_queue_timeout_ms", "mem_limit");
            add(findings,
                "重点核查 Admission Control、查询超时、内存上限与并发策略是否协调。",
                "如果日志明显指向 RPC timeout、Cancelled 或 memory pressure，需要区分 SQL 模式问题和参数边界问题。");
            proposedValues.put("admission_control_queue_timeout_ms", "根据排队时长与业务 SLA 适度调整");
        } else {
            add(findings, "当前服务类型尚未内置专项规则，将以通用配置、日志和源码线索为主。");
        }

        if (!hasText(request == null ? null : request.getCurrentSymptoms())) {
            findings.add("未填写当前症状说明，建议补充现象、时间窗、受影响节点或关键报错，以便缩小参数建议范围。");
        }
        if (!hasText(context == null ? null : context.getComponentVersion())) {
            findings.add("当前未采集到组件版本，后续大模型建议会缺少版本实现依据。");
        }
        if (context != null && context.getConfigEntries().isEmpty()) {
            findings.add("当前没有拿到可展示的组件配置参数，建议先确认 Cloudera Manager 配置采集是否正常。");
        }

        String riskLevel = findings.size() >= 5 ? "HIGH" : findings.size() >= 3 ? "MEDIUM" : "LOW";
        return new ParameterOptimizationRuleAnalysis(riskLevel, findings, focusParameters, proposedValues);
    }

    private void add(List<String> values, String... items) {
        if (items == null) {
            return;
        }
        for (String item : items) {
            if (hasText(item)) {
                values.add(item.trim());
            }
        }
    }

    private String normalizeServiceType(String serviceType) {
        if (serviceType == null) {
            return "HDFS";
        }
        String normalized = serviceType.trim().toUpperCase(Locale.ROOT);
        if ("HIVE".equals(normalized) || "HIVE_ON_TEZ".equals(normalized)) {
            return "HIVE_ON_TEZ";
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
