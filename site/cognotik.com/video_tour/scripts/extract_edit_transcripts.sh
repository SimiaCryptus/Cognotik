#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Configuration
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VIDEO_DIR="${SCRIPT_DIR}/edit"
AUDIO_DIR="${SCRIPT_DIR}/edit/audio"
TRANSCRIPT_DIR="${SCRIPT_DIR}/edit/transcripts"

# AWS Configuration — override via environment variables if needed
S3_BUCKET="${TRANSCRIBE_S3_BUCKET:-}"
S3_PREFIX="${TRANSCRIBE_S3_PREFIX:-video-tour-transcription}"
AWS_REGION="${AWS_REGION:-us-east-1}"
LANGUAGE_CODE="${TRANSCRIBE_LANGUAGE_CODE:-en-US}"

# Poll interval in seconds when waiting for transcription jobs
POLL_INTERVAL="${TRANSCRIBE_POLL_INTERVAL:-15}"

# Whether to clean up S3 audio files after transcription
CLEANUP_S3="${TRANSCRIBE_CLEANUP_S3:-true}"

# =============================================================================
# Validation
# =============================================================================
if [[ -z "$S3_BUCKET" ]]; then
  echo "ERROR: S3 bucket not set. Export TRANSCRIBE_S3_BUCKET or pass it as env var."
  echo ""
  echo "Usage:"
  echo "  TRANSCRIBE_S3_BUCKET=my-bucket ./extract_transcripts.sh"
  exit 1
fi

for cmd in ffmpeg aws jq; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is not installed. Run ./install_deps.sh first."
    exit 1
  fi
done


# Verify AWS credentials
if ! aws sts get-caller-identity --region "$AWS_REGION" &>/dev/null; then
  echo "ERROR: AWS credentials are not configured or are invalid."
  echo "Run 'aws configure' to set up credentials."
  exit 1
fi

# =============================================================================
# Setup
# =============================================================================
mkdir -p "$AUDIO_DIR" "$TRANSCRIPT_DIR"

echo "=== Video Transcription Pipeline ==="
echo "  S3 Bucket:     s3://${S3_BUCKET}/${S3_PREFIX}/"
echo "  AWS Region:    ${AWS_REGION}"
echo "  Language:      ${LANGUAGE_CODE}"
echo "  Video Dir:     ${VIDEO_DIR}"
echo "  Audio Dir:     ${AUDIO_DIR}"
echo "  Transcript Dir: ${TRANSCRIPT_DIR}"
echo ""

# =============================================================================
# Step 1: Extract audio from each video using ffmpeg
# =============================================================================
echo "--- Step 1: Extracting audio from videos ---"
declare -a JOB_NAMES=()
declare -a BASE_NAMES=()


for video_path in "${VIDEO_DIR}"/*; do
   [[ -f "$video_path" ]] || continue
   video_file="$(basename "$video_path")"
   base_name="${video_file%.*}"
  audio_file="${AUDIO_DIR}/${base_name}.mp3"


  BASE_NAMES+=("$base_name")

  if [[ -f "$audio_file" ]]; then
    echo "  [SKIP] Audio already exists: ${audio_file}"
  else
    echo "  [EXTRACT] ${video_file} -> ${audio_file}"
    ffmpeg -i "$video_path" \
      -vn \
      -acodec libmp3lame \
      -ar 16000 \
      -ac 1 \
      -q:a 5 \
      -y \
      "$audio_file" \
      -loglevel warning -stats
  fi
done

if [[ ${#BASE_NAMES[@]} -eq 0 ]]; then
  echo "ERROR: No video files were found to process."
  exit 1
fi

echo ""
echo "  Audio extraction complete. ${#BASE_NAMES[@]} file(s) processed."
echo ""

# =============================================================================
# Step 2: Upload audio files to S3
# =============================================================================
echo "--- Step 2: Uploading audio files to S3 ---"

for base_name in "${BASE_NAMES[@]}"; do
  audio_file="${AUDIO_DIR}/${base_name}.mp3"
  s3_key="${S3_PREFIX}/${base_name}.mp3"
  s3_uri="s3://${S3_BUCKET}/${s3_key}"

  echo "  [UPLOAD] ${audio_file} -> ${s3_uri}"
  aws s3 cp "$audio_file" "$s3_uri" \
    --region "$AWS_REGION" \
    --quiet
done

echo ""
echo "  Upload complete."
echo ""

# =============================================================================
# Step 3: Start AWS Transcribe jobs
# =============================================================================
echo "--- Step 3: Starting transcription jobs ---"

TIMESTAMP=$(date +%s)

for base_name in "${BASE_NAMES[@]}"; do
  s3_key="${S3_PREFIX}/${base_name}.mp3"
  s3_uri="s3://${S3_BUCKET}/${s3_key}"
  # Job names must be unique and match [a-zA-Z0-9._-]+
  job_name="transcript-${base_name}-${TIMESTAMP}"
  # Sanitize job name: replace spaces and special chars with hyphens
  job_name="$(echo "$job_name" | sed 's/[^a-zA-Z0-9._-]/-/g')"

  transcript_file="${TRANSCRIPT_DIR}/${base_name}.txt"
  if [[ -f "$transcript_file" ]]; then
    echo "  [SKIP] Transcript already exists: ${transcript_file}"
    continue
  fi

  echo "  [START] Job: ${job_name}"
  echo "          Source: ${s3_uri}"

  aws transcribe start-transcription-job \
    --region "$AWS_REGION" \
    --transcription-job-name "$job_name" \
    --language-code "$LANGUAGE_CODE" \
    --media-format "mp3" \
    --media "MediaFileUri=${s3_uri}" \
    --query 'TranscriptionJob.TranscriptionJobStatus' \
    --output text

  JOB_NAMES+=("${job_name}:${base_name}")
done

if [[ ${#JOB_NAMES[@]} -eq 0 ]]; then
  echo "  No new transcription jobs to start (all transcripts already exist)."
  echo ""
  echo "=== Done ==="
  exit 0
fi

echo ""
echo "  ${#JOB_NAMES[@]} transcription job(s) started."
echo ""

# =============================================================================
# Step 4: Poll for job completion and download results
# =============================================================================
echo "--- Step 4: Waiting for transcription jobs to complete ---"

declare -A PENDING_JOBS=()
for entry in "${JOB_NAMES[@]}"; do
  job_name="${entry%%:*}"
  base_name="${entry##*:}"
  PENDING_JOBS["$job_name"]="$base_name"
done

while [[ ${#PENDING_JOBS[@]} -gt 0 ]]; do
  echo "  [POLL] ${#PENDING_JOBS[@]} job(s) remaining... (checking every ${POLL_INTERVAL}s)"

  declare -a COMPLETED_KEYS=()

  for job_name in "${!PENDING_JOBS[@]}"; do
    base_name="${PENDING_JOBS[$job_name]}"

    status=$(aws transcribe get-transcription-job \
      --region "$AWS_REGION" \
      --transcription-job-name "$job_name" \
      --query 'TranscriptionJob.TranscriptionJobStatus' \
      --output text 2>/dev/null || echo "UNKNOWN")

    case "$status" in
      COMPLETED)
        echo "  [DONE] ${job_name} -> COMPLETED"

        # Get the transcript URI
        transcript_uri=$(aws transcribe get-transcription-job \
          --region "$AWS_REGION" \
          --transcription-job-name "$job_name" \
          --query 'TranscriptionJob.Transcript.TranscriptFileUri' \
          --output text)

        # Download the JSON result
        json_file="${TRANSCRIPT_DIR}/${base_name}.json"
        txt_file="${TRANSCRIPT_DIR}/${base_name}.txt"
        srt_file="${TRANSCRIPT_DIR}/${base_name}.srt"

        echo "  [DOWNLOAD] Transcript JSON -> ${json_file}"
        curl -fsSL "$transcript_uri" -o "$json_file"

        # Extract plain text transcript
        jq -r '.results.transcripts[0].transcript' "$json_file" > "$txt_file"
        echo "  [SAVED] Plain text -> ${txt_file}"

        # Generate SRT subtitle file from word-level timestamps
        if jq -e '.results.items' "$json_file" &>/dev/null; then
          echo "  [GENERATE] SRT subtitles -> ${srt_file}"
          python3 -c "
import json, sys

with open('${json_file}') as f:
    data = json.load(f)

items = data.get('results', {}).get('items', [])
srt_index = 1
words = []
start_time = None
MAX_WORDS = 10
MAX_DURATION = 5.0

def format_ts(seconds):
    s = float(seconds)
    h = int(s // 3600)
    m = int((s % 3600) // 60)
    sec = int(s % 60)
    ms = int((s - int(s)) * 1000)
    return f'{h:02d}:{m:02d}:{sec:02d},{ms:03d}'

def flush(words, start_time, end_time, idx):
    if not words:
        return idx
    print(idx)
    print(f'{format_ts(start_time)} --> {format_ts(end_time)}')
    print(' '.join(words))
    print()
    return idx + 1

end_time = None
for item in items:
    if item['type'] == 'pronunciation':
        word = item['alternatives'][0]['content']
        st = item.get('start_time')
        et = item.get('end_time')
        if st is not None:
            if start_time is None:
                start_time = st
            end_time = et
            words.append(word)
            if len(words) >= MAX_WORDS or (float(et) - float(start_time)) >= MAX_DURATION:
                srt_index = flush(words, start_time, end_time, srt_index)
                words = []
                start_time = None
    elif item['type'] == 'punctuation':
        punct = item['alternatives'][0]['content']
        if words:
            words[-1] += punct

srt_index = flush(words, start_time, end_time, srt_index)
" > "$srt_file" 2>/dev/null || echo "  [WARN] Could not generate SRT (python3 may not be available)"
        fi

        # Delete the transcription job (cleanup)
        aws transcribe delete-transcription-job \
          --region "$AWS_REGION" \
          --transcription-job-name "$job_name" 2>/dev/null || true

        COMPLETED_KEYS+=("$job_name")
        ;;
      FAILED)
        echo "  [FAIL] ${job_name} -> FAILED"
        failure_reason=$(aws transcribe get-transcription-job \
          --region "$AWS_REGION" \
          --transcription-job-name "$job_name" \
          --query 'TranscriptionJob.FailureReason' \
          --output text 2>/dev/null || echo "Unknown reason")
        echo "         Reason: ${failure_reason}"

        aws transcribe delete-transcription-job \
          --region "$AWS_REGION" \
          --transcription-job-name "$job_name" 2>/dev/null || true

        COMPLETED_KEYS+=("$job_name")
        ;;
      IN_PROGRESS)
        # Still running, do nothing
        ;;
      *)
        echo "  [WARN] ${job_name} -> ${status}"
        ;;
    esac
  done

  # Remove completed jobs from pending map
  for key in "${COMPLETED_KEYS[@]}"; do
    unset "PENDING_JOBS[$key]"
  done

  # Sleep before next poll if there are still pending jobs
  if [[ ${#PENDING_JOBS[@]} -gt 0 ]]; then
    sleep "$POLL_INTERVAL"
  fi
done

echo ""

# =============================================================================
# Step 5: Cleanup S3 audio files (optional)
# =============================================================================
if [[ "$CLEANUP_S3" == "true" ]]; then
  echo "--- Step 5: Cleaning up S3 audio files ---"
  for base_name in "${BASE_NAMES[@]}"; do
    s3_key="${S3_PREFIX}/${base_name}.mp3"
    s3_uri="s3://${S3_BUCKET}/${s3_key}"
    echo "  [DELETE] ${s3_uri}"
    aws s3 rm "$s3_uri" --region "$AWS_REGION" --quiet 2>/dev/null || true
  done
  echo ""
fi

# =============================================================================
# Summary
# =============================================================================
echo "=== Transcription Complete ==="
echo ""
echo "Output files:"
for base_name in "${BASE_NAMES[@]}"; do
  txt_file="${TRANSCRIPT_DIR}/${base_name}.txt"
  srt_file="${TRANSCRIPT_DIR}/${base_name}.srt"
  if [[ -f "$txt_file" ]]; then
    char_count=$(wc -c < "$txt_file" | xargs)
    echo "  [TXT] ${txt_file} (${char_count} chars)"
  fi
  if [[ -f "$srt_file" ]]; then
    line_count=$(wc -l < "$srt_file" | xargs)
    echo "  [SRT] ${srt_file} (${line_count} lines)"
  fi
done
echo ""
echo "Done."