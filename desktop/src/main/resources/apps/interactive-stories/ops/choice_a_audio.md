---
transforms: 
  - ../story/([^./]+)a\.md -> ../story/$1a.wav
  - ../story/([^./]+)\.wav -> ../story/$1a.wav
related:
   - ../story/audio_style.md
task_type: GenerateAudio
---