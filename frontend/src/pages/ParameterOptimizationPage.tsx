import { useEffect, useMemo, useState } from "react";
import {
  analyzeParameterOptimization,
  getParameterOptimizationContext,
  getParameterOptimizationHistory,
  getParameterOptimizationResult,
  saveKnowledgeQuickEntry
} from "../lib/api";
import type {
  KnowledgeQuickEntryRequest,
  ParameterConfigEntry,
  ParameterOptimizationContextPreview,
  ParameterOptimizationRequest,
  ParameterOptimizationResult,
  ParameterOptimizationServiceType
} from "../lib/types";

const EMPTY_REQUEST: ParameterOptimizationRequest = {
  serviceType: "IMPALA",
  currentSymptoms: "",
  optimizationGoal: "在不破坏当前业务语义和稳定性边界的前提下，给出适合当前组件版本的参数优化建议。",
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
  serviceType: "",
  componentVersion: "",
  serviceState: "",
  healthSummary: "",
  configEntries: {},
  scopedConfigEntries: [],
  recentSignals: []
};

type KnowledgeDomain =
  | "HDFS_PARAM"
  | "HDFS_SOURCE"
  | "YARN_PARAM"
  | "YARN_SOURCE"
  | "HIVE_PARAM"
  | "HIVE_SOURCE"
  | "IMPALA_PARAM"
  | "IMPALA_SOURCE";

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function serviceLabel(serviceType: ParameterOptimizationServiceType) {
  switch (serviceType) {
    case "HDFS":
      return "HDFS";
    case "YARN":
      return "YARN";
    case "HIVE_ON_TEZ":
      return "Hive on Tez";
    case "IMPALA":
      return "Impala";
    default:
      return serviceType;
  }
}

function defaultKnowledgeDomain(serviceType: ParameterOptimizationServiceType): KnowledgeDomain {
  switch (serviceType) {
    case "HDFS":
      return "HDFS_PARAM";
    case "YARN":
      return "YARN_PARAM";
    case "HIVE_ON_TEZ":
      return "HIVE_PARAM";
    case "IMPALA":
    default:
      return "IMPALA_PARAM";
  }
}

function sectionOrPlaceholder(items: string[], emptyText: string) {
  if (!items.length) {
    return <p className="muted">{emptyText}</p>;
  }
  return (
    <ul className="tight-list">
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
  );
}

function renderScope(entry: ParameterConfigEntry) {
  if (entry.scopeType === "SERVICE") {
    return `服务级 / ${entry.scopeName}`;
  }
  if (entry.roleType) {
    return `${entry.roleType} / ${entry.scopeName}`;
  }
  return entry.scopeName;
}

export function ParameterOptimizationPage() {
  const [form, setForm] = useState<ParameterOptimizationRequest>(EMPTY_REQUEST);
  const [knowledgeForm, setKnowledgeForm] = useState<KnowledgeQuickEntryRequest>({
    domain: defaultKnowledgeDomain(EMPTY_REQUEST.serviceType),
    content: ""
  });
  const [contextPreview, setContextPreview] = useState<ParameterOptimizationContextPreview>(EMPTY_CONTEXT);
  const [history, setHistory] = useState<ParameterOptimizationResult[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedResult, setSelectedResult] = useState<ParameterOptimizationResult | null>(null);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [loadingContext, setLoadingContext] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [savingKnowledge, setSavingKnowledge] = useState(false);
  const [configFilter, setConfigFilter] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadHistory();
    void loadContext(EMPTY_REQUEST.serviceType);
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
        const detail = await getParameterOptimizationResult(initialId);
        mergeResult(detail);
      } else {
        setSelectedResult(null);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化历史加载失败");
    } finally {
      setLoadingHistory(false);
    }
  }

  async function loadContext(serviceType: ParameterOptimizationServiceType) {
    setLoadingContext(true);
    setError(null);
    try {
      const result = await getParameterOptimizationContext(serviceType);
      setContextPreview(result);
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

  async function handleAnalyze() {
    if (!form.currentSymptoms.trim() && !form.manualConfigNote.trim() && !form.sourceCodeHints.trim() && !form.useCurrentClusterConfig) {
      setError("请至少提供当前症状、管理员备注、源码提示，或者启用当前组件配置采集。");
      return;
    }
    setAnalyzing(true);
    setError(null);
    try {
      const result = await analyzeParameterOptimization(form);
      mergeResult(result);
      await loadContext(form.serviceType);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数优化分析失败");
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleSaveKnowledge() {
    if (!knowledgeForm.content.trim()) {
      setError("请先填写参数优化知识内容。");
      return;
    }
    setSavingKnowledge(true);
    setError(null);
    try {
      await saveKnowledgeQuickEntry(knowledgeForm);
      setKnowledgeForm((current) => ({
        ...current,
        content: ""
      }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "参数知识保存失败");
    } finally {
      setSavingKnowledge(false);
    }
  }

  function updateField<K extends keyof ParameterOptimizationRequest>(field: K, value: ParameterOptimizationRequest[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  const scopedConfigEntries = useMemo(
    () => [...contextPreview.scopedConfigEntries].sort((left, right) => {
      const scopeCompare = renderScope(left).localeCompare(renderScope(right), "zh-CN");
      if (scopeCompare !== 0) {
        return scopeCompare;
      }
      return left.configKey.localeCompare(right.configKey, "zh-CN");
    }),
    [contextPreview.scopedConfigEntries]
  );

  const filteredConfigEntries = useMemo(() => {
    const keyword = configFilter.trim().toLowerCase();
    if (!keyword) {
      return scopedConfigEntries;
    }
    return scopedConfigEntries.filter((entry) =>
      entry.configKey.toLowerCase().includes(keyword)
      || entry.configValue.toLowerCase().includes(keyword)
      || renderScope(entry).toLowerCase().includes(keyword)
      || entry.valueSource.toLowerCase().includes(keyword)
    );
  }, [configFilter, scopedConfigEntries]);

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">参数优化中心</p>
          <h2>先拉取当前组件的 Cloudera Manager 配置参数，再结合日志、版本、知识库和源码线索给出参数优化建议</h2>
          <p className="lead">
            这个功能独立于事件诊断。你可以先切换到目标服务，查看当前组件完整的非敏感 CM 配置参数，再调用大模型生成优化建议，并明确说明每个参数为什么这样调整。
          </p>
          <div className="detail-actions">
            <button className="primary-button" disabled={analyzing} onClick={() => void handleAnalyze()} type="button">
              {analyzing ? "分析中..." : "开始参数优化"}
            </button>
            <button className="secondary-button" disabled={loadingContext} onClick={() => void loadContext(form.serviceType)} type="button">
              {loadingContext ? "采集中..." : "采集当前配置"}
            </button>
          </div>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">当前组件</span>
            <strong>{serviceLabel(form.serviceType)}</strong>
            <div className="status-list">
              <span>{contextPreview.clusterName ? `集群 ${contextPreview.clusterName}` : "集群 -"}</span>
              <span>{contextPreview.componentVersion ? `版本 ${contextPreview.componentVersion}` : "版本待识别"}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">配置快照状态</span>
            <ul className="tight-list">
              <li>{contextPreview.message}</li>
              <li>{scopedConfigEntries.length ? `已展示 ${scopedConfigEntries.length} 个非敏感配置项` : "当前没有可展示的配置项"}</li>
            </ul>
          </div>
        </div>
      </section>

      {error ? <div className="error-message">{error}</div> : null}

      <section className="chat-layout sql-optimization-layout">
        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">输入上下文</p>
              <h3>选择组件并补充分析线索</h3>
            </div>
          </div>

          <label className="app-field">
            <span>目标组件</span>
            <select
              className="app-select"
              value={form.serviceType}
              onChange={(event) => {
                const next = event.target.value as ParameterOptimizationServiceType;
                updateField("serviceType", next);
                void loadContext(next);
              }}
            >
              <option value="HDFS">HDFS</option>
              <option value="YARN">YARN</option>
              <option value="HIVE_ON_TEZ">Hive on Tez</option>
              <option value="IMPALA">Impala</option>
            </select>
          </label>

          <label className="app-field">
            <span>优化目标</span>
            <input
              className="app-input"
              value={form.optimizationGoal}
              onChange={(event) => updateField("optimizationGoal", event.target.value)}
              placeholder="例如：降低排队等待、缓解内存峰值、提升查询稳定性"
            />
          </label>

          <label className="app-field">
            <span>当前症状</span>
            <textarea
              className="app-textarea"
              value={form.currentSymptoms}
              onChange={(event) => updateField("currentSymptoms", event.target.value)}
              placeholder="输入当前组件的现象，例如超时、内存不足、排队严重、块恢复缓慢等"
            />
          </label>

          <label className="app-field">
            <span>源码提示</span>
            <textarea
              className="app-textarea"
              value={form.sourceCodeHints}
              onChange={(event) => updateField("sourceCodeHints", event.target.value)}
              placeholder="可选：粘贴当前版本源码中的参数定义、默认值、读取逻辑或关键调用链"
            />
          </label>

          <label className="app-field">
            <span>管理员备注</span>
            <textarea
              className="app-textarea"
              value={form.manualConfigNote}
              onChange={(event) => updateField("manualConfigNote", event.target.value)}
              placeholder="可选：补充现网限制、变更边界、历史调优经验或特殊背景"
            />
          </label>

          <label className="detail-chip-row" style={{ alignItems: "center" }}>
            <input
              checked={form.useCurrentClusterConfig}
              onChange={(event) => updateField("useCurrentClusterConfig", event.target.checked)}
              type="checkbox"
            />
            <span>分析时自动带入当前集群配置、版本和最近服务日志</span>
          </label>

          <div className="subpanel stack-sm">
            <p className="eyebrow">管理员维护参数知识</p>
            <label className="app-field">
              <span>知识领域</span>
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
                placeholder="输入参数经验、源码依据、风险边界或验证建议。保存后会直接写入数据库，后续参数优化会自动命中。"
              />
            </label>
            <button className="secondary-button" disabled={savingKnowledge} onClick={() => void handleSaveKnowledge()} type="button">
              {savingKnowledge ? "保存中..." : "保存参数知识"}
            </button>
          </div>
        </aside>

        <section className="panel chat-main">
          <div className="panel-head">
            <div>
              <p className="eyebrow">当前配置与优化结果</p>
              <h3>{selectedResult ? `${serviceLabel(selectedResult.serviceType as ParameterOptimizationServiceType)} 参数优化建议` : "等待生成参数优化结果"}</h3>
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
              <span className="status-kicker">最近结果</span>
              <strong>{selectedResult ? formatTime(selectedResult.createdAt) : "暂无"}</strong>
              <div className="status-list">
                <span>{selectedResult ? `来源 ${selectedResult.analysisSource}` : "来源 -"}</span>
                <span>{selectedResult?.componentVersion ? `版本 ${selectedResult.componentVersion}` : "版本 -"}</span>
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
                  <span>{scopedConfigEntries.length ? `配置项 ${scopedConfigEntries.length}` : "配置项 0"}</span>
                </div>
              </div>
              <div style={{ minWidth: 260 }}>
                <input
                  className="app-input"
                  value={configFilter}
                  onChange={(event) => setConfigFilter(event.target.value)}
                  placeholder="按作用域、参数名或值过滤，例如 handler、memory、timeout"
                />
              </div>
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
                    {filteredConfigEntries.map((entry) => (
                      <tr key={`${entry.scopeType}-${entry.scopeName}-${entry.configKey}`}>
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
            {sectionOrPlaceholder(contextPreview.recentSignals, "当前没有抓到最近日志信号。")}
          </div>

          {selectedResult ? (
            <div className="stack-md">
              <div className="subpanel stack-sm">
                <p className="eyebrow">参数问题总结</p>
                <p className="compact-lead">{selectedResult.problemSummary}</p>
              </div>

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
                        {selectedResult.recommendations.map((item) => (
                          <tr key={`${item.configKey}-${item.recommendedValue}`}>
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
                {sectionOrPlaceholder(selectedResult.sourceEvidence, "当前没有提取到明确的依据说明。")}
              </div>

              <div className="subpanel stack-sm">
                <p className="eyebrow">预期收益</p>
                {sectionOrPlaceholder(selectedResult.expectedBenefits, "当前没有明确的收益说明。")}
              </div>

              <div className="subpanel stack-sm">
                <p className="eyebrow">风险提示</p>
                {sectionOrPlaceholder(selectedResult.riskNotes, "当前没有额外的风险提示。")}
              </div>

              <div className="subpanel stack-sm">
                <p className="eyebrow">验证步骤</p>
                {sectionOrPlaceholder(selectedResult.validationSteps, "当前没有验证步骤。")}
              </div>

              <div className="subpanel stack-sm">
                <p className="eyebrow">规则分析命中</p>
                {sectionOrPlaceholder(selectedResult.ruleFindings, "当前没有命中额外规则。")}
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
          ) : history.length ? (
            <div className="stack-sm">
              {history.map((item) => (
                <div className={`chat-thread${selectedId === item.id ? " chat-thread-active" : ""}`} key={item.id}>
                  <button className="chat-thread-main" onClick={() => void handleSelect(item.id)} type="button">
                    <div className="chat-thread-head">
                      <strong>{serviceLabel(item.serviceType as ParameterOptimizationServiceType)}</strong>
                      <span>{formatTime(item.createdAt)}</span>
                    </div>
                    <div className="inline-metadata">
                      <span>{item.componentVersion ? `版本 ${item.componentVersion}` : "版本 -"}</span>
                      <span>{item.analysisSource}</span>
                    </div>
                    <span>{item.problemSummary}</span>
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <p className="muted">当前还没有参数优化记录。</p>
          )}
        </aside>
      </section>
    </div>
  );
}
