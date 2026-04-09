import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SeverityBadge } from "../components/SeverityBadge";
import { getDashboardSummary, getIncidents, getSystemStatus } from "../lib/api";
import type { DashboardSummary, Incident, SystemStatus } from "../lib/types";

const serviceLabelMap: Record<string, string> = {
  YARN: "YARN",
  IMPALA: "Impala",
  HDFS: "HDFS",
  HIVE_ON_TEZ: "Hive on Tez",
  CROSS_COMPONENT: "跨组件"
};

const statusLabelMap: Record<string, string> = {
  OPEN: "待处理",
  DIAGNOSING: "诊断中",
  CLOSED: "已关闭"
};

function formatTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function buildIncidentKey(incident: Incident) {
  return [
    incident.clusterName,
    incident.serviceType,
    incident.title.replace(/\s+/g, " "),
    incident.summary.replace(/\s+/g, " ").replace(/[0-9]+(?:\.[0-9]+)?/g, "#")
  ].join("|");
}

function dedupeIncidents(items: Incident[]) {
  const seen = new Set<string>();
  return items.filter((incident) => {
    const key = incident.incidentNo || buildIncidentKey(incident);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return incident.status !== "CLOSED";
  });
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

function buildRiskSignals(summary: DashboardSummary | null, systemStatus: SystemStatus | null, incidents: Incident[]) {
  const signals: string[] = [];

  if (!systemStatus?.clouderaManagerEnabled) {
    signals.push("Cloudera Manager 尚未启用，当前事件视图只反映本地已接入数据。");
  }
  if ((summary?.criticalIncidents ?? 0) > 0) {
    signals.push("当前存在严重事件，请优先核对影响范围和禁止动作。");
  }
  if (incidents.some((incident) => incident.status === "DIAGNOSING")) {
    signals.push("有事件仍在诊断中，建议继续补采日志、JMX 与上下游证据。");
  }
  if (signals.length === 0) {
    signals.push("当前没有额外高优先级风险信号，请持续关注新事件和状态变化。");
  }
  return signals;
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
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    void loadDashboard();
  }, []);

  async function loadDashboard() {
    setRefreshing(true);
    const [summaryResult, incidentsResult, statusResult] = await Promise.allSettled([
      getDashboardSummary(),
      getIncidents(),
      getSystemStatus()
    ]);

    if (summaryResult.status === "fulfilled") {
      setSummary(summaryResult.value);
    }
    if (incidentsResult.status === "fulfilled") {
      setIncidents(dedupeIncidents(incidentsResult.value ?? []));
      setLoadError(null);
    } else {
      setIncidents([]);
      setLoadError("事件列表暂时不可用，请稍后重试。");
    }
    if (statusResult.status === "fulfilled") {
      setSystemStatus(statusResult.value);
    }

    if (summaryResult.status === "rejected" && incidentsResult.status === "rejected" && statusResult.status === "rejected") {
      setSummary(null);
      setSystemStatus(null);
      setLoadError("后端当前不可用，无法加载实时事件数据。");
    }

    setRefreshing(false);
  }

  const serviceStats = useMemo(() => buildServiceStats(incidents), [incidents]);
  const riskSignals = useMemo(() => buildRiskSignals(summary, systemStatus, incidents), [summary, systemStatus, incidents]);
  const newestIncident = incidents[0] ?? null;

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel command-hero">
        <div className="hero-copy">
          <p className="eyebrow">事件指挥中心</p>
          <h2>面向 Hadoop 数据平台的信号优先事件研判</h2>
          <p className="lead">聚合事件、告警与诊断信号，先判断影响，再推进受控处置。</p>

          <div className="detail-actions">
            <Link className="primary-button" to="/settings">
              管理集成配置
            </Link>
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
              <span>{`活动事件：${systemStatus?.incidentCount ?? incidents.length}`}</span>
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
              <p className="eyebrow">覆盖情况</p>
              <h3>当前风险覆盖面</h3>
            </div>
          </div>

          {serviceStats.length === 0 ? (
            <div className="empty-state">暂时还没有可用的事件分布数据。</div>
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

        <article className="panel featured-panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">最新焦点</p>
              <h3>最近一条高信号事件</h3>
            </div>
          </div>

          {newestIncident ? (
            <div className="featured-incident">
              <div className="featured-head">
                <SeverityBadge value={newestIncident.severity} />
                <span>{serviceLabelMap[newestIncident.serviceType] ?? newestIncident.serviceType}</span>
                <span>{statusLabelMap[newestIncident.status] ?? newestIncident.status}</span>
              </div>
              <h4>{newestIncident.title}</h4>
              <p className="lead">{newestIncident.summary}</p>
              <div className="inline-metadata">
                <span>{`集群：${newestIncident.clusterName}`}</span>
                <span>{`负责人：${newestIncident.owner || "未分配"}`}</span>
                <span>{`发生时间：${formatTime(newestIncident.occurredAt)}`}</span>
              </div>
              <Link className="text-link" to={`/incidents/${newestIncident.id}`}>
                打开事件详情
              </Link>
            </div>
          ) : (
            <div className="empty-state">暂时还没有事件数据。</div>
          )}
        </article>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">实时队列</p>
            <h3>活动事件队列</h3>
          </div>
        </div>

        {loadError ? <div className="empty-state">{loadError}</div> : null}
        {!loadError && incidents.length === 0 ? (
          <div className="empty-state">当前没有活动事件，请先完成数据源接入并同步告警。</div>
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
                  <span>{`发生时间：${formatTime(incident.occurredAt)}`}</span>
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
