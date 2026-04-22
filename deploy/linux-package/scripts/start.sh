#!/usr/bin/env bash
set -euo pipefail

FOREGROUND=false
if [[ "${1:-}" == "--foreground" ]]; then
  FOREGROUND=true
fi

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_ROOT}/common.sh"

ensure_postgres_running
set_guardian_environment

JAR_PATH="${BUNDLE_ROOT}/app/hadoop-guardian-backend.jar"
FRONTEND_DIR="${BUNDLE_ROOT}/frontend"
LOGS_DIR="$(get_logs_dir)"
PID_FILE="$(get_backend_pid_file)"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Backend jar not found: ${JAR_PATH}" >&2
  exit 1
fi

if [[ ! -f "${FRONTEND_DIR}/index.html" ]]; then
  echo "Frontend static files not found: ${FRONTEND_DIR}" >&2
  exit 1
fi

stop_backend_process >/dev/null

JAVA_CMD="$(resolve_java_cmd)"
ARGS=(
  "-Djava.net.preferIPv4Stack=true"
  "-jar"
  "${JAR_PATH}"
  "--server.port=${GuardianServerPort}"
  "--server.address=0.0.0.0"
  "--spring.profiles.active=postgres"
  "--spring.web.resources.static-locations=file:./frontend/,classpath:/static/"
)

if [[ "${FOREGROUND}" == "true" ]]; then
  cd "${BUNDLE_ROOT}"
  exec "${JAVA_CMD}" "${ARGS[@]}"
fi

STDOUT_LOG="${LOGS_DIR}/backend-stdout.log"
STDERR_LOG="${LOGS_DIR}/backend-stderr.log"

cd "${BUNDLE_ROOT}"
nohup "${JAVA_CMD}" "${ARGS[@]}" >>"${STDOUT_LOG}" 2>>"${STDERR_LOG}" &
BACKEND_PID=$!
printf '%s\n' "${BACKEND_PID}" > "${PID_FILE}"

if ! wait_for_backend_ready "${GuardianServerPort}"; then
  if kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
  echo "Backend did not become ready on port ${GuardianServerPort}. Check ${STDERR_LOG}" >&2
  tail -n 20 "${STDERR_LOG}" 2>/dev/null || true
  exit 1
fi

echo "System started. Local URL=http://127.0.0.1:${GuardianServerPort}"
echo "If network policy allows, access from browser: http://<server-ip>:${GuardianServerPort}"
