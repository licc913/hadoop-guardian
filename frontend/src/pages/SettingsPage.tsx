import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  getClouderaManagerCurrentStatus,
  getDataSourceConfig,
  getKnowledgeArticles,
  saveDataSourceConfig,
  saveKnowledgeQuickEntry,
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
  KnowledgeQuickEntryRequest
} from "../lib/types";

type ToggleFieldProps = {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
};

const emptyConfig: DataSourceConfig = {
  clouderaManager: { enabled: false, baseUrl: "", apiVersion: "v51", username: "", password: "", passwordConfigured: false, clusterName: "", configured: false },
  logSource: { enabled: false, providerType: "ELASTICSEARCH", baseUrl: "", authType: "BASIC", authToken: "", authTokenConfigured: false, indexPattern: "hadoop-*", defaultTimeWindowMinutes: 30 },
  llm: { enabled: false, endpoint: "", apiKey: "", apiKeyConfigured: false, model: "", connectTimeoutMs: 10000, readTimeoutMs: 30000, temperature: 0.2, maxTokens: 2048, configured: false },
  jmxEndpoints: [],
  diagnosticScripts: []
};

const emptyKnowledgeEntry: KnowledgeQuickEntryRequest = { domain: "IMPALA", content: "" };
const suggestedKnowledgeDomains = ["IMPALA", "YARN", "HDFS", "HIVE_ON_TEZ", "HIVE_METASTORE", "SPARK", "RANGER", "KAFKA", "ZOOKEEPER", "OOZIE", "HBASE", "CROSS_COMPONENT"];

function emptyJmxEndpoint(): JmxEndpoint {
  return { enabled: true, serviceType: "HDFS", roleType: "NAMENODE", targetHost: "", port: 9870, path: "/jmx", protocol: "HTTP", authType: "NONE", username: "", password: "", passwordConfigured: false, metricWhitelist: "" };
}

function emptyDiagnosticScript(): DiagnosticScript {
  return { enabled: true, scriptName: "", commandPath: "", allowedArgs: "", timeoutSeconds: 180, requiresApproval: false, hostScope: "", serviceScope: "HDFS", description: "" };
}

function formatDateTime(value?: string | null) {
  if (!value) return "尚未执行";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function normalizeJmxPath(path: string) {
  if (!path) return "/jmx";
  return path.startsWith("/") ? path : `/${path}`;
}

function buildJmxPreviewUrl(endpoint: JmxEndpoint) {
  const protocol = (endpoint.protocol || "HTTP").toLowerCase();
  const host = (endpoint.targetHost || "host").replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, "").replace(/\/.*$/, "");
  const [hostname, explicitPort] = host.split(":");
  const port = explicitPort || String(endpoint.port);
  return `${protocol}://${hostname || "host"}:${port}${normalizeJmxPath(endpoint.path)}`;
}

function renderMetricSection(title: string, values: string[] | undefined) {
  if (!values || values.length === 0) return null;
  return (
    <div className="stack-xs">
      <strong>{title}</strong>
      <ul className="tight-list">
        {values.map((item) => <li key={`${title}-${item}`}>{item}</li>)}
      </ul>
    </div>
  );
}

function renderIntegrationResult(title: string, result: IntegrationTestResponse | null) {
  if (!result) return null;
  return (
    <div className={`subpanel result-panel ${result.success ? "result-success" : "result-failure"}`}>
      <div className="panel-head"><strong>{title}</strong><span>{result.success ? "成功" : "失败"}</span></div>
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

function ToggleField({ label, description, checked, onChange }: ToggleFieldProps) {
  return (
    <label className={`toggle-row toggle-card ${checked ? "toggle-card-active" : ""}`}>
      <span className="toggle-copy">
        <strong>{label}</strong>
        <em>{description}</em>
      </span>
      <span className="toggle-switch">
        <input
          aria-label={label}
          checked={checked}
          className="toggle-input"
          onChange={(event) => onChange(event.target.checked)}
          type="checkbox"
        />
        <span className={`toggle-track ${checked ? "toggle-track-active" : ""}`}>
          <span className="toggle-thumb" />
        </span>
      </span>
    </label>
  );
}

export function SettingsPage() {
  const [config, setConfig] = useState<DataSourceConfig>(emptyConfig);
  const [knowledgeArticles, setKnowledgeArticles] = useState<KnowledgeArticle[]>([]);
  const [knowledgeEntry, setKnowledgeEntry] = useState<KnowledgeQuickEntryRequest>(emptyKnowledgeEntry);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingKnowledge, setSavingKnowledge] = useState(false);
  const [testingCm, setTestingCm] = useState(false);
  const [testingCmCurrent, setTestingCmCurrent] = useState(false);
  const [testingLog, setTestingLog] = useState(false);
  const [testingLlm, setTestingLlm] = useState(false);
  const [testingJmx, setTestingJmx] = useState(false);
  const [testingScripts, setTestingScripts] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cmResult, setCmResult] = useState<ClouderaManagerSyncResponse | null>(null);
  const [cmCurrentResult, setCmCurrentResult] = useState<CmCurrentStatusResponse | null>(null);
  const [logResult, setLogResult] = useState<IntegrationTestResponse | null>(null);
  const [llmResult, setLlmResult] = useState<IntegrationTestResponse | null>(null);
  const [jmxResult, setJmxResult] = useState<JmxProbeResponse | null>(null);
  const [scriptResult, setScriptResult] = useState<IntegrationTestResponse | null>(null);

  const validationSummary = useMemo(() => {
    if (testingCm) return "正在同步 CM 历史告警。";
    if (testingCmCurrent) return "正在采集 CM 当前状态。";
    if (testingLog) return "正在测试日志平台连接。";
    if (testingLlm) return "正在测试大模型连接。";
    if (testingJmx) return "正在测试 JMX 端点。";
    if (testingScripts) return "正在校验诊断脚本。";
    if (savingKnowledge) return "正在写入知识条目。";
    return "每个模块都支持单独保存并测试。";
  }, [savingKnowledge, testingCm, testingCmCurrent, testingJmx, testingLlm, testingLog, testingScripts]);

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

  function clearFeedback() {
    setMessage(null);
    setError(null);
  }

  async function reloadKnowledgeArticles() {
    setKnowledgeArticles(await getKnowledgeArticles());
  }

  async function persistConfig(nextConfig?: DataSourceConfig) {
    const target = nextConfig ?? config;
    setSaving(true);
    clearFeedback();
    try {
      const saved = await saveDataSourceConfig(target);
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
    setConfig((current) => ({ ...current, jmxEndpoints: current.jmxEndpoints.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item) }));
  }
  function updateScript(index: number, patch: Partial<DiagnosticScript>) {
    setConfig((current) => ({ ...current, diagnosticScripts: current.diagnosticScripts.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item) }));
  }
  function addJmxEndpoint() { setConfig((current) => ({ ...current, jmxEndpoints: [...current.jmxEndpoints, emptyJmxEndpoint()] })); }
  function removeJmxEndpoint(index: number) { setConfig((current) => ({ ...current, jmxEndpoints: current.jmxEndpoints.filter((_, itemIndex) => itemIndex !== index) })); }
  function addDiagnosticScript() { setConfig((current) => ({ ...current, diagnosticScripts: [...current.diagnosticScripts, emptyDiagnosticScript()] })); }
  function removeDiagnosticScript(index: number) { setConfig((current) => ({ ...current, diagnosticScripts: current.diagnosticScripts.filter((_, itemIndex) => itemIndex !== index) })); }

  async function handleSaveAndSyncCm() {
    const saved = await persistConfig({ ...config, clouderaManager: { ...config.clouderaManager, enabled: true } });
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
    const saved = await persistConfig({ ...config, clouderaManager: { ...config.clouderaManager, enabled: true } });
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

  async function handleSaveAndTestJmx() {
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

  async function handleSaveKnowledge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!knowledgeEntry.domain.trim()) return setError("请输入服务领域。");
    if (!knowledgeEntry.content.trim()) return setError("请输入知识内容。");
    clearFeedback();
    setSavingKnowledge(true);
    try {
      const created = await saveKnowledgeQuickEntry({ domain: knowledgeEntry.domain.trim().toUpperCase(), content: knowledgeEntry.content.trim() });
      setKnowledgeArticles((current) => [created, ...current.filter((item) => item.id !== created.id)]);
      await reloadKnowledgeArticles();
      setKnowledgeEntry(emptyKnowledgeEntry);
      setMessage("知识条目已写入数据库。");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "知识条目录入失败。");
    } finally {
      setSavingKnowledge(false);
    }
  }

  if (loading) return <div className="panel">正在加载集成配置...</div>;

  return (
    <div className="stack-xl">
      <section className="hero hero-grid panel">
        <div className="hero-copy">
          <p className="eyebrow">集成中心</p>
          <h2>统一管理 CM、JMX、日志平台、大模型与自建知识库</h2>
          <p className="lead">保持现有运维工作台风格，同时把知识录入改成更轻量的实际使用方式。</p>
        </div>
        <div className="hero-side validation-grid">
          <div className="status-card"><span className="status-kicker">当前状态</span><strong>{config.llm.configured ? "AI 链路已就绪" : "当前以知识库 / 规则为主"}</strong><div className="status-list"><span>{`CM：${config.clouderaManager.enabled ? "已启用" : "未启用"}`}</span><span>{`JMX：${config.jmxEndpoints.length} 个端点`}</span><span>{`知识：${knowledgeArticles.length} 条`}</span></div></div>
          <div className="status-card warm-card"><span className="status-kicker">最近动作</span><p className="compact-lead">{validationSummary}</p><ul className="tight-list">{message ? <li>{message}</li> : null}{error ? <li>{error}</li> : null}{!message && !error ? <li>保存后可立刻做连接测试、当前状态采集或进入 AI 对话工作台。</li> : null}</ul></div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">Cloudera Manager</p><h3>历史告警同步与当前状态采集</h3></div></div>
        <div className="form-grid">
          <label className="toggle-row"><span>启用 CM 集成</span><input checked={config.clouderaManager.enabled} onChange={(event) => updateCm("enabled", event.target.checked)} type="checkbox" /></label>
          <label><span>基础地址</span><input value={config.clouderaManager.baseUrl} onChange={(event) => updateCm("baseUrl", event.target.value)} /></label>
          <label><span>API 版本</span><input value={config.clouderaManager.apiVersion} onChange={(event) => updateCm("apiVersion", event.target.value)} /></label>
          <label><span>用户名</span><input value={config.clouderaManager.username} onChange={(event) => updateCm("username", event.target.value)} /></label>
          <label><span>密码</span><input type="password" placeholder={config.clouderaManager.passwordConfigured ? "已保存，留空则保持当前密码" : "请输入 CM 密码"} value={config.clouderaManager.password} onChange={(event) => updateCm("password", event.target.value)} /></label>
          <label><span>集群名称</span><input value={config.clouderaManager.clusterName} onChange={(event) => updateCm("clusterName", event.target.value)} /></label>
        </div>
        <div className="detail-actions">
          <button className="primary-button" disabled={saving || testingCm} onClick={() => void handleSaveAndSyncCm()} type="button">{testingCm ? "同步中..." : "保存并同步历史告警"}</button>
          <button className="secondary-button" disabled={saving || testingCmCurrent} onClick={() => void handleSaveAndFetchCmCurrentStatus()} type="button">{testingCmCurrent ? "采集中..." : "保存并采集当前状态"}</button>
        </div>
        {cmResult ? <div className={`subpanel result-panel ${cmResult.success ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>CM 历史告警同步结果</strong><span>{cmResult.success ? "成功" : "失败"}</span></div><p className="compact-lead">{cmResult.message}</p><div className="inline-metadata"><span>{`时间：${formatDateTime(cmResult.validatedAt)}`}</span><span>{`拉取：${cmResult.fetchedCount}`}</span><span>{`导入：${cmResult.importedCount}`}</span><span>{`跳过：${cmResult.skippedCount}`}</span></div>{cmResult.details ? <pre className="result-details">{cmResult.details}</pre> : null}</div> : null}
        {cmCurrentResult ? <div className={`subpanel result-panel ${cmCurrentResult.success ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>CM 当前状态快照</strong><span>{cmCurrentResult.success ? "成功" : "失败"}</span></div><p className="compact-lead">{cmCurrentResult.message}</p><div className="inline-metadata"><span>{`时间：${formatDateTime(cmCurrentResult.collectedAt)}`}</span><span>{`服务数：${cmCurrentResult.serviceCount}`}</span><span>{`异常服务：${cmCurrentResult.unhealthyServiceCount}`}</span></div>{cmCurrentResult.details ? <pre className="result-details">{cmCurrentResult.details}</pre> : null}<div className="stack-md">{cmCurrentResult.services.slice(0, 6).map((service) => <div key={`${service.serviceName}-${service.serviceType}`} className="subpanel"><div className="inline-metadata"><span>{service.serviceName || service.serviceType}</span><span>{`健康：${service.healthSummary || "未返回"}`}</span><span>{`状态：${service.serviceState || service.entityStatus || "未返回"}`}</span><span>{`异常角色：${service.unhealthyRoleCount}/${service.roleCount}`}</span></div>{service.roleHighlights.length > 0 ? <div className="stack-xs"><strong>角色状态摘要</strong><ul className="list">{service.roleHighlights.map((item) => <li key={item}>{item}</li>)}</ul></div> : null}{service.logHighlights.length > 0 ? <div className="stack-xs"><strong>诊断用日志摘要（WARN / ERROR）</strong><ul className="list">{service.logHighlights.map((item) => <li key={item}>{item}</li>)}</ul></div> : null}{service.logPreviewLines.length > 0 ? <div className="stack-xs"><strong>服务日志预览</strong><ul className="list">{service.logPreviewLines.map((item) => <li key={item}>{item}</li>)}</ul></div> : null}</div>)}</div></div> : null}
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">大模型诊断</p><h3>诊断模型连接信息</h3></div></div>
        <div className="form-grid">
          <label className="toggle-row"><span>启用大模型诊断</span><input checked={config.llm.enabled} onChange={(event) => updateLlm("enabled", event.target.checked)} type="checkbox" /></label>
          <label><span>接口地址</span><input value={config.llm.endpoint} onChange={(event) => updateLlm("endpoint", event.target.value)} /></label>
          <label><span>模型名称</span><input value={config.llm.model} onChange={(event) => updateLlm("model", event.target.value)} /></label>
          <label><span>API Key</span><input type="password" placeholder={config.llm.apiKeyConfigured ? "已保存，留空则保持当前 Key" : "请输入模型 API Key"} value={config.llm.apiKey} onChange={(event) => updateLlm("apiKey", event.target.value)} /></label>
          <label><span>连接超时（毫秒）</span><input type="number" value={config.llm.connectTimeoutMs} onChange={(event) => updateLlm("connectTimeoutMs", Number(event.target.value))} /></label>
          <label><span>读取超时（毫秒）</span><input type="number" value={config.llm.readTimeoutMs} onChange={(event) => updateLlm("readTimeoutMs", Number(event.target.value))} /></label>
          <label><span>温度</span><input type="number" min="0" max="2" step="0.1" value={config.llm.temperature} onChange={(event) => updateLlm("temperature", Number(event.target.value))} /></label>
          <label><span>最大输出 Token</span><input type="number" value={config.llm.maxTokens} onChange={(event) => updateLlm("maxTokens", Number(event.target.value))} /></label>
        </div>
        <div className="detail-actions"><button className="primary-button" disabled={saving || testingLlm} onClick={() => void handleSaveAndTestLlm()} type="button">{testingLlm ? "测试中..." : "保存并测试大模型连接"}</button><Link className="secondary-button" to="/llm-console">打开 AI 对话工作台</Link></div>
        {renderIntegrationResult("大模型连接测试结果", llmResult)}
        <div className="subpanel hint-card stack-md"><strong>说明</strong><p className="muted">设置页只保留模型连接测试。多轮追问、历史会话和长文本分析统一放到独立的 AI 对话工作台里。</p></div>
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">JMX 端点</p><h3>指标采集连通性配置</h3></div><button className="secondary-button" onClick={addJmxEndpoint} type="button">新增端点</button></div>
        <div className="stack-lg">{config.jmxEndpoints.map((endpoint, index) => <article key={endpoint.id ?? `jmx-${index}`} className="subpanel"><div className="panel-head"><strong>{endpoint.serviceType} / {endpoint.roleType}</strong><button className="secondary-button" onClick={() => removeJmxEndpoint(index)} type="button">删除</button></div><div className="form-grid"><label className="toggle-row"><span>启用端点</span><input checked={endpoint.enabled} onChange={(event) => updateJmx(index, { enabled: event.target.checked })} type="checkbox" /></label><label><span>服务类型</span><input value={endpoint.serviceType} onChange={(event) => updateJmx(index, { serviceType: event.target.value })} /></label><label><span>角色类型</span><input value={endpoint.roleType} onChange={(event) => updateJmx(index, { roleType: event.target.value })} /></label><label><span>目标主机</span><input value={endpoint.targetHost} onChange={(event) => updateJmx(index, { targetHost: event.target.value })} /></label><label><span>端口</span><input type="number" value={endpoint.port} onChange={(event) => updateJmx(index, { port: Number(event.target.value) })} /></label><label><span>路径</span><input value={endpoint.path} onChange={(event) => updateJmx(index, { path: event.target.value })} /></label><label><span>协议</span><input value={endpoint.protocol} onChange={(event) => updateJmx(index, { protocol: event.target.value })} /></label><label><span>认证方式</span><input value={endpoint.authType} onChange={(event) => updateJmx(index, { authType: event.target.value })} /></label><label><span>用户名</span><input value={endpoint.username} onChange={(event) => updateJmx(index, { username: event.target.value })} /></label><label><span>密码</span><input type="password" placeholder={endpoint.passwordConfigured ? "已保存，留空则保持当前密码" : "请输入 JMX 密码"} value={endpoint.password} onChange={(event) => updateJmx(index, { password: event.target.value })} /></label><label><span>指标白名单</span><input value={endpoint.metricWhitelist} onChange={(event) => updateJmx(index, { metricWhitelist: event.target.value })} /></label></div><div className="inline-metadata"><span>{`目标地址：${buildJmxPreviewUrl(endpoint)}`}</span></div></article>)}</div>
        <div className="detail-actions"><button className="primary-button" disabled={saving || testingJmx} onClick={() => void handleSaveAndTestJmx()} type="button">{testingJmx ? "测试中..." : "保存并测试 JMX 连接"}</button></div>
        {jmxResult ? <div className={`subpanel result-panel ${jmxResult.failureCount === 0 ? "result-success" : "result-failure"}`}><div className="panel-head"><strong>JMX 测试结果</strong><span>{jmxResult.failureCount === 0 ? "成功" : "部分失败"}</span></div><div className="inline-metadata"><span>{`校验时间：${formatDateTime(jmxResult.validatedAt)}`}</span><span>{`总数：${jmxResult.totalCount}`}</span><span>{`成功：${jmxResult.successCount}`}</span><span>{`失败：${jmxResult.failureCount}`}</span></div><p className="compact-lead">成功返回的指标会直接进入后端诊断链路，用于补充实时诊断清单和 AI guidance。</p><div className="stack-md">{jmxResult.results.map((result, index) => <div key={`${result.targetHost}-${result.endpointId ?? "na"}-${index}`} className="subpanel"><div className="inline-metadata"><span>{result.serviceType}</span><span>{result.roleType}</span><span>{`目标主机：${result.targetHost}`}</span></div><p className="compact-lead">{result.message}</p><div className="inline-metadata"><span>{`状态：${result.success ? "成功" : "失败"}`}</span><span>{`Bean 数：${result.beanCount}`}</span></div>{renderMetricSection("实际采集到的指标值", result.observedMetrics)}{renderMetricSection("匹配到的 Bean / 指标名", result.sampleMetrics)}</div>)}</div></div> : null}
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">日志平台</p><h3>日志采集接入</h3></div></div>
        <div className="form-grid">
          <label className="toggle-row"><span>启用日志平台</span><input checked={config.logSource.enabled} onChange={(event) => updateLog("enabled", event.target.checked)} type="checkbox" /></label>
          <label><span>提供方类型</span><input value={config.logSource.providerType} onChange={(event) => updateLog("providerType", event.target.value)} /></label>
          <label><span>基础地址</span><input value={config.logSource.baseUrl} onChange={(event) => updateLog("baseUrl", event.target.value)} /></label>
          <label><span>认证方式</span><input value={config.logSource.authType} onChange={(event) => updateLog("authType", event.target.value)} /></label>
          <label><span>认证令牌</span><input type="password" placeholder={config.logSource.authTokenConfigured ? "已保存，留空则保持当前令牌" : "请输入认证令牌"} value={config.logSource.authToken} onChange={(event) => updateLog("authToken", event.target.value)} /></label>
          <label><span>索引模式</span><input value={config.logSource.indexPattern} onChange={(event) => updateLog("indexPattern", event.target.value)} /></label>
        </div>
        <div className="detail-actions"><button className="primary-button" disabled={saving || testingLog} onClick={() => void handleSaveAndTestLogSource()} type="button">{testingLog ? "测试中..." : "保存并测试日志平台"}</button></div>
        {renderIntegrationResult("日志平台测试结果", logResult)}
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">诊断脚本</p><h3>自动化采集脚本登记</h3></div><button className="secondary-button" onClick={addDiagnosticScript} type="button">新增脚本</button></div>
        <div className="stack-lg">{config.diagnosticScripts.map((script, index) => <article key={script.id ?? `script-${index}`} className="subpanel"><div className="panel-head"><strong>{script.scriptName || `脚本 ${index + 1}`}</strong><button className="secondary-button" onClick={() => removeDiagnosticScript(index)} type="button">删除</button></div><div className="form-grid"><label className="toggle-row"><span>启用脚本</span><input checked={script.enabled} onChange={(event) => updateScript(index, { enabled: event.target.checked })} type="checkbox" /></label><label><span>脚本名称</span><input value={script.scriptName} onChange={(event) => updateScript(index, { scriptName: event.target.value })} /></label><label><span>命令路径</span><input value={script.commandPath} onChange={(event) => updateScript(index, { commandPath: event.target.value })} /></label><label><span>允许参数</span><input value={script.allowedArgs} onChange={(event) => updateScript(index, { allowedArgs: event.target.value })} /></label><label><span>超时（秒）</span><input type="number" value={script.timeoutSeconds} onChange={(event) => updateScript(index, { timeoutSeconds: Number(event.target.value) })} /></label><label className="toggle-row"><span>需要审批</span><input checked={script.requiresApproval} onChange={(event) => updateScript(index, { requiresApproval: event.target.checked })} type="checkbox" /></label><label><span>主机范围</span><input value={script.hostScope} onChange={(event) => updateScript(index, { hostScope: event.target.value })} /></label><label><span>服务范围</span><input value={script.serviceScope} onChange={(event) => updateScript(index, { serviceScope: event.target.value })} /></label><label><span>说明</span><input value={script.description} onChange={(event) => updateScript(index, { description: event.target.value })} /></label></div></article>)}</div>
        <div className="detail-actions"><button className="primary-button" disabled={saving || testingScripts} onClick={() => void handleSaveAndValidateScripts()} type="button">{testingScripts ? "校验中..." : "保存并校验脚本路径"}</button></div>
        {renderIntegrationResult("诊断脚本校验结果", scriptResult)}
      </section>

      <section className="panel">
        <div className="panel-head"><div><p className="eyebrow">自建知识库</p><h3>知识快速录入</h3></div></div>
        <div className="two-column-section">
          <form className="subpanel stack-md quick-entry-form" onSubmit={handleSaveKnowledge}>
            <label className="stack-sm"><span>服务领域</span><input value={knowledgeEntry.domain} onChange={(event) => setKnowledgeEntry((current) => ({ ...current, domain: event.target.value.toUpperCase() }))} placeholder="例如 IMPALA、HDFS、RANGER，也支持自定义" /></label>
            <div className="suggestion-chip-group">{suggestedKnowledgeDomains.map((domain) => <button key={domain} className={`suggestion-chip ${knowledgeEntry.domain === domain ? "suggestion-chip-active" : ""}`} type="button" onClick={() => setKnowledgeEntry((current) => ({ ...current, domain }))}>{domain}</button>)}</div>
            <label className="stack-sm"><span>知识内容</span><textarea className="app-textarea" value={knowledgeEntry.content} onChange={(event) => setKnowledgeEntry((current) => ({ ...current, content: event.target.value }))} placeholder="直接记录故障现象、判断思路、排查步骤和处理建议。系统会自动整理成知识条目写入数据库。" /></label>
            <p className="muted">这里只保留“服务 + 内容”两个核心输入，后台会自动补齐标题、摘要、关键字和处理步骤。</p>
            <div className="detail-actions"><button className="primary-button" disabled={savingKnowledge} type="submit">{savingKnowledge ? "保存中..." : "保存知识"}</button></div>
          </form>
          <div className="panel stack-md">
            <div className="panel-head"><strong>已保存知识条目</strong><span>{knowledgeArticles.length}</span></div>
            {knowledgeArticles.length === 0 ? <div className="empty-state">当前还没有知识条目。</div> : <div className="stack-md">{knowledgeArticles.slice(0, 10).map((article) => <div key={article.id} className="subpanel"><div className="panel-head"><strong>{article.title}</strong><span>{article.domain}</span></div><p className="compact-lead">{article.summary}</p></div>)}</div>}
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="detail-actions"><button className="primary-button" disabled={saving} type="button" onClick={() => void persistConfig()}>{saving ? "保存中..." : "保存全部集成配置"}</button></div>
        {message ? <div className="flash-message">{message}</div> : null}
        {error ? <div className="error-message">{error}</div> : null}
      </section>
    </div>
  );
}
