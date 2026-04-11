# Video Tour AI Operations Pipeline

A collection of AI-driven operation files (`.op.md`) that define transforms for automating the video tour production
workflow.
These operations are designed to be executed by an AI agent to process raw video recordings into polished, publishable
video tour content.

## Overview

The ops pipeline bridges the gap between raw source recordings and finished video output.
Each `.op.md` file declares **input transforms** (source file patterns → output file patterns) and **instructions** that
guide an AI agent through the production task.

```
source/ recordings
    ↓
┌─────────────────────────────────────┐
│  planscript_revision.op.md          │  Rehearsal transcript → speaking script
│  edit_video.op.md                   │  SRT transcript → ffmpeg edit script
│  document_video.op.md               │  Transcript → documented edit markdown
│  video_composite.op.md              │  Edited clips → compiled video tour
└─────────────────────────────────────┘
    ↓
edit/ and final video_tour.mp4
```

## Operations

### `planscript_revision.op.md`

**Purpose:** Converts a raw spoken rehearsal transcript into a refined markdown speaking script for recording the final
demo.

**Transforms:**

| Input                         | Output                  |
|-------------------------------|-------------------------|
| `edit/transcripts/<name>.srt` | `planscripts/<name>.md` |
| `reference/<name>.md`         | `planscripts/<name>.md` |

**Behavior:**

- Reads the raw SRT transcript of a rehearsal recording
- Refines spoken language for clarity, conciseness, and engagement
- Produces a structured markdown script with narration and corresponding on-screen actions
- Preserves the essence and flow of the original demo while improving delivery
- Outputs a script suitable for teleprompter-style reading during final recording

---

### `edit_video.op.md`

**Purpose:** Generates a bash edit script containing ffmpeg commands to trim, cut, and polish a source video based on
its SRT transcript and segment analysis.

**Transforms:**

| Input                           | Output           |
|---------------------------------|------------------|
| `source/transcripts/<name>.srt` | `edit_<name>.sh` |
| `source/segments/<name>.srt`    | `edit_<name>.sh` |

**Behavior:**

- Reads the SRT transcript and/or VAD segment data for a source video
- Determines necessary edits: start/end trimming, implicit cut removal, mis-spoken dialogue removal
- Identifies longer silences (processing/loading time) and time-compresses them with muted audio rather than cutting
- Adds intro and outro transitions with billboard/title cards
- Outputs a self-contained bash script (`edit_<name>.sh`) with all ffmpeg commands
- Input video: `source/<name>.mp4` → Output video: `edit/<name>.mp4`

---

### `document_video.op.md`

**Purpose:** Creates a documented edit markdown file that describes the editing decisions and structure for a video
segment.

**Transforms:**

| Input                         | Output           |
|-------------------------------|------------------|
| `edit/transcripts/<name>.txt` | `edit/<name>.md` |
| `reference/<name>.md`         | `edit/<name>.md` |

**Behavior:**

- Reads transcript text and reference documentation for a video segment
- Analyzes the content to determine editing decisions
- Produces a markdown document describing the edit plan, including trim points, cuts, silence compression, and
  transitions
- Serves as both documentation and an intermediate representation for the editing pipeline

---

### `video_composite.op.md`

**Purpose:** Compiles all edited video segments into a single cohesive video tour experience.

**Specifies:** `compile_video_tour.sh`

**Related files:**

- `edit/transcripts/*.srt` — Transcript files for section ordering
- `files.txt` — File listing for composition order
- `README.md` — Project context

**Behavior:**

- Uses ffmpeg to concatenate edited video segments in the correct order
- Applies transition effects between sections for visual continuity
- Inserts visual section markers to delineate different parts of the tour
- References formatted markdown transcripts for section ordering and content alignment
- Outputs the final `video_tour.mp4`

## Transform Syntax

Each operation file uses frontmatter `transforms` to declare input-output mappings using regex patterns:

```yaml
transforms:
  - ../source/transcripts/([^/\.]+)\.srt -> ../edit_$1.sh
```

- The left side is a regex pattern matching input files
- The right side is the output path with `$1`, `$2`, etc. as capture group references
- Multiple transforms can be declared; the operation applies to any matching input

Operations may also declare:

- **`related`** — Additional context files the agent should read
- **`specifies`** — The specific output file the operation generates (for non-transform outputs)

## Workflow Integration

These operations are designed to work alongside the bash scripts in `../scripts/`:

```bash
# 1. Analyze and transcribe source videos (bash scripts)
./scripts/segment_audio.sh source
TRANSCRIBE_S3_BUCKET=my-bucket ./scripts/extract_transcripts.sh source

# 2. Generate speaking scripts from rehearsal transcripts (AI op)
#    planscript_revision.op.md: source transcripts → planscripts/*.md

# 3. Generate edit scripts from transcripts and segments (AI op)
#    edit_video.op.md: source SRTs → edit_*.sh

# 4. Execute edit scripts and normalize audio (bash scripts)
./scripts/runall_edits.sh

# 5. Document the edits (AI op)
#    document_video.op.md: edit transcripts → edit/*.md

# 6. Compile final video tour (AI op → bash script)
#    video_composite.op.md → compile_video_tour.sh
./compile_video_tour.sh
```

## Directory Context

```
video_tour/
├── ops/                        # This directory — AI operation definitions
│   ├── README.md               # This file
│   ├── planscript_revision.op.md
│   ├── edit_video.op.md
│   ├── document_video.op.md
│   └── video_composite.op.md
├── source/                     # Raw recordings and analysis output
│   ├── transcripts/            # SRT/TXT/JSON transcripts
│   └── segments/               # VAD segmentation data
├── edit/                       # Edited video files and transcripts
│   └── transcripts/            # Transcripts of edited videos
├── reference/                  # Reference documentation per segment
├── planscripts/                # Refined speaking scripts
├── scripts/                    # Bash automation scripts
├── edit_*.sh                   # Generated per-video edit scripts
├── compile_video_tour.sh       # Generated composite script
└── video_tour.mp4              # Final output
```