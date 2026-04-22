#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONFIG_PATH="${BUNDLE_ROOT}/config/deploy-env.sh"

if [[ ! -f "${CONFIG_PATH}" ]]; then
  echo "Deploy config not found: ${CONFIG_PATH}" >&2
  exit 1
fi

# shellcheck source=/dev/null
source "${CONFIG_PATH}"

resolve_bundle_path() {
  local path_value="${1:-}"
  if [[ -z "${path_value}" ]]; then
    return 1
  fi
  if [[ "${path_value}" = /* ]]; then
    printf '%s\n' "${path_value}"
  else
    printf '%s\n' "${BUNDLE_ROOT}/${path_value#./}"
  fi
}

resolve_embedded_postgres_home() {
  ensure_embedded_postgres_runtime_extracted
  local embedded_home
  embedded_home="$(resolve_bundle_path "${GuardianEmbeddedPostgresHome}")"
  if [[ -d "${embedded_home}/bin" ]]; then
    printf '%s\n' "${embedded_home}"
    return 0
  fi

  if [[ -d "${embedded_home}" ]]; then
    local candidate
    candidate="$(find "${embedded_home}" -maxdepth 2 -type d -name bin | head -n 1 || true)"
    if [[ -n "${candidate}" ]]; then
      printf '%s\n' "$(cd "${candidate}/.." && pwd)"
      return 0
    fi
  fi

  echo "Embedded PostgreSQL home not found under ${embedded_home}" >&2
  exit 1
}

ensure_embedded_postgres_runtime_extracted() {
  if [[ "${GuardianUseEmbeddedPostgres}" != "true" ]]; then
    return 0
  fi

  local embedded_home
  embedded_home="$(resolve_bundle_path "${GuardianEmbeddedPostgresHome}")"
  if [[ -d "${embedded_home}/bin" ]]; then
    return 0
  fi

  local archive_path
  archive_path="$(resolve_bundle_path "${GuardianEmbeddedPostgresArchive}")"
  if [[ ! -f "${archive_path}" ]]; then
    echo "Embedded PostgreSQL archive not found: ${archive_path}" >&2
    exit 1
  fi

  if ! command -v tar >/dev/null 2>&1; then
    echo "tar is required to unpack embedded PostgreSQL runtime." >&2
    exit 1
  fi

  local runtime_root
  runtime_root="$(dirname "$(dirname "${embedded_home}")")"
  mkdir -p "${runtime_root}"
  tar -xzf "${archive_path}" -C "${runtime_root}"

  if [[ ! -d "${embedded_home}/bin" ]]; then
    local extracted_home
    extracted_home="$(find "${runtime_root}" -maxdepth 2 -type d -path "*/bin" | head -n 1 || true)"
    if [[ -n "${extracted_home}" ]]; then
      extracted_home="$(cd "${extracted_home}/.." && pwd)"
      mkdir -p "$(dirname "${embedded_home}")"
      if [[ ! -e "${embedded_home}" ]]; then
        ln -s "${extracted_home}" "${embedded_home}" 2>/dev/null || cp -R "${extracted_home}" "${embedded_home}"
      fi
    fi
  fi

  if [[ ! -d "${embedded_home}/bin" ]]; then
    echo "Embedded PostgreSQL runtime unpack failed: ${embedded_home}" >&2
    exit 1
  fi
}

prepare_embedded_postgres_env() {
  if [[ "${GuardianUseEmbeddedPostgres}" != "true" ]]; then
    return 0
  fi

  local embedded_home
  embedded_home="$(resolve_embedded_postgres_home)"
  export LD_LIBRARY_PATH="${embedded_home}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"
  export PATH="${embedded_home}/bin:${PATH}"
}

get_logs_dir() {
  local logs_dir="${BUNDLE_ROOT}/logs"
  mkdir -p "${logs_dir}"
  printf '%s\n' "${logs_dir}"
}

resolve_java_cmd() {
  if [[ -n "${GuardianJavaHome:-}" ]]; then
    local java_home
    java_home="$(resolve_bundle_path "${GuardianJavaHome}")"
    if [[ -x "${java_home}/bin/java" ]]; then
      printf '%s\n' "${java_home}/bin/java"
      return 0
    fi
  fi

  if [[ -x "${BUNDLE_ROOT}/runtime/java/bin/java" ]]; then
    printf '%s\n' "${BUNDLE_ROOT}/runtime/java/bin/java"
    return 0
  fi

  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi

  echo "Java runtime not found. Set GuardianJavaHome in config/deploy-env.sh." >&2
  exit 1
}

resolve_postgres_binary() {
  local binary_name="$1"

  if [[ "${GuardianUseEmbeddedPostgres}" == "true" ]]; then
    prepare_embedded_postgres_env
    local embedded_home
    embedded_home="$(resolve_embedded_postgres_home)"
    if [[ -x "${embedded_home}/bin/${binary_name}" ]]; then
      printf '%s\n' "${embedded_home}/bin/${binary_name}"
      return 0
    fi
    echo "Embedded PostgreSQL binary not found: ${embedded_home}/bin/${binary_name}" >&2
    exit 1
  fi

  if [[ -n "${GuardianPsqlPath:-}" ]]; then
    local configured_path
    configured_path="$(resolve_bundle_path "${GuardianPsqlPath}")"
    if [[ "${binary_name}" == "psql" && -x "${configured_path}" ]]; then
      printf '%s\n' "${configured_path}"
      return 0
    fi
    local sibling
    sibling="$(cd "$(dirname "${configured_path}")" && pwd)/${binary_name}"
    if [[ -x "${sibling}" ]]; then
      printf '%s\n' "${sibling}"
      return 0
    fi
  fi

  if command -v "${binary_name}" >/dev/null 2>&1; then
    command -v "${binary_name}"
    return 0
  fi

  echo "${binary_name} not found. Check config/deploy-env.sh." >&2
  exit 1
}

resolve_psql_cmd() {
  resolve_postgres_binary "psql"
}

resolve_createdb_cmd() {
  resolve_postgres_binary "createdb"
}

resolve_pg_ctl_cmd() {
  resolve_postgres_binary "pg_ctl"
}

resolve_initdb_cmd() {
  resolve_postgres_binary "initdb"
}

resolve_pg_isready_cmd() {
  resolve_postgres_binary "pg_isready"
}

get_backend_pid_file() {
  printf '%s\n' "$(get_logs_dir)/backend.pid"
}

get_embedded_postgres_data_dir() {
  resolve_bundle_path "${GuardianEmbeddedPostgresDataDir}"
}

get_embedded_postgres_log_file() {
  printf '%s\n' "$(get_logs_dir)/postgresql.log"
}

test_postgres_ready() {
  local pg_isready
  pg_isready="$(resolve_pg_isready_cmd)"
  PGPASSWORD="${GuardianDbPassword}" "${pg_isready}" \
    -h "${GuardianDbHost}" \
    -p "${GuardianDbPort}" \
    -U "${GuardianDbUsername}" \
    -d postgres >/dev/null 2>&1
}

ensure_embedded_postgres_initialized() {
  if [[ "${GuardianUseEmbeddedPostgres}" != "true" ]]; then
    return 0
  fi

  local data_dir
  data_dir="$(get_embedded_postgres_data_dir)"
  if [[ -f "${data_dir}/PG_VERSION" ]]; then
    return 0
  fi

  mkdir -p "$(dirname "${data_dir}")" "$(get_logs_dir)"
  prepare_embedded_postgres_env

  local password_file
  password_file="$(get_logs_dir)/postgres-password.txt"
  printf '%s' "${GuardianDbPassword}" > "${password_file}"

  local initdb
  initdb="$(resolve_initdb_cmd)"
  "${initdb}" \
    -D "${data_dir}" \
    -U "${GuardianDbUsername}" \
    -A scram-sha-256 \
    --pwfile="${password_file}" \
    --encoding=UTF8

  rm -f "${password_file}"

  {
    echo
    echo "listen_addresses = '127.0.0.1'"
    echo "port = ${GuardianDbPort}"
    echo "max_connections = 100"
  } >> "${data_dir}/postgresql.auto.conf"

  {
    echo
    echo "host all all 127.0.0.1/32 scram-sha-256"
    echo "host all all ::1/128 scram-sha-256"
  } >> "${data_dir}/pg_hba.conf"
}

ensure_postgres_running() {
  if [[ "${GuardianUseEmbeddedPostgres}" != "true" ]]; then
    return 0
  fi

  ensure_embedded_postgres_initialized

  if test_postgres_ready; then
    return 0
  fi

  local pg_ctl
  pg_ctl="$(resolve_pg_ctl_cmd)"
  local data_dir
  data_dir="$(get_embedded_postgres_data_dir)"
  local log_file
  log_file="$(get_embedded_postgres_log_file)"

  "${pg_ctl}" start -D "${data_dir}" -l "${log_file}" -w -t 60 -o "-p ${GuardianDbPort}"
}

wait_for_pid_exit() {
  local pid="$1"
  local attempts=30
  while (( attempts > 0 )); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    attempts=$((attempts - 1))
  done
  return 1
}

stop_backend_process() {
  local pid_file
  pid_file="$(get_backend_pid_file)"
  local stopped=false

  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(head -n 1 "${pid_file}" | tr -d '[:space:]')"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
      wait_for_pid_exit "${pid}" || kill -9 "${pid}" >/dev/null 2>&1 || true
      stopped=true
    fi
    rm -f "${pid_file}"
  fi

  printf '%s\n' "${stopped}"
}

stop_postgres_process() {
  if [[ "${GuardianUseEmbeddedPostgres}" != "true" ]]; then
    printf '%s\n' "false"
    return 0
  fi

  local data_dir
  data_dir="$(get_embedded_postgres_data_dir)"
  if [[ ! -f "${data_dir}/postmaster.pid" ]]; then
    printf '%s\n' "false"
    return 0
  fi

  local pg_ctl
  pg_ctl="$(resolve_pg_ctl_cmd)"
  "${pg_ctl}" stop -D "${data_dir}" -m fast -w -t 60 >/dev/null
  printf '%s\n' "true"
}

wait_for_backend_ready() {
  local port="$1"
  local attempts=24
  while (( attempts > 0 )); do
    if command -v curl >/dev/null 2>&1; then
      if curl -fsS "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
        return 0
      fi
    elif (echo >"/dev/tcp/127.0.0.1/${port}") >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
    attempts=$((attempts - 1))
  done
  return 1
}

set_guardian_environment() {
  export SPRING_PROFILES_ACTIVE="postgres"
  export GUARDIAN_DB_URL="jdbc:postgresql://${GuardianDbHost}:${GuardianDbPort}/${GuardianDbName}"
  export GUARDIAN_DB_USERNAME="${GuardianDbUsername}"
  export GUARDIAN_DB_PASSWORD="${GuardianDbPassword}"
  export GUARDIAN_SECURITY_ENABLED="${GuardianSecurityEnabled}"
  export GUARDIAN_SECURITY_SIGNING_SECRET="${GuardianSecuritySigningSecret}"
  export GUARDIAN_ADMIN_USERNAME="${GuardianAdminUsername}"
  export GUARDIAN_ADMIN_PASSWORD="${GuardianAdminPassword}"
  export GUARDIAN_ADMIN_DISPLAY_NAME="${GuardianAdminDisplayName}"
  export GUARDIAN_OPERATOR_USERNAME="${GuardianOperatorUsername}"
  export GUARDIAN_OPERATOR_PASSWORD="${GuardianOperatorPassword}"
  export GUARDIAN_OPERATOR_DISPLAY_NAME="${GuardianOperatorDisplayName}"
  export GUARDIAN_CM_ENABLED="${GuardianCmEnabled}"
  export GUARDIAN_CM_BASE_URL="${GuardianCmBaseUrl}"
  export GUARDIAN_CM_API_VERSION="${GuardianCmApiVersion}"
  export GUARDIAN_CM_USERNAME="${GuardianCmUsername}"
  export GUARDIAN_CM_PASSWORD="${GuardianCmPassword}"
  export GUARDIAN_CM_CLUSTER_NAME="${GuardianCmClusterName}"
  export GUARDIAN_LLM_ENABLED="${GuardianLlmEnabled}"
  export GUARDIAN_LLM_ENDPOINT="${GuardianLlmEndpoint}"
  export GUARDIAN_LLM_API_KEY="${GuardianLlmApiKey}"
  export GUARDIAN_LLM_MODEL="${GuardianLlmModel}"
  export GUARDIAN_LLM_CONNECT_TIMEOUT_MS="${GuardianLlmConnectTimeoutMs}"
  export GUARDIAN_LLM_READ_TIMEOUT_MS="${GuardianLlmReadTimeoutMs}"
  export GUARDIAN_LLM_TEMPERATURE="${GuardianLlmTemperature}"
  export GUARDIAN_LLM_MAX_TOKENS="${GuardianLlmMaxTokens}"
}
