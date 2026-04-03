#!/usr/bin/env bash
# Full deploy script for the Patreon auth Lambda
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAMBDA_DIR="$SCRIPT_DIR/../lambda"
TF_DIR="$SCRIPT_DIR/../terraform"

# ── 1. Install dependencies & package ────────────────────────────────────────
echo "==> Installing Lambda dependencies..."
cd "$LAMBDA_DIR"
npm ci --omit=dev

echo "==> Packaging Lambda zip..."
zip -qr lambda.zip index.js node_modules package.json
echo "    lambda.zip created ($(du -sh lambda.zip | cut -f1))"

# ── 2. Deploy infrastructure ──────────────────────────────────────────────────
echo "==> Running Terraform..."
cd "$TF_DIR"
terraform init -input=false
terraform apply -input=false -auto-approve

# ── 3. Capture outputs ────────────────────────────────────────────────────────
KMS_KEY_ID=$(terraform output -raw kms_key_id)
API_ENDPOINT=$(terraform output -raw api_endpoint)

echo ""
echo "✅  Deploy complete"
echo "    API endpoint : $API_ENDPOINT"
echo "    KMS key ID   : $KMS_KEY_ID"
echo ""
echo "Endpoints:"
echo "    OAuth start  : $API_ENDPOINT/auth"
echo "    OAuth callback: $API_ENDPOINT/callback"
echo "    Verify token : $API_ENDPOINT/verify"
echo "    Live check   : $API_ENDPOINT/verify/live"
echo ""
echo "To encrypt a secret:"
echo "    $SCRIPT_DIR/encrypt_secret.sh $KMS_KEY_ID 'your-secret-value'"