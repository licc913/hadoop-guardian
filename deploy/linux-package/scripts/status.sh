#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_ROOT}/common.sh"

PID_FILE="$(get_backend_pid_file)"
BACKEND_STATUS="stopped"
if [[ -f "${PID_FILE}" ]]; then
  BACKEND_PID="$(head -n 1 "${PID_FILE}" | tr -d '[:space:]')"
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    BACKEND_STATUS="running(pid=${BACKEND_PID})"
  fi
fi

POSTGRES_STATUS="external"
if [[ "${GuardianUseEmbeddedPostgres}" == "true" ]]; then
  if test_postgres_ready; then
    POSTGRES_STATUS="running"
  else
    POSTGRES_STATUS="stopped"
  fi
fi

echo "Backend: ${BACKEND_STATUS}"
echo "PostgreSQL: ${POSTGRES_STATUS}"
echo "Local URL: http://127.0.0.1:${GuardianServerPort}"
echo "Remote URL: http://<server-ip>:${GuardianServerPort}"
