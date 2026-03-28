#!/bin/bash
set -euo pipefail

# Central entrypoint for project tests
# All test operations should route through this script
# to ensure consistent environment and error handling.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Tests started at $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="

if [ ! -d "node_modules" ]; then
  echo "ERROR: node_modules not found. Run install.sh first." >&2
  exit 1
fi

npm run test

TEST_EXIT=$?

if [ $TEST_EXIT -eq 0 ]; then
  echo "=== Tests passed ==="
else
  echo "=== Tests failed with exit code $TEST_EXIT ===" >&2
fi

exit $TEST_EXIT