INSERT INTO cloudera_manager_settings (
    id, enabled, base_url, api_version, username, password, cluster_name
)
VALUES (
    1, TRUE, 'http://cm.prod.local:7180', 'v51', 'admin', 'admin', '生产集群-A'
)
ON CONFLICT (id) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    base_url = EXCLUDED.base_url,
    api_version = EXCLUDED.api_version,
    username = EXCLUDED.username,
    password = EXCLUDED.password,
    cluster_name = EXCLUDED.cluster_name;

INSERT INTO log_source_settings (
    id, enabled, provider_type, base_url, auth_type, auth_token, index_pattern, default_time_window_minutes
)
VALUES (
    1, TRUE, 'ELASTICSEARCH', 'http://log-gateway.internal:9200', 'BASIC', 'elastic-reader-token',
    'hadoop-guardian-*', 30
)
ON CONFLICT (id) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    provider_type = EXCLUDED.provider_type,
    base_url = EXCLUDED.base_url,
    auth_type = EXCLUDED.auth_type,
    auth_token = EXCLUDED.auth_token,
    index_pattern = EXCLUDED.index_pattern,
    default_time_window_minutes = EXCLUDED.default_time_window_minutes;

INSERT INTO jmx_endpoint_registry (
    enabled, service_type, role_type, target_host, port, path, protocol, auth_type, username, password, metric_whitelist
)
SELECT TRUE, 'HDFS', 'NAMENODE', 'nn01.prod.local', 9870, '/jmx', 'HTTP', 'NONE', NULL, NULL,
       'RpcActivityForPort8020,FSNamesystem,JvmMetrics'
WHERE NOT EXISTS (
    SELECT 1 FROM jmx_endpoint_registry
    WHERE service_type = 'HDFS' AND role_type = 'NAMENODE' AND target_host = 'nn01.prod.local'
);

INSERT INTO jmx_endpoint_registry (
    enabled, service_type, role_type, target_host, port, path, protocol, auth_type, username, password, metric_whitelist
)
SELECT TRUE, 'YARN', 'RESOURCEMANAGER', 'rm01.prod.local', 8088, '/jmx', 'HTTP', 'NONE', NULL, NULL,
       'Hadoop:service=ResourceManager,name=QueueMetrics,q0=root,Hadoop:service=ResourceManager,name=ClusterMetrics'
WHERE NOT EXISTS (
    SELECT 1 FROM jmx_endpoint_registry
    WHERE service_type = 'YARN' AND role_type = 'RESOURCEMANAGER' AND target_host = 'rm01.prod.local'
);

INSERT INTO diagnostic_script_registry (
    enabled, script_name, command_path, allowed_args, timeout_seconds, requires_approval, host_scope, service_scope, description
)
SELECT TRUE, 'diagnose_hdfs_hotspots', 'D:\hadoop-guardian\scripts\diagnose_hdfs.py',
       '--path --window-minutes --topk', 180, FALSE, 'namenode-hosts', 'HDFS',
       '读取热点目录、小文件密度和 NameNode 元数据压力相关信号。'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_hdfs_hotspots'
);

INSERT INTO diagnostic_script_registry (
    enabled, script_name, command_path, allowed_args, timeout_seconds, requires_approval, host_scope, service_scope, description
)
SELECT TRUE, 'diagnose_yarn_queues', 'D:\hadoop-guardian\scripts\diagnose_yarn.py',
       '--queue --window-minutes --tenant', 180, TRUE, 'resourcemanager-hosts', 'YARN',
       '分析队列 pending、资源争抢和租户高峰冲击。'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_yarn_queues'
);

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-PG-ZH-001', 'MANUAL_TEST', 'pg-zh-yarn-001', '生产集群-A', 'YARN', 'CRITICAL',
    'YARN 队列 pending 持续堆积',
    'root.default 队列在最近 15 分钟内 pending 应用持续增长，Hive on Tez 作业获取容器明显变慢。',
    '影响 Hive on Tez 离线任务与部分资源敏感型作业。', 'guardian', 'OPEN', NOW() - INTERVAL '18 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-PG-ZH-001');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-PG-ZH-002', 'MANUAL_TEST', 'pg-zh-hdfs-001', '生产集群-A', 'HDFS', 'HIGH',
    'NameNode RPC 延迟升高',
    'NameNode RPC 处理耗时升高，同时活跃文件数与目录扫描压力上升，疑似小文件与热点目录共同放大元数据压力。',
    '影响多个依赖 HDFS 元数据操作的查询和任务。', 'guardian', 'DIAGNOSING', NOW() - INTERVAL '42 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-PG-ZH-002');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-PG-ZH-003', 'MANUAL_TEST', 'pg-zh-impala-001', '生产集群-A', 'IMPALA', 'HIGH',
    'Impala admission control 拥堵',
    '高峰期多个大查询同时进入 admission control，导致查询排队时间显著升高，用户侧体感明显变慢。',
    '影响交互式分析和部分看板查询。', 'guardian', 'OPEN', NOW() - INTERVAL '9 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-PG-ZH-003');

INSERT INTO incident_evidence_item (incident_id, order_no, evidence_text)
SELECT i.id, v.order_no, v.evidence_text
FROM incident_event i
JOIN (
    VALUES
        ('HG-PG-ZH-001', 0, '队列 root.default pending 数在 15 分钟内从 12 增长到 147。'),
        ('HG-PG-ZH-001', 1, 'NodeManager 整体健康正常，问题更偏向资源争抢而非节点失效。'),
        ('HG-PG-ZH-001', 2, 'Hive on Tez 应用获取首个 container 的耗时显著升高。'),
        ('HG-PG-ZH-002', 0, 'NameNode RPC 平均耗时升高，JMX 指标显示活跃文件数持续增加。'),
        ('HG-PG-ZH-002', 1, '疑似热点目录存在大量小文件，触发频繁 listStatus 与 getBlockLocations。'),
        ('HG-PG-ZH-002', 2, 'DataNode 整体存活正常，暂未见副本异常集中爆发。'),
        ('HG-PG-ZH-003', 0, 'Impala admission control 等待队列持续存在，平均等待时长超过 90 秒。'),
        ('HG-PG-ZH-003', 1, '集群 CPU 未打满，内存保留与并发查询阈值更像主要限制项。')
) AS v(incident_no, order_no, evidence_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM incident_evidence_item e
    WHERE e.incident_id = i.id AND e.order_no = v.order_no
);

INSERT INTO incident_avoided_action (incident_id, order_no, action_text)
SELECT i.id, v.order_no, v.action_text
FROM incident_event i
JOIN (
    VALUES
        ('HG-PG-ZH-001', 0, '未确认资源争抢根因前，不要直接重启 ResourceManager。'),
        ('HG-PG-ZH-001', 1, '不要直接调整全局队列容量，避免扩大影响面。'),
        ('HG-PG-ZH-002', 0, '未核实热点目录前，不要直接重启 NameNode。'),
        ('HG-PG-ZH-002', 1, '不要在高峰期执行大范围 fsck 或全量目录扫描。'),
        ('HG-PG-ZH-003', 0, '未确认热点 SQL 前，不要盲目扩大全局并发阈值。')
) AS v(incident_no, order_no, action_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM incident_avoided_action a
    WHERE a.incident_id = i.id AND a.order_no = v.order_no
);

INSERT INTO diagnosis_result (
    incident_id, subsystem, root_cause, confidence, impact_level, cross_component_path, created_at
)
SELECT
    i.id,
    'YARN',
    'YARN 队列资源被批量 Hive on Tez 作业持续占用，导致交互式与离线混部资源竞争放大。',
    0.87,
    'SEV1',
    'Hive on Tez -> YARN',
    NOW() - INTERVAL '10 minutes'
FROM incident_event i
WHERE i.incident_no = 'HG-PG-ZH-001'
  AND NOT EXISTS (
      SELECT 1 FROM diagnosis_result d
      WHERE d.incident_id = i.id AND d.subsystem = 'YARN'
  );

INSERT INTO diagnosis_result (
    incident_id, subsystem, root_cause, confidence, impact_level, cross_component_path, created_at
)
SELECT
    i.id,
    'HDFS',
    '热点目录小文件和频繁元数据访问共同推高 NameNode RPC 压力，问题更接近元数据层拥堵而非 DataNode 故障。',
    0.81,
    'SEV2',
    'Hive on Tez -> HDFS',
    NOW() - INTERVAL '26 minutes'
FROM incident_event i
WHERE i.incident_no = 'HG-PG-ZH-002'
  AND NOT EXISTS (
      SELECT 1 FROM diagnosis_result d
      WHERE d.incident_id = i.id AND d.subsystem = 'HDFS'
  );

INSERT INTO diagnosis_recommendation_item (diagnosis_id, order_no, recommendation_text)
SELECT d.id, v.order_no, v.recommendation_text
FROM diagnosis_result d
JOIN incident_event i ON i.id = d.incident_id
JOIN (
    VALUES
        ('HG-PG-ZH-001', 'YARN', 0, '优先确认是队列容量治理问题，还是租户瞬时突发流量。'),
        ('HG-PG-ZH-001', 'YARN', 1, '补采 ResourceManager 调度日志和队列指标，识别主要资源占用方。'),
        ('HG-PG-ZH-001', 'YARN', 2, '必要时按审批流程对特定租户作业做限流或错峰。'),
        ('HG-PG-ZH-002', 'HDFS', 0, '先定位热点目录和小文件来源，再决定是否做目录级治理。'),
        ('HG-PG-ZH-002', 'HDFS', 1, '抓取 NameNode GC、RPC 和 top user 操作记录做交叉确认。'),
        ('HG-PG-ZH-002', 'HDFS', 2, '避免在高峰期进行大范围元数据扫描操作。')
) AS v(incident_no, subsystem, order_no, recommendation_text)
  ON i.incident_no = v.incident_no AND d.subsystem = v.subsystem
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_recommendation_item r
    WHERE r.diagnosis_id = d.id AND r.order_no = v.order_no
);

INSERT INTO diagnosis_followup_item (diagnosis_id, order_no, followup_text)
SELECT d.id, v.order_no, v.followup_text
FROM diagnosis_result d
JOIN incident_event i ON i.id = d.incident_id
JOIN (
    VALUES
        ('HG-PG-ZH-001', 'YARN', 0, '补充队列维度容量治理基线。'),
        ('HG-PG-ZH-001', 'YARN', 1, '评估 Hive on Tez 高峰窗口的作业准入策略。'),
        ('HG-PG-ZH-002', 'HDFS', 0, '补充热点目录小文件治理计划。'),
        ('HG-PG-ZH-002', 'HDFS', 1, '建立 NameNode RPC 延迟与活跃文件数增长的基线告警。')
) AS v(incident_no, subsystem, order_no, followup_text)
  ON i.incident_no = v.incident_no AND d.subsystem = v.subsystem
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_followup_item f
    WHERE f.diagnosis_id = d.id AND f.order_no = v.order_no
);

INSERT INTO action_recommendation (
    incident_id, diagnosis_result_id, action_name, action_type, risk_level,
    requires_approval, recommendation_text, status, created_at
)
SELECT
    i.id, d.id, '限制高峰作业准入', 'GUARDED_SCRIPT', 'MEDIUM', TRUE,
    '对高峰窗口内的 Hive on Tez 大作业执行限流或错峰，优先缓解 YARN 队列拥堵，再观察 pending 和首个 container 获取耗时。',
    'PENDING_APPROVAL', NOW() - INTERVAL '9 minutes'
FROM incident_event i
JOIN diagnosis_result d ON d.incident_id = i.id AND d.subsystem = 'YARN'
WHERE i.incident_no = 'HG-PG-ZH-001'
  AND NOT EXISTS (
      SELECT 1 FROM action_recommendation a
      WHERE a.incident_id = i.id AND a.action_name = '限制高峰作业准入'
  );

INSERT INTO action_recommendation (
    incident_id, diagnosis_result_id, action_name, action_type, risk_level,
    requires_approval, recommendation_text, status, created_at
)
SELECT
    i.id, d.id, '补采 NameNode 诊断上下文', 'DIAGNOSTIC_COLLECTION', 'LOW', FALSE,
    '抓取 NameNode RPC、GC、top user 操作记录和热点目录样本，先补证据再决定是否需要目录级治理。',
    'APPROVED', NOW() - INTERVAL '25 minutes'
FROM incident_event i
JOIN diagnosis_result d ON d.incident_id = i.id AND d.subsystem = 'HDFS'
WHERE i.incident_no = 'HG-PG-ZH-002'
  AND NOT EXISTS (
      SELECT 1 FROM action_recommendation a
      WHERE a.incident_id = i.id AND a.action_name = '补采 NameNode 诊断上下文'
  );

INSERT INTO approval_record (
    incident_id, action_recommendation_id, approval_status, requested_by, approver, comment, requested_at, decided_at
)
SELECT
    i.id, a.id, 'PENDING', 'guardian-operator', NULL,
    '等待值班负责人确认是否在高峰窗口内执行限流。', NOW() - INTERVAL '8 minutes', NULL
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = '限制高峰作业准入'
WHERE i.incident_no = 'HG-PG-ZH-001'
  AND NOT EXISTS (
      SELECT 1 FROM approval_record r
      WHERE r.incident_id = i.id AND r.action_recommendation_id = a.id
  );

INSERT INTO approval_record (
    incident_id, action_recommendation_id, approval_status, requested_by, approver, comment, requested_at, decided_at
)
SELECT
    i.id, a.id, 'APPROVED', 'guardian-operator', 'oncall-lead',
    '只读采集动作风险较低，允许立即执行。', NOW() - INTERVAL '24 minutes', NOW() - INTERVAL '23 minutes'
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = '补采 NameNode 诊断上下文'
WHERE i.incident_no = 'HG-PG-ZH-002'
  AND NOT EXISTS (
      SELECT 1 FROM approval_record r
      WHERE r.incident_id = i.id AND r.action_recommendation_id = a.id
  );

INSERT INTO execution_record (
    incident_id, action_recommendation_id, execution_status, executor, execution_summary, started_at, finished_at
)
SELECT
    i.id, a.id, 'SUCCESS', 'guardian-bot',
    '已完成 NameNode 诊断上下文采集，补齐了 RPC 指标快照、热点目录样本和最近 30 分钟日志片段。',
    NOW() - INTERVAL '22 minutes', NOW() - INTERVAL '20 minutes'
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = '补采 NameNode 诊断上下文'
WHERE i.incident_no = 'HG-PG-ZH-002'
  AND NOT EXISTS (
      SELECT 1 FROM execution_record e
      WHERE e.incident_id = i.id AND e.action_recommendation_id = a.id
  );

INSERT INTO postmortem_record (
    incident_id, summary, root_cause, impact_statement, updated_at
)
SELECT
    i.id,
    '一次由热点目录小文件放大 NameNode 元数据压力的存储侧退化事件。',
    '热点目录中的小文件密集写入叠加高频元数据访问，导致 NameNode RPC 延迟上升，进而影响依赖 HDFS 元数据操作的任务与查询。',
    '影响多个依赖 HDFS 元数据操作的任务和查询，持续约 40 分钟，未出现数据持久性风险。',
    NOW() - INTERVAL '12 minutes'
FROM incident_event i
WHERE i.incident_no = 'HG-PG-ZH-002'
  AND NOT EXISTS (
      SELECT 1 FROM postmortem_record p
      WHERE p.incident_id = i.id
  );

INSERT INTO postmortem_timeline_item (postmortem_id, order_no, timeline_text)
SELECT p.id, v.order_no, v.timeline_text
FROM postmortem_record p
JOIN incident_event i ON i.id = p.incident_id
JOIN (
    VALUES
        ('HG-PG-ZH-002', 0, '01:42 告警触发，NameNode RPC 延迟与活跃文件数同时升高。'),
        ('HG-PG-ZH-002', 1, '01:49 值班同学确认 DataNode 与副本状态基本正常，初步排除存储节点大面积故障。'),
        ('HG-PG-ZH-002', 2, '01:58 执行只读诊断采集，识别出热点目录和小文件特征。'),
        ('HG-PG-ZH-002', 3, '02:10 明确问题核心在元数据层拥堵，而不是 NameNode 进程异常。')
) AS v(incident_no, order_no, timeline_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM postmortem_timeline_item t
    WHERE t.postmortem_id = p.id AND t.order_no = v.order_no
);

INSERT INTO postmortem_prevention_item (postmortem_id, order_no, prevention_text)
SELECT p.id, v.order_no, v.prevention_text
FROM postmortem_record p
JOIN incident_event i ON i.id = p.incident_id
JOIN (
    VALUES
        ('HG-PG-ZH-002', 0, '对热点目录建立小文件规模阈值和写入增长趋势预警。'),
        ('HG-PG-ZH-002', 1, '在高峰窗口前对高频元数据操作作业做治理或错峰。'),
        ('HG-PG-ZH-002', 2, '补充 NameNode RPC 延迟与活跃文件数联动告警。')
) AS v(incident_no, order_no, prevention_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM postmortem_prevention_item t
    WHERE t.postmortem_id = p.id AND t.order_no = v.order_no
);

INSERT INTO knowledge_article (
    domain, scenario_key, title, summary, applicability, risk_level, requires_approval,
    source_name, source_url, created_at, updated_at
)
SELECT
    'YARN', 'zh_yarn_queue_contention',
    'YARN 队列争抢导致 Hive on Tez 作业排队',
    '适用于 ResourceManager 健康正常但队列 pending 快速堆积、首个 container 获取变慢的场景。',
    '当 Hive on Tez 与批量离线作业混部，且用户反馈作业提交后长时间无资源分配时。',
    'MEDIUM', TRUE, 'Guardian Playbook', 'https://guardian.local/knowledge/yarn-queue-contention',
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_article WHERE scenario_key = 'zh_yarn_queue_contention'
);

INSERT INTO knowledge_article (
    domain, scenario_key, title, summary, applicability, risk_level, requires_approval,
    source_name, source_url, created_at, updated_at
)
SELECT
    'HDFS', 'zh_hdfs_namenode_metadata_pressure',
    'NameNode 元数据压力升高导致 RPC 延迟增加',
    '适用于活跃文件数增长、热点目录明显、小文件密集写入并伴随 NameNode RPC 延迟升高的场景。',
    '当 DataNode 基本健康，但 NameNode 指标与元数据访问相关告警同时升高时。',
    'LOW', FALSE, 'Guardian Playbook', 'https://guardian.local/knowledge/hdfs-metadata-pressure',
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_article WHERE scenario_key = 'zh_hdfs_namenode_metadata_pressure'
);

INSERT INTO knowledge_keyword_item (article_id, order_no, keyword_text)
SELECT a.id, v.order_no, v.keyword_text
FROM knowledge_article a
JOIN (
    VALUES
        ('zh_yarn_queue_contention', 0, 'pending'),
        ('zh_yarn_queue_contention', 1, '队列'),
        ('zh_yarn_queue_contention', 2, 'Hive on Tez'),
        ('zh_yarn_queue_contention', 3, 'container'),
        ('zh_hdfs_namenode_metadata_pressure', 0, 'NameNode'),
        ('zh_hdfs_namenode_metadata_pressure', 1, 'RPC'),
        ('zh_hdfs_namenode_metadata_pressure', 2, '小文件'),
        ('zh_hdfs_namenode_metadata_pressure', 3, '热点目录')
) AS v(scenario_key, order_no, keyword_text) ON a.scenario_key = v.scenario_key
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_keyword_item k
    WHERE k.article_id = a.id AND k.order_no = v.order_no
);

INSERT INTO knowledge_step_item (article_id, order_no, step_text)
SELECT a.id, v.order_no, v.step_text
FROM knowledge_article a
JOIN (
    VALUES
        ('zh_yarn_queue_contention', 0, '先确认队列 pending、资源占用率和调度延迟是否同步升高。'),
        ('zh_yarn_queue_contention', 1, '抓取 ResourceManager 调度日志和大租户作业清单，识别资源占用主因。'),
        ('zh_yarn_queue_contention', 2, '如需限流或错峰，先完成审批并明确影响租户。'),
        ('zh_hdfs_namenode_metadata_pressure', 0, '检查 NameNode RPC、GC、活跃文件数与热点目录指标。'),
        ('zh_hdfs_namenode_metadata_pressure', 1, '定位小文件来源并确认是否存在高频 listStatus 或 getBlockLocations。'),
        ('zh_hdfs_namenode_metadata_pressure', 2, '优先做只读证据采集，再决定是否进入治理动作。')
) AS v(scenario_key, order_no, step_text) ON a.scenario_key = v.scenario_key
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_step_item s
    WHERE s.article_id = a.id AND s.order_no = v.order_no
);

INSERT INTO knowledge_validation_item (article_id, order_no, validation_text)
SELECT a.id, v.order_no, v.validation_text
FROM knowledge_article a
JOIN (
    VALUES
        ('zh_yarn_queue_contention', 0, '确认 NodeManager 整体健康正常，避免误判为节点故障。'),
        ('zh_yarn_queue_contention', 1, '确认限制动作是否会影响高优先级业务窗口。'),
        ('zh_hdfs_namenode_metadata_pressure', 0, '确认 DataNode 与副本状态整体正常。'),
        ('zh_hdfs_namenode_metadata_pressure', 1, '确认元数据压力集中在少数热点目录。')
) AS v(scenario_key, order_no, validation_text) ON a.scenario_key = v.scenario_key
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_validation_item s
    WHERE s.article_id = a.id AND s.order_no = v.order_no
);

INSERT INTO knowledge_caution_item (article_id, order_no, caution_text)
SELECT a.id, v.order_no, v.caution_text
FROM knowledge_article a
JOIN (
    VALUES
        ('zh_yarn_queue_contention', 0, '不要在未审批情况下直接限制全局队列。'),
        ('zh_yarn_queue_contention', 1, '不要把资源争抢误判成 ResourceManager 故障。'),
        ('zh_hdfs_namenode_metadata_pressure', 0, '不要在高峰时段直接重启 NameNode。'),
        ('zh_hdfs_namenode_metadata_pressure', 1, '不要在未确认热点目录前执行大范围 fsck。')
) AS v(scenario_key, order_no, caution_text) ON a.scenario_key = v.scenario_key
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_caution_item s
    WHERE s.article_id = a.id AND s.order_no = v.order_no
);
