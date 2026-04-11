#!/usr/bin/env bash
set -euo pipefail

SUBDIR_NAME=${1:-"edit"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${EDIT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}}"

# Copy recursively to a timestamped backup directory
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="${SCRIPT_DIR}/${SUBDIR_NAME}_${TIMESTAMP}"
mkdir -p "$BACKUP_DIR"
cp -r "$SRC_DIR/"* "$BACKUP_DIR/"
echo "Backup of '$SRC_DIR' created at '$BACKUP_DIR'"

