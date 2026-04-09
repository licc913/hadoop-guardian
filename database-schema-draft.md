# Hadoop Guardian 数据库表设计初稿

## 设计原则

- 以事件、诊断、处置、审计、复盘为主线。
- 不存储 `Cloudera Manager` 已有的完整监控明细，只保留必要索引、快照与诊断结果。
- 大对象内容如日志全文、报告正文优先落对象存储，数据库中保存引用。

## 核心表

### 1. incident_event

告警与事件主表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_no | varchar(64) | 事件编号 |
| source_type | varchar(32) | 来源，如 `CM_ALERT` |
| source_id | varchar(128) | 外部事件 ID |
| cluster_name | varchar(128) | 集群名 |
| service_type | varchar(64) | `HDFS` `YARN` `HIVE_ON_TEZ` `IMPALA` |
| severity | varchar(32) | 严重级别 |
| title | varchar(256) | 事件标题 |
| summary | text | 事件摘要 |
| status | varchar(32) | `OPEN` `DIAGNOSING` `RESOLVED` `CLOSED` |
| occurred_at | timestamp | 发生时间 |
| resolved_at | timestamp | 恢复时间 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

### 2. incident_context_snapshot

事件上下文快照。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| snapshot_type | varchar(64) | `CM_HEALTH` `METRIC_SUMMARY` `ROLE_STATE` |
| snapshot_json | jsonb | 快照内容 |
| collected_at | timestamp | 采集时间 |

### 3. incident_evidence

证据索引表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| evidence_type | varchar(64) | `LOG` `METRIC` `EVENT` `SQL_SAMPLE` |
| source_system | varchar(64) | 来源系统 |
| source_ref | varchar(512) | 外部引用 |
| content_excerpt | text | 摘要片段 |
| object_path | varchar(512) | 对象存储路径 |
| collected_at | timestamp | 采集时间 |

### 4. diagnosis_task

诊断任务表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| task_type | varchar(64) | `AUTO_DIAGNOSIS` `MANUAL_DIAGNOSIS` |
| status | varchar(32) | `PENDING` `RUNNING` `SUCCESS` `FAILED` |
| started_at | timestamp | 开始时间 |
| finished_at | timestamp | 结束时间 |
| trigger_by | varchar(64) | 触发人或系统 |
| error_message | text | 错误信息 |
| created_at | timestamp | 创建时间 |

### 5. diagnosis_result

诊断结果表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| task_id | bigint | 关联任务 |
| subsystem | varchar(64) | `HDFS` `YARN` `HIVE_ON_TEZ` `IMPALA` `CROSS_COMPONENT` |
| root_cause | varchar(256) | 主根因 |
| confidence | numeric(5,2) | 置信度 |
| impact_level | varchar(32) | 影响等级 |
| diagnosis_json | jsonb | 结构化诊断详情 |
| created_at | timestamp | 创建时间 |

### 6. action_recommendation

处置建议表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| diagnosis_result_id | bigint | 关联诊断结果 |
| action_name | varchar(128) | 动作名称 |
| action_type | varchar(64) | `READONLY` `SCRIPT` `CM_API` |
| risk_level | varchar(32) | `LOW` `MEDIUM` `HIGH` |
| requires_approval | boolean | 是否需审批 |
| recommendation_text | text | 建议内容 |
| execution_payload | jsonb | 执行参数 |
| status | varchar(32) | `SUGGESTED` `APPROVED` `REJECTED` `EXECUTED` |
| created_at | timestamp | 创建时间 |

### 7. approval_record

审批记录表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| recommendation_id | bigint | 关联建议 |
| approver | varchar(128) | 审批人 |
| decision | varchar(32) | `APPROVED` `REJECTED` |
| comment | text | 审批意见 |
| decided_at | timestamp | 审批时间 |

### 8. execution_record

执行记录表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| recommendation_id | bigint | 关联建议 |
| executor_type | varchar(64) | `SCRIPT_PLATFORM` `CM_API` |
| executor_ref | varchar(256) | 外部执行单号 |
| status | varchar(32) | `PENDING` `RUNNING` `SUCCESS` `FAILED` |
| request_payload | jsonb | 请求参数 |
| response_payload | jsonb | 响应结果 |
| started_at | timestamp | 开始时间 |
| finished_at | timestamp | 结束时间 |

### 9. incident_timeline

事件时间线表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| event_type | varchar(64) | `ALERT` `DIAGNOSIS` `APPROVAL` `EXECUTION` `RECOVERY` |
| event_time | timestamp | 事件时间 |
| detail | text | 时间线内容 |
| actor | varchar(128) | 执行主体 |

### 10. postmortem_report

复盘报告表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| incident_id | bigint | 关联事件 |
| title | varchar(256) | 复盘标题 |
| summary | text | 摘要 |
| root_cause | text | 根因说明 |
| improvement_items | jsonb | 改进项 |
| report_path | varchar(512) | 报告存储路径 |
| generated_at | timestamp | 生成时间 |

### 11. knowledge_article

知识库条目表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| category | varchar(64) | `SOP` `PATTERN` `POSTMORTEM` |
| subsystem | varchar(64) | 所属子系统 |
| title | varchar(256) | 标题 |
| tags | varchar(512) | 标签 |
| content_path | varchar(512) | 正文路径 |
| version | integer | 版本 |
| status | varchar(32) | `ACTIVE` `ARCHIVED` |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

### 12. audit_log

统一审计日志表。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| biz_type | varchar(64) | 业务类型 |
| biz_id | bigint | 业务 ID |
| action | varchar(64) | 操作类型 |
| operator | varchar(128) | 操作人 |
| detail_json | jsonb | 详细内容 |
| created_at | timestamp | 创建时间 |

## 关键索引建议

- `incident_event(source_type, source_id)` 唯一索引
- `incident_event(cluster_name, service_type, status, occurred_at)` 组合索引
- `diagnosis_task(incident_id, status)` 索引
- `diagnosis_result(incident_id, subsystem)` 索引
- `action_recommendation(incident_id, status)` 索引
- `execution_record(incident_id, status)` 索引
- `incident_timeline(incident_id, event_time)` 索引
- `audit_log(biz_type, biz_id, created_at)` 索引

## 说明

- 事件、诊断、审批、执行、复盘是一条主链。
- 数据库主要存结构化结果与索引，不承担海量日志原文存储。
- 如后续需要，可再补充租户表、用户表、规则表、通知表。
