import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { LoadingButton } from "../components/LoadingButton";
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
  ATLAS: "Atlas",
  HUE: "Hue"
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
    signals.push("Cloudera Manager 尚未启用，当前无法自动补齐实时事件队列。");
  }
  if (unhealthyServices.length > 0) {
    signals.push(`当前快照发现 ${unhealthyServices.length} 个异常服务，后台会继续补抓角色日志。`);
  }
  if ((summary?.criticalIncidents ?? 0) > 0) {
    signals.push("诊断队列中存在严重事件，请优先处理影响范围最大的服务。");
  }
  if ((summary?.diagnosingIncidents ?? 0) > 0) {
    signals.push("部分事件正在诊断中，可继续补充日志、JMX 和知识库证据。");
  }
  if (signals.length === 0) {
    signals.push("当前没有新的高优先级风险信号，可以继续观察下一轮采集结果。");
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
    return `当前实时快照发现 ${unhealthyServices.length} 个服务存在异常角色或 WARN/ERROR 日志，异常服务会继续进入诊断队列。`;
  }
  if (currentStatus.serviceCount > 0) {
    return `当前实时快照已返回 ${currentStatus.serviceCount} 个服务，暂未发现需要进入诊断队列的新异常服务。`;
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
  const [openingIncidentId, setOpeningIncidentId] = useState<number | null>(null);

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

      setSummary(summaryResult.status === "fulfilled" ? summaryResult.value : null);

      if (incidentsResult.status === "fulfilled") {
        setIncidents(incidentsResult.value ?? []);
      } else {
        setIncidents([]);
        setLoadError("实时诊断队列暂时不可用，请稍后重试。");
      }

      setSystemStatus(statusResult.status === "fulfilled" ? statusResult.value : null);

      if (
        summaryResult.status === "rejected" &&
        incidentsResult.status === "rejected" &&
        statusResult.status === "rejected"
      ) {
        setLoadError("后端当前不可用，无法加载实时诊断数据。");
      }
    } finally {
      setRefreshing(false);
      setOpeningIncidentId(null);
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
            首页只关注当前实时异常。系统会先采集 Cloudera Manager 当前状态，再由后台继续补抓服务日志。
            只要事件没有被关闭，就会持续保留在诊断队列中。
          </p>
          <div className="detail-actions">
            <Link className="primary-button" to="/settings">管理集成配置</Link>
            <LoadingButton className="secondary-button" loading={refreshing} loadingText="正在刷新视图" onClick={() => void loadDashboard()}>
              刷新视图
            </LoadingButton>
          </div>
          {openingIncidentId != null ? <div className="flash-message">正在打开事件详情...</div> : null}
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
          <p className="muted">
            这里展示服务快照；真正进入诊断队列，需要后台完成实时事件生成和服务日志补抓。
          </p>
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
                <span>{`发生时间：${formatTime(queueHead.occurredAt)}`}</span>
              </div>
              <Link
                className={`text-link ${openingIncidentId === queueHead.id ? "text-link-pending" : ""}`}
                to={`/incidents/${queueHead.id}`}
                onClick={() => setOpeningIncidentId(queueHead.id)}
              >
                {openingIncidentId === queueHead.id ? "正在打开..." : "打开事件详情"}
              </Link>
            </div>
          ) : (
            <div className="empty-state">当前没有需要进入诊断队列的实时异常服务。</div>
          )}
        </article>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">实时事件队列</p>
            <h3>待处理事件</h3>
          </div>
          <span className="muted">{`共 ${incidents.length} 条`}</span>
        </div>

        {loadError ? <div className="error-message">{loadError}</div> : null}

        {incidents.length === 0 ? (
          <div className="empty-state">
            当前没有待处理事件。若快照里已显示异常服务，请等待后台补抓日志并生成实时事件。
          </div>
        ) : (
          <div className="incident-table">
            {incidents.map((incident, index) => (
              <Link
                key={incident.id}
                className={`incident-row ${openingIncidentId === incident.id ? "incident-row-pending" : ""}`}
                to={`/incidents/${incident.id}`}
                onClick={() => setOpeningIncidentId(incident.id)}
              >
                <span className="incident-order">{String(index + 1).padStart(2, "0")}</span>
                <div className="row-copy stack-sm">
                  <div className="inline-metadata">
                    <SeverityBadge value={incident.severity} />
                    <span>{serviceLabelMap[incident.serviceType] ?? incident.serviceType}</span>
                    <span>{statusLabelMap[incident.status] ?? incident.status}</span>
                    <span>{incident.incidentNo}</span>
                  </div>
                  <strong>{incident.title}</strong>
                  <p className="compact-lead">{incident.summary}</p>
                  <div className="inline-metadata">
                    <span>{`集群：${incident.clusterName}`}</span>
                    <span>{`负责人：${incident.owner || "未分配"}`}</span>
                    <span>{`最近采集：${formatTime(incident.lastSeenAt || incident.occurredAt)}`}</span>
                  </div>
                </div>
                <span className="incident-open-state">
                  {openingIncidentId === incident.id ? "打开中..." : "查看详情"}
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">风险分布</p>
            <h3>当前队列按服务分布</h3>
          </div>
        </div>
        {serviceStats.length === 0 ? (
          <div className="empty-state">队列里暂无事件，暂无服务分布可展示。</div>
        ) : (
          <div className="distribution-list">
            {serviceStats.map((item) => (
              <div className="distribution-row" key={item.label}>
                <span>{item.label}</span>
                <div className="distribution-bar">
                  <div className="distribution-fill" style={{ width: `${Math.min(100, item.count * 12)}%` }} />
                </div>
                <strong>{item.count}</strong>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
