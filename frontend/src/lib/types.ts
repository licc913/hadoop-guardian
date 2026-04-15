export type Incident = {
  id: number;
  incidentNo: string;
  clusterName: string;
  serviceType: string;
  severity: string;
  status: string;
  title: string;
  summary: string;
  impactScope: string;
  owner: string;
  occurredAt: string;
  evidence: string[];
  avoidedActions: string[];
};

export type Diagnosis = {
  id: number;
  incidentId: number;
  subsystem: string;
  rootCause: string;
  confidence: number;
  impactLevel: string;
  crossComponentPath: string;
  recommendations: string[];
  followUps: string[];
  createdAt: string;
};

export type DiagnosisMode = "AUTO" | "LLM_ONLY" | "KNOWLEDGE_ONLY";

export type DiagnosisTaskResponse = {
  createdNewDiagnosis: boolean;
  message: string;
  details: string | null;
  reasonCodes: string[];
  diagnosis: Diagnosis | null;
  diagnosisSource: string;
  requestedMode: DiagnosisMode;
  usedFallback: boolean;
};

export type IncidentCloseResponse = {
  success: boolean;
  message: string;
  incident: Incident;
  closedAt: string;
};

export type ActionRecommendation = {
  id: number;
  incidentId: number;
  diagnosisId: number;
  actionName: string;
  actionType: string;
  riskLevel: string;
  requiresApproval: boolean;
  recommendationText: string;
  status: string;
  createdAt: string;
};

export type ApprovalRecord = {
  id: number;
  incidentId: number;
  actionRecommendationId: number;
  approvalStatus: string;
  requestedBy: string;
  approver: string | null;
  comment: string | null;
  requestedAt: string;
  decidedAt: string | null;
};

export type ExecutionRecord = {
  id: number;
  incidentId: number;
  actionRecommendationId: number;
  executionStatus: string;
  executor: string;
  executionSummary: string;
  startedAt: string;
  finishedAt: string | null;
};

export type PostmortemRecord = {
  id: number;
  incidentId: number;
  summary: string;
  rootCause: string;
  impactStatement: string;
  timeline: string[];
  preventionItems: string[];
  updatedAt: string;
};

export type KnowledgeSuggestion = {
  id: number;
  domain: string;
  scenarioKey: string;
  title: string;
  summary: string;
  applicability: string;
  riskLevel: string;
  requiresApproval: boolean;
  sourceName: string;
  sourceUrl: string;
  score: number;
  matchedKeywords: string[];
  matchReasons: string[];
  steps: string[];
  validationChecks: string[];
  cautionItems: string[];
};

export type AiGuidance = {
  incidentSynopsis: string;
  probableScenario: string;
  confidence: number;
  confidenceLabel: string;
  confidenceMethod: string;
  confidenceReasons: string[];
  signalHighlights: string[];
  missingSignals: string[];
  evidenceHighlights: string[];
  recommendedOrder: string[];
  planSummary: string;
  concretePlan: string[];
  operatorNotes: string[];
  linkedKnowledgeIds: number[];
  jmxInsights: string[];
};

export type KnowledgeArticle = {
  id: number;
  domain: string;
  scenarioKey: string;
  title: string;
  summary: string;
  applicability: string;
  riskLevel: string;
  requiresApproval: boolean;
  sourceName: string;
  sourceUrl: string;
  symptoms: string[];
  matchKeywords: string[];
  steps: string[];
  validationChecks: string[];
  cautionItems: string[];
};

export type KnowledgeArticleRequest = Omit<KnowledgeArticle, "id">;

export type KnowledgeQuickEntryRequest = {
  domain: string;
  content: string;
};

export type DashboardSummary = {
  openIncidents: number;
  diagnosingIncidents: number;
  criticalIncidents: number;
  actionRequiredIncidents: number;
};

export type ClouderaManagerSyncResponse = {
  success: boolean;
  enabled: boolean;
  fetchedCount: number;
  importedCount: number;
  skippedCount: number;
  message: string;
  details: string;
  validatedAt: string;
  endpoint: string;
  importedIncidents: string[];
};

export type CmServiceStatus = {
  serviceName: string;
  serviceType: string;
  serviceState: string;
  healthSummary: string;
  entityStatus: string;
  roleCount: number;
  unhealthyRoleCount: number;
  roleHighlights: string[];
  logHighlights: string[];
  logPreviewLines: string[];
};

export type CmCurrentStatusResponse = {
  success: boolean;
  enabled: boolean;
  message: string;
  details: string;
  endpoint: string;
  collectedAt: string;
  serviceCount: number;
  unhealthyServiceCount: number;
  services: CmServiceStatus[];
};

export type SystemStatus = {
  backendUp: boolean;
  clouderaManagerEnabled: boolean;
  databaseMode: string;
  incidentCount: number;
};

export type ClouderaManagerSettings = {
  enabled: boolean;
  baseUrl: string;
  apiVersion: string;
  username: string;
  password: string;
  passwordConfigured: boolean;
  clusterName: string;
  configured: boolean;
};

export type LogSourceSettings = {
  enabled: boolean;
  providerType: string;
  baseUrl: string;
  authType: string;
  authToken: string;
  authTokenConfigured: boolean;
  indexPattern: string;
  defaultTimeWindowMinutes: number | null;
};

export type LlmSettings = {
  enabled: boolean;
  endpoint: string;
  apiKey: string;
  apiKeyConfigured: boolean;
  model: string;
  connectTimeoutMs: number;
  readTimeoutMs: number;
  temperature: number;
  maxTokens: number;
  configured: boolean;
};

export type JmxEndpoint = {
  id?: number;
  enabled: boolean;
  serviceType: string;
  roleType: string;
  targetHost: string;
  port: number;
  path: string;
  protocol: string;
  authType: string;
  username: string;
  password: string;
  passwordConfigured: boolean;
  metricWhitelist: string;
};

export type DiagnosticScript = {
  id?: number;
  enabled: boolean;
  scriptName: string;
  commandPath: string;
  allowedArgs: string;
  timeoutSeconds: number;
  requiresApproval: boolean;
  hostScope: string;
  serviceScope: string;
  description: string;
};

export type JmxProbeResult = {
  endpointId: number | null;
  serviceType: string;
  roleType: string;
  targetHost: string;
  success: boolean;
  message: string;
  beanCount: number;
  sampleMetrics: string[];
  observedMetrics: string[];
};

export type JmxProbeResponse = {
  totalCount: number;
  successCount: number;
  failureCount: number;
  results: JmxProbeResult[];
  validatedAt: string;
};

export type DataSourceConfig = {
  clouderaManager: ClouderaManagerSettings;
  logSource: LogSourceSettings;
  llm: LlmSettings;
  jmxEndpoints: JmxEndpoint[];
  diagnosticScripts: DiagnosticScript[];
};

export type IntegrationTestResponse = {
  success: boolean;
  configured: boolean;
  message: string;
  details: string;
  endpoint: string;
  validatedAt: string;
};

export type LlmPromptResponse = {
  success: boolean;
  message: string;
  answer: string;
  model: string;
  respondedAt: string;
};

export type LlmChatMessage = {
  role: "user" | "assistant";
  content: string;
};

export type CurrentUser = {
  token: string;
  username: string;
  displayName: string;
  role: string;
};
