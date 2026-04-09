$GuardianJavaHome = ".\runtime\java"

$GuardianUseEmbeddedPostgres = $true
$GuardianEmbeddedPostgresHome = ".\runtime\postgresql"
$GuardianEmbeddedPostgresDataDir = ".\data\postgresql"
$GuardianPsqlPath = ""

$GuardianDbHost = "127.0.0.1"
$GuardianDbPort = 15432
$GuardianDbName = "guardian"
$GuardianDbUsername = "guardian"
$GuardianDbPassword = "guardian"

$GuardianServerPort = 8080
$GuardianSecurityEnabled = $true
$GuardianSecuritySigningSecret = "guardian-intranet-demo-change-me"
$GuardianAdminUsername = "admin"
$GuardianAdminPassword = "admin123"
$GuardianAdminDisplayName = "Platform Admin"
$GuardianOperatorUsername = "operator"
$GuardianOperatorPassword = "operator123"
$GuardianOperatorDisplayName = "Operations User"

$GuardianCmEnabled = $false
$GuardianCmBaseUrl = "http://cm.example.local:7180"
$GuardianCmApiVersion = "v51"
$GuardianCmUsername = "admin"
$GuardianCmPassword = "admin"
$GuardianCmClusterName = "cluster"

$GuardianLlmEnabled = $false
$GuardianLlmEndpoint = "http://llm.example.local:8000/v1/chat/completions"
$GuardianLlmApiKey = ""
$GuardianLlmModel = ""
$GuardianLlmConnectTimeoutMs = 10000
$GuardianLlmReadTimeoutMs = 30000
$GuardianLlmTemperature = 0.2
$GuardianLlmMaxTokens = 900

$GuardianInitSchema = $true
$GuardianInitDemoData = $false
