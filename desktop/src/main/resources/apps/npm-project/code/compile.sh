#!/bin/bash
set -euo pipefail

# Central entrypoint for project build
# All build operations should route through this script
# to ensure consistent environment and error handling.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Build started at $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="

if [ ! -d "node_modules" ]; then
  echo "ERROR: node_modules not found. Run install.sh first." >&2
  exit 1
fi

npm run build

BUILD_EXIT=$?

if [ $BUILD_EXIT -eq 0 ]; then
  echo "=== Build succeeded ==="
else
  echo "=== Build failed with exit code $BUILD_EXIT ===" >&2
fi

exit $BUILD_EXIT