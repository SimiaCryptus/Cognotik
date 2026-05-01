---
transforms: 
  - ../story/([^./]+)c\.md -> ../story/$1c.wav
  - ../story/([^./]+)\.wav -> ../story/$1c.wav
related:
   - ../story/audio_style.md
task_type: GenerateAudio
---