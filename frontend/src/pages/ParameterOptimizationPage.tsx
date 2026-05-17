import { useEffect, useMemo, useState } from "react";
import { LoadingButton } from "../components/LoadingButton";
import {
  checkParameterOptimizationCmConfig,
  getParameterOptimizationContext,
  getParameterOptimizationHistory,
  getParameterOptimizationResult,
  getParameterOptimizationTask,
  saveKnowledgeQuickEntry,
  startParameterOptimizationTask
} from "../lib/api";
import type {
  CmConfigCheckResponse,
  KnowledgeQuickEntryRequest,
  ParameterConfigEntry,
  ParameterOptimizationContextPreview,
  ParameterOptimizationRequest,
  ParameterOptimizationResult,
  ParameterOptimizationServiceType,
  ParameterOptimizationTaskResponse
} from "../lib/types";

const SERVICE_OPTIONS: Array<{ value: ParameterOptimizationServiceType; label: string }> = [
  { value: "HDFS", label: "HDFS" },
  { value: "YARN", label: "YARN" },
  { value: "HIVE_ON_TEZ", label: "Hive on Tez" },
  { value: "IMPALA", label: "Impala" }
];

const EMPTY_REQUEST: ParameterOptimizationRequest = {
  serviceType: "IMPALA",
  currentSymptoms: "",
  optimizationGoal: "在不破坏当前业务稳定性的前提下，结合当前版本、CM 配置、日志和知识库给出参数优化建议。",
  sourceCodeHints: "",
  manualConfigNote: "",
  createdBy: "frontend-operator",
  useCurrentClusterConfig: true
};

const EMPTY_CONTEXT: ParameterOptimizationContextPreview = {
  configured: false,
  available: false,
  message: "尚未采集当前组件配置。",
  clusterName: "",
  serviceName: "",
  serviceType: "IMPALA",
  componentVersion: "",
  serviceState: "",
  healthSummary: "",
  configEntries: {},
  scopedConfigEntries: [],
  recentSignals: []
};

function serviceLabel(serviceType: string) {
  return SERVICE_OPTIONS.find((item) => item.value === serviceType)?.label ?? serviceType;
}

function defaultKnowledgeDomain(serviceType: ParameterOptimizationServiceType) {
  if (serviceType === "HIVE_ON_TEZ") return "HIVE_PARAM";
  return `${serviceType}_PARAM`;
}

function formatTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function taskStatusLabel(task: ParameterOptimizationTaskResponse | null) {
  if (!task) return "未启动";
  if (task.status === "COMPLETED") return "已完成";
  if (task.status === "FAILED") return "失败";
  return "分析中";
}

function renderScope(entry: ParameterConfigEntry) {
  if (entry.scopeType === "ROLE_CONFIG_GROUP") {
    return `${entry.roleType || "ROLE"} @ ${entry.scopeName || "unknown"}`;
  }
  return `SERVICE @ ${entry.scopeName || "service"}`;
}

function renderList(values: string[] | null | undefined, emptyText: string) {
  if (!values || values.length === 0) {
    return <p className="muted">{emptyText}</p>;
  }
  return (
    <ul className="list">
      {values.map((item, index) => (
        <li key={`${item}-${index}`}>{item}</li>
      ))}
    </ul>
  );
}

export function ParameterOptimizationPage() {
  const [form, setForm] = useState<ParameterOptimizationRequest>(EMPTY_REQUEST);
  const [contextPreview, setContextPreview] = useState<ParameterOptimizationContextPreview>(EMPTY_CONTEXT);
  const [history, setHistory] = useState<ParameterOptimizationResult[]>([]);
  const [selectedResult, setSelectedResult] = useState<ParameterOptimizationResult | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [activeTask, setActiveTask] = useState<ParameterOptimizationTaskResponse | null>(null);
  const [cmCheck, setCmCheck] = useState<CmConfigCheckResponse | null>(null);
  const [knowledgeForm, setKnowledgeForm] = useState<KnowledgeQuickEntryRequest>({
    domain: defaultKnowledgeDomain(EMPTY_REQUEST.serviceType),
    content: ""
  });
  const [configFilter, setConfigFilter] = useState("");
  const [scopeFilter, setScopeFilter] = useState("ALL");
  const [sourceFilter, setSourceFilter] = useState("ALL");
  const [loadingContext, setLoadingContext] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [checkingCm, setCheckingCm] = useState(false);
  const [savingKnowledge, setSavingKnowledge] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const scopedConfigEntries = contextPreview.scopedConfigEntries ?? [];
  const scopeOptions = useMemo(() => {
    const values = new Set<string>(["ALL"]);
    scopedConfigEntries.forEach((entry) => values.add(renderScope(entry)));
    return Array.from(values);
  }, [scopedConfigEntries]);

  const sourceOptions = useMemo(() => {
    const values = new Set<string>(["ALL"]);
    scopedConfigEntries.forEach((entry) => values.add(entry.valueSource || "UNKNOWN"));
    return Array.from(values);
  }, [scopedConfigEntries]);

  const filteredConfigEntries = useMemo(() => {
    const keyword = configFilter.trim().toLowerCase();
    return scopedConfigEntries.filter((entry) => {
      const scope = renderScope(entry);
      const source = entry.valueSource || "UNKNOWN";
      const matchesKeyword = !keyword || [scope, source, entry.configKey, entry.configValue]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
      const matchesScope = scopeFilter === "ALL" || scope === scopeFilter;
      const matchesSource = sourceFilter === "ALL" || source === sourceFilter;
      return matchesKeyword && matchesScope && matchesSource;
    });
  }, [configFilter, scopedConfigEntries, scopeFilter, sourceFilter]);

  useEffect(() => {
    void loadContext(EMPTY_REQUEST.serviceType);
    void loadHistory();
  }, []);

  useEffect(() => {
    setKnowledgeForm((current) => ({
      ...current,
      domain: defaultKnowledgeDomain(form.serviceType)
    }));
  }, [form.serviceType]);

  async function loadHistory() {
    setLoadingHistory(true);
    try {
      const result = await getParameterOptimizationHistory();
      setHistory(result);
      const initialId = result[0]?.id ?? null;
      setSelectedId(initialId);
      if (initialId != null) {
        setSelectedResult(await getParameterOptimizationResult(initialId));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化历史加载失败");
    } finally {
      setLoadingHistory(false);
    }
  }

  async function loadContext(serviceType: ParameterOptimizationServiceType, forceRefresh = false) {
    setLoadingContext(true);
    setError(null);
    try {
      setContextPreview(await getParameterOptimizationContext(serviceType, forceRefresh));
    } catch (cause) {
      setContextPreview({
        ...EMPTY_CONTEXT,
        serviceType,
        message: cause instanceof Error ? cause.message : "当前配置采集失败"
      });
    } finally {
      setLoadingContext(false);
    }
  }

  function mergeResult(result: ParameterOptimizationResult) {
    setHistory((current) => {
      const next = [result, ...current.filter((item) => item.id !== result.id)];
      next.sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
      return next;
    });
    setSelectedResult(result);
    setSelectedId(result.id);
  }

  async function handleSelect(recordId: number) {
    setError(null);
    setSelectedId(recordId);
    try {
      const detail = await getParameterOptimizationResult(recordId);
      setSelectedResult(detail);
      setHistory((current) => [detail, ...current.filter((item) => item.id !== recordId)]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化详情加载失败");
    }
  }

  async function handleCheckCmConfig() {
    setCheckingCm(true);
    setError(null);
    try {
      setCmCheck(await checkParameterOptimizationCmConfig(form.serviceType));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "CM 配置采集自检失败");
    } finally {
      setCheckingCm(false);
    }
  }

  async function handleAnalyze() {
    if (!form.currentSymptoms.trim()
      && !form.manualConfigNote.trim()
      && !form.sourceCodeHints.trim()
      && !form.useCurrentClusterConfig) {
      setError("请至少提供当前症状、管理员备注、源码提示，或启用当前组件配置采集。");
      return;
    }
    setAnalyzing(true);
    setError(null);
    setMessage("参数优化任务已提交，后台正在采集配置并调用大模型。");
    try {
      const task = await startParameterOptimizationTask(form);
      setActiveTask(task);
      for (let attempt = 0; attempt < 180; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 2000));
        const latest = await getParameterOptimizationTask(task.taskId);
        setActiveTask(latest);
        if (latest.status === "COMPLETED" && latest.result) {
          mergeResult(latest.result);
          setMessage(latest.message);
          return;
        }
        if (latest.status === "FAILED") {
          throw new Error(latest.errorMessage || latest.message || "参数优化任务失败");
        }
      }
      throw new Error("参数优化任务等待超时，请稍后在历史记录中查看结果。");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化任务提交失败");
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleSaveKnowledge() {
    if (!knowledgeForm.domain.trim()) {
      setError("请输入知识域。");
      return;
    }
    if (!knowledgeForm.content.trim()) {
      setError("请输入知识内容。");
      return;
    }
    setSavingKnowledge(true);
    setError(null);
    try {
      await saveKnowledgeQuickEntry({
        domain: knowledgeForm.domain.trim().toUpperCase(),
        content: knowledgeForm.content.trim()
      });
      setKnowledgeForm((current) => ({ ...current, content: "" }));
      setMessage("参数优化知识已写入数据库，后续分析会自动参与检索。");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化知识保存失败");
    } finally {
      setSavingKnowledge(false);
    }
  }

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">参数优化中心</p>
          <h2>拉取当前组件 CM 配置参数，再结合日志、版本、知识库和大模型给出优化建议</h2>
          <p className="lead">
            该功能独立于事件诊断。选择 HDFS、YARN、Hive on Tez 或 Impala 后，可以查看服务级和角色配置组参数，并生成可解释的调参建议。
          </p>
          <div className="detail-actions">
            <LoadingButton className="primary-button" loading={analyzing} loadingText="汇总配置并请求大模型" onClick={() => void handleAnalyze()}>
              开始参数优化
            </LoadingButton>
            <LoadingButton className="secondary-button" loading={loadingContext} loadingText="正在采集 CM 参数" onClick={() => void loadContext(form.serviceType, true)}>
              采集当前配置
            </LoadingButton>
            <LoadingButton className="secondary-button" loading={checkingCm} loadingText="正在校验 CM 配置" onClick={() => void handleCheckCmConfig()}>
              CM 配置自检
            </LoadingButton>
          </div>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">任务与配置状态</span>
            <strong>{contextPreview.available ? "当前配置可用" : "等待配置采集"}</strong>
            <ul className="tight-list">
              <li>{activeTask ? `${taskStatusLabel(activeTask)}：${activeTask.message}` : "尚未启动参数优化任务。"}</li>
              <li>{scopedConfigEntries.length ? `已展示 ${scopedConfigEntries.length} 个非敏感配置项。` : "当前没有可展示的配置项。"}</li>
              {message ? <li>{message}</li> : null}
              {error ? <li>{error}</li> : null}
            </ul>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">当前组件</span>
            <strong>{serviceLabel(form.serviceType)}</strong>
            <div className="status-list">
              <span>{contextPreview.clusterName || "cluster -"}</span>
              <span>{contextPreview.serviceName || "service -"}</span>
              <span>{contextPreview.componentVersion || "version -"}</span>
            </div>
          </div>
        </div>
      </section>

      {cmCheck ? (
        <section className={`panel result-panel ${cmCheck.success ? "result-success" : "result-failure"}`}>
          <div className="panel-head">
            <div>
              <p className="eyebrow">CM 配置采集自检</p>
              <h3>{cmCheck.message}</h3>
            </div>
            <div className="inline-metadata">
              <span>{`服务级配置: ${cmCheck.serviceConfigCount}`}</span>
              <span>{`角色配置组: ${cmCheck.roleConfigGroupCount}`}</span>
              <span>{`角色配置项: ${cmCheck.roleConfigCount}`}</span>
            </div>
          </div>
          <div className="table-shell">
            <table className="detail-table">
              <thead>
                <tr>
                  <th>检查步骤</th>
                  <th>状态</th>
                  <th>数量</th>
                  <th>耗时</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                {cmCheck.steps.map((step) => (
                  <tr key={`${step.step}-${step.endpoint}`}>
                    <td>{step.step}</td>
                    <td>{step.success ? "成功" : "失败"}</td>
                    <td>{step.itemCount}</td>
                    <td>{step.durationMs}ms</td>
                    <td>{step.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <div className="chat-layout">
        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">输入上下文</p>
              <h3>SQL 无关，仅做组件参数优化</h3>
            </div>
          </div>
          <div className="stack-md">
            <label className="app-field">
              <span>组件类型</span>
              <select
                className="app-select"
                value={form.serviceType}
                onChange={(event) => {
                  const next = event.target.value as ParameterOptimizationServiceType;
                  setForm((current) => ({ ...current, serviceType: next }));
                  setContextPreview((current) => ({ ...current, serviceType: next }));
                  void loadContext(next);
                }}
              >
                {SERVICE_OPTIONS.map((item) => (
                  <option key={item.value} value={item.value}>{item.label}</option>
                ))}
              </select>
            </label>
            <label className="app-field">
              <span>当前症状</span>
              <textarea
                className="app-textarea"
                value={form.currentSymptoms}
                onChange={(event) => setForm((current) => ({ ...current, currentSymptoms: event.target.value }))}
                placeholder="例如：查询超时、NameNode handler 打满、NodeManager 容器启动慢、Impala admission 排队等。"
              />
            </label>
            <label className="app-field">
              <span>优化目标</span>
              <textarea
                className="app-textarea"
                value={form.optimizationGoal}
                onChange={(event) => setForm((current) => ({ ...current, optimizationGoal: event.target.value }))}
              />
            </label>
            <label className="app-field">
              <span>源码 / 版本线索</span>
              <textarea
                className="app-textarea"
                value={form.sourceCodeHints}
                onChange={(event) => setForm((current) => ({ ...current, sourceCodeHints: event.target.value }))}
                placeholder="可粘贴当前版本源码、配置项说明、CDH/CDP 版本差异或已知 bug 线索。"
              />
            </label>
            <label className="app-field">
              <span>人工补充说明</span>
              <textarea
                className="app-textarea"
                value={form.manualConfigNote}
                onChange={(event) => setForm((current) => ({ ...current, manualConfigNote: event.target.value }))}
                placeholder="可补充业务峰值、集群规模、队列策略、变更窗口、不能调整的参数等。"
              />
            </label>
            <label className="toggle-row">
              <input
                checked={form.useCurrentClusterConfig}
                onChange={(event) => setForm((current) => ({ ...current, useCurrentClusterConfig: event.target.checked }))}
                type="checkbox"
              />
              <span>分析时自动带入当前集群配置、版本和最近服务日志</span>
            </label>
          </div>

          <div className="subpanel stack-sm">
            <p className="eyebrow">管理员知识库</p>
            <label className="app-field">
              <span>知识域</span>
              <select
                className="app-select"
                value={knowledgeForm.domain}
                onChange={(event) => setKnowledgeForm((current) => ({ ...current, domain: event.target.value }))}
              >
                <option value="HDFS_PARAM">HDFS 参数经验</option>
                <option value="HDFS_SOURCE">HDFS 源码依据</option>
                <option value="YARN_PARAM">YARN 参数经验</option>
                <option value="YARN_SOURCE">YARN 源码依据</option>
                <option value="HIVE_PARAM">Hive on Tez 参数经验</option>
                <option value="HIVE_SOURCE">Hive on Tez 源码依据</option>
                <option value="IMPALA_PARAM">Impala 参数经验</option>
                <option value="IMPALA_SOURCE">Impala 源码依据</option>
              </select>
            </label>
            <label className="app-field">
              <span>知识内容</span>
              <textarea
                className="app-textarea"
                value={knowledgeForm.content}
                onChange={(event) => setKnowledgeForm((current) => ({ ...current, content: event.target.value }))}
                placeholder="录入参数经验、源码依据、风险边界或验证建议。保存后直接写入数据库。"
              />
            </label>
            <LoadingButton className="secondary-button" loading={savingKnowledge} loadingText="正在保存参数知识" onClick={() => void handleSaveKnowledge()}>
              保存参数知识
            </LoadingButton>
          </div>
        </aside>

        <section className="panel chat-main">
          <div className="panel-head">
            <div>
              <p className="eyebrow">当前配置与优化结果</p>
              <h3>{selectedResult ? `${serviceLabel(selectedResult.serviceType)} 参数优化建议` : "等待生成参数优化结果"}</h3>
            </div>
            <div className="inline-metadata">
              <span>{contextPreview.serviceName ? `服务 ${contextPreview.serviceName}` : "服务 -"}</span>
              <span>{contextPreview.healthSummary ? `健康 ${contextPreview.healthSummary}` : "健康 -"}</span>
              <span>{selectedResult?.llmModel ? `模型 ${selectedResult.llmModel}` : "模型 -"}</span>
            </div>
          </div>

          <div className="summary-grid sql-summary-grid">
            <div className="status-card">
              <span className="status-kicker">配置采集</span>
              <strong>{contextPreview.available ? "已就绪" : "待补充"}</strong>
              <div className="status-list">
                <span>{contextPreview.message}</span>
              </div>
            </div>
            <div className="status-card">
              <span className="status-kicker">分析任务</span>
              <strong>{taskStatusLabel(activeTask)}</strong>
              <div className="status-list">
                <span>{activeTask?.message || "点击开始参数优化后会后台执行并刷新结果。"}</span>
                <span>{selectedResult ? `最近结果 ${formatTime(selectedResult.createdAt)}` : "最近结果 -"}</span>
              </div>
            </div>
          </div>

          <div className="subpanel stack-sm">
            <div className="panel-head">
              <div>
                <p className="eyebrow">当前组件配置参数</p>
                <div className="inline-metadata">
                  <span>{contextPreview.clusterName ? `集群 ${contextPreview.clusterName}` : "集群 -"}</span>
                  <span>{contextPreview.serviceState ? `状态 ${contextPreview.serviceState}` : "状态 -"}</span>
                  <span>{`已筛选 ${filteredConfigEntries.length}/${scopedConfigEntries.length}`}</span>
                </div>
              </div>
            </div>
            <div className="toolbar compact-toolbar">
              <input
                className="app-input"
                value={configFilter}
                onChange={(event) => setConfigFilter(event.target.value)}
                placeholder="按作用域、参数名或值过滤，例如 handler、memory、timeout"
              />
              <select className="app-select" value={scopeFilter} onChange={(event) => setScopeFilter(event.target.value)}>
                {scopeOptions.map((scope) => (
                  <option key={scope} value={scope}>{scope === "ALL" ? "全部作用域" : scope}</option>
                ))}
              </select>
              <select className="app-select" value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}>
                {sourceOptions.map((source) => (
                  <option key={source} value={source}>{source === "ALL" ? "全部来源" : source}</option>
                ))}
              </select>
            </div>
            {filteredConfigEntries.length ? (
              <div className="table-shell">
                <table className="detail-table">
                  <thead>
                    <tr>
                      <th>作用域</th>
                      <th>参数名</th>
                      <th>当前值</th>
                      <th>来源</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredConfigEntries.map((entry, index) => (
                      <tr key={`${entry.scopeType}-${entry.scopeName}-${entry.configKey}-${index}`}>
                        <td>{renderScope(entry)}</td>
                        <td>{entry.configKey}</td>
                        <td>{entry.configValue}</td>
                        <td>{entry.valueSource}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="muted">
                {scopedConfigEntries.length
                  ? "没有匹配当前筛选条件的配置项。"
                  : "当前还没有可用的组件配置快照，可先点击“采集当前配置”。"}
              </p>
            )}
          </div>

          <div className="subpanel stack-sm">
            <p className="eyebrow">最近服务日志信号</p>
            {renderList(contextPreview.recentSignals, "当前没有抓到最近日志信号。")}
          </div>

          {selectedResult ? (
            <div className="stack-md">
              <div className="subpanel stack-sm">
                <p className="eyebrow">参数问题总结</p>
                <p className="compact-lead">{selectedResult.problemSummary}</p>
              </div>

              <details className="subpanel stack-sm">
                <summary className="panel-head">
                  <span>
                    <p className="eyebrow">大模型输入上下文</p>
                    <strong>本次提交给模型的核心配置、日志与人工补充信息</strong>
                  </span>
                  <span>{selectedResult.configSnapshotText.length} 字符</span>
                </summary>
                <pre className="result-details">{selectedResult.configSnapshotText || "本次没有可展示的配置上下文。"}</pre>
                {selectedResult.sourceCodeHints ? (
                  <pre className="result-details">{`源码/版本线索：\n${selectedResult.sourceCodeHints}`}</pre>
                ) : null}
              </details>

              <div className="subpanel stack-sm">
                <p className="eyebrow">建议调整项</p>
                {selectedResult.recommendations.length ? (
                  <div className="table-shell">
                    <table className="detail-table">
                      <thead>
                        <tr>
                          <th>参数名</th>
                          <th>当前值</th>
                          <th>建议值</th>
                          <th>为什么这样优化</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedResult.recommendations.map((item, index) => (
                          <tr key={`${item.configKey}-${item.recommendedValue}-${index}`}>
                            <td>{item.configKey}</td>
                            <td>{item.currentValue || "-"}</td>
                            <td>{item.recommendedValue || "-"}</td>
                            <td>{item.reason || "-"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="muted">本次分析没有给出明确的参数调整项。</p>
                )}
              </div>

              <div className="subpanel stack-sm">
                <p className="eyebrow">为什么这样优化</p>
                {renderList(selectedResult.sourceEvidence, "当前没有提取到明确的依据说明。")}
              </div>
              <div className="subpanel stack-sm">
                <p className="eyebrow">预期收益</p>
                {renderList(selectedResult.expectedBenefits, "当前没有明确的收益说明。")}
              </div>
              <div className="subpanel stack-sm">
                <p className="eyebrow">风险提示</p>
                {renderList(selectedResult.riskNotes, "当前没有额外的风险提示。")}
              </div>
              <div className="subpanel stack-sm">
                <p className="eyebrow">验证步骤</p>
                {renderList(selectedResult.validationSteps, "当前没有补充验证步骤。")}
              </div>
              <div className="subpanel stack-sm">
                <p className="eyebrow">规则分析命中</p>
                {renderList(selectedResult.ruleFindings, "当前没有命中额外规则。")}
              </div>
            </div>
          ) : (
            <div className="subpanel stack-sm">
              <p className="eyebrow">结果区域</p>
              <p className="muted">完成一次参数优化分析后，这里会展示问题总结、建议参数、优化原因、风险和验证步骤。</p>
            </div>
          )}
        </section>

        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">历史分析</p>
              <h3>最近参数优化记录</h3>
            </div>
          </div>
          {loadingHistory ? (
            <p className="muted">正在加载历史记录...</p>
          ) : history.length === 0 ? (
            <div className="empty-state">还没有参数优化历史。</div>
          ) : (
            <div className="stack-sm">
              {history.map((item) => (
                <button
                  className={`history-card ${selectedId === item.id ? "history-card-active" : ""}`}
                  key={item.id}
                  onClick={() => void handleSelect(item.id)}
                  type="button"
                >
                  <strong>{serviceLabel(item.serviceType)}</strong>
                  <span>{item.problemSummary}</span>
                  <small>{formatTime(item.createdAt)}</small>
                </button>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
