#!/bin/bash
# encrypt_credentials.sh

KMS_KEY_ID="your-kms-key-id"  # Get from terraform output

# Encrypt API key
ENCRYPTED_API_KEY=$(aws kms encrypt \
  --key-id "$KMS_KEY_ID" \
  --plaintext "your-google-api-key" \
  --query CiphertextBlob \
  --output text)

# Encrypt Engine ID
ENCRYPTED_ENGINE_ID=$(aws kms encrypt \
  --key-id "$KMS_KEY_ID" \
  --plaintext "your-search-engine-id" \
  --query CiphertextBlob \
  --output text)

echo "Add these to your terraform.tfvars:"
echo "encrypted_google_api_key = \"$ENCRYPTED_API_KEY\""
echo "encrypted_search_engine_id = \"$ENCRYPTED_ENGINE_ID\""