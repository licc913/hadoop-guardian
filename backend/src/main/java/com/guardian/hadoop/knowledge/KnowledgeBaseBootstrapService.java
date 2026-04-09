package com.guardian.hadoop.knowledge;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseBootstrapService implements ApplicationRunner {

    private final KnowledgeArticleRepository repository;

    public KnowledgeBaseBootstrapService(KnowledgeArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        save("HDFS", "hdfs-low-space-under-replication", "HDFS 容量压力与副本不足处置",
            "适用于磁盘使用率持续升高、欠副本块上升、DataNode 下线后恢复缓慢的场景。先确认是否是容量不足、节点故障还是副本恢复窗口过长，再决定是否做清理、扩容或节点维护。",
            "事件标题、证据或诊断中出现容量接近阈值、欠副本块、dead datanode、磁盘满等信号时优先匹配。", "SEV2", true,
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            Arrays.asList("集群容量使用率持续高于阈值", "欠副本块数量增长", "DataNode 心跳异常或节点宕机"),
            Arrays.asList("capacity", "disk full", "under replicated", "欠副本", "dead datanode", "磁盘"),
            Arrays.asList("先执行 hdfs dfsadmin -report，确认 live/dead/decommissioning 节点、容量余量和欠副本块趋势。",
                "若由单个 DataNode 故障引起，先恢复节点或完成退役，再观察副本恢复速度，避免直接强制退出安全模式。",
                "若为集群整体容量不足，先停止非必要大写入任务并清理可回收数据，再评估扩容或临时降副本策略。",
                "若单机多盘分布严重失衡，优先评估 hdfs diskbalancer -plan / -execute 进行盘内平衡，避免直接做高风险数据迁移。"),
            Arrays.asList("欠副本块趋势停止上升", "dead datanode 数恢复正常", "容量告警回落，写入延迟恢复"),
            Arrays.asList("不要在原因未明时强制退出安全模式", "不要直接删除业务数据目录，先确认回收策略和影响范围"));

        save("HDFS", "hdfs-small-files-namenode-pressure", "HDFS 小文件与 NameNode 元数据压力处置",
            "适用于 NameNode 堆内存上升、GC 频繁、命名空间压力高和小文件数量过多的场景。应优先定位热点目录和小文件来源，而不是直接扩大 JVM 后忽略根因。",
            "事件证据中出现 NameNode heap、GC、namespace、small files、metadata 等关键词时优先匹配。", "SEV2", true,
            "Cloudera Runtime 文档：优化 NameNode 命名空间资源",
            "https://docs.cloudera.com/runtime/latest/scaling-namespaces/topics/hdfs-optimizing-namenode-disk-space-with-hadoop-archives.html",
            Arrays.asList("NameNode 堆使用率持续高位", "GC 暂停时间拉长", "文件数和块数增长快于容量增长"),
            Arrays.asList("namenode", "heap", "gc", "namespace", "small files", "metadata", "小文件"),
            Arrays.asList("先确认 NameNode 文件数、块数和堆占用趋势，区分是短时高峰还是命名空间长期膨胀。",
                "对热点目录做样本统计，确认小文件来源任务、分区策略或写入频率。",
                "对可合并的小文件评估 HAR、合并作业或上游写入批量化，减少 NameNode 对象数量。",
                "如需调高 NameNode 堆，应先核对高可用两侧配置一致，并同步评估 GC 影响。"),
            Arrays.asList("堆使用率和 GC 时间回落", "热点目录文件数增长放缓", "新写入任务已切换到更合理的文件粒度"),
            Arrays.asList("不要直接修改 fsimage 或 edits 元数据文件", "不要把单纯扩堆当成最终治理方案"));

        save("HDFS", "hdfs-fsck-corrupt-blocks", "HDFS 坏块与缺块排查处置",
            "适用于 fsck 报告缺块、坏块或文件损坏的场景。重点是先确认影响范围，再决定是否迁移、删除损坏副本或恢复源数据。",
            "事件证据中出现 corrupt blocks、missing blocks、fsck、文件损坏等关键词时优先匹配。", "SEV1", true,
            "Apache Hadoop HDFS Users Guide",
            "https://hadoop.apache.org/docs/current3/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html",
            Arrays.asList("fsck 报告 corrupt blocks", "关键目录出现 missing blocks", "业务读取报坏块或文件损坏"),
            Arrays.asList("fsck", "corrupt", "missing blocks", "坏块", "损坏"),
            Arrays.asList("先对受影响路径执行 hdfs fsck，明确是单文件、单目录还是系统性坏块问题。",
                "核对是否由节点故障、磁盘故障或历史复制不足引起，再决定修复顺序。",
                "对可重建的数据优先按业务来源回灌；对不可重建的关键文件先做隔离和影响通报。",
                "若需要使用 fsck 的 move 或 delete 选项，必须先经过审批，并确认不会扩大业务影响。"),
            Arrays.asList("坏块数量不再增长", "关键路径读取恢复", "受影响目录已完成修复或隔离"),
            Arrays.asList("不要在未评估影响时直接执行 fsck -delete", "不要把 fsck 当作自动修复工具，它首先是排查工具"));

        save("HDFS", "hdfs-balancer-hotspot", "HDFS 数据倾斜与热点节点处置",
            "适用于 DataNode 容量分布明显失衡、部分节点成为热点、扩容后数据分布长期不均的场景。",
            "事件中出现 balancer、diskbalancer、hot datanode、数据倾斜、rebalance 等信号时优先匹配。", "SEV3", true,
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            Arrays.asList("单机容量或流量显著高于平均水平", "新增节点长期没有承载到足够数据", "跨盘分布导致单节点磁盘热点"),
            Arrays.asList("balancer", "diskbalancer", "hotspot", "rebalance", "倾斜", "热点"),
            Arrays.asList("先区分是节点间数据倾斜还是单节点内多盘倾斜，分别选择 balancer 或 diskbalancer。",
                "在低峰窗口评估并执行 balancer / diskbalancer，避免与大规模业务写入叠加。",
                "执行前确认网络与磁盘余量，避免重平衡过程把节点压到更高负载。",
                "执行后复核各节点使用率、热点目录与读写延迟，确认倾斜已经回落。"),
            Arrays.asList("节点间容量差异回落到阈值内", "新增节点开始正常承载数据", "热点节点 CPU、磁盘、网络压力回落"),
            Arrays.asList("不要在容量已经接近上限时大规模 rebalance", "不要把重平衡与退役、扩容、海量写入同时进行"));

        save("YARN", "yarn-queue-pending-contention", "YARN 队列拥塞与资源争抢处置",
            "适用于 pending application 增长、队列资源饥饿、容器分配缓慢和业务作业普遍等待资源的场景。应先确认是哪类队列、用户或大作业占满了资源，再决定是否做限流或迁移。",
            "事件或证据出现 pending、queue、capacity、container allocation、starvation 等信号时匹配。", "SEV2", true,
            "Apache Hadoop Capacity Scheduler",
            "https://hadoop.apache.org/docs/stable/hadoop-yarn/hadoop-yarn-site/CapacityScheduler.html",
            Arrays.asList("Pending application 或 container 持续升高", "AM 获得资源明显变慢", "单个租户或队列占满容量"),
            Arrays.asList("pending", "queue", "capacity", "container", "am", "resource contention", "拥塞"),
            Arrays.asList("先确认拥塞发生在哪个队列，核对 queue capacity、max capacity 和活跃应用数。",
                "检查是否为单个租户、大查询或批量作业集中提交导致，必要时先限流或迁移到低峰窗口。",
                "若是资源池配置偏小，结合 Capacity Scheduler 当前配置和业务优先级调整队列容量。",
                "对等待中的 Hive on Tez 作业，先识别最占资源的 AM 与长作业，而不是盲目重启 ResourceManager。"),
            Arrays.asList("队列 pending 回落", "新提交应用能在预期时间内获得容器", "核心业务队列不再被低优先级作业挤占"),
            Arrays.asList("不要在高峰期直接重启 ResourceManager", "不要绕过容量策略临时大幅放开所有队列上限"));

        save("YARN", "yarn-nodemanager-graceful-decommission", "YARN NodeManager 异常与优雅退役处置",
            "适用于 NodeManager 异常抖动、磁盘或健康检查失败、需要下线节点但希望降低对运行中任务影响的场景。",
            "证据包含 nodemanager unhealthy、decommission、lost node、health checker 等关键词时匹配。", "SEV3", true,
            "Apache Hadoop Graceful Decommission of YARN Nodes",
            "https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/GracefulDecommission.html",
            Arrays.asList("NodeManager health check 失败", "节点频繁 lost / rejoin", "计划性下线节点需要保留运行中任务"),
            Arrays.asList("nodemanager", "decommission", "unhealthy", "lost node", "health checker", "退役"),
            Arrays.asList("先确认节点是否为临时网络抖动、磁盘故障还是宿主机资源异常，避免误下线健康节点。",
                "需要下线时优先使用 exclude list 配合 yarn rmadmin -refreshNodes 的优雅退役方式，给运行中容器留出完成窗口。",
                "为退役设置合理 timeout，并观察 RM 上 decommissioning 状态，确认没有关键应用长时间卡住。",
                "节点恢复后再决定是 recommission 还是替换硬件，不要在节点状态不稳时立即恢复调度。"),
            Arrays.asList("节点进入 DECOMMISSIONED 或恢复心跳正常", "关键应用未出现大规模失败", "RM 节点状态稳定"),
            Arrays.asList("不要直接关闭节点电源替代退役流程", "不要在未核对关键作业前强制清理容器"));

        save("HIVE_ON_TEZ", "hive-tez-session-pool-resource-wait", "Hive on Tez 会话池与 AM 资源等待处置",
            "适用于 Hive 查询大面积排队、Tez DAG 长时间停留在获取资源阶段、默认 Tez 会话池不足或队列映射不合理的场景。",
            "事件中包含 Tez session、AM wait、queue wait、default queue、concurrency 等信号时优先匹配。", "SEV2", true,
            "Cloudera 文档：Hive on Tez configurations",
            "https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/configuring-apache-hive/topics/hive-on-tez-configurations.html",
            Arrays.asList("Hive 查询在编译后长时间等待执行", "Tez AM 资源获取慢", "并发查询被单一默认队列限制"),
            Arrays.asList("tez", "am", "queue wait", "default queue", "session pool", "hive on tez", "并发"),
            Arrays.asList("先确认 hive.server2.tez.default.queues、sessions.per.default.queue、initialize.default.sessions 的当前配置。",
                "判断是否是队列数量不足导致所有查询争用同一个 Tez 会话池，必要时为关键业务增加默认队列映射。",
                "若集群资源本身紧张，先回到 YARN 侧确认队列拥塞，不要只在 HiveServer2 上反复重启。",
                "调整会话池或队列映射后，复核并发能力和闲时资源浪费，避免把所有 Tez AM 常驻撑满资源池。"),
            Arrays.asList("Tez DAG 启动等待时间回落", "关键队列上的 Hive 查询恢复并发", "HS2 和 YARN 队列资源曲线趋稳"),
            Arrays.asList("不要在根因仍是 YARN 拥塞时只重启 HiveServer2", "不要盲目增大所有默认会话数导致空闲资源长期占用"));

        save("HIVE_ON_TEZ", "hive-metastore-schema-connectivity", "Hive Metastore 模式与连接异常处置",
            "适用于 Hive 查询无法访问元数据、Metastore 连接失败、Schema 版本不一致或升级后兼容性异常的场景。",
            "事件或日志中出现 metastore、schema verification、schematool、connection url、JDO 等信号时优先匹配。", "SEV2", true,
            "Apache Hive Metastore Administration",
            "https://hive.apache.org/docs/latest/admin/adminmanual-metastore-administration/",
            Arrays.asList("HiveServer2 报 Metastore 连接失败", "Schema verification 报错", "升级或切换数据库后元数据不可用"),
            Arrays.asList("metastore", "schema", "schematool", "jdo", "connectionurl", "hms"),
            Arrays.asList("先核对 Hive Metastore 连接的 RDBMS、ConnectionURL 与认证参数是否正确。",
                "确认 schema 版本与 Hive 二进制兼容，必要时使用 schematool 进行只读检查或升级准备。",
                "若升级过后出现不兼容，先停止业务侧重试风暴，再做 schema 校验和回退评估。",
                "在 Metastore 恢复前，不要批量执行依赖元数据的刷新或重建动作，以免扩大影响。"),
            Arrays.asList("Metastore 连接恢复", "schema 校验通过", "HS2/Impala 对关键库表的元数据访问恢复"),
            Arrays.asList("不要在未备份元数据库的情况下直接执行 schema 升级", "不要关闭 schema verification 作为长期解决方案"));

        save("IMPALA", "impala-admission-control-queueing", "Impala Admission Control 排队与内存争抢处置",
            "适用于查询被排队、admission control 拒绝或等待、并发过高引起内存压力的场景。应优先检查资源池、并发数和内存上限，而不是直接放开限制。",
            "事件、摘要或诊断中出现 admission、queued、pool、memory limit、max running queries 等信号时匹配。", "SEV2", true,
            "Cloudera 文档：Admission Control and Query Queuing",
            "https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/impala-manage/topics/impala-admission.html",
            Arrays.asList("查询长时间排队", "Admission control 拒绝或超时", "Impala 内存压力导致大查询失败"),
            Arrays.asList("impala", "admission", "queued", "pool", "memory", "max running queries", "排队"),
            Arrays.asList("先查看查询所在资源池、当前 running / queued 数和 admission reason，区分是并发限制还是内存限制。",
                "对大查询和高并发查询分开建池或调整资源池上限，避免共享池被重型查询持续占满。",
                "结合 PROFILE 或 summary 核对估算内存与实际内存偏差，必要时调整 Max Running Queries、Max Memory 和 queue timeout。",
                "在多租户场景优先做资源池治理，不要直接全局关闭 admission control。"),
            Arrays.asList("排队深度回落", "超时失败减少", "资源池内 running / queued 与业务优先级匹配"),
            Arrays.asList("不要直接关闭 admission control 让所有查询同时冲击集群", "不要只提高单查询内存上限而忽略整体池容量"));

        save("IMPALA", "impala-metadata-refresh", "Impala 元数据不一致处置",
            "适用于表新增分区不可见、文件变更后查询结果异常、跨引擎写入后 Impala 仍读到旧元数据的场景。应优先按影响范围选择 REFRESH 或 INVALIDATE METADATA。",
            "事件中出现 metadata stale、partition missing、refresh、invalidate metadata 等信号时匹配。", "SEV3", true,
            "Cloudera 文档：REFRESH",
            "https://docs.cloudera.com/cdp-private-cloud-base/7.1.8/impala-sql-reference/topics/impala-refresh.html",
            Arrays.asList("新增分区在 Impala 中不可见", "外部写入后查询仍返回旧数据", "Catalog 元数据不同步"),
            Arrays.asList("metadata", "refresh", "invalidate", "partition", "catalog", "stale", "元数据"),
            Arrays.asList("先确认变更范围是单表、单分区还是全局元数据失效，尽量使用影响面更小的 REFRESH。",
                "只有在元数据缓存明显失效且单表刷新无效时，才考虑 INVALIDATE METADATA，并评估 catalog 压力。",
                "若问题由 Hive/HDFS 侧文件或分区变更触发，先确认上游已完成写入和 metastore 更新。",
                "执行后抽样验证关键表和分区，确认查询结果与 HDFS/Metastore 一致。"),
            Arrays.asList("关键表查询结果恢复正确", "新分区可见", "catalog 延迟和元数据错误告警回落"),
            Arrays.asList("不要在业务高峰期对大量表做全局 INVALIDATE METADATA", "不要在上游写入未完成时重复 REFRESH 造成误判"));
    }

    private void save(String domain, String scenarioKey, String title, String summary, String applicability,
                      String riskLevel, boolean requiresApproval, String sourceName, String sourceUrl,
                      List<String> symptoms, List<String> keywords, List<String> steps,
                      List<String> validations, List<String> cautions) {
        KnowledgeArticleEntity entity = repository.findByScenarioKey(scenarioKey).orElseGet(KnowledgeArticleEntity::new);
        entity.setDomain(domain);
        entity.setScenarioKey(scenarioKey);
        entity.setTitle(title);
        entity.setSummary(summary);
        entity.setApplicability(applicability);
        entity.setRiskLevel(riskLevel);
        entity.setRequiresApproval(requiresApproval);
        entity.setSourceName(sourceName);
        entity.setSourceUrl(sourceUrl);
        entity.setSymptoms(symptoms);
        entity.setMatchKeywords(keywords);
        entity.setSteps(steps);
        entity.setValidationChecks(validations);
        entity.setCautionItems(cautions);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }
}
