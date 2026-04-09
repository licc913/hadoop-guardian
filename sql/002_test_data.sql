INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-20260324-001', 'MANUAL_TEST', 'manual-yarn-001', 'prod-cluster', 'YARN', 'CRITICAL',
    'YARN 队列 pending 持续堆积',
    'root.default 队列在最近 15 分钟内 pending 应用持续增长，Hive on Tez 作业获取容器明显变慢。',
    '影响 Hive on Tez 离线任务与部分资源敏感型作业。', 'guardian', 'OPEN', NOW() - INTERVAL '18 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-20260324-001');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-20260324-002', 'MANUAL_TEST', 'manual-hdfs-001', 'prod-cluster', 'HDFS', 'HIGH',
    'NameNode RPC 延迟升高',
    'NameNode RPC 处理耗时升高，同时活跃文件数与目录扫描压力上升，疑似小文件与热点目录共同放大元数据压力。',
    '影响多个依赖 HDFS 元数据操作的查询和任务。', 'guardian', 'DIAGNOSING', NOW() - INTERVAL '42 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-20260324-002');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-20260324-003', 'MANUAL_TEST', 'manual-impala-001', 'prod-cluster', 'IMPALA', 'HIGH',
    'Impala admission control 拥堵',
    '高峰期多个大查询同时进入 admission control，导致查询排队时长升高，用户侧感知明显变慢。',
    '影响交互式分析和部分看板查询。', 'guardian', 'OPEN', NOW() - INTERVAL '9 minutes'
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-20260324-003');

INSERT INTO incident_evidence_item (incident_id, order_no, evidence_text)
SELECT i.id, v.order_no, v.evidence_text
FROM incident_event i
JOIN (
    VALUES
        ('HG-20260324-001', 0, '队列 root.default pending 数在 15 分钟内从 12 增长到 147。'),
        ('HG-20260324-001', 1, 'NodeManager 整体健康正常，问题更偏向资源争抢而非节点失效。'),
        ('HG-20260324-001', 2, 'Hive on Tez 应用获取首个 container 的耗时显著升高。'),
        ('HG-20260324-002', 0, 'NameNode RPC 平均耗时升高，JMX 指标显示活跃文件数持续增长。'),
        ('HG-20260324-002', 1, '疑似热点目录存在大量小文件，触发频繁 listStatus 与 getBlockLocations。'),
        ('HG-20260324-002', 2, 'DataNode 整体存活正常，暂未见副本异常集中爆发。'),
        ('HG-20260324-003', 0, 'Impala admission control 等待队列持续存在，平均等待时长超过 90 秒。'),
        ('HG-20260324-003', 1, '集群 CPU 未打满，内存保留与并发查询阈值更像主要限制项。')
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
        ('HG-20260324-001', 0, '未确认资源争抢根因前不要直接重启 ResourceManager。'),
        ('HG-20260324-001', 1, '不要直接调整全局队列容量，避免扩大影响面。'),
        ('HG-20260324-002', 0, '未核实热点目录前不要直接重启 NameNode。'),
        ('HG-20260324-002', 1, '不要在高峰期执行大规模 fsck 或全量目录扫描。'),
        ('HG-20260324-003', 0, '未确认热点 SQL 前不要盲目扩大全局并发阈值。')
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
WHERE i.incident_no = 'HG-20260324-001'
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
WHERE i.incident_no = 'HG-20260324-002'
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
        ('HG-20260324-001', 'YARN', 0, '优先确认是队列容量治理问题还是租户瞬时突发流量。'),
        ('HG-20260324-001', 'YARN', 1, '补采 ResourceManager 调度日志和队列指标，识别主要资源占用方。'),
        ('HG-20260324-001', 'YARN', 2, '必要时按审批流程对特定租户作业做限流或错峰。'),
        ('HG-20260324-002', 'HDFS', 0, '先定位热点目录和小文件来源，再决定是否做目录级治理。'),
        ('HG-20260324-002', 'HDFS', 1, '抓取 NameNode GC、RPC 和 top user 操作记录做交叉确认。'),
        ('HG-20260324-002', 'HDFS', 2, '避免在高峰期进行大范围元数据扫描操作。')
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
        ('HG-20260324-001', 'YARN', 0, '补充队列维度容量治理基线。'),
        ('HG-20260324-001', 'YARN', 1, '评估 Hive on Tez 高峰窗口的作业准入策略。'),
        ('HG-20260324-002', 'HDFS', 0, '补充热点目录小文件治理计划。'),
        ('HG-20260324-002', 'HDFS', 1, '建立 NameNode RPC 延迟与文件数增长的基线告警。')
) AS v(incident_no, subsystem, order_no, followup_text)
  ON i.incident_no = v.incident_no AND d.subsystem = v.subsystem
WHERE NOT EXISTS (
    SELECT 1 FROM diagnosis_followup_item f
    WHERE f.diagnosis_id = d.id AND f.order_no = v.order_no
);
