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
WHERE i.incident_no = 'HG-20260324-001'
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
WHERE i.incident_no = 'HG-20260324-002'
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
WHERE i.incident_no = 'HG-20260324-001'
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
WHERE i.incident_no = 'HG-20260324-002'
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
WHERE i.incident_no = 'HG-20260324-002'
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
    '热点目录中小文件密集写入叠加高频元数据访问，导致 NameNode RPC 延迟上升，进而影响依赖 HDFS 元数据操作的任务与查询。',
    '影响多个依赖 HDFS 元数据操作的任务和查询，持续约 40 分钟，未出现数据持久性风险。',
    NOW() - INTERVAL '12 minutes'
FROM incident_event i
WHERE i.incident_no = 'HG-20260324-002'
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
        ('HG-20260324-002', 0, '01:42 告警触发，NameNode RPC 延迟与活跃文件数同时升高。'),
        ('HG-20260324-002', 1, '01:49 值班同学确认 DataNode 与副本状态基本正常，初步排除存储节点大面积故障。'),
        ('HG-20260324-002', 2, '01:58 执行只读诊断采集，识别热点目录和小文件特征。'),
        ('HG-20260324-002', 3, '02:10 明确问题核心在元数据层拥堵，而不是 NameNode 进程异常。')
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
        ('HG-20260324-002', 0, '对热点目录建立小文件规模阈值和写入增长趋势预警。'),
        ('HG-20260324-002', 1, '在高峰窗口前对高频元数据操作作业做治理或错峰。'),
        ('HG-20260324-002', 2, '补充 NameNode RPC 延迟与活跃文件数联动告警。')
) AS v(incident_no, order_no, prevention_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM postmortem_prevention_item t
    WHERE t.postmortem_id = p.id AND t.order_no = v.order_no
);
