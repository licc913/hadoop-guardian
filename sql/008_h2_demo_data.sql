MERGE INTO cloudera_manager_settings (id, enabled, base_url, api_version, username, password, cluster_name)
KEY (id)
VALUES (1, FALSE, 'http://cm.example.local:7180', 'v51', 'admin', 'admin', 'prod-cluster');

MERGE INTO log_source_settings (id, enabled, provider_type, base_url, auth_type, auth_token, index_pattern, default_time_window_minutes)
KEY (id)
VALUES (1, TRUE, 'ELASTICSEARCH', 'http://log-gateway.internal:9200', 'BASIC', 'elastic-reader-token', 'hadoop-guardian-*', 30);

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
       'Collect hotspot directory and NameNode metadata pressure evidence.'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_hdfs_hotspots'
);

INSERT INTO diagnostic_script_registry (
    enabled, script_name, command_path, allowed_args, timeout_seconds, requires_approval, host_scope, service_scope, description
)
SELECT TRUE, 'diagnose_yarn_queues', 'D:\hadoop-guardian\scripts\diagnose_yarn.py',
       '--queue --window-minutes --tenant', 180, TRUE, 'resourcemanager-hosts', 'YARN',
       'Analyze queue contention, pending apps, and tenant spikes.'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_yarn_queues'
);

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-H2-001', 'MANUAL_TEST', 'manual-yarn-001', 'prod-cluster', 'YARN', 'CRITICAL',
    'YARN queue pending backlog',
    'The root.default queue backlog has grown for 15 minutes and Hive on Tez jobs are waiting longer for containers.',
    'Affects Hive on Tez batch pipelines and latency-sensitive jobs.', 'guardian', 'OPEN',
    DATEADD('MINUTE', -18, CURRENT_TIMESTAMP())
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-H2-001');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-H2-002', 'MANUAL_TEST', 'manual-hdfs-001', 'prod-cluster', 'HDFS', 'HIGH',
    'NameNode RPC latency elevated',
    'NameNode RPC latency is elevated and metadata operations are slower than normal, likely driven by small files in a hotspot path.',
    'Affects metadata-heavy reads and Hive jobs that depend on HDFS listings.', 'guardian', 'DIAGNOSING',
    DATEADD('MINUTE', -42, CURRENT_TIMESTAMP())
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-H2-002');

INSERT INTO incident_event (
    incident_no, source_type, source_id, cluster_name, service_type, severity, title, summary,
    impact_scope, owner, status, occurred_at
)
SELECT
    'HG-H2-003', 'MANUAL_TEST', 'manual-impala-001', 'prod-cluster', 'IMPALA', 'MEDIUM',
    'Impala admission control queueing',
    'Interactive queries are queued in admission control during business hours and dashboards are slower than expected.',
    'Affects BI dashboard refreshes and ad hoc analyst queries.', 'guardian', 'OPEN',
    DATEADD('MINUTE', -9, CURRENT_TIMESTAMP())
WHERE NOT EXISTS (SELECT 1 FROM incident_event WHERE incident_no = 'HG-H2-003');

INSERT INTO incident_evidence_item (incident_id, order_no, evidence_text)
SELECT i.id, v.order_no, v.evidence_text
FROM incident_event i
JOIN (
    VALUES
        ('HG-H2-001', 0, 'root.default pending apps rose from 12 to 147 in 15 minutes.'),
        ('HG-H2-001', 1, 'NodeManager health is stable, suggesting contention instead of host failure.'),
        ('HG-H2-001', 2, 'Hive on Tez first-container wait time is above normal.'),
        ('HG-H2-002', 0, 'NameNode RPC latency and metadata load increased together.'),
        ('HG-H2-002', 1, 'A hotspot directory shows a rapid increase in small files.'),
        ('HG-H2-002', 2, 'DataNode availability is healthy and replication looks stable.'),
        ('HG-H2-003', 0, 'Impala query pool has queued requests for more than 90 seconds.'),
        ('HG-H2-003', 1, 'Cluster CPU is not saturated, so admission policy is the likely bottleneck.')
) v(incident_no, order_no, evidence_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM incident_evidence_item e
    WHERE e.incident_id = i.id AND e.order_no = v.order_no
);

INSERT INTO incident_avoided_action (incident_id, order_no, action_text)
SELECT i.id, v.order_no, v.action_text
FROM incident_event i
JOIN (
    VALUES
        ('HG-H2-001', 0, 'Do not restart ResourceManager before confirming scheduler contention.'),
        ('HG-H2-001', 1, 'Do not expand all queue limits without approval.'),
        ('HG-H2-002', 0, 'Do not restart NameNode before confirming metadata pressure.'),
        ('HG-H2-002', 1, 'Do not run a full fsck during peak traffic.'),
        ('HG-H2-003', 0, 'Do not disable admission control globally as a quick fix.')
) v(incident_no, order_no, action_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM incident_avoided_action a
    WHERE a.incident_id = i.id AND a.order_no = v.order_no
);

INSERT INTO diagnosis_result (
    incident_id, subsystem, root_cause, confidence, impact_level, cross_component_path, created_at
)
SELECT
    i.id, 'YARN',
    'Queue contention is most likely caused by bursty Hive on Tez demand competing for the same scheduler capacity.',
    0.87, 'SEV1', 'Hive on Tez -> YARN', DATEADD('MINUTE', -10, CURRENT_TIMESTAMP())
FROM incident_event i
WHERE i.incident_no = 'HG-H2-001'
  AND NOT EXISTS (
      SELECT 1 FROM diagnosis_result d
      WHERE d.incident_id = i.id AND d.subsystem = 'YARN'
  );

INSERT INTO diagnosis_result (
    incident_id, subsystem, root_cause, confidence, impact_level, cross_component_path, created_at
)
SELECT
    i.id, 'HDFS',
    'A hotspot directory with many small files is amplifying NameNode metadata pressure and increasing RPC latency.',
    0.81, 'SEV2', 'Hive on Tez -> HDFS', DATEADD('MINUTE', -26, CURRENT_TIMESTAMP())
FROM incident_event i
WHERE i.incident_no = 'HG-H2-002'
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
        ('HG-H2-001', 'YARN', 0, 'Inspect queue capacity, max capacity, and active application counts before changing policy.'),
        ('HG-H2-001', 'YARN', 1, 'Collect ResourceManager scheduler logs and identify the top resource consumers.'),
        ('HG-H2-001', 'YARN', 2, 'If needed, throttle only the busiest tenant or move jobs into a lower peak window.'),
        ('HG-H2-002', 'HDFS', 0, 'Confirm the hotspot path and small-file growth source before any remediation.'),
        ('HG-H2-002', 'HDFS', 1, 'Capture NameNode GC, RPC, and top-user evidence for cross-checking.'),
        ('HG-H2-002', 'HDFS', 2, 'Avoid metadata-heavy scans during peak hours.')
) v(incident_no, subsystem, order_no, recommendation_text)
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
        ('HG-H2-001', 'YARN', 0, 'Add a queue baseline for pending growth and first-container latency.'),
        ('HG-H2-001', 'YARN', 1, 'Review peak-hour Hive on Tez submission strategy.'),
        ('HG-H2-002', 'HDFS', 0, 'Create a small-file cleanup plan for the hotspot directory.'),
        ('HG-H2-002', 'HDFS', 1, 'Add a combined alert on NameNode RPC latency and file-count growth.')
) v(incident_no, subsystem, order_no, followup_text)
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
    i.id, d.id, 'Throttle peak Hive workloads', 'GUARDED_SCRIPT', 'MEDIUM', TRUE,
    'Temporarily reduce the most expensive Hive on Tez submissions during the peak window and re-check pending depth.',
    'PENDING_APPROVAL', DATEADD('MINUTE', -9, CURRENT_TIMESTAMP())
FROM incident_event i
JOIN diagnosis_result d ON d.incident_id = i.id AND d.subsystem = 'YARN'
WHERE i.incident_no = 'HG-H2-001'
  AND NOT EXISTS (
      SELECT 1 FROM action_recommendation a
      WHERE a.incident_id = i.id AND a.action_name = 'Throttle peak Hive workloads'
  );

INSERT INTO action_recommendation (
    incident_id, diagnosis_result_id, action_name, action_type, risk_level,
    requires_approval, recommendation_text, status, created_at
)
SELECT
    i.id, d.id, 'Collect NameNode evidence pack', 'DIAGNOSTIC_COLLECTION', 'LOW', FALSE,
    'Capture NameNode RPC, GC, hotspot directory samples, and the most recent top-user access profile.',
    'APPROVED', DATEADD('MINUTE', -25, CURRENT_TIMESTAMP())
FROM incident_event i
JOIN diagnosis_result d ON d.incident_id = i.id AND d.subsystem = 'HDFS'
WHERE i.incident_no = 'HG-H2-002'
  AND NOT EXISTS (
      SELECT 1 FROM action_recommendation a
      WHERE a.incident_id = i.id AND a.action_name = 'Collect NameNode evidence pack'
  );

INSERT INTO approval_record (
    incident_id, action_recommendation_id, approval_status, requested_by, approver, comment, requested_at, decided_at
)
SELECT
    i.id, a.id, 'PENDING', 'guardian-operator', NULL,
    'Waiting for on-call lead confirmation before limiting peak jobs.',
    DATEADD('MINUTE', -8, CURRENT_TIMESTAMP()), NULL
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = 'Throttle peak Hive workloads'
WHERE i.incident_no = 'HG-H2-001'
  AND NOT EXISTS (
      SELECT 1 FROM approval_record r
      WHERE r.incident_id = i.id AND r.action_recommendation_id = a.id
  );

INSERT INTO approval_record (
    incident_id, action_recommendation_id, approval_status, requested_by, approver, comment, requested_at, decided_at
)
SELECT
    i.id, a.id, 'APPROVED', 'guardian-operator', 'oncall-lead',
    'Read-only evidence collection is low risk and can proceed immediately.',
    DATEADD('MINUTE', -24, CURRENT_TIMESTAMP()), DATEADD('MINUTE', -23, CURRENT_TIMESTAMP())
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = 'Collect NameNode evidence pack'
WHERE i.incident_no = 'HG-H2-002'
  AND NOT EXISTS (
      SELECT 1 FROM approval_record r
      WHERE r.incident_id = i.id AND r.action_recommendation_id = a.id
  );

INSERT INTO execution_record (
    incident_id, action_recommendation_id, execution_status, executor, execution_summary, started_at, finished_at
)
SELECT
    i.id, a.id, 'SUCCESS', 'guardian-bot',
    'Captured NameNode RPC metrics, GC signal snapshots, hotspot directory samples, and recent access evidence.',
    DATEADD('MINUTE', -22, CURRENT_TIMESTAMP()), DATEADD('MINUTE', -20, CURRENT_TIMESTAMP())
FROM incident_event i
JOIN action_recommendation a ON a.incident_id = i.id AND a.action_name = 'Collect NameNode evidence pack'
WHERE i.incident_no = 'HG-H2-002'
  AND NOT EXISTS (
      SELECT 1 FROM execution_record e
      WHERE e.incident_id = i.id AND e.action_recommendation_id = a.id
  );

INSERT INTO postmortem_record (
    incident_id, summary, root_cause, impact_statement, updated_at
)
SELECT
    i.id,
    'A hotspot directory amplified NameNode metadata pressure and degraded metadata-heavy workloads.',
    'Small-file growth in a frequently scanned path pushed NameNode RPC latency above the normal operating baseline.',
    'Several metadata-heavy jobs and listings were slower for roughly 40 minutes, but no data durability risk was observed.',
    DATEADD('MINUTE', -12, CURRENT_TIMESTAMP())
FROM incident_event i
WHERE i.incident_no = 'HG-H2-002'
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
        ('HG-H2-002', 0, '01:42 alert fired for elevated NameNode RPC latency and rising file count.'),
        ('HG-H2-002', 1, '01:49 on-call confirmed DataNode health and ruled out replication instability.'),
        ('HG-H2-002', 2, '01:58 read-only evidence collection identified a hotspot directory and small-file pattern.'),
        ('HG-H2-002', 3, '02:10 incident narrowed to metadata pressure instead of a NameNode process fault.')
) v(incident_no, order_no, timeline_text) ON i.incident_no = v.incident_no
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
        ('HG-H2-002', 0, 'Alert on hotspot-path file growth before it materially increases metadata load.'),
        ('HG-H2-002', 1, 'Throttle or reschedule metadata-heavy maintenance before the peak window.'),
        ('HG-H2-002', 2, 'Track NameNode RPC latency together with namespace growth for faster triage.')
) v(incident_no, order_no, prevention_text) ON i.incident_no = v.incident_no
WHERE NOT EXISTS (
    SELECT 1 FROM postmortem_prevention_item t
    WHERE t.postmortem_id = p.id AND t.order_no = v.order_no
);
