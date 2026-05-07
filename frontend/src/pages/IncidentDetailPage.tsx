import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { DiagnosisCard } from "../components/DiagnosisCard";
import { SeverityBadge } from "../components/SeverityBadge";
import {
  closeIncident,
  createApprovalRecord,
  createDiagnosisTask,
  createExecutionRecord,
  getActionRecommendations,
  getAiGuidance,
  getApprovalRecords,
  getDiagnoses,
  getExecutionRecords,
  getIncident,
  getIncidentServiceLogs,
  getKnowledgeSuggestions,
  getPostmortem,
  resumeIncident,
  savePostmortem,
  suppressIncident
} from "../lib/api";
import type {
  ActionRecommendation,
  AiGuidance,
  ApprovalRecord,
  CmServiceLogSnapshot,
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

const diagnosisModeOptions: Array<{ value: DiagnosisMode; label: string; description: string }> = [
  { value: "AUTO", label: "自动", description: "优先调用 AI 大模型，失败时回退到知识库或规则。" },
  { value: "LLM_ONLY", label: "仅 AI 大模型", description: "只接受 AI 输出，不回退到知识库或规则。" },
  { value: "KNOWLEDGE_ONLY", label: "仅知识库/规则", description: "只走知识库或规则链路，不调用大模型。" }
];

type HistoryPayload = {
  actions: ActionRecommendation[];
  approvals: ApprovalRecord[];
  executions: ExecutionRecord[];
  postmortem: PostmortemRecord | null;
};

function formatTime(value?: string | null) {
  if (!value) return "未记录";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function uniq(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((item): item is string => Boolean(item && item.trim()))));
}

function buildDiagnosisSignature(diagnosis: Diagnosis) {
  return [
    diagnosis.subsystem,
    diagnosis.rootCause,
    diagnosis.impactLevel,
    diagnosis.crossComponentPath,
    ...(diagnosis.recommendations ?? []),
    ...(diagnosis.followUps ?? [])
  ].join("|");
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
  return {
    title: primary.title,
    summary: primary.summary,
    firstStep: primary.steps[0] ?? null,
    reason: primary.matchReasons[0] ?? null
  };
}

function renderLogLine(item: CmServiceLogSnapshot) {
  return `[${item.logType}] ${item.logText}`;
}

function splitTextareaLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
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
  const [serviceLogs, setServiceLogs] = useState<CmServiceLogSnapshot[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isClosing, setIsClosing] = useState(false);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [isSignalLoading, setIsSignalLoading] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [flashMessage, setFlashMessage] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [diagnosisMode, setDiagnosisMode] = useState<DiagnosisMode>("AUTO");
  const [currentResult, setCurrentResult] = useState<DiagnosisTaskResponse | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const [isSuppressing, setIsSuppressing] = useState(false);
  const [approvalStatus, setApprovalStatus] = useState("PENDING");
  const [approvalComment, setApprovalComment] = useState("");
  const [executionStatus, setExecutionStatus] = useState("RUNNING");
  const [executionSummary, setExecutionSummary] = useState("");
  const [postmortemSummary, setPostmortemSummary] = useState("");
  const [postmortemRootCause, setPostmortemRootCause] = useState("");
  const [postmortemImpact, setPostmortemImpact] = useState("");
  const [postmortemTimeline, setPostmortemTimeline] = useState("");
  const [postmortemPrevention, setPostmortemPrevention] = useState("");

  const loadIncidentSignals = async () => {
    const [diagnosisResult, knowledgeResult, aiResult, serviceLogResult] = await Promise.allSettled([
      getDiagnoses(parsedIncidentId),
      getKnowledgeSuggestions(parsedIncidentId),
      getAiGuidance(parsedIncidentId),
      getIncidentServiceLogs(parsedIncidentId)
    ]);

    setDiagnoses(diagnosisResult.status === "fulfilled" ? dedupeDiagnoses(diagnosisResult.value ?? []) : []);
    setKnowledge(knowledgeResult.status === "fulfilled" ? knowledgeResult.value ?? [] : []);
    setAiGuidance(aiResult.status === "fulfilled" ? aiResult.value : null);
    setServiceLogs(serviceLogResult.status === "fulfilled" ? serviceLogResult.value ?? [] : []);
  };

  const refreshIncidentSignals = async () => {
    setIsSignalLoading(true);
    try {
      await loadIncidentSignals();
    } finally {
      setIsSignalLoading(false);
    }
  };

  const loadHistoryDetail = async (): Promise<HistoryPayload> => {
    setIsHistoryLoading(true);
    try {
      const [actionResult, approvalResult, executionResult, postmortemResult] = await Promise.allSettled([
        getActionRecommendations(parsedIncidentId),
        getApprovalRecords(parsedIncidentId),
        getExecutionRecords(parsedIncidentId),
        getPostmortem(parsedIncidentId)
      ]);

      const payload: HistoryPayload = {
        actions: actionResult.status === "fulfilled" ? actionResult.value ?? [] : [],
        approvals: approvalResult.status === "fulfilled" ? approvalResult.value ?? [] : [],
        executions: executionResult.status === "fulfilled" ? executionResult.value ?? [] : [],
        postmortem: postmortemResult.status === "fulfilled" ? postmortemResult.value : null
      };

      setActions(payload.actions);
      setApprovals(payload.approvals);
      setExecutions(payload.executions);
      setPostmortem(payload.postmortem);
      setPostmortemSummary(payload.postmortem?.summary ?? "");
      setPostmortemRootCause(payload.postmortem?.rootCause ?? "");
      setPostmortemImpact(payload.postmortem?.impactStatement ?? "");
      setPostmortemTimeline((payload.postmortem?.timeline ?? []).join("\n"));
      setPostmortemPrevention((payload.postmortem?.preventionItems ?? []).join("\n"));
      setHistoryLoaded(true);
      return payload;
    } finally {
      setIsHistoryLoading(false);
    }
  };

  const loadCoreIncidentDetail = async () => {
    setIsBootstrapping(true);
    setLoadError(null);
    try {
      const incidentResult = await getIncident(parsedIncidentId);
      setIncident(incidentResult);
      setCurrentResult(null);
      void refreshIncidentSignals();
    } catch {
      setIncident(null);
      setLoadError("加载事件详情失败，请确认后端已经启动且该事件存在。");
    } finally {
      setIsBootstrapping(false);
    }
  };

  useEffect(() => {
    setDiagnoses([]);
    setActions([]);
    setKnowledge([]);
    setAiGuidance(null);
    setApprovals([]);
    setExecutions([]);
    setPostmortem(null);
    setServiceLogs([]);
    setHistoryLoaded(false);
    setShowHistory(false);
    setApprovalStatus("PENDING");
    setApprovalComment("");
    setExecutionStatus("RUNNING");
    setExecutionSummary("");
    setPostmortemSummary("");
    setPostmortemRootCause("");
    setPostmortemImpact("");
    setPostmortemTimeline("");
    setPostmortemPrevention("");
    void loadCoreIncidentDetail();
  }, [parsedIncidentId]);

  useEffect(() => {
    if (!incident || incident.status === "CLOSED" || serviceLogs.length > 0 || isSignalLoading) {
      return undefined;
    }
    const handle = window.setTimeout(() => {
      void refreshIncidentSignals();
    }, 15000);
    return () => window.clearTimeout(handle);
  }, [incident, serviceLogs.length, isSignalLoading]);

  useEffect(() => {
    if (!showHistory || historyLoaded) {
      return;
    }
    void loadHistoryDetail();
  }, [showHistory, historyLoaded, parsedIncidentId]);

  const knowledgeSummary = useMemo(() => buildKnowledgeSummary(knowledge), [knowledge]);
  const currentDiagnosis = useMemo(() => (currentResult ? buildCurrentDiagnosis(currentResult, diagnoses) : null), [currentResult, diagnoses]);
  const signalHighlights = aiGuidance?.signalHighlights ?? [];
  const jmxInsights = aiGuidance?.jmxInsights ?? [];
  const visibleServiceLogs = useMemo(() => serviceLogs.slice(0, 12), [serviceLogs]);
  const currentAction = useMemo(() => actions[0] ?? null, [actions]);

  if (loadError) return <div className="panel empty-state">{loadError}</div>;
  if (!incident) return <div className="panel">{isBootstrapping ? "正在加载事件详情..." : "未找到当前事件。"}</div>;

  async function handleCreateDiagnosisTask() {
    setIsSubmitting(true);
    setFlashMessage(null);
    try {
      const result = await createDiagnosisTask(parsedIncidentId, diagnosisMode);
      await refreshIncidentSignals();
      if (showHistory) {
        await loadHistoryDetail();
      }
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
      const response = await closeIncident(parsedIncidentId, "已人工确认本次事件处理完成。");
      setIncident(response.incident);
      setActions((current) => current.map((item) => ({ ...item, status: "CLOSED" })));
      setFlashMessage(response.message);
    } catch (error) {
      setFlashMessage(error instanceof Error ? `关闭事件失败：${error.message}` : "关闭事件失败，请检查后端日志。");
    } finally {
      setIsClosing(false);
    }
  }

  async function handleSuppressToggle() {
    if (!incident) return;
    setIsSuppressing(true);
    setFlashMessage(null);
    try {
      const response = incident.governanceStatus === "SUPPRESSED"
        ? await resumeIncident(parsedIncidentId, "Operator resumed the incident from the active queue.")
        : await suppressIncident(parsedIncidentId, "Operator suppressed repetitive event noise for controlled triage.", 120);
      setIncident(response.incident);
      setFlashMessage(response.message);
    } catch (error) {
      setFlashMessage(error instanceof Error ? `事件治理操作失败：${error.message}` : "事件治理操作失败，请检查后端日志。");
    } finally {
      setIsSuppressing(false);
    }
  }

  async function handleCreateApproval() {
    if (!currentAction) {
      setFlashMessage("当前没有可用的动作建议，无法新增审批记录。");
      return;
    }
    try {
      const record = await createApprovalRecord(parsedIncidentId, {
        actionRecommendationId: currentAction.id,
        approvalStatus,
        requestedBy: "frontend-operator",
        approver: approvalStatus === "PENDING" ? null : "on-duty-approver",
        comment: approvalComment || null
      });
      setApprovals((current) => [...current, record].sort((left, right) => left.requestedAt.localeCompare(right.requestedAt)));
      setFlashMessage("审批记录已写入。");
      setApprovalComment("");
      if (!showHistory) {
        setShowHistory(true);
      }
    } catch (error) {
      setFlashMessage(error instanceof Error ? `写入审批记录失败：${error.message}` : "写入审批记录失败。");
    }
  }

  async function handleCreateExecution() {
    if (!currentAction) {
      setFlashMessage("当前没有可用的动作建议，无法新增执行记录。");
      return;
    }
    try {
      const record = await createExecutionRecord(parsedIncidentId, {
        actionRecommendationId: currentAction.id,
        executionStatus,
        executor: "frontend-operator",
        executionSummary: executionSummary || "Execution record created from incident detail page."
      });
      setExecutions((current) => [...current, record].sort((left, right) => left.startedAt.localeCompare(right.startedAt)));
      setFlashMessage("执行记录已写入。");
      setExecutionSummary("");
      if (!showHistory) {
        setShowHistory(true);
      }
    } catch (error) {
      setFlashMessage(error instanceof Error ? `写入执行记录失败：${error.message}` : "写入执行记录失败。");
    }
  }

  async function handleSavePostmortem() {
    try {
      const record = await savePostmortem(parsedIncidentId, {
        summary: postmortemSummary,
        rootCause: postmortemRootCause,
        impactStatement: postmortemImpact,
        timeline: splitTextareaLines(postmortemTimeline),
        preventionItems: splitTextareaLines(postmortemPrevention)
      });
      setPostmortem(record);
      setPostmortemSummary(record.summary);
      setPostmortemRootCause(record.rootCause);
      setPostmortemImpact(record.impactStatement);
      setPostmortemTimeline((record.timeline ?? []).join("\n"));
      setPostmortemPrevention((record.preventionItems ?? []).join("\n"));
      setFlashMessage("复盘记录已保存。");
      if (!showHistory) {
        setShowHistory(true);
      }
    } catch (error) {
      setFlashMessage(error instanceof Error ? `保存复盘记录失败：${error.message}` : "保存复盘记录失败。");
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
          <span>{`治理状态：${incident.governanceStatus || "ACTIVE"}`}</span>
          <span>{`出现次数：${incident.occurrenceCount ?? 1}`}</span>
          <span>{`责任人：${incident.owner || "未分配"}`}</span>
          <span>{`发生时间：${formatTime(incident.occurredAt)}`}</span>
          {incident.lastSeenAt ? <span>{`最近采集：${formatTime(incident.lastSeenAt)}`}</span> : null}
          {incident.suppressedUntil ? <span>{`抑制到：${formatTime(incident.suppressedUntil)}`}</span> : null}
        </div>
        {incident.governanceNote ? <p className="muted">{incident.governanceNote}</p> : null}

        <div className="two-column-section">
          <section className="subpanel">
            <h3>影响范围</h3>
            <p>{incident.impactScope || "暂无影响范围描述。"}</p>
          </section>
          <section className="subpanel">
            <h3>事件证据</h3>
            {incident.evidence.length === 0 ? (
              <div className="empty-state">当前没有事件证据条目。</div>
            ) : (
              <ul className="list">{incident.evidence.slice(0, 8).map((item) => <li key={item}>{item}</li>)}</ul>
            )}
          </section>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">诊断入口</p>
            <h3>启动诊断任务</h3>
            <p className="muted">事件详情页不再加载 CM 当前状态清单，只展示当前事件、服务日志和诊断结果，避免进入详情页时阻塞慢接口。</p>
          </div>
          <div className="detail-actions">
            <Link to="/incidents" className="button button-secondary">返回事件列表</Link>
            {incident.status !== "CLOSED" ? (
              <button className="button button-secondary" disabled={isSuppressing} type="button" onClick={() => void handleSuppressToggle()}>
                {isSuppressing ? "处理中..." : incident.governanceStatus === "SUPPRESSED" ? "恢复事件" : "抑制事件"}
              </button>
            ) : null}
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
              <input
                className="diagnosis-mode-input"
                type="radio"
                name="diagnosisMode"
                value={option.value}
                checked={diagnosisMode === option.value}
                onChange={() => setDiagnosisMode(option.value)}
              />
              <span className="diagnosis-mode-title">{option.label}</span>
              <span className="diagnosis-mode-copy">{option.description}</span>
            </label>
          ))}
        </div>

        <div className="diagnosis-meta-row">
          <span>{`当前事件证据：${incident.evidence.length} 条`}</span>
          <span>{`历史诊断：${diagnoses.length} 条`}</span>
          <span>{`知识命中：${knowledge.length} 条`}</span>
          <span>{`服务日志：${serviceLogs.length} 条`}</span>
          <span>{`JMX 信号：${jmxInsights.length} 条`}</span>
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

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">事件服务日志</p>
            <h3>当前诊断输入</h3>
          </div>
          <span className="muted">{isSignalLoading ? "刷新中..." : "已完成"}</span>
        </div>
        {visibleServiceLogs.length === 0 ? (
          <div className="empty-state">当前没有匹配到该事件的服务日志快照，请先确认 CM 集群名、服务角色日志采集和定时任务是否正常。</div>
        ) : (
          <ul className="list">{visibleServiceLogs.map((item, index) => <li key={`${item.collectedAt}-${index}`}>{renderLogLine(item)}</li>)}</ul>
        )}
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
                <span>{`置信度：${Math.round(currentDiagnosis.confidence * 100)}%`}</span>
              </div>
              {currentDiagnosis.recommendations.length > 0 ? (
                <ul className="list">{currentDiagnosis.recommendations.map((item) => <li key={item}>{item}</li>)}</ul>
              ) : null}
              {currentDiagnosis.followUps.length > 0 ? (
                <>
                  <strong>待补充证据</strong>
                  <ul className="list">{currentDiagnosis.followUps.map((item) => <li key={item}>{item}</li>)}</ul>
                </>
              ) : null}
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

      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">辅助信号</p>
            <h3>AI Guidance 与 JMX</h3>
          </div>
          <span className="muted">{isSignalLoading ? "刷新中..." : "已完成"}</span>
        </div>
        {signalHighlights.length === 0 && jmxInsights.length === 0 ? (
          <div className="empty-state">当前没有额外的辅助信号，诊断将主要基于事件证据、服务日志和知识库继续进行。</div>
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

      {showHistory ? (
        <>
          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">诊断记录</p>
                <h3>诊断历史</h3>
              </div>
              <span className="muted">{`共 ${diagnoses.length} 条`}</span>
            </div>
            {isHistoryLoading ? (
              <div className="empty-state">正在加载历史结果...</div>
            ) : diagnoses.length === 0 ? (
              <div className="empty-state">当前还没有诊断记录。</div>
            ) : (
              <div className="stack-md">{diagnoses.map((item) => <DiagnosisCard key={item.id} diagnosis={item} />)}</div>
            )}
          </section>

          <div className="two-column-section">
            <section className="panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">处置建议</p>
                  <h3>动作建议</h3>
                </div>
                <span className="muted">{`共 ${actions.length} 条`}</span>
              </div>
              {isHistoryLoading ? (
                <div className="empty-state">正在加载动作建议...</div>
              ) : actions.length === 0 ? (
                <div className="empty-state">当前还没有动作建议。</div>
              ) : (
                <ul className="list">
                  {actions.map((item) => (
                    <li key={item.id}>
                      <strong>{item.actionName}</strong>
                      <div>{item.recommendationText}</div>
                      <div className="inline-metadata">
                        <span>{item.actionType}</span>
                        <span>{item.riskLevel}</span>
                        <span>{item.status}</span>
                        <span>{formatTime(item.createdAt)}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">知识命中</p>
                  <h3>知识库建议</h3>
                </div>
                <span className="muted">{`共 ${knowledge.length} 条`}</span>
              </div>
              {knowledge.length === 0 ? (
                <div className="empty-state">当前没有匹配到知识库建议。</div>
              ) : (
                <ul className="list">
                  {knowledge.map((item) => (
                    <li key={item.id}>
                      <strong>{item.title}</strong>
                      <div>{item.summary}</div>
                      <div className="inline-metadata">
                        <span>{item.domain}</span>
                        <span>{item.riskLevel}</span>
                        <span>{item.sourceName}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <div className="two-column-section">
            <section className="panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">审批记录</p>
                  <h3>审批轨迹</h3>
                </div>
                <span className="muted">{`共 ${approvals.length} 条`}</span>
              </div>
              {isHistoryLoading ? (
                <div className="empty-state">正在加载审批记录...</div>
              ) : approvals.length === 0 ? (
                <div className="empty-state">当前还没有审批记录。</div>
              ) : (
                <ul className="list">
                  {approvals.map((item) => (
                    <li key={item.id}>
                      <strong>{item.approvalStatus}</strong>
                      <div className="inline-metadata">
                        <span>{`申请人 ${item.requestedBy}`}</span>
                        <span>{`审批人 ${item.approver || "未审批"}`}</span>
                        <span>{formatTime(item.requestedAt)}</span>
                      </div>
                      {item.comment ? <div>{item.comment}</div> : null}
                    </li>
                  ))}
                </ul>
              )}
              <div className="stack-sm">
                <strong>新增审批记录</strong>
                <div className="form-grid">
                  <label>
                    <span>审批状态</span>
                    <select value={approvalStatus} onChange={(event) => setApprovalStatus(event.target.value)}>
                      <option value="PENDING">PENDING</option>
                      <option value="APPROVED">APPROVED</option>
                      <option value="REJECTED">REJECTED</option>
                    </select>
                  </label>
                  <label>
                    <span>审批备注</span>
                    <input value={approvalComment} onChange={(event) => setApprovalComment(event.target.value)} placeholder="补充审批意见" />
                  </label>
                </div>
                <div className="toolbar">
                  <button className="button button-secondary" type="button" onClick={() => void handleCreateApproval()}>
                    保存审批记录
                  </button>
                </div>
              </div>
            </section>

            <section className="panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">执行记录</p>
                  <h3>执行轨迹</h3>
                </div>
                <span className="muted">{`共 ${executions.length} 条`}</span>
              </div>
              {isHistoryLoading ? (
                <div className="empty-state">正在加载执行记录...</div>
              ) : executions.length === 0 ? (
                <div className="empty-state">当前还没有执行记录。</div>
              ) : (
                <ul className="list">
                  {executions.map((item) => (
                    <li key={item.id}>
                      <strong>{item.executionStatus}</strong>
                      <div>{item.executionSummary}</div>
                      <div className="inline-metadata">
                        <span>{`执行人 ${item.executor}`}</span>
                        <span>{formatTime(item.startedAt)}</span>
                        <span>{formatTime(item.finishedAt)}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
              <div className="stack-sm">
                <strong>新增执行记录</strong>
                <div className="form-grid">
                  <label>
                    <span>执行状态</span>
                    <select value={executionStatus} onChange={(event) => setExecutionStatus(event.target.value)}>
                      <option value="RUNNING">RUNNING</option>
                      <option value="SUCCESS">SUCCESS</option>
                      <option value="FAILED">FAILED</option>
                    </select>
                  </label>
                  <label>
                    <span>执行摘要</span>
                    <input value={executionSummary} onChange={(event) => setExecutionSummary(event.target.value)} placeholder="记录执行动作和结果" />
                  </label>
                </div>
                <div className="toolbar">
                  <button className="button button-secondary" type="button" onClick={() => void handleCreateExecution()}>
                    保存执行记录
                  </button>
                </div>
              </div>
            </section>
          </div>

          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">复盘记录</p>
                <h3>Postmortem</h3>
              </div>
            </div>
            {isHistoryLoading ? (
              <div className="empty-state">正在加载复盘记录...</div>
            ) : (
              <div className="stack-md">
                {!postmortem ? (
                  <div className="empty-state">当前还没有复盘记录。</div>
                ) : (
                  <>
                    <p><strong>总结：</strong>{postmortem.summary}</p>
                    <p><strong>根因：</strong>{postmortem.rootCause}</p>
                    <p><strong>影响：</strong>{postmortem.impactStatement}</p>
                  </>
                )}
                {postmortem && postmortem.timeline.length > 0 ? (
                  <>
                    <strong>时间线</strong>
                    <ul className="list">{postmortem.timeline.map((item) => <li key={item}>{item}</li>)}</ul>
                  </>
                ) : null}
                {postmortem && postmortem.preventionItems.length > 0 ? (
                  <>
                    <strong>预防项</strong>
                    <ul className="list">{postmortem.preventionItems.map((item) => <li key={item}>{item}</li>)}</ul>
                  </>
                ) : null}
                <div className="stack-sm">
                  <strong>编辑复盘记录</strong>
                  <label>
                    <span className="muted">总结</span>
                    <textarea className="app-textarea" value={postmortemSummary} onChange={(event) => setPostmortemSummary(event.target.value)} />
                  </label>
                  <label>
                    <span className="muted">根因</span>
                    <textarea className="app-textarea" value={postmortemRootCause} onChange={(event) => setPostmortemRootCause(event.target.value)} />
                  </label>
                  <label>
                    <span className="muted">影响说明</span>
                    <textarea className="app-textarea" value={postmortemImpact} onChange={(event) => setPostmortemImpact(event.target.value)} />
                  </label>
                  <div className="form-grid">
                    <label>
                      <span>时间线</span>
                      <textarea className="app-textarea" value={postmortemTimeline} onChange={(event) => setPostmortemTimeline(event.target.value)} placeholder="每行一条" />
                    </label>
                    <label>
                      <span>预防项</span>
                      <textarea className="app-textarea" value={postmortemPrevention} onChange={(event) => setPostmortemPrevention(event.target.value)} placeholder="每行一条" />
                    </label>
                  </div>
                  <div className="toolbar">
                    <button className="button button-secondary" type="button" onClick={() => void handleSavePostmortem()}>
                      保存复盘记录
                    </button>
                  </div>
                </div>
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
