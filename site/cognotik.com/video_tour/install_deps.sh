#!/usr/bin/env bash
set -euo pipefail

echo "=== Installing dependencies for video transcription ==="

# --- Install ffmpeg ---
if command -v ffmpeg &>/dev/null; then
  echo "[OK] ffmpeg is already installed: $(ffmpeg -version | head -1)"
else
  echo "[*] Installing ffmpeg..."
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    sudo apt-get update && sudo apt-get install -y ffmpeg
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    brew install ffmpeg
  else
    echo "[ERROR] Unsupported OS. Please install ffmpeg manually."
    exit 1
  fi
  echo "[OK] ffmpeg installed: $(ffmpeg -version | head -1)"
fi

# --- Install AWS CLI v2 ---
if command -v aws &>/dev/null; then
  AWS_VERSION=$(aws --version 2>&1)
  echo "[OK] AWS CLI is already installed: $AWS_VERSION"
else
  echo "[*] Installing AWS CLI v2..."
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    ARCH=$(uname -m)
    curl -fSL "https://awscli.amazonaws.com/awscli-exe-linux-${ARCH}.zip" -o /tmp/awscliv2.zip
    unzip -qo /tmp/awscliv2.zip -d /tmp
    sudo /tmp/aws/install
    rm -rf /tmp/awscliv2.zip /tmp/aws
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    curl -fSL "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o /tmp/AWSCLIV2.pkg
    sudo installer -pkg /tmp/AWSCLIV2.pkg -target /
    rm -f /tmp/AWSCLIV2.pkg
  else
    echo "[ERROR] Unsupported OS. Please install AWS CLI v2 manually."
    exit 1
  fi
  echo "[OK] AWS CLI v2 installed: $(aws --version)"
fi

# --- Install jq (for JSON parsing) ---
if command -v jq &>/dev/null; then
  echo "[OK] jq is already installed: $(jq --version)"
else
  echo "[*] Installing jq..."
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    sudo apt-get update && sudo apt-get install -y jq
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    brew install jq
  else
    echo "[ERROR] Unsupported OS. Please install jq manually."
    exit 1
  fi
  echo "[OK] jq installed: $(jq --version)"
fi

echo ""
echo "=== All dependencies installed ==="
echo ""
echo "Make sure to configure AWS credentials before running extract_transcripts.sh:"
echo "  aws configure"
echo ""
echo "Required IAM permissions:"
echo "  - s3:PutObject, s3:GetObject, s3:DeleteObject"
echo "  - transcribe:StartTranscriptionJob, transcribe:GetTranscriptionJob"