# Video Tour Transcription Pipeline

This pipeline extracts audio from video files, uploads them to AWS S3, and uses
AWS Transcribe to generate text transcripts and SRT subtitle files.

## Overview

The pipeline performs the following steps:

1. **Extract audio** — Uses `ffmpeg` to extract mono 16 kHz MP3 audio from each video listed in `files.txt`.
2. **Upload to S3** — Uploads the extracted audio files to a configured S3 bucket.
3. **Start transcription** — Kicks off AWS Transcribe jobs for each audio file.
4. **Poll & download** — Polls for job completion, then downloads the resulting JSON, extracts plain text (`.txt`), and
   generates SRT subtitles (`.srt`).
5. **Cleanup** — Optionally deletes the uploaded audio files from S3.

## Prerequisites

- **ffmpeg** — for audio extraction
- **AWS CLI v2** — for S3 and Transcribe operations
- **jq** — for JSON parsing
- **python3** — for SRT subtitle generation (optional but recommended)

Run the dependency installer to set everything up:

|```
./install_deps.sh
|```

### AWS Configuration

You must have valid AWS credentials configured:

|```
aws configure
|```

#### Required IAM Permissions

The IAM user or role must have the following permissions:

| Service        | Actions                                                                                                   |
|----------------|-----------------------------------------------------------------------------------------------------------|
| **S3**         | `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`                                                         |
| **Transcribe** | `transcribe:StartTranscriptionJob`, `transcribe:GetTranscriptionJob`, `transcribe:DeleteTranscriptionJob` |
| **STS**        | `sts:GetCallerIdentity` (used for credential validation)                                                  |

## Usage

|```
TRANSCRIBE_S3_BUCKET=my-bucket ./extract_transcripts.sh
|```

### Environment Variables

| Variable                   | Default                    | Description                                          |
|----------------------------|----------------------------|------------------------------------------------------|
| `TRANSCRIBE_S3_BUCKET`     | *(required)*               | S3 bucket name for temporary audio storage           |
| `TRANSCRIBE_S3_PREFIX`     | `video-tour-transcription` | S3 key prefix for uploaded audio files               |
| `AWS_REGION`               | `us-east-1`                | AWS region for S3 and Transcribe                     |
| `TRANSCRIBE_LANGUAGE_CODE` | `en-US`                    | Language code for transcription                      |
| `TRANSCRIBE_POLL_INTERVAL` | `15`                       | Seconds between polling for job completion           |
| `TRANSCRIBE_CLEANUP_S3`    | `true`                     | Whether to delete S3 audio files after transcription |

## File Structure

|```
video_tour/
├── README.md                  # This file
├── install_deps.sh            # Dependency installer (ffmpeg, aws-cli, jq)
├── extract_transcripts.sh     # Main transcription pipeline script
├── files.txt                  # List of video files to process (one per line)
├── *.mp4                      # Source video files
├── audio/                     # Extracted audio files (generated)
│   └── *.mp3
└── transcripts/               # Transcription output (generated)
    ├── *.json                 # Raw AWS Transcribe JSON response
    ├── *.txt                  # Plain text transcript
    └── *.srt                  # SRT subtitle file
|```

## `files.txt` Format

List one video filename per line. Empty lines and lines starting with `#` are ignored:

|```

# Main demo videos

Comic_Generator.mp4
Filesystem.mp4
Install_Windows.mp4
Philosophical_Calculator.mp4
Plugin_Install.mp4
Sys_Wizard.mp4
WebApp_Factory.mp4
|```

## Output

After a successful run, the `transcripts/` directory will contain three files per video:

- **`<name>.json`** — Full AWS Transcribe response with word-level timestamps and confidence scores.
- **`<name>.txt`** — Plain text transcript.
- **`<name>.srt`** — SRT subtitle file with timed captions (max 10 words or 5 seconds per segment).

## Idempotency

The script is designed to be re-run safely:

- **Audio extraction** is skipped if the `.mp3` file already exists in `audio/`.
- **Transcription jobs** are skipped if the `.txt` file already exists in `transcripts/`.
- If all transcripts already exist, the script exits early without starting any AWS jobs.

## Troubleshooting

| Problem                                     | Solution                                                                                                    |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `ERROR: S3 bucket not set`                  | Export `TRANSCRIBE_S3_BUCKET` before running the script                                                     |
| `ERROR: 'ffmpeg' is not installed`          | Run `./install_deps.sh`                                                                                     |
| `ERROR: AWS credentials are not configured` | Run `aws configure` or set `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`                                    |
| `[WARN] Video not found`                    | Ensure the video files listed in `files.txt` exist in the script directory                                  |
| `[WARN] Could not generate SRT`             | Install `python3` for SRT subtitle generation                                                               |
| Transcription job `FAILED`                  | Check the failure reason in the output; common causes include unsupported audio formats or S3 access issues |


