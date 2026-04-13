# Cognotik Interactive Video Tour

An interactive, chapter-based video tour showcasing the features and capabilities of Cognotik. Visitors can browse individual chapters or play through the entire tour sequentially.

## Overview

The video tour presents seven guided walkthroughs covering installation, configuration, and key features of Cognotik:

1. **💻 Installing on Windows** — Full setup process from download to first launch
2. **🔌 Installing Plugins** — Extending capabilities via the plugin marketplace
3. **🧙 System Wizard** — Configuring core settings and AI model connections
4. **📁 Filesystem Access** — Session filesystem, downloads, markdown rendering, and Git version control
5. **🎨 Comic Book Generator** — Generating a full comic book from script to rendered HTML pages
6. **🧮 Philosophical Calculator** — AI-powered app generation demo with philosophical commentary
7. **🏭 WebApp Factory** — Generating complete web applications from natural language descriptions

## Project Structure

```
video_tour/
├── index.html              # Main HTML entry point
├── styles.css              # Stylesheet for the tour UI
├── tour.js                 # Application logic (player, navigation, state)
├── tour-data.js            # Chapter definitions (metadata, file paths, transcripts)
├── marked.min.js           # Markdown parser for rendering transcripts
├── *.md                    # Markdown transcript files for each chapter
├── edit/                   # Edited video files (.mp4)
├── source/                 # Original source video files
│   ├── audio/              # Extracted audio (MP3)
│   ├── transcripts/        # Transcription output (JSON, TXT, SRT)
│   └── segments/           # VAD segmentation output (JSON, TXT, SRT)
├── ops/                    # Operation specs for video processing
│   ├── edit_video.op.md    # Edit script generation spec
│   ├── video_composite.op.md  # Video compilation spec
│   └── planscript_revision.op.md  # Transcript-to-planscript spec
├── scripts/                # Build and processing scripts
│   ├── install_deps.sh     # Dependency installer
│   ├── install_vad_deps.sh # VAD Python dependency installer
│   ├── extract_transcripts.sh  # AWS Transcribe pipeline
│   ├── segment_audio.sh    # VAD silence segmentation
│   ├── normalize_audio.sh  # EBU R128 audio normalization
│   └── runall_edits.sh     # Run all edits + normalize
└── edit_*.sh               # Per-video ffmpeg edit scripts
```

## Features

- **Chapter-based navigation** — Browse and jump between chapters via sidebar or overview grid
- **Play All mode** — Autoplay through all chapters sequentially
- **Progress tracking** — Visual progress bar with step indicators
- **Transcript support** — Expandable markdown transcripts rendered inline for each chapter
- **End-of-chapter overlay** — Replay or advance to the next chapter when a video ends
- **Responsive layout** — Works across desktop and mobile viewports

## Usage

Serve the `video_tour/` directory via any static file server. The tour loads chapter data from `tour-data.js` and plays videos from the `edit/` directory.

```bash
# Example: serve locally with Python
cd site/cognotik.com/video_tour
python -m http.server 8000
```

Then open `http://localhost:8000` in a browser.

## Video Processing Workflow

See [scripts/README.md](scripts/README.md) for detailed documentation on the video processing pipeline, including:

1. **Dependency installation** — `install_deps.sh`
2. **Audio segmentation** — `segment_audio.sh` (VAD-based speech/silence detection)
3. **Transcription** — `extract_transcripts.sh` (AWS Transcribe)
4. **Editing** — Per-video `edit_*.sh` scripts with ffmpeg
5. **Normalization** — `normalize_audio.sh` (EBU R128 loudness leveling)
6. **Batch processing** — `runall_edits.sh` (run all edits + normalize)

## Dependencies

- A modern web browser with HTML5 video support
- Video files in H.264/MP4 format in the `edit/` directory
- [marked.min.js](https://github.com/markedjs/marked) for transcript rendering (included)

## License

&copy; Cognotik