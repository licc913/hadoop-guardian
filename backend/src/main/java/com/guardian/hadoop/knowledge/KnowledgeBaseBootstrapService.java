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
        seedHdfsGuides();
        seedYarnGuides();
        seedHiveGuides();
        seedImpalaGuides();
        seedCrossComponentGuides();
    }

    private void seedHdfsGuides() {
        save(
            "HDFS",
            "official-hdfs-capacity-report",
            "HDFS 容量与副本健康巡检",
            "基于 Apache Hadoop HDFS Commands Guide 提炼的容量巡检条目，适用于磁盘逼近上限、under replicated block 增长、DataNode 波动后的恢复判断。",
            "出现容量告警、dead datanode、under replicated、disk full、replication backlog 等信号时优先匹配。",
            "SEV2",
            true,
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            Arrays.asList(
                "集群剩余容量快速下降",
                "under replicated blocks 持续上升",
                "DataNode 下线后恢复缓慢"
            ),
            Arrays.asList("hdfs", "dfsadmin", "capacity", "under replicated", "disk full", "datanode"),
            Arrays.asList(
                "先执行 hdfs dfsadmin -report，确认 live/dead/decommissioning 节点和剩余容量。",
                "区分是单节点故障、整体容量不足，还是副本恢复窗口过长。",
                "优先恢复异常 DataNode 或暂停大写入任务，再评估扩容与副本策略调整。"
            ),
            Arrays.asList(
                "under replicated blocks 停止继续增长",
                "dead datanode 数量恢复正常",
                "容量告警和写入延迟回落"
            ),
            Arrays.asList(
                "不要在根因未明确时直接降低副本数。",
                "不要直接删除业务目录，先确认回收策略和业务影响。"
            )
        );

        save(
            "HDFS",
            "official-hdfs-fsck-corrupt-blocks",
            "HDFS 坏块与缺块排查",
            "基于 Apache Hadoop HdfsUserGuide 的 fsck 诊断要点，适用于 corrupt blocks、missing blocks、文件损坏和读失败问题。",
            "当事件或日志出现 fsck、corrupt、missing blocks、checksum、read failure 等关键词时优先匹配。",
            "SEV1",
            true,
            "Apache Hadoop HdfsUserGuide",
            "https://hadoop.apache.org/docs/current3/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html",
            Arrays.asList(
                "fsck 报告 corrupt blocks",
                "关键目录出现 missing blocks",
                "业务读取文件时出现坏块或校验失败"
            ),
            Arrays.asList("fsck", "corrupt", "missing blocks", "checksum", "block"),
            Arrays.asList(
                "先对受影响目录执行 hdfs fsck，确认影响范围是单文件、单目录还是系统性坏块。",
                "结合 DataNode 和磁盘健康状态判断是介质故障、节点故障还是历史副本不足。",
                "优先恢复可重建数据，关键且不可重建的数据先做隔离和影响通报。"
            ),
            Arrays.asList(
                "坏块数量不再增长",
                "关键路径读写恢复",
                "受影响目录完成修复或隔离"
            ),
            Arrays.asList(
                "不要在未评估影响前直接执行 fsck -delete。",
                "不要把 fsck 当成自动修复工具，它首先是排查工具。"
            )
        );

        save(
            "HDFS",
            "official-hdfs-balancer-diskbalancer",
            "HDFS 数据倾斜与热点节点处理",
            "基于 Apache Hadoop HDFS Commands Guide 中 balancer / diskbalancer 能力提炼，适用于热点节点、扩容后数据分布不均、单机多盘倾斜。",
            "出现 balancer、diskbalancer、hot datanode、rebalance、skew 等迹象时优先匹配。",
            "SEV3",
            true,
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            Arrays.asList(
                "个别 DataNode 容量或流量显著高于平均水平",
                "新增节点长期没有承载足够数据",
                "单节点内多块磁盘冷热不均"
            ),
            Arrays.asList("balancer", "diskbalancer", "hotspot", "rebalance", "skew"),
            Arrays.asList(
                "先区分是节点间倾斜还是节点内多盘倾斜，再选择 balancer 或 diskbalancer。",
                "在低峰期执行重平衡，避免与批量写入和副本恢复叠加。",
                "执行前确认网络和磁盘余量，执行后复核热点节点的 CPU、磁盘和网络压力。"
            ),
            Arrays.asList(
                "节点间容量差异回落到阈值内",
                "新增节点开始正常承载数据",
                "热点节点资源使用率回归正常"
            ),
            Arrays.asList(
                "不要在容量已接近上限时做大规模 rebalance。",
                "不要把重平衡与退役、扩容、海量写入同时进行。"
            )
        );

        save(
            "HDFS",
            "official-hdfs-namenode-small-files",
            "HDFS 安全模式与 NameNode 受压排查",
            "基于 Apache Hadoop HDFS Commands Guide 提炼，适用于 NameNode 长时间安全模式、块汇报恢复缓慢、元数据压力偏高导致的恢复窗口拉长。",
            "出现 safemode、namenode、block report、leave safemode、small files、namespace 等信号时优先匹配。",
            "SEV2",
            true,
            "Apache Hadoop HDFS Commands Guide",
            "https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HDFSCommands.html",
            Arrays.asList(
                "NameNode 长时间停留在安全模式",
                "块汇报恢复缓慢",
                "小文件过多导致 namespace 压力偏高"
            ),
            Arrays.asList("namenode", "safemode", "dfsadmin", "block report", "small files", "namespace"),
            Arrays.asList(
                "先通过 hdfs dfsadmin -safemode get 和 -report 判断是否仍在等待块汇报或副本恢复。",
                "确认是节点恢复慢、块数过多还是容量不足导致安全模式迟迟无法退出。",
                "优先恢复异常节点和块汇报，再评估小文件治理、归档合并和上游写入粒度优化。"
            ),
            Arrays.asList(
                "安全模式可正常退出",
                "块汇报延迟恢复正常",
                "NameNode 元数据压力回落"
            ),
            Arrays.asList(
                "不要在根因未明时强制 leave safemode。",
                "不要直接修改 fsimage 或 edits 文件。"
            )
        );
    }

    private void seedYarnGuides() {
        save(
            "YARN",
            "official-yarn-capacity-scheduler-pending",
            "YARN CapacityScheduler 队列拥塞与 Pending 排查",
            "基于 Apache Hadoop CapacityScheduler 官方文档提炼，适用于 pending application 增长、队列饥饿、AM 长时间停留在 ACCEPTED。",
            "事件、日志或队列观察结果出现 pending、accepted、capacity、max-parallel-apps、maximum-am-resource-percent 等信号时匹配。",
            "SEV2",
            true,
            "Apache Hadoop Capacity Scheduler",
            "https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/CapacityScheduler.html",
            Arrays.asList(
                "Pending application 或 container 持续增长",
                "应用长时间停留在 ACCEPTED",
                "某个队列或租户长期占满资源"
            ),
            Arrays.asList("yarn", "capacity scheduler", "pending", "accepted", "queue", "am resource"),
            Arrays.asList(
                "先核对队列 capacity、maximum-capacity、maximum-applications 和 maximum-am-resource-percent。",
                "确认是系统级上限、队列上限还是单用户并发限制导致应用无法进入 RUNNING。",
                "优先按队列和租户治理，不要直接通过重启 ResourceManager 规避拥塞。"
            ),
            Arrays.asList(
                "Pending 深度回落",
                "关键队列应用可按预期进入 RUNNING",
                "队列容量与业务优先级重新匹配"
            ),
            Arrays.asList(
                "不要在高峰期直接重启 ResourceManager。",
                "不要绕过队列策略临时放开全部上限。"
            )
        );

        save(
            "YARN",
            "official-yarn-graceful-decommission",
            "YARN NodeManager 优雅退役与异常节点处理",
            "基于 Apache Hadoop Graceful Decommission of YARN Nodes 文档提炼，适用于 NodeManager 异常、节点抖动、需要下线节点但希望降低对运行任务影响的场景。",
            "出现 decommission、unhealthy node、lost node、refreshNodes、decommissioning 等信号时匹配。",
            "SEV3",
            true,
            "Apache Hadoop Graceful Decommission of YARN Nodes",
            "https://hadoop.apache.org/docs/r3.2.3/hadoop-yarn/hadoop-yarn-site/GracefulDecommission.html",
            Arrays.asList(
                "NodeManager health check 失败",
                "节点频繁 lost / rejoin",
                "需要下线节点但仍有运行中的容器"
            ),
            Arrays.asList("yarn", "nodemanager", "decommission", "refreshNodes", "unhealthy", "lost node"),
            Arrays.asList(
                "先确认是短时网络抖动、磁盘故障还是宿主机资源异常。",
                "需要下线时优先用 exclude list 配合 yarn rmadmin -refreshNodes 做优雅退役。",
                "设置合理 timeout 并观察节点是否进入 DECOMMISSIONING / DECOMMISSIONED。"
            ),
            Arrays.asList(
                "节点进入 DECOMMISSIONED 或恢复健康",
                "关键应用未出现大规模失败",
                "RM 节点状态恢复稳定"
            ),
            Arrays.asList(
                "不要直接关机代替优雅退役。",
                "不要在未核对关键作业前强制清理容器。"
            )
        );
    }

    private void seedHiveGuides() {
        save(
            "HIVE_ON_TEZ",
            "official-hive-tez-session-pool",
            "Hive on Tez 会话池与 AM 资源等待",
            "基于 Apache Hive Configuration Properties 与 Hive on Tez 官方文档提炼，适用于查询大量排队、DAG 长时间停留在获取资源阶段、默认队列映射不合理。",
            "事件或日志出现 tez session、AM wait、queue wait、default queue、concurrency 等信号时匹配。",
            "SEV2",
            true,
            "Apache Hive Configuration Properties",
            "https://hive.apache.org/docs/latest/configuration-properties_27842758/",
            Arrays.asList(
                "Hive 查询在编译后长时间等待执行",
                "Tez AM 获取资源缓慢",
                "并发查询集中在默认队列"
            ),
            Arrays.asList("tez", "session pool", "queue wait", "default queue", "am wait", "hive on tez"),
            Arrays.asList(
                "核对 hive.server2.tez.default.queues、sessions.per.default.queue、initialize.default.sessions。",
                "区分是队列映射问题还是 YARN 资源本身紧张。",
                "优先为关键业务单独映射队列或拆分会话池，再评估扩容。"
            ),
            Arrays.asList(
                "Tez DAG 启动等待时间回落",
                "关键队列上的 Hive 查询恢复并发",
                "HS2 与 YARN 队列指标恢复平稳"
            ),
            Arrays.asList(
                "不要在根因仍是 YARN 拥塞时只重启 HiveServer2。",
                "不要盲目增大会话池导致空闲资源长期驻留。"
            )
        );

        save(
            "HIVE_ON_TEZ",
            "official-hive-metastore-administration",
            "Hive Metastore 连接与模式校验",
            "基于 Apache Hive Metastore Administration 文档提炼，适用于 Metastore 连接失败、Schema 校验失败、升级后兼容性异常。",
            "日志中出现 metastore、schema verification、schematool、connection url、JDO 等关键词时优先匹配。",
            "SEV2",
            true,
            "Apache Hive Metastore Administration",
            "https://hive.apache.org/docs/latest/admin/adminmanual-metastore-administration/",
            Arrays.asList(
                "HiveServer2 或 Impala 无法访问元数据",
                "Schema verification 报错",
                "升级后出现 schema 不兼容或连接异常"
            ),
            Arrays.asList("metastore", "schema", "schematool", "jdo", "hms", "connection url"),
            Arrays.asList(
                "先核对 Metastore 所依赖 RDBMS 的连接串、账号和网络连通性。",
                "确认 schema 版本与当前 Hive 二进制兼容，必要时执行只读 schematool 检查。",
                "升级引发异常时先控制重试风暴，再做 schema 校验和回退评估。"
            ),
            Arrays.asList(
                "Metastore 连接恢复",
                "schema 校验通过",
                "HS2/Impala 对关键库表的元数据访问恢复"
            ),
            Arrays.asList(
                "不要在未备份元数据库的情况下直接升级 schema。",
                "不要把关闭 schema verification 当成长期方案。"
            )
        );

        save(
            "HIVE_ON_TEZ",
            "official-hive-partition-metadata-sync",
            "Hive 分区与元数据同步排查",
            "基于 Apache Hive DDL 与 Metastore 管理文档提炼，适用于分区新增后不可见、修复后查询仍旧读取旧目录、跨引擎写入后的元数据滞后。",
            "日志或现象出现 partition missing、repair table、msck、stale metadata、location mismatch 时匹配。",
            "SEV3",
            true,
            "Apache Hive Language Manual DDL",
            "https://hive.apache.org/docs/latest/language/languagemanual-ddl/",
            Arrays.asList(
                "新增分区在 Hive 中不可见",
                "修复后查询结果仍不正确",
                "跨引擎写入导致 Metastore 与 HDFS 不一致"
            ),
            Arrays.asList("partition", "msck", "repair", "metadata", "stale", "location"),
            Arrays.asList(
                "确认上游写入已完成并核对分区目录是否真实存在。",
                "按影响范围优先使用单表或单分区级修复，而不是全库扫描。",
                "修复后抽样核对分区位置、行数和下游查询结果。"
            ),
            Arrays.asList(
                "新分区可见且位置正确",
                "下游查询结果恢复",
                "Metastore 与 HDFS 目录保持一致"
            ),
            Arrays.asList(
                "不要在业务高峰对大库做全量 repair。",
                "不要在上游写入未完成时提前刷新元数据。"
            )
        );

        save(
            "HIVE_ON_TEZ",
            "official-hive-stats-optimization",
            "Hive 统计信息与执行计划偏差排查",
            "基于 Apache Hive 官方统计信息与优化文档提炼，适用于 CBO 选错执行计划、Join 倾斜、广播误判和查询突然变慢。",
            "日志或执行计划出现 stats missing、CBO、row count inaccurate、broadcast join、skew 等信号时匹配。",
            "SEV3",
            true,
            "Apache Hive LanguageManual Analyze",
            "https://hive.apache.org/docs/latest/language/languagemanual-analyze/",
            Arrays.asList(
                "同一 SQL 执行计划突然改变",
                "广播 Join 或大表 Join 选择异常",
                "统计信息长期缺失或明显失真"
            ),
            Arrays.asList("analyze", "stats", "cbo", "join", "skew", "row count"),
            Arrays.asList(
                "先确认关键表和分区的统计信息是否过期或缺失。",
                "对核心表执行 analyze table 采样或增量更新，再观察计划是否恢复。",
                "若存在跨引擎写入，补齐统计信息刷新流程，避免依赖旧行数估计。"
            ),
            Arrays.asList(
                "执行计划恢复稳定",
                "关键查询耗时回落",
                "统计信息更新时间与数据更新时间一致"
            ),
            Arrays.asList(
                "不要在未确认数据落盘完成前刷新统计信息。",
                "不要把禁用 CBO 当成长期替代方案。"
            )
        );
    }

    private void seedImpalaGuides() {
        save(
            "IMPALA",
            "official-impala-admission-control",
            "Impala Admission Control 排队与资源争抢",
            "基于 Cloudera Impala admission control 文档提炼，适用于 queued、admission rejected、内存池紧张、并发过高导致的大查询等待。",
            "事件、摘要或 PROFILE 中出现 admission、queued、pool、memory limit、max running queries 等信号时匹配。",
            "SEV2",
            true,
            "Apache Impala Admission Control and Query Queuing",
            "https://impala.apache.org/docs/build/html/topics/impala_admission.html",
            Arrays.asList(
                "查询长时间排队",
                "Admission control 拒绝或超时",
                "资源池被重查询长期占满"
            ),
            Arrays.asList("impala", "admission", "queued", "pool", "memory", "max running queries"),
            Arrays.asList(
                "先确认查询所在资源池的 running / queued 深度与 admission reason。",
                "区分是并发上限触发还是内存上限触发，再调整池容量或队列策略。",
                "多租户场景优先治理资源池，不要直接全局关闭 admission control。"
            ),
            Arrays.asList(
                "队列深度回落",
                "Admission timeout 明显减少",
                "关键资源池的并发与业务优先级重新匹配"
            ),
            Arrays.asList(
                "不要直接关闭 admission control 让所有查询同时冲击集群。",
                "不要只提高单查询内存上限而忽略资源池总容量。"
            )
        );

        save(
            "IMPALA",
            "official-impala-refresh-metadata",
            "Impala REFRESH 与元数据一致性处理",
            "基于 Cloudera Impala REFRESH 文档提炼，适用于分区新增后不可见、文件变更后结果异常、跨引擎写入后仍读取旧元数据。",
            "事件或日志出现 refresh、stale metadata、partition missing、catalog update 等信号时匹配。",
            "SEV3",
            true,
            "Apache Impala REFRESH Statement",
            "https://impala.apache.org/docs/build/html/topics/impala_refresh.html",
            Arrays.asList(
                "新增分区在 Impala 中不可见",
                "外部写入后查询仍返回旧数据",
                "Catalog 元数据同步延迟"
            ),
            Arrays.asList("refresh", "metadata", "partition", "catalog", "stale"),
            Arrays.asList(
                "先确认变更范围是单表、单分区还是跨多表。",
                "优先使用影响面更小的 REFRESH，而不是直接全局 INVALIDATE METADATA。",
                "刷新后抽样核对关键表、关键分区和下游查询结果。"
            ),
            Arrays.asList(
                "新分区可见",
                "Catalog 更新延迟回落",
                "查询结果与 HDFS/Metastore 一致"
            ),
            Arrays.asList(
                "不要在高峰期对大量表做全局刷新。",
                "不要在上游写入未完成时重复刷新制造误判。"
            )
        );

        save(
            "IMPALA",
            "official-impala-invalidate-metadata",
            "Impala INVALIDATE METADATA 使用边界",
            "基于 Cloudera Impala metadata 管理文档提炼，适用于元数据缓存明显失效且单表刷新无效的场景。",
            "当出现 invalidate metadata、catalog stale、metadata corruption、cross-engine schema drift 等信号时匹配。",
            "SEV2",
            true,
            "Apache Impala INVALIDATE METADATA Statement",
            "https://impala.apache.org/docs/build/plain-html/topics/impala_invalidate_metadata.html",
            Arrays.asList(
                "REFRESH 后问题仍未恢复",
                "Catalog 缓存与 Metastore 持续不一致",
                "跨引擎修改表结构后 Impala 读取异常"
            ),
            Arrays.asList("invalidate metadata", "catalog", "schema drift", "metadata cache"),
            Arrays.asList(
                "先确认单表 REFRESH 是否足够，避免把局部问题升级成全局操作。",
                "评估 INVALIDATE METADATA 对 catalog 服务和并发查询的冲击窗口。",
                "执行后优先回归关键业务表，确认 schema 与分区都已同步。"
            ),
            Arrays.asList(
                "关键表恢复正常查询",
                "Catalog 延迟恢复",
                "元数据相关报错消失"
            ),
            Arrays.asList(
                "不要把 INVALIDATE METADATA 当成日常通用刷新手段。",
                "不要在高并发时对大量表做全局失效。"
            )
        );

        save(
            "IMPALA",
            "official-impala-compute-stats",
            "Impala COMPUTE STATS 与执行计划优化",
            "基于 Cloudera Impala 统计信息文档提炼，适用于基数估算错误、Join 计划异常、扫描放大和突然变慢的问题。",
            "执行计划中出现 stale stats、missing stats、bad cardinality、broadcast misfire 等信号时优先匹配。",
            "SEV3",
            true,
            "Apache Impala COMPUTE STATS Statement",
            "https://impala.apache.org/docs/build3x/html/topics/impala_compute_stats.html",
            Arrays.asList(
                "同一 SQL 执行计划突然劣化",
                "Join 顺序明显异常",
                "大表扫描和 shuffle 显著放大"
            ),
            Arrays.asList("compute stats", "stale stats", "cardinality", "join", "profile"),
            Arrays.asList(
                "先确认关键表和关键分区统计信息是否过期或缺失。",
                "优先对高频大表做增量或分区级统计刷新，再观察计划变化。",
                "结合 PROFILE 验证估算行数与实际行数是否重新接近。"
            ),
            Arrays.asList(
                "执行计划恢复合理",
                "PROFILE 中估算与实际偏差收敛",
                "关键 SQL 延迟明显回落"
            ),
            Arrays.asList(
                "不要在装载尚未稳定完成时立刻刷新全部统计。",
                "不要只靠 SQL hint 掩盖长期缺失的统计信息。"
            )
        );
    }

    private void seedCrossComponentGuides() {
        save(
            "CROSS_COMPONENT",
            "official-cross-hive-impala-metadata-chain",
            "Hive / Impala 跨引擎元数据链路排查",
            "结合 Apache Hive 和 Cloudera Impala 官方文档整理，适用于 Hive 写入成功但 Impala 查询异常、分区可见性不一致和元数据传播滞后。",
            "当同一问题同时涉及 Hive、Metastore、Impala 和 HDFS 时优先匹配。",
            "SEV2",
            true,
            "Apache Hive + Apache Impala Official Docs",
            "https://hive.apache.org/docs/latest/language/languagemanual-ddl/",
            Arrays.asList(
                "Hive 写入成功但 Impala 仍查询旧数据",
                "新分区只在某一个引擎中可见",
                "Metastore、Catalog 与 HDFS 目录状态不一致"
            ),
            Arrays.asList("hive", "impala", "metastore", "refresh", "partition", "catalog"),
            Arrays.asList(
                "先确认 HDFS 目录和 Hive Metastore 分区元数据是否已更新。",
                "对 Impala 侧选择 REFRESH 或 INVALIDATE METADATA，并验证结果。",
                "补齐统计信息与元数据刷新链路，避免跨引擎写入后长期依赖人工修复。"
            ),
            Arrays.asList(
                "Hive 与 Impala 对同一对象的可见性恢复一致",
                "分区与行数抽样结果一致",
                "Catalog 和 Metastore 相关告警回落"
            ),
            Arrays.asList(
                "不要在未确认 HDFS 数据已落盘完成前刷新元数据。",
                "不要把跨引擎问题简单归因到单一组件。"
            )
        );
    }

    private void save(String domain, String scenarioKey, String title, String summary, String applicability,
                      String riskLevel, boolean requiresApproval, String sourceName, String sourceUrl,
                      List<String> symptoms, List<String> keywords, List<String> steps,
                      List<String> validations, List<String> cautions) {
        KnowledgeArticleEntity entity = repository.findByScenarioKey(scenarioKey)
            .orElseGet(KnowledgeArticleEntity::new);
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
