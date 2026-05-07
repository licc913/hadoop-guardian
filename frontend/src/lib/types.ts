export type Incident = {
  id: number;
  incidentNo: string;
  clusterName: string;
  serviceType: string;
  severity: string;
  status: string;
  governanceStatus: string;
  eventFingerprint: string;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  occurrenceCount: number;
  suppressedUntil: string | null;
  governanceNote: string | null;
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
  suppressedIncidents: number;
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

export type CmServiceLogSnapshot = {
  clusterName: string;
  serviceName: string;
  serviceType: string;
  logType: string;
  logText: string;
  collectedAt: string;
};

export type CrossComponentRelatedIncident = {
  incidentId: number;
  incidentNo: string;
  serviceType: string;
  severity: string;
  status: string;
  title: string;
  correlationScore: number;
  relationSummary: string;
  matchedSignals: string[];
  sharedNodes: string[];
  occurredAt: string | null;
};

export type CrossComponentAnalysis = {
  primaryService: string;
  probablePath: string;
  confidence: number;
  confidenceLabel: string;
  summary: string;
  impactAssessment: string;
  correlatedServices: string[];
  sharedNodes: string[];
  signalHighlights: string[];
  recommendedChecks: string[];
  relatedIncidents: CrossComponentRelatedIncident[];
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
  recentLogs: CmServiceLogSnapshot[];
};

export type SystemStatus = {
  backendUp: boolean;
  clouderaManagerEnabled: boolean;
  databaseMode: string;
  incidentCount: number;
  suppressedIncidentCount: number;
  lastCmCollectionAt: string | null;
  lastCmCollectionSuccess: boolean;
  lastCmCollectionMessage: string;
  lastCmRecentLogCount: number;
  inspectionRunningCount: number;
  inspectionFailedCount: number;
  lastInspectionStartedAt: string | null;
  lastInspectionCompletedAt: string | null;
  lastInspectionStatus: string;
  lastInspectionMessage: string;
};

export type IncidentGovernanceResponse = {
  success: boolean;
  message: string;
  incident: Incident;
  effectiveAt: string;
};

export type ApprovalRecordCreateRequest = {
  actionRecommendationId?: number;
  approvalStatus: string;
  requestedBy: string;
  approver?: string | null;
  comment?: string | null;
};

export type ExecutionRecordCreateRequest = {
  actionRecommendationId?: number;
  executionStatus: string;
  executor: string;
  executionSummary: string;
};

export type PostmortemUpsertRequest = {
  summary: string;
  rootCause: string;
  impactStatement: string;
  timeline: string[];
  preventionItems: string[];
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

export type ClusterInspectionReport = {
  id: number;
  clusterName: string;
  reportTitle: string;
  overallRisk: string;
  status: string;
  summary: string;
  markdownContent: string;
  generatedBy: string;
  llmModel: string | null;
  sourceCollectedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type SqlOptimizationEngine = "IMPALA" | "HIVE";

export type SqlOptimizationRequest = {
  engineType: SqlOptimizationEngine;
  originalSql: string;
  tableSchemaNote: string;
  partitionInfo: string;
  explainText: string;
  errorText: string;
  optimizationGoal: string;
  createdBy?: string;
};

export type SqlOptimizationResult = {
  id: number;
  engineType: SqlOptimizationEngine;
  originalSql: string;
  tableSchemaNote: string | null;
  partitionInfo: string | null;
  explainText: string | null;
  errorText: string | null;
  optimizationGoal: string | null;
  problemSummary: string;
  optimizedSql: string;
  optimizationPoints: string[];
  riskNotes: string[];
  validationSteps: string[];
  ruleFindings: string[];
  llmModel: string | null;
  analysisSource: string;
  createdBy: string;
  createdAt: string;
};

export type ParameterOptimizationServiceType = "HDFS" | "YARN" | "HIVE_ON_TEZ" | "IMPALA";

export type ParameterOptimizationRequest = {
  serviceType: ParameterOptimizationServiceType;
  currentSymptoms: string;
  optimizationGoal: string;
  sourceCodeHints: string;
  manualConfigNote: string;
  createdBy?: string;
  useCurrentClusterConfig: boolean;
};

export type ParameterRecommendation = {
  configKey: string;
  currentValue: string;
  recommendedValue: string;
  reason: string;
};

export type ParameterConfigEntry = {
  scopeType: string;
  scopeName: string;
  roleType: string;
  configKey: string;
  configValue: string;
  valueSource: string;
};

export type ParameterOptimizationContextPreview = {
  configured: boolean;
  available: boolean;
  message: string;
  clusterName: string;
  serviceName: string;
  serviceType: string;
  componentVersion: string;
  serviceState: string;
  healthSummary: string;
  configEntries: Record<string, string>;
  scopedConfigEntries: ParameterConfigEntry[];
  recentSignals: string[];
};

export type ParameterOptimizationResult = {
  id: number;
  clusterName: string;
  serviceName: string;
  serviceType: string;
  componentVersion: string | null;
  currentSymptoms: string | null;
  optimizationGoal: string | null;
  configSnapshotText: string;
  sourceCodeHints: string | null;
  problemSummary: string;
  recommendations: ParameterRecommendation[];
  sourceEvidence: string[];
  expectedBenefits: string[];
  riskNotes: string[];
  validationSteps: string[];
  ruleFindings: string[];
  llmModel: string | null;
  analysisSource: string;
  createdBy: string;
  createdAt: string;
};
