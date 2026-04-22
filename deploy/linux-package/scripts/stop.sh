#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SCRIPT_ROOT}/common.sh"

BACKEND_STOPPED="$(stop_backend_process)"
POSTGRES_STOPPED="$(stop_postgres_process)"

echo "Backend stopped: ${BACKEND_STOPPED}"
echo "Embedded PostgreSQL stopped: ${POSTGRES_STOPPED}"
