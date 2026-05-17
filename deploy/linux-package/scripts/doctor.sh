#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

print_check() {
  local status="$1"
  local name="$2"
  local detail="${3:-}"
  printf '[%s] %s' "${status}" "${name}"
  if [[ -n "${detail}" ]]; then
    printf ' - %s' "${detail}"
  fi
  printf '\n'
}

check_file() {
  local path="$1"
  local name="$2"
  if [[ -e "${path}" ]]; then
    print_check "OK" "${name}" "${path}"
  else
    print_check "FAIL" "${name}" "missing: ${path}"
  fi
}

echo "Hadoop Guardian deployment doctor"
echo "Bundle: ${BUNDLE_ROOT}"
echo

check_file "${BUNDLE_ROOT}/app/hadoop-guardian-backend.jar" "backend jar"
check_file "${BUNDLE_ROOT}/frontend/index.html" "frontend index"
check_file "${CONFIG_PATH}" "deploy config"

if java_cmd="$(resolve_java_cmd 2>/dev/null)"; then
  java_version="$("${java_cmd}" -version 2>&1 | head -n 1 || true)"
  print_check "OK" "java runtime" "${java_cmd} ${java_version}"
else
  print_check "FAIL" "java runtime" "set GuardianJavaHome in config/deploy-env.sh"
fi

if [[ "${GuardianUseEmbeddedPostgres}" == "true" ]]; then
  if pg_ctl="$(resolve_pg_ctl_cmd 2>/dev/null)"; then
    print_check "OK" "postgres runtime" "${pg_ctl}"
  else
    print_check "FAIL" "postgres runtime" "embedded PostgreSQL binary not found"
  fi
else
  if command -v psql >/dev/null 2>&1 || [[ -n "${GuardianPsqlPath:-}" ]]; then
    print_check "OK" "external postgres client" "${GuardianPsqlPath:-$(command -v psql)}"
  else
    print_check "WARN" "external postgres client" "psql not found in PATH"
  fi
fi

if test_postgres_ready; then
  print_check "OK" "postgres connection" "${GuardianDbHost}:${GuardianDbPort}/${GuardianDbName}"
else
  print_check "WARN" "postgres connection" "not ready; run deploy.sh or start.sh first"
fi

if command -v curl >/dev/null 2>&1; then
  if curl -fsS "http://127.0.0.1:${GuardianServerPort}/actuator/health" >/dev/null 2>&1; then
    print_check "OK" "backend health" "http://127.0.0.1:${GuardianServerPort}/actuator/health"
  else
    print_check "WARN" "backend health" "not reachable on port ${GuardianServerPort}"
  fi
else
  print_check "WARN" "curl" "not installed; backend health check skipped"
fi

if [[ "${GuardianCmEnabled}" == "true" ]]; then
  if command -v curl >/dev/null 2>&1; then
    cm_url="${GuardianCmBaseUrl%/}/api/${GuardianCmApiVersion}/clusters/${GuardianCmClusterName}/services"
    if curl -fsS -u "${GuardianCmUsername}:${GuardianCmPassword}" "${cm_url}" >/dev/null 2>&1; then
      print_check "OK" "Cloudera Manager API" "${cm_url}"
    else
      print_check "WARN" "Cloudera Manager API" "cannot reach ${cm_url}"
    fi
  fi
else
  print_check "INFO" "Cloudera Manager API" "disabled in config"
fi

if [[ "${GuardianLlmEnabled}" == "true" ]]; then
  if [[ -n "${GuardianLlmEndpoint}" && -n "${GuardianLlmApiKey}" && -n "${GuardianLlmModel}" ]]; then
    print_check "OK" "LLM config" "${GuardianLlmEndpoint} model=${GuardianLlmModel}"
  else
    print_check "WARN" "LLM config" "enabled but endpoint/api key/model is incomplete"
  fi
else
  print_check "INFO" "LLM config" "disabled in config"
fi

echo
echo "Doctor finished. FAIL means deployment must be fixed; WARN means runtime-dependent checks may need service startup or network access."
