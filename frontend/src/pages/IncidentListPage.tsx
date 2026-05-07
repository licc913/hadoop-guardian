import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SeverityBadge } from "../components/SeverityBadge";
import {
  getClouderaManagerCurrentStatus,
  getDashboardSummary,
  getIncidents,
  getSystemStatus
} from "../lib/api";
import type {
  CmCurrentStatusResponse,
  CmServiceStatus,
  DashboardSummary,
  Incident,
  SystemStatus
} from "../lib/types";

const serviceLabelMap: Record<string, string> = {
  YARN: "YARN",
  IMPALA: "Impala",
  HDFS: "HDFS",
  HIVE_ON_TEZ: "Hive on Tez",
  HIVE_METASTORE: "Hive Metastore",
  HBASE: "HBase",
  RANGER: "Ranger",
  SPARK: "Spark",
  KAFKA: "Kafka",
  CROSS_COMPONENT: "跨组件"
};

const statusLabelMap: Record<string, string> = {
  OPEN: "待处理",
  DIAGNOSING: "诊断中",
  CLOSED: "已关闭"
};

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function buildServiceStats(incidents: Incident[]) {
  return Object.entries(
    incidents.reduce<Record<string, number>>((accumulator, incident) => {
      accumulator[incident.serviceType] = (accumulator[incident.serviceType] ?? 0) + 1;
      return accumulator;
    }, {})
  )
    .sort((left, right) => right[1] - left[1])
    .map(([serviceType, count]) => ({
      label: serviceLabelMap[serviceType] ?? serviceType,
      count
    }));
}

function isHealthyService(service: CmServiceStatus) {
  const health = (service.healthSummary || "").toUpperCase();
  const state = (service.serviceState || service.entityStatus || "").toUpperCase();
  if (service.unhealthyRoleCount > 0) {
    return false;
  }
  if (health.includes("BAD") || health.includes("CONCERN") || health.includes("UNKNOWN")) {
    return false;
  }
  return !(state.includes("STOPPED") || state.includes("BAD"));
}

function buildRiskSignals(summary: DashboardSummary | null, systemStatus: SystemStatus | null, unhealthyServices: CmServiceStatus[]) {
  const signals: string[] = [];
  if (!systemStatus?.clouderaManagerEnabled) {
    signals.push("Cloudera Manager 尚未启用，当前无法自动生成实时诊断队列。");
  }
  if (unhealthyServices.length > 0) {
    signals.push(`当前实时快照发现 ${unhealthyServices.length} 个服务存在异常角色或日志告警。`);
  }
  if ((summary?.criticalIncidents ?? 0) > 0) {
    signals.push("诊断队列中存在严重事件，请优先处理影响范围最大的服务。");
  }
  if ((summary?.diagnosingIncidents ?? 0) > 0) {
    signals.push("部分事件正在诊断中，可继续补充角色日志、JMX 与知识库证据。");
  }
  if (signals.length === 0) {
    signals.push("当前没有新增高优先级风险信号，可以继续观察下一次实时采集结果。");
  }
  return signals;
}

function buildCurrentStatusLead(currentStatus: CmCurrentStatusResponse | null, unhealthyServices: CmServiceStatus[]) {
  if (!currentStatus) {
    return "当前还没有拿到 Cloudera Manager 实时快照。";
  }
  if (!currentStatus.success) {
    return currentStatus.details || currentStatus.message;
  }
  if (unhealthyServices.length > 0) {
    return `当前实时快照显示 ${unhealthyServices.length} 个服务存在异常角色或 ERROR/WARN 日志，已可进入诊断队列。`;
  }
  if (currentStatus.serviceCount > 0) {
    return `当前实时快照已返回 ${currentStatus.serviceCount} 个服务，暂未发现需要进入诊断队列的异常服务。`;
  }
  return currentStatus.message;
}

function MetricCard({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <article className={`panel metric-card metric-card-${tone}`}>
      <p className="eyebrow">{label}</p>
      <strong>{value}</strong>
    </article>
  );
}

export function IncidentListPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null);
  const [cmCurrentStatus, setCmCurrentStatus] = useState<CmCurrentStatusResponse | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    void loadDashboard();
  }, []);

  async function loadDashboard() {
    setRefreshing(true);
    setLoadError(null);

    try {
      void getClouderaManagerCurrentStatus()
        .then((result) => {
          setCmCurrentStatus(result);
        })
        .catch(() => {
          setCmCurrentStatus(null);
        });

      const [summaryResult, incidentsResult, statusResult] = await Promise.allSettled([
        getDashboardSummary(),
        getIncidents(),
        getSystemStatus()
      ]);

      if (summaryResult.status === "fulfilled") {
        setSummary(summaryResult.value);
      } else {
        setSummary(null);
      }

      if (incidentsResult.status === "fulfilled") {
        setIncidents(incidentsResult.value ?? []);
      } else {
        setIncidents([]);
        setLoadError("实时诊断队列暂时不可用，请稍后重试。");
      }

      if (statusResult.status === "fulfilled") {
        setSystemStatus(statusResult.value);
      } else {
        setSystemStatus(null);
      }

      if (
        summaryResult.status === "rejected" &&
        incidentsResult.status === "rejected" &&
        statusResult.status === "rejected"
      ) {
        setLoadError("后端当前不可用，无法加载实时诊断数据。");
      }
    } finally {
      setRefreshing(false);
    }
  }

  const serviceStats = useMemo(() => buildServiceStats(incidents), [incidents]);
  const queueHead = incidents[0] ?? null;
  const unhealthyServices = useMemo(
    () => (cmCurrentStatus?.success ? cmCurrentStatus.services.filter((service) => !isHealthyService(service)).slice(0, 6) : []),
    [cmCurrentStatus]
  );
  const currentServiceSnapshot = useMemo(() => {
    if (!cmCurrentStatus?.success) {
      return [];
    }
    if (unhealthyServices.length > 0) {
      return unhealthyServices;
    }
    return cmCurrentStatus.services.slice(0, 6);
  }, [cmCurrentStatus, unhealthyServices]);
  const riskSignals = useMemo(
    () => buildRiskSignals(summary, systemStatus, unhealthyServices),
    [summary, systemStatus, unhealthyServices]
  );
  const currentStatusLead = useMemo(
    () => buildCurrentStatusLead(cmCurrentStatus, unhealthyServices),
    [cmCurrentStatus, unhealthyServices]
  );

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel command-hero">
        <div className="hero-copy">
          <p className="eyebrow">事件指挥中心</p>
          <h2>只展示当前实时异常，并将未关闭事件持续保留在诊断队列</h2>
          <p className="lead">
            首页不再混入历史告警。系统会先采集 Cloudera Manager 当前状态与角色日志，再把仍需处理的实时事件放入诊断队列；
            只要事件没有被关闭，就会继续保留在队列里。
          </p>
          <div className="detail-actions">
            <Link className="primary-button" to="/settings">管理集成配置</Link>
            <button className="secondary-button" disabled={refreshing} onClick={() => void loadDashboard()} type="button">
              {refreshing ? "刷新中..." : "刷新视图"}
            </button>
          </div>
        </div>

        <div className="hero-side">
          <div className="status-card status-card-live">
            <span className="status-kicker">平台状态</span>
            <strong>{systemStatus?.backendUp ? "服务在线" : "状态待确认"}</strong>
            <div className="status-list">
              <span>{`数据库：${systemStatus?.databaseMode ?? "-"}`}</span>
              <span>{`CM：${systemStatus?.clouderaManagerEnabled ? "已启用" : "未启用"}`}</span>
              <span>{`实时队列：${systemStatus?.incidentCount ?? incidents.length}`}</span>
              <span>{`抑制中：${systemStatus?.suppressedIncidentCount ?? 0}`}</span>
              <span>{`巡检运行中：${systemStatus?.inspectionRunningCount ?? 0}`}</span>
              <span>{`巡检失败：${systemStatus?.inspectionFailedCount ?? 0}`}</span>
            </div>
            <div className="stack-sm">
              <span className="muted">{`CM 最近采集：${systemStatus?.lastCmCollectionAt ? formatTime(systemStatus.lastCmCollectionAt) : "未记录"}`}</span>
              <span className="muted">{systemStatus?.lastCmCollectionMessage || "尚未记录 CM 采集状态"}</span>
              <span className="muted">{systemStatus?.lastInspectionMessage || "尚未记录巡检任务状态"}</span>
            </div>
          </div>

          <div className="status-card warm-card">
            <span className="status-kicker">风险信号</span>
            <ul className="tight-list">
              {riskSignals.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section className="summary-grid">
        <MetricCard label="待处理事件" value={summary?.openIncidents ?? 0} tone="neutral" />
        <MetricCard label="诊断中" value={summary?.diagnosingIncidents ?? 0} tone="neutral" />
        <MetricCard label="严重事件" value={summary?.criticalIncidents ?? 0} tone="critical" />
        <MetricCard label="待处置项" value={summary?.actionRequiredIncidents ?? 0} tone="accent" />
      </section>

      <section className="overview-grid">
        <article className="panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">当前服务状态</p>
              <h3>CM 实时服务快照</h3>
            </div>
          </div>

          <p className="compact-lead">{currentStatusLead}</p>
          {cmCurrentStatus?.endpoint ? (
            <div className="inline-metadata">
              <span>{`接口：${cmCurrentStatus.endpoint}`}</span>
              {cmCurrentStatus.collectedAt ? <span>{`采集时间：${formatTime(cmCurrentStatus.collectedAt)}`}</span> : null}
            </div>
          ) : null}

          {currentServiceSnapshot.length === 0 ? (
            <div className="empty-state">{cmCurrentStatus?.details || "当前实时快照还没有返回服务明细。"}</div>
          ) : (
            <div className="stack-md">
              {currentServiceSnapshot.map((service) => (
                <div key={`${service.serviceName}-${service.serviceType}`} className="subpanel">
                  <div className="inline-metadata">
                    <span>{service.serviceName || service.serviceType}</span>
                    <span>{`健康：${service.healthSummary || "未返回"}`}</span>
                    <span>{`状态：${service.serviceState || service.entityStatus || "未返回"}`}</span>
                    <span>{`异常角色：${service.unhealthyRoleCount}/${service.roleCount}`}</span>
                  </div>
                  {service.logHighlights.length > 0 ? (
                    <ul className="list">
                      {service.logHighlights.slice(0, 3).map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  ) : service.logPreviewLines.length > 0 ? (
                    <ul className="list">
                      {service.logPreviewLines.slice(0, 3).map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  ) : service.roleHighlights.length > 0 ? (
                    <ul className="list">
                      {service.roleHighlights.slice(0, 3).map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </article>

        <article className="panel featured-panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">队列头部</p>
              <h3>当前最高优先级实时事件</h3>
            </div>
          </div>

          {queueHead ? (
            <div className="featured-incident">
              <div className="featured-head">
                <SeverityBadge value={queueHead.severity} />
                <span>{serviceLabelMap[queueHead.serviceType] ?? queueHead.serviceType}</span>
                <span>{statusLabelMap[queueHead.status] ?? queueHead.status}</span>
              </div>
              <h4>{queueHead.title}</h4>
              <p className="lead">{queueHead.summary}</p>
              <div className="inline-metadata">
                <span>{`集群：${queueHead.clusterName}`}</span>
                <span>{`负责人：${queueHead.owner || "未分配"}`}</span>
                <span>{`最近采集时间：${formatTime(queueHead.occurredAt)}`}</span>
              </div>
              <Link className="text-link" to={`/incidents/${queueHead.id}`}>打开事件详情</Link>
            </div>
          ) : (
            <div className="empty-state">当前没有需要进入诊断队列的实时异常服务。</div>
          )}
        </article>
      </section>

      <section className="overview-grid">
        <article className="panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">覆盖情况</p>
              <h3>实时队列分布</h3>
            </div>
          </div>

          {serviceStats.length === 0 ? (
            <div className="empty-state">当前还没有可用的实时队列分布数据。</div>
          ) : (
            <div className="distribution-list">
              {serviceStats.map((item) => (
                <div key={item.label} className="distribution-row">
                  <span>{item.label}</span>
                  <div className="distribution-bar">
                    <div className="distribution-fill" style={{ width: `${Math.max(20, item.count * 28)}px` }} />
                  </div>
                  <strong>{item.count}</strong>
                </div>
              ))}
            </div>
          )}
        </article>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">实时诊断队列</p>
            <h3>待诊断 / 待处理事件队列</h3>
          </div>
        </div>

        {loadError ? <div className="empty-state">{loadError}</div> : null}
        {!loadError && incidents.length === 0 ? (
          <div className="empty-state">当前没有实时异常事件。请先在设置页执行“保存并采集当前状态”。</div>
        ) : null}

        <div className="incident-table">
          {incidents.map((incident, index) => (
            <Link key={incident.id} className="incident-row" to={`/incidents/${incident.id}`}>
              <div className="incident-order">{String(index + 1).padStart(2, "0")}</div>
              <div className="incident-main">
                <p className="incident-no">{incident.incidentNo}</p>
                <h4>{incident.title}</h4>
                <p className="row-copy">{incident.summary}</p>
                <div className="inline-metadata">
                  <span>{`集群：${incident.clusterName}`}</span>
                  <span>{`负责人：${incident.owner || "未分配"}`}</span>
                  <span>{`最近采集时间：${formatTime(incident.occurredAt)}`}</span>
                </div>
              </div>
              <div className="row-meta">
                <SeverityBadge value={incident.severity} />
                <span>{serviceLabelMap[incident.serviceType] ?? incident.serviceType}</span>
                <span>{statusLabelMap[incident.status] ?? incident.status}</span>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
