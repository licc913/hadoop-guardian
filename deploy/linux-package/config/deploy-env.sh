GuardianJavaHome=""

GuardianUseEmbeddedPostgres=true
GuardianEmbeddedPostgresArchive="./runtime/percona-postgresql-14.22-ssl1.1-linux-x86_64.tar.gz"
GuardianEmbeddedPostgresHome="./runtime/postgresql/percona-postgresql14"
GuardianEmbeddedPostgresDataDir="./data/postgresql"
GuardianPsqlPath=""

GuardianDbHost="127.0.0.1"
GuardianDbPort=5432
GuardianDbName="guardian"
GuardianDbUsername="guardian"
GuardianDbPassword="guardian"

GuardianServerPort=18080
GuardianSecurityEnabled=true
GuardianSecuritySigningSecret="guardian-linux-demo-change-me"
GuardianAdminUsername="admin"
GuardianAdminPassword="admin123"
GuardianAdminDisplayName="Platform Admin"
GuardianOperatorUsername="operator"
GuardianOperatorPassword="operator123"
GuardianOperatorDisplayName="Operations User"

GuardianCmEnabled=false
GuardianCmBaseUrl="http://cm.example.local:7180"
GuardianCmApiVersion="v51"
GuardianCmUsername="admin"
GuardianCmPassword="admin"
GuardianCmClusterName="cluster"
GuardianCmRoleLogTimeoutMs=20000

GuardianLlmEnabled=false
GuardianLlmEndpoint="http://llm.example.local:8000/v1/chat/completions"
GuardianLlmApiKey=""
GuardianLlmModel=""
GuardianLlmConnectTimeoutMs=10000
GuardianLlmReadTimeoutMs=30000
GuardianLlmTemperature=0.2
GuardianLlmMaxTokens=0

GuardianInitSchema=true
GuardianInitDemoData=false
