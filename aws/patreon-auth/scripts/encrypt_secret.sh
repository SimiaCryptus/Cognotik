#!/usr/bin/env bash
# Usage: ./encrypt_secret.sh <kms-key-id> <plaintext-value>
# Prints the base64-encoded ciphertext ready to paste into terraform.tfvars
set -euo pipefail

KEY_ID="${1:?Usage: $0 <kms-key-id> <plaintext>}"
PLAINTEXT="${2:?Usage: $0 <kms-key-id> <plaintext>}"

aws kms encrypt \
  --key-id  "$KEY_ID" \
  --plaintext "fileb://<(printf '%s' "$PLAINTEXT")" \
  --query CiphertextBlob \
  --output text