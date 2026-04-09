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
SELECT TRUE, 'diagnose_hdfs_hotspots', 'D:\\hadoop-guardian\\scripts\\diagnose_hdfs.py',
       '--path --window-minutes --topk', 180, FALSE, 'namenode-hosts', 'HDFS',
       '读取热点目录、小文件密度和 NameNode 元数据压力相关信号。'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_hdfs_hotspots'
);

INSERT INTO diagnostic_script_registry (
    enabled, script_name, command_path, allowed_args, timeout_seconds, requires_approval, host_scope, service_scope, description
)
SELECT TRUE, 'diagnose_yarn_queues', 'D:\\hadoop-guardian\\scripts\\diagnose_yarn.py',
       '--queue --window-minutes --tenant', 180, TRUE, 'resourcemanager-hosts', 'YARN',
       '分析队列 pending、资源争抢和租户高峰冲击。'
WHERE NOT EXISTS (
    SELECT 1 FROM diagnostic_script_registry WHERE script_name = 'diagnose_yarn_queues'
);
