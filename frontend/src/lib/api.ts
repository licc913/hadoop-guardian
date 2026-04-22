import type {
  ActionRecommendation,
  AiGuidance,
  ApprovalRecord,
  ClouderaManagerSettings,
  ClouderaManagerSyncResponse,
  ClusterInspectionReport,
  CmCurrentStatusResponse,
  CmServiceLogSnapshot,
  CurrentUser,
  DashboardSummary,
  DataSourceConfig,
  Diagnosis,
  DiagnosisMode,
  DiagnosisTaskResponse,
  ExecutionRecord,
  Incident,
  IncidentCloseResponse,
  IntegrationTestResponse,
  JmxProbeResponse,
  KnowledgeArticle,
  KnowledgeArticleRequest,
  KnowledgeQuickEntryRequest,
  KnowledgeSuggestion,
  LlmChatMessage,
  LlmPromptResponse,
  PostmortemRecord,
  SystemStatus
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";
const AUTH_STORAGE_KEY = "guardian-auth";

function getAuthToken(): string | null {
  return window.localStorage.getItem(AUTH_STORAGE_KEY);
}

export function setAuthToken(token: string | null) {
  if (token) {
    window.localStorage.setItem(AUTH_STORAGE_KEY, token);
  } else {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  }
}

async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers ?? {});
  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

async function apiFetchWithTimeout(path: string, timeoutMs: number, init?: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await apiFetch(path, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`接口请求超时，超过 ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function readJson<T>(path: string): Promise<T> {
  const response = await apiFetch(path);
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  return (await response.json()) as T;
}

async function writeJson<T>(path: string, method: string, payload?: unknown): Promise<T> {
  const response = await apiFetch(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  return (await response.json()) as T;
}

async function buildApiError(response: Response): Promise<string> {
  const fallback = `API failed: ${response.status}`;
  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("application/json")) {
      const body = await response.json() as { message?: string; details?: string; error?: string };
      const details = [body.message, body.details, body.error].filter(Boolean).join(" | ");
      return details ? `${fallback} | ${details}` : fallback;
    }
    const text = (await response.text()).trim();
    return text ? `${fallback} | ${text}` : fallback;
  } catch {
    return fallback;
  }
}

export function getDashboardSummary(): Promise<DashboardSummary> {
  return readJson("/dashboard/summary");
}

export function getSystemStatus(): Promise<SystemStatus> {
  return readJson("/system/status");
}

export function getIncidents(): Promise<Incident[]> {
  return readJson("/incidents");
}

export function getIncident(incidentId: number): Promise<Incident> {
  return readJson(`/incidents/${incidentId}`);
}

export function closeIncident(incidentId: number, closeReason: string): Promise<IncidentCloseResponse> {
  return writeJson(`/incidents/${incidentId}/close`, "POST", {
    closedBy: "frontend-operator",
    closeReason
  });
}

export function getDiagnoses(incidentId: number): Promise<Diagnosis[]> {
  return readJson(`/incidents/${incidentId}/diagnoses`);
}

export function getActionRecommendations(incidentId: number): Promise<ActionRecommendation[]> {
  return readJson(`/incidents/${incidentId}/actions`);
}

export function getKnowledgeSuggestions(incidentId: number): Promise<KnowledgeSuggestion[]> {
  return readJson(`/incidents/${incidentId}/knowledge-suggestions`);
}

export function getAiGuidance(incidentId: number): Promise<AiGuidance> {
  return readJson(`/incidents/${incidentId}/ai-guidance`);
}

export function getApprovalRecords(incidentId: number): Promise<ApprovalRecord[]> {
  return readJson(`/incidents/${incidentId}/approvals`);
}

export function getExecutionRecords(incidentId: number): Promise<ExecutionRecord[]> {
  return readJson(`/incidents/${incidentId}/executions`);
}

export function getPostmortem(incidentId: number): Promise<PostmortemRecord> {
  return readJson(`/incidents/${incidentId}/postmortem`);
}

export function createDiagnosisTask(
  incidentId: number,
  diagnosisMode: DiagnosisMode = "AUTO"
): Promise<DiagnosisTaskResponse> {
  return writeJson(`/incidents/${incidentId}/diagnosis-tasks`, "POST", {
    triggerBy: "frontend-operator",
    triggerReason: "由事件详情页人工发起诊断",
    diagnosisMode
  });
}

export function syncClouderaManagerAlerts(): Promise<ClouderaManagerSyncResponse> {
  return writeJson("/integrations/cloudera-manager/sync-alerts", "POST");
}

export function getClouderaManagerCurrentStatus(): Promise<CmCurrentStatusResponse> {
  return writeJsonWithTimeout("/integrations/cloudera-manager/current-status", "POST", 90000);
}

export function getClouderaManagerCurrentLogs(): Promise<CmServiceLogSnapshot[]> {
  return readJson("/integrations/cloudera-manager/current-logs");
}

export function getIncidentServiceLogs(incidentId: number): Promise<CmServiceLogSnapshot[]> {
  return readJson(`/incidents/${incidentId}/service-logs`);
}

async function writeJsonWithTimeout<T>(path: string, method: string, timeoutMs: number, payload?: unknown): Promise<T> {
  const response = await apiFetchWithTimeout(path, timeoutMs, {
    method,
    headers: { "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  return (await response.json()) as T;
}

export function getClouderaManagerSettings(): Promise<ClouderaManagerSettings> {
  return readJson("/integrations/cloudera-manager/settings");
}

export function saveClouderaManagerSettings(payload: ClouderaManagerSettings): Promise<ClouderaManagerSettings> {
  return writeJson("/integrations/cloudera-manager/settings", "PUT", payload);
}

export function getDataSourceConfig(): Promise<DataSourceConfig> {
  return readJson("/integrations/datasources");
}

export function saveDataSourceConfig(payload: DataSourceConfig): Promise<DataSourceConfig> {
  return writeJson("/integrations/datasources", "PUT", payload);
}

export function testJmxEndpoints(): Promise<JmxProbeResponse> {
  return writeJson("/integrations/datasources/jmx/test", "POST");
}

export function testLogSourceConnection(): Promise<IntegrationTestResponse> {
  return writeJson("/integrations/datasources/log-source/test", "POST");
}

export function testLlmConnection(): Promise<IntegrationTestResponse> {
  return writeJson("/integrations/datasources/llm/test", "POST");
}

export function askLlmQuestion(question: string, history: LlmChatMessage[] = []): Promise<LlmPromptResponse> {
  return writeJson("/integrations/datasources/llm/chat", "POST", { question, history });
}

export function testDiagnosticScripts(): Promise<IntegrationTestResponse> {
  return writeJson("/integrations/datasources/scripts/test", "POST");
}

export function getKnowledgeArticles(): Promise<KnowledgeArticle[]> {
  return readJson("/knowledge/articles");
}

export function saveKnowledgeArticle(payload: KnowledgeArticleRequest): Promise<KnowledgeArticle> {
  return writeJson("/knowledge/articles", "POST", payload);
}

export function saveKnowledgeQuickEntry(payload: KnowledgeQuickEntryRequest): Promise<KnowledgeArticle> {
  return writeJson("/knowledge/quick-entry", "POST", payload);
}

export function getClusterInspectionReports(): Promise<ClusterInspectionReport[]> {
  return readJson("/inspections");
}

export function getClusterInspectionReport(reportId: number): Promise<ClusterInspectionReport> {
  return readJson(`/inspections/${reportId}`);
}

export function createClusterInspectionReport(triggeredBy = "frontend-operator"): Promise<ClusterInspectionReport> {
  return writeJson("/inspections", "POST", { triggeredBy });
}

export async function downloadClusterInspectionReportDocx(reportId: number): Promise<Blob> {
  const response = await apiFetch(`/inspections/${reportId}/export/docx`);
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  return response.blob();
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  const currentUser = (await response.json()) as CurrentUser;
  setAuthToken(currentUser.token);
  return currentUser;
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await apiFetch("/auth/me");
  if (!response.ok) {
    throw new Error(await buildApiError(response));
  }
  return (await response.json()) as CurrentUser;
}

export function logout() {
  setAuthToken(null);
}
