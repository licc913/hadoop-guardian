import { useEffect, useMemo, useState } from "react";
import {
  askLlmQuestion,
  getClouderaManagerCurrentStatus,
  getDataSourceConfig,
  getKnowledgeArticles,
  saveDataSourceConfig,
  saveKnowledgeArticle,
  syncClouderaManagerAlerts,
  testDiagnosticScripts,
  testJmxEndpoints,
  testLlmConnection,
  testLogSourceConnection
} from "../lib/api";
import type {
  ClouderaManagerSyncResponse,
  CmCurrentStatusResponse,
  DataSourceConfig,
  DiagnosticScript,
  IntegrationTestResponse,
  JmxEndpoint,
  JmxProbeResponse,
  KnowledgeArticle,
  KnowledgeArticleRequest,
  LlmPromptResponse
} from "../lib/types";

const emptyConfig: DataSourceConfig = {
  clouderaManager: {
    enabled: false,
    baseUrl: "",
    apiVersion: "v51",
    username: "",
    password: "",
    passwordConfigured: false,
    clusterName: "",
    configured: false
  },
  logSource: {
    enabled: false,
    providerType: "ELASTICSEARCH",
    baseUrl: "",
    authType: "BASIC",
    authToken: "",
    authTokenConfigured: false,
    indexPattern: "hadoop-*",
    defaultTimeWindowMinutes: 30
  },
  llm: {
    enabled: false,
    endpoint: "",
    apiKey: "",
    apiKeyConfigured: false,
    model: "",
    connectTimeoutMs: 10000,
    readTimeoutMs: 30000,
    temperature: 0.2,
    maxTokens: 900,
    configured: false
  },
  jmxEndpoints: [],
  diagnosticScripts: []
};

const emptyKnowledgeArticle: KnowledgeArticleRequest = {
  domain: "IMPALA",
  scenarioKey: "",
  title: "",
  summary: "",
  applicability: "",
  riskLevel: "MEDIUM",
  requiresApproval: false,
  sourceName: "内建知识库",
  sourceUrl: "internal://knowledge",
  symptoms: [""],
  matchKeywords: [""],
  steps: [""],
  validationChecks: [""],
  cautionItems: [""]
};

function emptyJmxEndpoint(): JmxEndpoint {
  return {
    enabled: true,
    serviceType: "HDFS",
    roleType: "NAMENODE",
    targetHost: "",
    port: 9870,
    path: "/jmx",
    protocol: "HTTP",
    authType: "NONE",
    username: "",
    password: "",
    passwordConfigured: false,
    metricWhitelist: ""
  };
}

function emptyDiagnosticScript(): DiagnosticScript {
  return {
    enabled: true,
    scriptName: "",
    commandPath: "",
    allowedArgs: "",
    timeoutSeconds: 180,
    requiresApproval: false,
    hostScope: "",
    serviceScope: "HDFS",
    description: ""
  };
}

function normalizeJmxPath(path: string) {
  if (!path) {
    return "/jmx";
  }
  return path.startsWith("/") ? path : `/${path}`;
}

function buildJmxPreviewUrl(endpoint: JmxEndpoint) {
  const protocol = (endpoint.protocol || "HTTP").toLowerCase();
  const host = (endpoint.targetHost || "host").replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, "").replace(/\/.*$/, "");
  const [hostname, explicitPort] = host.split(":");
  const port = explicitPort || String(endpoint.port);
  return `${protocol}://${hostname || "host"}:${port}${normalizeJmxPath(endpoint.path)}`;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "尚未执行";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function parseLines(value: string) {
  return value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

function renderIntegrationResult(title: string, result: IntegrationTestResponse | null) {
  if (!result) {
    return null;
  }

  return (
    <div className={`subpanel result-panel ${result.success ? "result-success" : "result-failure"}`}>
      <div className="panel-head">
        <strong>{title}</strong>
        <span>{result.success ? "成功" : "失败"}</span>
      </div>
      <p className="compact-lead">{result.message}</p>
      <div className="inline-metadata">
        <span>{`校验时间：${formatDateTime(result.validatedAt)}`}</span>
        <span>{`配置状态：${result.configured ? "完整" : "待补齐"}`}</span>
        {result.endpoint ? <span>{`目标：${result.endpoint}`}</span> : null}
      </div>
      {result.details ? <pre className="result-details">{result.details}</pre> : null}
    </div>
  );
}

export function SettingsPage() {
  const [config, setConfig] = useState<DataSourceConfig>(emptyConfig);
  const [knowledgeArticles, setKnowledgeArticles] = useState<KnowledgeArticle[]>([]);
  const [knowledgeForm, setKnowledgeForm] = useState<KnowledgeArticleRequest>(emptyKnowledgeArticle);
  const [llmQuestion, setLlmQuestion] = useState("");

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingKnowledge, setSavingKnowledge] = useState(false);
  const [testingCm, setTestingCm] = useState(false);
  const [testingCmCurrent, setTestingCmCurrent] = useState(false);
  const [testingLog, setTestingLog] = useState(false);
  const [testingLlm, setTestingLlm] = useState(false);
  const [testingJmx, setTestingJmx] = useState(false);
  const [testingScripts, setTestingScripts] = useState(false);
  const [askingLlm, setAskingLlm] = useState(false);

  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cmResult, setCmResult] = useState<ClouderaManagerSyncResponse | null>(null);
  const [cmCurrentResult, setCmCurrentResult] = useState<CmCurrentStatusResponse | null>(null);
  const [logResult, setLogResult] = useState<IntegrationTestResponse | null>(null);
  const [llmResult, setLlmResult] = useState<IntegrationTestResponse | null>(null);
  const [jmxResult, setJmxResult] = useState<JmxProbeResponse | null>(null);
  const [scriptResult, setScriptResult] = useState<IntegrationTestResponse | null>(null);
  const [llmChatResult, setLlmChatResult] = useState<LlmPromptResponse | null>(null);

  const validationSummary = useMemo(() => {
    if (testingCm) return "正在同步 CM 历史告警。";
    if (testingCmCurrent) return "正在采集 CM 当前服务状态。";
    if (testingLog) return "正在测试日志平台连接。";
    if (testingLlm) return "正在测试大模型连接。";
    if (testingJmx) return "正在测试 JMX 端点。";
    if (testingScripts) return "正在校验诊断脚本。";
    if (askingLlm) return "正在向大模型提问。";
    return "每个模块都支持单独保存并测试。";
  }, [askingLlm, testingCm, testingCmCurrent, testingJmx, testingLlm, testingLog, testingScripts]);

  useEffect(() => {
    async function load() {
      try {
        const [configData, knowledgeData] = await Promise.all([getDataSourceConfig(), getKnowledgeArticles()]);
        setConfig(configData);
        setKnowledgeArticles(knowledgeData);
        setError(null);
      } catch {
        setError("加载配置失败，请确认后端服务已经启动。");
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, []);

  function clearTransientFeedback() {
    setMessage(null);
    setError(null);
  }

  async function reloadKnowledgeArticles() {
    const data = await getKnowledgeArticles();
    setKnowledgeArticles(data);
  }

  async function persistConfig() {
    setSaving(true);
    clearTransientFeedback();
    try {
      const saved = await saveDataSourceConfig(config);
      setConfig(saved);
      setMessage("集成配置已保存。");
      return saved;
    } catch {
      setError("保存失败，请检查后端状态和输入内容。");
      return null;
    } finally {
      setSaving(false);
    }
  }

  function updateCm<K extends keyof DataSourceConfig["clouderaManager"]>(key: K, value: DataSourceConfig["clouderaManager"][K]) {
    setConfig((current) => ({ ...current, clouderaManager: { ...current.clouderaManager, [key]: value } }));
  }
  function updateLog<K extends keyof DataSourceConfig["logSource"]>(key: K, value: DataSourceConfig["logSource"][K]) {
    setConfig((current) => ({ ...current, logSource: { ...current.logSource, [key]: value } }));
  }
  function updateLlm<K extends keyof DataSourceConfig["llm"]>(key: K, value: DataSourceConfig["llm"][K]) {
    setConfig((current) => ({ ...current, llm: { ...current.llm, [key]: value } }));
  }
  function updateJmx(index: number, patch: Partial<JmxEndpoint>) {
    setConfig((current) => ({
      ...current,
      jmxEndpoints: current.jmxEndpoints.map((item, currentIndex) => (currentIndex === index ? { ...item, ...patch } : item))
    }));
  }
  function updateScript(index: number, patch: Partial<DiagnosticScript>) {
    setConfig((current) => ({
      ...current,
      diagnosticScripts: current.diagnosticScripts.map((item, currentIndex) => (currentIndex === index ? { ...item, ...patch } : item))
    }));
  }
  function addJmxEndpoint() {
    setConfig((current) => ({ ...current, jmxEndpoints: [...current.jmxEndpoints, emptyJmxEndpoint()] }));
  }
  function removeJmxEndpoint(index: number) {
    setConfig((current) => ({ ...current, jmxEndpoints: current.jmxEndpoints.filter((_, i) => i !== index) }));
  }
  function addDiagnosticScript() {
    setConfig((current) => ({ ...current, diagnosticScripts: [...current.diagnosticScripts, emptyDiagnosticScript()] }));
  }
  function removeDiagnosticScript(index: number) {
    setConfig((current) => ({ ...current, diagnosticScripts: current.diagnosticScripts.filter((_, i) => i !== index) }));
  }
  function updateKnowledgeForm<K extends keyof KnowledgeArticleRequest>(key: K, value: KnowledgeArticleRequest[K]) {
    setKnowledgeForm((current) => ({ ...current, [key]: value }));
  }

  async function handleSaveAndSyncCm() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingCm(true);
    try {
      const result = await syncClouderaManagerAlerts();
      setCmResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "CM 历史告警同步失败。");
    } finally {
      setTestingCm(false);
    }
  }

  async function handleSaveAndFetchCmCurrentStatus() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingCmCurrent(true);
    try {
      const result = await getClouderaManagerCurrentStatus();
      setCmCurrentResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "CM 当前状态采集失败。");
    } finally {
      setTestingCmCurrent(false);
    }
  }

  async function handleSaveAndTestLogSource() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingLog(true);
    try {
      const result = await testLogSourceConnection();
      setLogResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "日志平台连接测试失败。");
    } finally {
      setTestingLog(false);
    }
  }

  async function handleSaveAndTestLlm() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingLlm(true);
    try {
      const result = await testLlmConnection();
      setLlmResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "大模型连接测试失败。");
    } finally {
      setTestingLlm(false);
    }
  }

  async function handleAskLlm() {
    if (!llmQuestion.trim()) {
      setError("请输入要提问的问题。");
      return;
    }
    clearTransientFeedback();
    setAskingLlm(true);
    try {
      const result = await askLlmQuestion(llmQuestion.trim());
      setLlmChatResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "大模型提问失败。");
    } finally {
      setAskingLlm(false);
    }
  }

  async function handleSaveAndTestJmx() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingJmx(true);
    try {
      const result = await testJmxEndpoints();
      setJmxResult(result);
      setMessage(`JMX 校验完成，成功 ${result.successCount} 个，失败 ${result.failureCount} 个。`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "JMX 连通性校验失败。");
    } finally {
      setTestingJmx(false);
    }
  }

  async function handleSaveAndValidateScripts() {
    clearTransientFeedback();
    const saved = await persistConfig();
    if (!saved) return;
    setTestingScripts(true);
    try {
      const result = await testDiagnosticScripts();
      setScriptResult(result);
      setMessage(result.message);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "诊断脚本校验失败。");
    } finally {
      setTestingScripts(false);
    }
  }

  async function handleSaveAll() {
    await persistConfig();
  }

  async function handleSaveKnowledge(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearTransientFeedback();
    setSavingKnowledge(true);
    try {
      await saveKnowledgeArticle({
        ...knowledgeForm,
        symptoms: parseLines(knowledgeForm.symptoms.join("\n")),
        matchKeywords: parseLines(knowledgeForm.matchKeywords.join("\n")),
        steps: parseLines(knowledgeForm.steps.join("\n")),
        validationChecks: parseLines(knowledgeForm.validationChecks.join("\n")),
        cautionItems: parseLines(knowledgeForm.cautionItems.join("\n"))
      });
      await reloadKnowledgeArticles();
      setKnowledgeForm(emptyKnowledgeArticle);
      setMessage("知识库条目已保存到数据库。");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "知识库条目保存失败。");
    } finally {
      setSavingKnowledge(false);
    }
  }

  if (loading) {
    return <div className="panel">正在加载集成配置...</div>;
  }

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">集成中心</p>
          <h2>统一管理外部接入、实时采集与诊断知识</h2>
          <p className="lead">这里集中维护 CM、日志平台、JMX、大模型和自建知识库。每个模块都支持单独保存并测试。</p>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card">
            <span className="status-kicker">当前状态</span>
            <strong>{config.llm.configured ? "诊断链路已具备 AI 能力" : "诊断链路当前以知识库/规则为主"}</strong>
            <div className="status-list">
              <span>{`CM：${config.clouderaManager.enabled ? "已启用" : "未启用"}`}</span>
              <span>{`JMX：${config.jmxEndpoints.length} 个端点`}</span>
              <span>{`知识：${knowledgeArticles.length} 条`}</span>
            </div>
          </div>
          <div className="status-card warm-card">
            <span className="status-kicker">最近动作</span>
            <p className="compact-lead">{validationSummary}</p>
            <ul className="tight-list">
              {message ? <li>{message}</li> : null}
              {error ? <li>{error}</li> : null}
              {!message && !error ? <li>保存后可立即做连接测试或状态采集。</li> : null}
            </ul>
          </div>
        </div>
      </section>

      <div className="stack-xl">
        <section className="panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Cloudera Manager</p>
              <h3>历史告警与当前状态</h3>
            </div>
          </div>
          <div className="form-grid">
            <label className="toggle-row"><span>启用 CM 集成</span><input checked={config.clouderaManager.enabled} onChange={(e) => updateCm("enabled", e.target.checked)} type="checkbox" /></label>
            <label><span>基础地址</span><input value={config.clouderaManager.baseUrl} onChange={(e) => updateCm("baseUrl", e.target.value)} /></label>
            <label><span>API 版本</span><input value={config.clouderaManager.apiVersion} onChange={(e) => updateCm("apiVersion", e.target.value)} /></label>
            <label><span>用户名</span><input value={config.clouderaManager.username} onChange={(e) => updateCm("username", e.target.value)} /></label>
            <label><span>密码</span><input type="password" placeholder={config.clouderaManager.passwordConfigured ? "已保存，留空则保持当前密码" : "请输入 CM 密码"} value={config.clouderaManager.password} onChange={(e) => updateCm("password", e.target.value)} /></label>
            <label><span>集群名称</span><input value={config.clouderaManager.clusterName} onChange={(e) => updateCm("clusterName", e.target.value)} /></label>
          </div>
          <div className="detail-actions">
            <button className="primary-button" disabled={saving || testingCm} onClick={() => void handleSaveAndSyncCm()} type="button">{testingCm ? "同步中..." : "保存并同步历史告警"}</button>
            <button className="secondary-button" disabled={saving || testingCmCurrent} onClick={() => void handleSaveAndFetchCmCurrentStatus()} type="button">{testingCmCurrent ? "采集中..." : "保存并采集当前状态"}</button>
          </div>
          {cmResult ? <div className={`subpanel result-panel ${cmResult.success ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>CM 历史告警同步结果</strong><span>{cmResult.success ? "成功" : "失败"}</span></div><p className="compact-lead">{cmResult.message}</p><div className="inline-metadata"><span>{`校验时间：${formatDateTime(cmResult.validatedAt)}`}</span><span>{`拉取数：${cmResult.fetchedCount}`}</span><span>{`导入数：${cmResult.importedCount}`}</span><span>{`跳过数：${cmResult.skippedCount}`}</span></div>{cmResult.details ? <pre className="result-details">{cmResult.details}</pre> : null}</div> : null}
          {cmCurrentResult ? <div className={`subpanel result-panel ${cmCurrentResult.success ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>CM 当前状态快照</strong><span>{cmCurrentResult.success ? "成功" : "失败"}</span></div><p className="compact-lead">{cmCurrentResult.message}</p><div className="inline-metadata"><span>{`采集时间：${formatDateTime(cmCurrentResult.collectedAt)}`}</span><span>{`服务数：${cmCurrentResult.serviceCount}`}</span><span>{`异常服务：${cmCurrentResult.unhealthyServiceCount}`}</span></div><div className="stack-md">{cmCurrentResult.services.slice(0, 6).map((service) => <div key={`${service.serviceName}-${service.serviceType}`} className="subpanel"><div className="inline-metadata"><span>{service.serviceName || service.serviceType}</span><span>{`健康：${service.healthSummary || "未返回"}`}</span><span>{`状态：${service.serviceState || service.entityStatus || "未返回"}`}</span><span>{`异常角色：${service.unhealthyRoleCount}/${service.roleCount}`}</span></div>{service.roleHighlights.length > 0 ? <ul className="list">{service.roleHighlights.map((item) => <li key={item}>{item}</li>)}</ul> : null}</div>)}</div></div> : null}
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">日志平台</p><h3>日志采集接入</h3></div></div>
          <div className="form-grid">
            <label className="toggle-row"><span>启用日志平台</span><input checked={config.logSource.enabled} onChange={(e) => updateLog("enabled", e.target.checked)} type="checkbox" /></label>
            <label><span>提供方类型</span><input value={config.logSource.providerType} onChange={(e) => updateLog("providerType", e.target.value)} /></label>
            <label><span>基础地址</span><input value={config.logSource.baseUrl} onChange={(e) => updateLog("baseUrl", e.target.value)} /></label>
            <label><span>认证方式</span><input value={config.logSource.authType} onChange={(e) => updateLog("authType", e.target.value)} /></label>
            <label><span>认证令牌</span><input type="password" placeholder={config.logSource.authTokenConfigured ? "已保存，留空则保持当前令牌" : "请输入认证令牌"} value={config.logSource.authToken} onChange={(e) => updateLog("authToken", e.target.value)} /></label>
            <label><span>索引模式</span><input value={config.logSource.indexPattern} onChange={(e) => updateLog("indexPattern", e.target.value)} /></label>
          </div>
          <div className="detail-actions"><button className="primary-button" disabled={saving || testingLog} onClick={() => void handleSaveAndTestLogSource()} type="button">{testingLog ? "测试中..." : "保存并测试日志平台"}</button></div>
          {renderIntegrationResult("日志平台测试结果", logResult)}
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">大模型诊断</p><h3>模型连接与在线问答</h3></div></div>
          <div className="form-grid">
            <label className="toggle-row"><span>启用大模型诊断</span><input checked={config.llm.enabled} onChange={(e) => updateLlm("enabled", e.target.checked)} type="checkbox" /></label>
            <label><span>接口地址</span><input value={config.llm.endpoint} onChange={(e) => updateLlm("endpoint", e.target.value)} /></label>
            <label><span>模型名称</span><input value={config.llm.model} onChange={(e) => updateLlm("model", e.target.value)} /></label>
            <label><span>API Key</span><input type="password" placeholder={config.llm.apiKeyConfigured ? "已保存，留空则保持当前 Key" : "请输入模型 API Key"} value={config.llm.apiKey} onChange={(e) => updateLlm("apiKey", e.target.value)} /></label>
            <label><span>连接超时（毫秒）</span><input type="number" value={config.llm.connectTimeoutMs} onChange={(e) => updateLlm("connectTimeoutMs", Number(e.target.value))} /></label>
            <label><span>读取超时（毫秒）</span><input type="number" value={config.llm.readTimeoutMs} onChange={(e) => updateLlm("readTimeoutMs", Number(e.target.value))} /></label>
            <label><span>温度</span><input type="number" min="0" max="2" step="0.1" value={config.llm.temperature} onChange={(e) => updateLlm("temperature", Number(e.target.value))} /></label>
            <label><span>最大输出 Token</span><input type="number" value={config.llm.maxTokens} onChange={(e) => updateLlm("maxTokens", Number(e.target.value))} /></label>
          </div>
          <div className="detail-actions">
            <button className="primary-button" disabled={saving || testingLlm} onClick={() => void handleSaveAndTestLlm()} type="button">{testingLlm ? "测试中..." : "保存并测试大模型"}</button>
          </div>
          {renderIntegrationResult("大模型连接测试结果", llmResult)}
          <div className="subpanel hint-card stack-md">
            <strong>在线提问窗口</strong>
            <label className="stack-sm"><span>问题内容</span><textarea className="app-textarea" value={llmQuestion} onChange={(e) => setLlmQuestion(e.target.value)} placeholder="输入你想直接向大模型提问的问题。" /></label>
            <div className="detail-actions"><button className="primary-button" disabled={askingLlm} type="button" onClick={() => void handleAskLlm()}>{askingLlm ? "提问中..." : "发送提问"}</button></div>
            {llmChatResult ? <div className={`subpanel result-panel ${llmChatResult.success ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>模型回复</strong><span>{llmChatResult.success ? "成功" : "失败"}</span></div><div className="inline-metadata"><span>{`时间：${formatDateTime(llmChatResult.respondedAt)}`}</span><span>{`模型：${llmChatResult.model}`}</span></div><pre className="result-details">{llmChatResult.answer}</pre></div> : null}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">JMX 端点</p><h3>指标采集连通性配置</h3></div><button className="secondary-button" onClick={addJmxEndpoint} type="button">新增端点</button></div>
          <div className="stack-lg">{config.jmxEndpoints.map((endpoint, index) => <article key={endpoint.id ?? `jmx-${index}`} className="subpanel"><div className="panel-head"><strong>{endpoint.serviceType} / {endpoint.roleType}</strong><button className="secondary-button" onClick={() => removeJmxEndpoint(index)} type="button">删除</button></div><div className="form-grid"><label className="toggle-row"><span>启用端点</span><input checked={endpoint.enabled} onChange={(e) => updateJmx(index, { enabled: e.target.checked })} type="checkbox" /></label><label><span>服务类型</span><input value={endpoint.serviceType} onChange={(e) => updateJmx(index, { serviceType: e.target.value })} /></label><label><span>角色类型</span><input value={endpoint.roleType} onChange={(e) => updateJmx(index, { roleType: e.target.value })} /></label><label><span>目标主机</span><input value={endpoint.targetHost} onChange={(e) => updateJmx(index, { targetHost: e.target.value })} /></label><label><span>端口</span><input type="number" value={endpoint.port} onChange={(e) => updateJmx(index, { port: Number(e.target.value) })} /></label><label><span>路径</span><input value={endpoint.path} onChange={(e) => updateJmx(index, { path: e.target.value })} /></label><label><span>协议</span><input value={endpoint.protocol} onChange={(e) => updateJmx(index, { protocol: e.target.value })} /></label><label><span>认证方式</span><input value={endpoint.authType} onChange={(e) => updateJmx(index, { authType: e.target.value })} /></label><label><span>用户名</span><input value={endpoint.username} onChange={(e) => updateJmx(index, { username: e.target.value })} /></label><label><span>密码</span><input type="password" placeholder={endpoint.passwordConfigured ? "已保存，留空则保持当前密码" : "请输入 JMX 密码"} value={endpoint.password} onChange={(e) => updateJmx(index, { password: e.target.value })} /></label><label><span>指标白名单</span><input value={endpoint.metricWhitelist} onChange={(e) => updateJmx(index, { metricWhitelist: e.target.value })} /></label></div><div className="inline-metadata"><span>{`目标地址：${buildJmxPreviewUrl(endpoint)}`}</span></div></article>)}</div>
          <div className="detail-actions"><button className="primary-button" disabled={saving || testingJmx} onClick={() => void handleSaveAndTestJmx()} type="button">{testingJmx ? "测试中..." : "保存并测试 JMX"}</button></div>
          {jmxResult ? <div className={`subpanel result-panel ${jmxResult.failureCount === 0 ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>JMX 测试结果</strong><span>{jmxResult.failureCount === 0 ? "成功" : "部分失败"}</span></div><div className="inline-metadata"><span>{`校验时间：${formatDateTime(jmxResult.validatedAt)}`}</span><span>{`总数：${jmxResult.totalCount}`}</span><span>{`成功：${jmxResult.successCount}`}</span><span>{`失败：${jmxResult.failureCount}`}</span></div><div className="stack-md">{jmxResult.results.map((result, index) => <div key={`${result.targetHost}-${result.endpointId ?? "na"}-${index}`} className="subpanel"><div className="inline-metadata"><span>{result.serviceType}</span><span>{result.roleType}</span><span>{`目标主机：${result.targetHost}`}</span></div><p className="compact-lead">{result.message}</p><div className="inline-metadata"><span>{`状态：${result.success ? "成功" : "失败"}`}</span><span>{`Bean 数：${result.beanCount}`}</span></div></div>)}</div></div> : null}
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">诊断脚本</p><h3>自动化采集脚本登记</h3></div><button className="secondary-button" onClick={addDiagnosticScript} type="button">新增脚本</button></div>
          <div className="stack-lg">{config.diagnosticScripts.map((script, index) => <article key={script.id ?? `script-${index}`} className="subpanel"><div className="panel-head"><strong>{script.scriptName || `脚本 ${index + 1}`}</strong><button className="secondary-button" onClick={() => removeDiagnosticScript(index)} type="button">删除</button></div><div className="form-grid"><label className="toggle-row"><span>启用脚本</span><input checked={script.enabled} onChange={(e) => updateScript(index, { enabled: e.target.checked })} type="checkbox" /></label><label><span>脚本名称</span><input value={script.scriptName} onChange={(e) => updateScript(index, { scriptName: e.target.value })} /></label><label><span>命令路径</span><input value={script.commandPath} onChange={(e) => updateScript(index, { commandPath: e.target.value })} /></label><label><span>允许参数</span><input value={script.allowedArgs} onChange={(e) => updateScript(index, { allowedArgs: e.target.value })} /></label><label><span>超时（秒）</span><input type="number" value={script.timeoutSeconds} onChange={(e) => updateScript(index, { timeoutSeconds: Number(e.target.value) })} /></label><label className="toggle-row"><span>需要审批</span><input checked={script.requiresApproval} onChange={(e) => updateScript(index, { requiresApproval: e.target.checked })} type="checkbox" /></label><label><span>主机范围</span><input value={script.hostScope} onChange={(e) => updateScript(index, { hostScope: e.target.value })} /></label><label><span>服务范围</span><input value={script.serviceScope} onChange={(e) => updateScript(index, { serviceScope: e.target.value })} /></label><label><span>说明</span><input value={script.description} onChange={(e) => updateScript(index, { description: e.target.value })} /></label></div></article>)}</div>
          <div className="detail-actions"><button className="primary-button" disabled={saving || testingScripts} onClick={() => void handleSaveAndValidateScripts()} type="button">{testingScripts ? "校验中..." : "保存并校验脚本路径"}</button></div>
          {renderIntegrationResult("诊断脚本校验结果", scriptResult)}
        </section>

        <section className="panel">
          <div className="panel-head"><div><p className="eyebrow">自建知识库</p><h3>知识条目录入</h3></div></div>
          <div className="two-column-section">
            <form className="subpanel stack-md" onSubmit={handleSaveKnowledge}>
              <label className="stack-sm"><span>领域</span><input value={knowledgeForm.domain} onChange={(e) => updateKnowledgeForm("domain", e.target.value)} /></label>
              <label className="stack-sm"><span>场景键</span><input value={knowledgeForm.scenarioKey} onChange={(e) => updateKnowledgeForm("scenarioKey", e.target.value)} /></label>
              <label className="stack-sm"><span>标题</span><input value={knowledgeForm.title} onChange={(e) => updateKnowledgeForm("title", e.target.value)} /></label>
              <label className="stack-sm"><span>摘要</span><textarea className="app-textarea" value={knowledgeForm.summary} onChange={(e) => updateKnowledgeForm("summary", e.target.value)} /></label>
              <label className="stack-sm"><span>适用范围</span><textarea className="app-textarea" value={knowledgeForm.applicability} onChange={(e) => updateKnowledgeForm("applicability", e.target.value)} /></label>
              <label className="stack-sm"><span>风险等级</span><input value={knowledgeForm.riskLevel} onChange={(e) => updateKnowledgeForm("riskLevel", e.target.value)} /></label>
              <label className="toggle-row"><span>需要审批</span><input checked={knowledgeForm.requiresApproval} onChange={(e) => updateKnowledgeForm("requiresApproval", e.target.checked)} type="checkbox" /></label>
              <label className="stack-sm"><span>来源名称</span><input value={knowledgeForm.sourceName} onChange={(e) => updateKnowledgeForm("sourceName", e.target.value)} /></label>
              <label className="stack-sm"><span>来源地址</span><input value={knowledgeForm.sourceUrl} onChange={(e) => updateKnowledgeForm("sourceUrl", e.target.value)} /></label>
              <label className="stack-sm"><span>症状，每行一条</span><textarea className="app-textarea" value={knowledgeForm.symptoms.join("\n")} onChange={(e) => updateKnowledgeForm("symptoms", e.target.value.split("\n"))} /></label>
              <label className="stack-sm"><span>匹配关键词，每行一条</span><textarea className="app-textarea" value={knowledgeForm.matchKeywords.join("\n")} onChange={(e) => updateKnowledgeForm("matchKeywords", e.target.value.split("\n"))} /></label>
              <label className="stack-sm"><span>处置步骤，每行一条</span><textarea className="app-textarea" value={knowledgeForm.steps.join("\n")} onChange={(e) => updateKnowledgeForm("steps", e.target.value.split("\n"))} /></label>
              <label className="stack-sm"><span>验证检查，每行一条</span><textarea className="app-textarea" value={knowledgeForm.validationChecks.join("\n")} onChange={(e) => updateKnowledgeForm("validationChecks", e.target.value.split("\n"))} /></label>
              <label className="stack-sm"><span>风险提示，每行一条</span><textarea className="app-textarea" value={knowledgeForm.cautionItems.join("\n")} onChange={(e) => updateKnowledgeForm("cautionItems", e.target.value.split("\n"))} /></label>
              <div className="detail-actions"><button className="primary-button" disabled={savingKnowledge} type="submit">{savingKnowledge ? "保存中..." : "保存知识条目"}</button></div>
            </form>
            <div className="panel stack-md">
              <div className="panel-head"><strong>已保存知识条目</strong><span>{knowledgeArticles.length}</span></div>
              {knowledgeArticles.length === 0 ? <div className="empty-state">当前还没有知识条目。</div> : <div className="stack-md">{knowledgeArticles.slice(0, 12).map((article) => <div key={article.id} className="subpanel"><div className="panel-head"><strong>{article.title}</strong><span>{article.domain}</span></div><p className="compact-lead">{article.summary}</p><div className="inline-metadata"><span>{`场景键：${article.scenarioKey}`}</span><span>{`风险：${article.riskLevel}`}</span><span>{`审批：${article.requiresApproval ? "需要" : "不需要"}`}</span></div></div>)}</div>}
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="detail-actions"><button className="primary-button" disabled={saving} type="button" onClick={() => void handleSaveAll()}>{saving ? "保存中..." : "保存全部集成配置"}</button></div>
          {message ? <div className="flash-message">{message}</div> : null}
          {error ? <div className="error-message">{error}</div> : null}
        </section>
      </div>
    </div>
  );
}
