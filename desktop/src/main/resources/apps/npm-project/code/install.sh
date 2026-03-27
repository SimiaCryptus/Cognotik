#!/bin/bash
set -euo pipefail

# Central entrypoint for dependency installation
# All install operations should route through this script
# to ensure consistent environment and error handling.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Install started at $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="

if [ ! -f "package.json" ]; then
  echo "ERROR: package.json not found in project directory." >&2
  exit 1
fi

npm install

INSTALL_EXIT=$?

if [ $INSTALL_EXIT -eq 0 ]; then
  echo "=== Install succeeded ==="
  echo "node_modules exists: $([ -d node_modules ] && echo yes || echo no)"
  echo "package-lock.json exists: $([ -f package-lock.json ] && echo yes || echo no)"
else
  echo "=== Install failed with exit code $INSTALL_EXIT ===" >&2
fi

exit $INSTALL_EXIT