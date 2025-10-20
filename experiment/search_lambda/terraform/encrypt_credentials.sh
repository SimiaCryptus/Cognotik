#!/bin/bash
 # encrypt_credentials.sh

 KMS_KEY_ID="205fdb46-3001-4172-a8fc-f3268b9b0caa"  # Get from terraform output

 # Encrypt API key
 ENCRYPTED_API_KEY=$(aws kms encrypt \
  --key-id "$KMS_KEY_ID" \
  --plaintext fileb://<(echo -n "mykey") \
  --query CiphertextBlob \
  --output text)

 # Encrypt Engine ID
 ENCRYPTED_ENGINE_ID=$(aws kms encrypt \
  --key-id "$KMS_KEY_ID" \
  --plaintext fileb://<(echo -n "mykey") \
  --query CiphertextBlob \
  --output text)

 echo "Add these to your terraform.tfvars:"
 echo "encrypted_google_api_key = \"$ENCRYPTED_API_KEY\""
 echo "encrypted_search_engine_id = \"$ENCRYPTED_ENGINE_ID\""