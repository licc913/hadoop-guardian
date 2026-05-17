import { useEffect, useMemo, useState } from "react";
import { LoadingButton } from "../components/LoadingButton";
import {
  analyzeSqlOptimization,
  getSqlOptimizationHistory,
  getSqlOptimizationResult,
  saveKnowledgeQuickEntry
} from "../lib/api";
import type {
  KnowledgeQuickEntryRequest,
  SqlOptimizationEngine,
  SqlOptimizationRequest,
  SqlOptimizationResult
} from "../lib/types";

const EMPTY_REQUEST: SqlOptimizationRequest = {
  engineType: "IMPALA",
  originalSql: "",
  tableSchemaNote: "",
  partitionInfo: "",
  explainText: "",
  errorText: "",
  optimizationGoal: "提升执行效率，同时保持结果语义不变。",
  createdBy: "frontend-operator"
};

const EMPTY_KNOWLEDGE_FORM: KnowledgeQuickEntryRequest = {
  domain: "IMPALA_SQL",
  content: ""
};

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function engineLabel(engineType: SqlOptimizationEngine) {
  return engineType === "HIVE" ? "Hive SQL" : "Impala SQL";
}

function renderList(items: string[], emptyText: string) {
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

export function SqlOptimizationPage() {
  const [form, setForm] = useState<SqlOptimizationRequest>(EMPTY_REQUEST);
  const [history, setHistory] = useState<SqlOptimizationResult[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedResult, setSelectedResult] = useState<SqlOptimizationResult | null>(null);
  const [knowledgeForm, setKnowledgeForm] = useState<KnowledgeQuickEntryRequest>(EMPTY_KNOWLEDGE_FORM);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [savingKnowledge, setSavingKnowledge] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadHistory();
  }, []);

  useEffect(() => {
    setKnowledgeForm((current) => ({
      ...current,
      domain: form.engineType === "HIVE" ? "HIVE_SQL" : "IMPALA_SQL"
    }));
  }, [form.engineType]);

  useEffect(() => {
    if (selectedId == null) {
      setSelectedResult(history[0] ?? null);
      return;
    }
    const local = history.find((item) => item.id === selectedId);
    if (local) {
      setSelectedResult(local);
    }
  }, [history, selectedId]);

  async function loadHistory() {
    setLoading(true);
    setError(null);
    try {
      const result = await getSqlOptimizationHistory();
      setHistory(result);
      const initialId = selectedId ?? result[0]?.id ?? null;
      setSelectedId(initialId);
      if (initialId != null) {
        const detail = await getSqlOptimizationResult(initialId);
        mergeResult(detail);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "SQL 优化历史加载失败");
    } finally {
      setLoading(false);
    }
  }

  function mergeResult(result: SqlOptimizationResult) {
    setHistory((current) => {
      const next = [result, ...current.filter((item) => item.id !== result.id)];
      next.sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
      return next;
    });
    if (selectedId == null || selectedId === result.id) {
      setSelectedResult(result);
    }
  }

  async function handleSelect(recordId: number) {
    setSelectedId(recordId);
    setError(null);
    try {
      const result = await getSqlOptimizationResult(recordId);
      mergeResult(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "SQL 优化详情加载失败");
    }
  }

  async function handleAnalyze() {
    if (!form.originalSql.trim()) {
      setError("请先输入待优化的 SQL。");
      return;
    }
    setAnalyzing(true);
    setError(null);
    try {
      const result = await analyzeSqlOptimization(form);
      mergeResult(result);
      setSelectedId(result.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "SQL 优化失败");
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleSaveKnowledge() {
    if (!knowledgeForm.content.trim()) {
      setError("请先填写 SQL 优化知识内容。");
      return;
    }
    setSavingKnowledge(true);
    setError(null);
    try {
      await saveKnowledgeQuickEntry(knowledgeForm);
      setKnowledgeForm({
        domain: knowledgeForm.domain,
        content: ""
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "SQL 优化知识保存失败");
    } finally {
      setSavingKnowledge(false);
    }
  }

  const currentSummary = useMemo(() => {
    if (!selectedResult) {
      return "当前还没有 SQL 优化记录。";
    }
    return selectedResult.problemSummary;
  }, [selectedResult]);

  function updateField<K extends keyof SqlOptimizationRequest>(field: K, value: SqlOptimizationRequest[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">SQL 优化中心</p>
          <h2>独立分析 Impala SQL 与 Hive SQL，结合规则扫描、最新知识库与大模型给出优化建议</h2>
          <p className="lead">
            这里不绑定事件诊断。后端会先做规则分析，再结合管理员维护的 SQL 优化知识库和大模型，输出问题总结、优化后 SQL、风险提示与验证步骤。
          </p>
          <div className="detail-actions">
            <LoadingButton className="primary-button" loading={analyzing} loadingText="正在分析 SQL 与知识库" onClick={() => void handleAnalyze()}>
              开始优化
            </LoadingButton>
            <button className="secondary-button" onClick={() => setForm(EMPTY_REQUEST)} type="button">
              重置输入
            </button>
          </div>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">优化历史</span>
            <strong>{history.length}</strong>
            <div className="status-list">
              <span>{selectedResult ? `当前: ${engineLabel(selectedResult.engineType)}` : "当前: 无"}</span>
              <span>{selectedResult ? `来源: ${selectedResult.analysisSource}` : "来源: -"}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">当前摘要</span>
            <ul className="tight-list">
              <li>{currentSummary}</li>
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
              <h3>SQL 与补充信息</h3>
            </div>
          </div>
          <label className="app-field">
            <span>执行引擎</span>
            <select className="app-select" value={form.engineType} onChange={(event) => updateField("engineType", event.target.value as SqlOptimizationEngine)}>
              <option value="IMPALA">Impala SQL</option>
              <option value="HIVE">Hive SQL</option>
            </select>
          </label>
          <label className="app-field">
            <span>优化目标</span>
            <input
              className="app-input"
              value={form.optimizationGoal}
              onChange={(event) => updateField("optimizationGoal", event.target.value)}
              placeholder="例如：减少扫描范围、降低 Shuffle、提升查询稳定性"
            />
          </label>
          <label className="app-field">
            <span>原始 SQL</span>
            <textarea
              className="app-textarea sql-editor"
              value={form.originalSql}
              onChange={(event) => updateField("originalSql", event.target.value)}
              placeholder="粘贴待优化的 Impala SQL 或 Hive SQL"
            />
          </label>
          <label className="app-field">
            <span>表结构说明</span>
            <textarea
              className="app-textarea"
              value={form.tableSchemaNote}
              onChange={(event) => updateField("tableSchemaNote", event.target.value)}
              placeholder="可选：字段、主键、数据量、热点字段等"
            />
          </label>
          <label className="app-field">
            <span>分区字段</span>
            <textarea
              className="app-textarea"
              value={form.partitionInfo}
              onChange={(event) => updateField("partitionInfo", event.target.value)}
              placeholder="可选：例如 dt、region、ds"
            />
          </label>
          <label className="app-field">
            <span>EXPLAIN / PROFILE</span>
            <textarea
              className="app-textarea"
              value={form.explainText}
              onChange={(event) => updateField("explainText", event.target.value)}
              placeholder="可选：Explain 或 Profile 结果"
            />
          </label>
          <label className="app-field">
            <span>报错信息</span>
            <textarea
              className="app-textarea"
              value={form.errorText}
              onChange={(event) => updateField("errorText", event.target.value)}
              placeholder="可选：执行报错、超时、资源不足等现象"
            />
          </label>

          <div className="subpanel stack-sm">
            <p className="eyebrow">管理员维护 SQL 知识</p>
            <label className="app-field">
              <span>知识领域</span>
              <select
                className="app-select"
                value={knowledgeForm.domain}
                onChange={(event) => setKnowledgeForm((current) => ({ ...current, domain: event.target.value }))}
              >
                <option value="IMPALA_SQL">Impala SQL</option>
                <option value="HIVE_SQL">Hive SQL</option>
              </select>
            </label>
            <label className="app-field">
              <span>知识内容</span>
              <textarea
                className="app-textarea"
                value={knowledgeForm.content}
                onChange={(event) => setKnowledgeForm((current) => ({ ...current, content: event.target.value }))}
                placeholder="输入优化经验、反模式、改写建议或验证方法。保存后，下次 SQL 优化会自动命中这些知识。"
              />
            </label>
            <LoadingButton className="secondary-button" loading={savingKnowledge} loadingText="正在保存 SQL 知识" onClick={() => void handleSaveKnowledge()}>
              保存 SQL 知识
            </LoadingButton>
          </div>
        </aside>

        <section className="panel chat-main">
          <div className="panel-head">
            <div>
              <p className="eyebrow">优化结果</p>
              <h3>{selectedResult ? `${engineLabel(selectedResult.engineType)} 优化记录` : "尚未生成优化结果"}</h3>
            </div>
            <div className="inline-metadata">
              <span>{selectedResult ? `时间 ${formatTime(selectedResult.createdAt)}` : "时间 -"}</span>
              <span>{selectedResult ? `来源 ${selectedResult.analysisSource}` : "来源 -"}</span>
              <span>{selectedResult?.llmModel ? `模型 ${selectedResult.llmModel}` : "模型 -"}</span>
            </div>
          </div>

          {selectedResult ? (
            <div className="stack-md">
              <div className="subpanel stack-sm">
                <p className="eyebrow">问题总结</p>
                <p className="compact-lead">{selectedResult.problemSummary}</p>
              </div>
              <div className="subpanel stack-sm">
                <p className="eyebrow">优化后 SQL</p>
                <pre className="chat-content sql-output">{selectedResult.optimizedSql}</pre>
              </div>
              <div className="summary-grid sql-summary-grid">
                <div className="subpanel stack-sm">
                  <p className="eyebrow">规则发现</p>
                  {renderList(selectedResult.ruleFindings, "当前没有命中额外规则。")}
                </div>
                <div className="subpanel stack-sm">
                  <p className="eyebrow">优化点</p>
                  {renderList(selectedResult.optimizationPoints, "当前没有提取到明确优化点。")}
                </div>
                <div className="subpanel stack-sm">
                  <p className="eyebrow">风险提示</p>
                  {renderList(selectedResult.riskNotes, "当前没有额外风险提示。")}
                </div>
                <div className="subpanel stack-sm">
                  <p className="eyebrow">验证步骤</p>
                  {renderList(selectedResult.validationSteps, "当前没有补充验证步骤。")}
                </div>
              </div>
            </div>
          ) : (
            <div className="empty-state">先在左侧输入 SQL 并点击“开始优化”，结果会显示在这里。</div>
          )}
        </section>

        <aside className="panel chat-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">历史与复用</p>
              <h3>最近优化结果</h3>
            </div>
          </div>
          {loading ? (
            <div className="empty-state">正在加载优化历史...</div>
          ) : history.length === 0 ? (
            <div className="empty-state">当前还没有 SQL 优化记录。</div>
          ) : (
            <div className="stack-sm">
              {history.map((item) => (
                <article key={item.id} className={`chat-thread ${item.id === (selectedId ?? selectedResult?.id) ? "chat-thread-active" : ""}`}>
                  <button className="chat-thread-main" onClick={() => void handleSelect(item.id)} type="button">
                    <div className="chat-thread-head">
                      <strong>{engineLabel(item.engineType)}</strong>
                      <span>{item.analysisSource}</span>
                    </div>
                    <span>{item.problemSummary}</span>
                    <span className="chat-thread-meta">{formatTime(item.createdAt)}</span>
                  </button>
                </article>
              ))}
            </div>
          )}
        </aside>
      </section>
    </div>
  );
}
