import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { DiagnosisCard } from "../components/DiagnosisCard";
import { SeverityBadge } from "../components/SeverityBadge";
import {
  closeIncident,
  createDiagnosisTask,
  getActionRecommendations,
  getAiGuidance,
  getApprovalRecords,
  getClouderaManagerCurrentStatus,
  getDiagnoses,
  getExecutionRecords,
  getIncident,
  getKnowledgeSuggestions,
  getPostmortem
} from "../lib/api";
import type {
  ActionRecommendation,
  AiGuidance,
  ApprovalRecord,
  CmServiceStatus,
  Diagnosis,
  DiagnosisMode,
  DiagnosisTaskResponse,
  ExecutionRecord,
  Incident,
  KnowledgeSuggestion,
  PostmortemRecord
} from "../lib/types";

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

const diagnosisModeOptions: Array<{ value: DiagnosisMode; label: string; description: string }> = [
  { value: "AUTO", label: "自动", description: "优先调用 AI 大模型，失败时回退到知识库或规则。" },
  { value: "LLM_ONLY", label: "仅 AI 大模型", description: "只接受 AI 输出，不回退到知识库或规则。" },
  { value: "KNOWLEDGE_ONLY", label: "仅知识库/规则", description: "只走知识库或规则链路，不调用大模型。" }
];

type DetailPayload = {
  incident: Incident;
  diagnoses: Diagnosis[];
  actions: ActionRecommendation[];
  knowledge: KnowledgeSuggestion[];
  aiGuidance: AiGuidance | null;
  approvals: ApprovalRecord[];
  executions: ExecutionRecord[];
  postmortem: PostmortemRecord | null;
  serviceStatus: CmServiceStatus | null;
};

function formatTime(value?: string | null) {
  if (!value) return "未记录";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function uniq(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((item): item is string => Boolean(item && item.trim()))));
}

function extractPersistedLogEvidence(incident: Incident | null) {
  if (!incident) return [];
  return uniq(
    (incident.evidence ?? []).filter((item) => {
      const upper = item.toUpperCase();
      return upper.includes("WARN") || upper.includes("WARNING") || upper.includes("ERROR") || item.includes("日志");
    })
  );
}

function buildDiagnosisSignature(diagnosis: Diagnosis) {
  return [diagnosis.subsystem, diagnosis.rootCause, diagnosis.impactLevel, diagnosis.crossComponentPath, ...(diagnosis.recommendations ?? []), ...(diagnosis.followUps ?? [])].join("|");
}

function dedupeDiagnoses(items: Diagnosis[]) {
  const seen = new Set<string>();
  return items.filter((item) => {
    const signature = buildDiagnosisSignature(item);
    if (seen.has(signature)) return false;
    seen.add(signature);
    return true;
  });
}

function describeDiagnosisSource(source: string, usedFallback: boolean) {
  if (source === "EXTERNAL_LLM") return "AI 大模型诊断";
  if (source === "KNOWLEDGE_BASE") return "知识库诊断";
  if (source === "RULE_FALLBACK") return usedFallback ? "规则回退诊断" : "规则诊断";
  if (source === "NONE") return "未生成诊断结果";
  return "未知诊断来源";
}

function describeRequestedMode(mode: DiagnosisMode) {
  return diagnosisModeOptions.find((item) => item.value === mode)?.label ?? mode;
}

function buildCurrentDiagnosis(result: DiagnosisTaskResponse, diagnoses: Diagnosis[]) {
  if (result.diagnosis) return result.diagnosis;
  if (result.createdNewDiagnosis && diagnoses.length > 0) return diagnoses[0];
  return null;
}

function buildKnowledgeSummary(knowledge: KnowledgeSuggestion[]) {
  const primary = knowledge[0];
  if (!primary) return null;
  return { title: primary.title, summary: primary.summary, firstStep: primary.steps[0] ?? null, reason: primary.matchReasons[0] ?? null };
}

function matchesServiceStatus(incident: Incident, serviceStatus: CmServiceStatus | null) {
  if (!serviceStatus) return false;
  const requested = incident.serviceType.toUpperCase();
  const current = serviceStatus.serviceType.toUpperCase();
  if (requested === current) return true;
  if (requested === "HIVE_ON_TEZ") return current.includes("HIVE") || current.includes("TEZ");
  return false;
}

function isHealthyService(serviceStatus: CmServiceStatus | null) {
  if (!serviceStatus) return false;
  const health = (serviceStatus.healthSummary || "").toUpperCase();
  const state = (serviceStatus.serviceState || serviceStatus.entityStatus || "").toUpperCase();
  if (serviceStatus.unhealthyRoleCount > 0) return false;
  if (health.includes("BAD") || health.includes("CONCERN") || health.includes("UNKNOWN")) return false;
  return !(state.includes("STOPPED") || state.includes("BAD"));
}

export function IncidentDetailPage() {
  const { incidentId } = useParams();
  const parsedIncidentId = Number(incidentId ?? "1");

  const [incident, setIncident] = useState<Incident | null>(null);
  const [diagnoses, setDiagnoses] = useState<Diagnosis[]>([]);
  const [actions, setActions] = useState<ActionRecommendation[]>([]);
  const [knowledge, setKnowledge] = useState<KnowledgeSuggestion[]>([]);
  const [aiGuidance, setAiGuidance] = useState<AiGuidance | null>(null);
  const [approvals, setApprovals] = useState<ApprovalRecord[]>([]);
  const [executions, setExecutions] = useState<ExecutionRecord[]>([]);
  const [postmortem, setPostmortem] = useState<PostmortemRecord | null>(null);
  const [serviceStatus, setServiceStatus] = useState<CmServiceStatus | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isClosing, setIsClosing] = useState(false);
  const [flashMessage, setFlashMessage] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [diagnosisMode, setDiagnosisMode] = useState<DiagnosisMode>("AUTO");
  const [currentResult, setCurrentResult] = useState<DiagnosisTaskResponse | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  async function loadIncidentDetail(): Promise<DetailPayload | null> {
    const [incidentResult, diagnosisResult, actionResult, knowledgeResult, aiResult, approvalResult, executionResult, statusResult] = await Promise.allSettled([
      getIncident(parsedIncidentId),
      getDiagnoses(parsedIncidentId),
      getActionRecommendations(parsedIncidentId),
      getKnowledgeSuggestions(parsedIncidentId),
      getAiGuidance(parsedIncidentId),
      getApprovalRecords(parsedIncidentId),
      getExecutionRecords(parsedIncidentId),
      getClouderaManagerCurrentStatus()
    ]);

    if (incidentResult.status !== "fulfilled") {
      setIncident(null);
      setLoadError("加载事件详情失败，请确认后端已经启动且该事件存在。");
      return null;
    }

    let postmortemData: PostmortemRecord | null = null;
    try {
      postmortemData = await getPostmortem(parsedIncidentId);
    } catch {
      postmortemData = null;
    }

    let matchedServiceStatus: CmServiceStatus | null = null;
    if (statusResult.status === "fulfilled" && statusResult.value.success) {
      matchedServiceStatus = statusResult.value.services.find((item) => matchesServiceStatus(incidentResult.value, item)) ?? null;
    }

    const payload: DetailPayload = {
      incident: incidentResult.value,
      diagnoses: diagnosisResult.status === "fulfilled" ? dedupeDiagnoses(diagnosisResult.value ?? []) : [],
      actions: actionResult.status === "fulfilled" ? actionResult.value ?? [] : [],
      knowledge: knowledgeResult.status === "fulfilled" ? knowledgeResult.value ?? [] : [],
      aiGuidance: aiResult.status === "fulfilled" ? aiResult.value : null,
      approvals: approvalResult.status === "fulfilled" ? approvalResult.value ?? [] : [],
      executions: executionResult.status === "fulfilled" ? executionResult.value ?? [] : [],
      postmortem: postmortemData,
      serviceStatus: matchedServiceStatus
    };

    setIncident(payload.incident);
    setDiagnoses(payload.diagnoses);
    setActions(payload.actions);
    setKnowledge(payload.knowledge);
    setAiGuidance(payload.aiGuidance);
    setApprovals(payload.approvals);
    setExecutions(payload.executions);
    setPostmortem(payload.postmortem);
    setServiceStatus(payload.serviceStatus);
    setLoadError(null);
    return payload;
  }

  useEffect(() => {
    void loadIncidentDetail();
  }, [parsedIncidentId]);

  const knowledgeSummary = useMemo(() => buildKnowledgeSummary(knowledge), [knowledge]);
  const currentDiagnosis = useMemo(() => (currentResult ? buildCurrentDiagnosis(currentResult, diagnoses) : null), [currentResult, diagnoses]);
  const signalHighlights: string[] = [];
  const jmxInsights = aiGuidance?.jmxInsights ?? [];
  const persistedLogEvidence = useMemo(() => extractPersistedLogEvidence(incident), [incident]);
  const realtimeEvidence = useMemo(
    () => uniq([...(serviceStatus?.logHighlights ?? []), ...(serviceStatus?.roleHighlights ?? []), ...persistedLogEvidence, ...jmxInsights]),
    [jmxInsights, persistedLogEvidence, serviceStatus]
  );
  const snapshotHighlights = useMemo(
    () => uniq([...(serviceStatus?.logHighlights ?? []), ...(serviceStatus?.logPreviewLines ?? []), ...(serviceStatus?.roleHighlights ?? [])]).slice(0, 8),
    [serviceStatus]
  );
  const diagnosisChecklist = useMemo(() => {
    const items: string[] = [];
    if (serviceStatus) {
      items.push(isHealthyService(serviceStatus)
        ? `当前 CM 实时快照显示 ${serviceStatus.serviceName || serviceStatus.serviceType} 暂未发现明显异常角色。`
        : `当前 CM 实时快照显示 ${serviceStatus.serviceName || serviceStatus.serviceType} 仍处于异常或待关注状态。`);
      items.push(...snapshotHighlights.slice(0, 6));
    }
    items.push(...persistedLogEvidence.slice(0, 6));
    items.push(...jmxInsights.slice(0, 3));
    return uniq(items).slice(0, 10);
  }, [jmxInsights, persistedLogEvidence, serviceStatus]);

  if (loadError) return <div className="panel empty-state">{loadError}</div>;
  if (!incident) return <div className="panel">正在加载事件详情...</div>;

  async function handleCreateDiagnosisTask() {
    setIsSubmitting(true);
    setFlashMessage(null);
    try {
      const result = await createDiagnosisTask(parsedIncidentId, diagnosisMode);
      await loadIncidentDetail();
      setCurrentResult(result);
      if (result.createdNewDiagnosis || result.diagnosisSource === "KNOWLEDGE_BASE") {
        setShowHistory(true);
      }
      const sourceText = `${describeDiagnosisSource(result.diagnosisSource, result.usedFallback)} / 模式：${describeRequestedMode(result.requestedMode)}`;
      const details = result.details && result.details !== result.message ? ` 详情：${result.details}` : "";
      setFlashMessage(`${result.message} 来源：${sourceText}.${details}`);
    } catch (error) {
      setCurrentResult(null);
      setFlashMessage(error instanceof Error ? `诊断任务执行失败：${error.message}` : "诊断任务执行失败，请检查后端日志和接口状态。");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCloseIncident() {
    setIsClosing(true);
    setFlashMessage(null);
    try {
      const response = await closeIncident(parsedIncidentId, "已人工确认本次事件处置完成。");
      await loadIncidentDetail();
      setFlashMessage(response.message);
    } catch (error) {
      setFlashMessage(error instanceof Error ? `关闭事件失败：${error.message}` : "关闭事件失败，请检查后端日志。");
    } finally {
      setIsClosing(false);
    }
  }

  return (
    <div className="page-section">
      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">事件详情</p>
            <h2>{incident.title}</h2>
            <p className="muted">{incident.summary}</p>
          </div>
          <div className="inline-metadata">
            <SeverityBadge value={incident.severity} />
            <SeverityBadge value={incident.status} />
          </div>
        </div>

        <div className="inline-metadata">
          <span>{`事件编号：${incident.incidentNo}`}</span>
          <span>{`集群：${incident.clusterName}`}</span>
          <span>{`服务：${serviceLabelMap[incident.serviceType] ?? incident.serviceType}`}</span>
          <span>{`状态：${statusLabelMap[incident.status] ?? incident.status}`}</span>
          <span>{`责任人：${incident.owner || "未分配"}`}</span>
          <span>{`发生时间：${formatTime(incident.occurredAt)}`}</span>
        </div>

        <div className="two-column-section">
          <section className="subpanel">
            <h3>影响范围</h3>
            <p>{incident.impactScope || "暂无影响范围描述。"}</p>
          </section>
          <section className="subpanel">
            <h3>证据摘要</h3>
            {incident.evidence.length === 0 ? <div className="empty-state">当前没有证据条目。</div> : <ul className="list">{incident.evidence.slice(0, 4).map((item) => <li key={item}>{item}</li>)}</ul>}
          </section>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">诊断入口</p>
            <h3>启动诊断任务</h3>
            <p className="muted">这里优先展示当前状态清单，历史诊断和处置结果继续折叠在下方。</p>
          </div>
          <div className="detail-actions">
            <Link to="/incidents" className="button button-secondary">返回事件列表</Link>
            {incident.status !== "CLOSED" ? (
              <button className="button button-secondary" disabled={isClosing} type="button" onClick={() => void handleCloseIncident()}>
                {isClosing ? "关闭中..." : "关闭事件"}
              </button>
            ) : null}
          </div>
        </div>

        <div className="diagnosis-mode-grid">
          {diagnosisModeOptions.map((option) => (
            <label key={option.value} className={`diagnosis-mode-option ${diagnosisMode === option.value ? "diagnosis-mode-option-active" : ""}`}>
              <input className="diagnosis-mode-input" type="radio" name="diagnosisMode" value={option.value} checked={diagnosisMode === option.value} onChange={() => setDiagnosisMode(option.value)} />
              <span className="diagnosis-mode-title">{option.label}</span>
              <span className="diagnosis-mode-copy">{option.description}</span>
            </label>
          ))}
        </div>

        <div className="diagnosis-meta-row">
          <span>{`当前事件证据：${incident.evidence.length} 条`}</span>
          <span>{`历史诊断：${diagnoses.length} 条`}</span>
          <span>{`知识命中：${knowledge.length} 条`}</span>
          <span>{`JMX 信号：${jmxInsights.length} 条`}</span>
        </div>

        <div className="subpanel">
          <div className="panel-head">
            <strong>实时诊断清单</strong>
            <span>{serviceStatus ? "当前状态 + JMX" : "历史事件 + JMX"}</span>
          </div>
          {diagnosisChecklist.length === 0 ? (
            <div className="empty-state">当前没有额外实时信号，建议先在设置页校验 CM 与 JMX，再发起诊断。</div>
          ) : (
            <ul className="list">
              {diagnosisChecklist.map((item) => <li key={item}>{item}</li>)}
            </ul>
          )}
        </div>

        <div className="toolbar">
          <button className="button" type="button" onClick={() => void handleCreateDiagnosisTask()} disabled={isSubmitting || incident.status === "CLOSED"}>
            {isSubmitting ? "诊断执行中..." : "启动诊断任务"}
          </button>
          <button className="button button-secondary" type="button" onClick={() => setShowHistory((value) => !value)}>
            {showHistory ? "收起历史结果" : "查看历史结果"}
          </button>
        </div>

        {flashMessage ? <div className="flash-message">{flashMessage}</div> : null}
      </section>

      {currentResult ? (
        <section className="panel diagnosis-result-banner">
          <div className="panel-head">
            <div>
              <p className="eyebrow">本次诊断输出</p>
              <h3>{describeDiagnosisSource(currentResult.diagnosisSource, currentResult.usedFallback)}</h3>
            </div>
            <span className="muted">{`模式：${describeRequestedMode(currentResult.requestedMode)}`}</span>
          </div>
          {currentDiagnosis ? (
            <div className="stack-md">
              <p className="lead">{currentDiagnosis.rootCause}</p>
              <div className="inline-metadata">
                <span>{`影响等级：${currentDiagnosis.impactLevel}`}</span>
                <span>{`跨组件路径：${currentDiagnosis.crossComponentPath}`}</span>
                <span>{`置信度：${Math.round(currentDiagnosis.confidence * 100)}%`}</span>
              </div>
              {currentDiagnosis.recommendations.length > 0 ? <ul className="list">{currentDiagnosis.recommendations.map((item) => <li key={item}>{item}</li>)}</ul> : null}
            </div>
          ) : knowledgeSummary ? (
            <div className="stack-md">
              <p className="lead">{knowledgeSummary.summary}</p>
              {knowledgeSummary.firstStep ? <p>{knowledgeSummary.firstStep}</p> : null}
              {knowledgeSummary.reason ? <p className="muted">{knowledgeSummary.reason}</p> : null}
            </div>
          ) : (
            <div className="empty-state">本次没有生成新的诊断结论，请检查上方提示信息。</div>
          )}
        </section>
      ) : null}

      <div className="two-column-section">
        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">当前状态</p><h3>CM 实时服务快照</h3></div></div>
          {!serviceStatus ? (
            <div className="empty-state">当前没有匹配到该服务的实时 CM 状态，诊断仍可基于历史事件、知识库和 JMX 信号继续执行。</div>
          ) : (
            <div className="stack-md">
              <div className="inline-metadata">
                <span>{`服务：${serviceStatus.serviceName || serviceStatus.serviceType}`}</span>
                <span>{`健康：${serviceStatus.healthSummary || "未返回"}`}</span>
                <span>{`状态：${serviceStatus.serviceState || serviceStatus.entityStatus || "未返回"}`}</span>
                <span>{`角色数：${serviceStatus.roleCount}`}</span>
                <span>{`异常角色：${serviceStatus.unhealthyRoleCount}`}</span>
              </div>
              {serviceStatus.roleHighlights.length > 0 ? <ul className="list">{serviceStatus.roleHighlights.map((item) => <li key={item}>{item}</li>)}</ul> : <div className="empty-state">当前服务快照里没有额外异常角色摘要。</div>}
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">实时采集</p><h3>JMX 与辅助信号</h3></div></div>
          {signalHighlights.length === 0 && jmxInsights.length === 0 ? (
            <div className="empty-state">当前没有聚合到额外实时信号，建议先在设置页完成 JMX 采集并重新发起诊断。</div>
          ) : (
            <div className="stack-md">
              {signalHighlights.length > 0 ? (
                <>
                  <strong>关键信号</strong>
                  <ul className="list">{signalHighlights.map((item) => <li key={item}>{item}</li>)}</ul>
                </>
              ) : null}
              {jmxInsights.length > 0 ? (
                <>
                  <strong>JMX 摘要</strong>
                  <ul className="list">{jmxInsights.map((item) => <li key={item}>{item}</li>)}</ul>
                </>
              ) : null}
            </div>
          )}
        </section>
      </div>

      {showHistory ? (
        <>
          <section className="panel">
            <div className="panel-head"><div><p className="eyebrow">诊断记录</p><h3>诊断历史</h3></div><span className="muted">{`共 ${diagnoses.length} 条`}</span></div>
            {diagnoses.length === 0 ? <div className="empty-state">当前还没有诊断记录。</div> : <div className="stack-md">{diagnoses.map((item) => <DiagnosisCard key={item.id} diagnosis={item} />)}</div>}
          </section>

          <div className="two-column-section">
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">处置建议</p><h3>动作建议</h3></div><span className="muted">{`共 ${actions.length} 条`}</span></div>
              {actions.length === 0 ? <div className="empty-state">当前还没有动作建议。</div> : <ul className="list">{actions.map((item) => <li key={item.id}><strong>{item.actionName}</strong><div>{item.recommendationText}</div><div className="inline-metadata"><span>{item.actionType}</span><span>{item.riskLevel}</span><span>{item.status}</span><span>{formatTime(item.createdAt)}</span></div></li>)}</ul>}
            </section>

            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">知识命中</p><h3>知识库建议</h3></div><span className="muted">{`共 ${knowledge.length} 条`}</span></div>
              {knowledge.length === 0 ? <div className="empty-state">当前没有命中的知识条目。</div> : <ul className="list">{knowledge.map((item) => <li key={item.id}><strong>{item.title}</strong><div>{item.summary}</div><div className="inline-metadata"><span>{serviceLabelMap[item.domain] ?? item.domain}</span><span>{item.riskLevel}</span><span>{`匹配分：${item.score}`}</span></div>{item.matchReasons[0] ? <div className="muted">{item.matchReasons[0]}</div> : null}</li>)}</ul>}
            </section>
          </div>

          <div className="two-column-section">
            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">辅助判断</p><h3>AI Guidance</h3></div></div>
              {!aiGuidance ? <div className="empty-state">当前没有 AI Guidance 返回。</div> : <div className="stack-md"><p>{aiGuidance.probableScenario}</p><div className="inline-metadata"><span>{`置信度：${Math.round(aiGuidance.confidence * 100)}%`}</span><span>{aiGuidance.confidenceLabel}</span></div>{aiGuidance.recommendedOrder.length > 0 ? <ul className="list">{aiGuidance.recommendedOrder.map((item) => <li key={item}>{item}</li>)}</ul> : null}</div>}
            </section>

            <section className="panel">
              <div className="panel-head"><div><p className="eyebrow">审批与执行</p><h3>处置闭环</h3></div></div>
              <section className="subpanel">
                <h4>审批记录</h4>
                {approvals.length === 0 ? <div className="empty-state">当前没有审批记录。</div> : <ul className="list">{approvals.map((item) => <li key={item.id}><strong>{item.approvalStatus}</strong><div className="inline-metadata"><span>{`申请人：${item.requestedBy}`}</span><span>{`审批人：${item.approver || "未处理"}`}</span><span>{`申请时间：${formatTime(item.requestedAt)}`}</span></div></li>)}</ul>}
              </section>
              <section className="subpanel">
                <h4>执行记录</h4>
                {executions.length === 0 ? <div className="empty-state">当前没有执行记录。</div> : <ul className="list">{executions.map((item) => <li key={item.id}><strong>{item.executionStatus}</strong><div>{item.executionSummary}</div><div className="inline-metadata"><span>{`执行人：${item.executor}`}</span><span>{`开始时间：${formatTime(item.startedAt)}`}</span><span>{`结束时间：${formatTime(item.finishedAt)}`}</span></div></li>)}</ul>}
              </section>
            </section>
          </div>

          <div className="two-column-section">
            <section className="panel">
              <h3>完整证据</h3>
              {incident.evidence.length === 0 ? <div className="empty-state">当前没有证据条目。</div> : <ul className="list">{incident.evidence.map((item) => <li key={item}>{item}</li>)}</ul>}
            </section>
            <section className="panel">
              <h3>避免动作</h3>
              {incident.avoidedActions.length === 0 ? <div className="empty-state">当前没有禁止动作提示。</div> : <ul className="list">{incident.avoidedActions.map((item) => <li key={item}>{item}</li>)}</ul>}
            </section>
          </div>

          <section className="panel">
            <div className="panel-head"><div><p className="eyebrow">复盘</p><h3>事后总结</h3></div></div>
            {!postmortem ? <div className="empty-state">当前没有复盘记录。</div> : <div className="stack-md"><p>{postmortem.summary}</p><p>{postmortem.rootCause}</p><p>{postmortem.impactStatement}</p>{postmortem.timeline.length > 0 ? <ul className="list">{postmortem.timeline.map((item) => <li key={item}>{item}</li>)}</ul> : null}</div>}
          </section>
        </>
      ) : null}
    </div>
  );
}
