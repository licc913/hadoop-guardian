#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_ROOT}/common.sh"

if [[ "${GuardianInitSchema}" != "true" && "${GuardianInitDemoData}" != "true" ]]; then
  echo "Database initialization is disabled in config."
  exit 0
fi

ensure_postgres_running

PSQL="$(resolve_psql_cmd)"
CREATEDB="$(resolve_createdb_cmd)"
SQL_DIR="${BUNDLE_ROOT}/sql"

export PGPASSWORD="${GuardianDbPassword}"
export PGCLIENTENCODING="UTF8"

DB_EXISTS="$("${PSQL}" -h "${GuardianDbHost}" -p "${GuardianDbPort}" -U "${GuardianDbUsername}" -d postgres -tAc "select 1 from pg_database where datname = '${GuardianDbName}';")"
if [[ -z "${DB_EXISTS// /}" ]]; then
  "${CREATEDB}" -h "${GuardianDbHost}" -p "${GuardianDbPort}" -U "${GuardianDbUsername}" "${GuardianDbName}"
fi

run_sql_file() {
  local file_name="$1"
  "${PSQL}" \
    -h "${GuardianDbHost}" \
    -p "${GuardianDbPort}" \
    -U "${GuardianDbUsername}" \
    -d "${GuardianDbName}" \
    -v ON_ERROR_STOP=1 \
    -f "${SQL_DIR}/${file_name}"
}

if [[ "${GuardianInitSchema}" == "true" ]]; then
  for file in \
    001_init_schema.sql \
    003_workflow_schema.sql \
    005_datasource_schema.sql \
    007_knowledge_base_schema.sql \
    012_knowledge_document_chunk_schema.sql \
    013_cluster_inspection_schema.sql; do
    run_sql_file "${file}"
  done
fi

if [[ "${GuardianInitDemoData}" == "true" ]]; then
  INCIDENT_COUNT="$("${PSQL}" -h "${GuardianDbHost}" -p "${GuardianDbPort}" -U "${GuardianDbUsername}" -d "${GuardianDbName}" -tAc "select count(*) from incident_event;")"
  if [[ "${INCIDENT_COUNT// /}" == "0" ]]; then
    run_sql_file "009_postgres_demo_data_zh.sql"
  fi
fi

echo "Database initialization completed."
