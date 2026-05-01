---
transforms: 
  - ../story/([^./]+)b\.md -> ../story/$1b.wav
  - ../story/([^./]+)\.wav -> ../story/$1b.wav
related:
   - ../story/audio_style.md
task_type: GenerateAudio
---