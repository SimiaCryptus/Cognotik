---
transforms: 
  - ../story/([^./]+)b\.md -> ../story/$1b.mp3
  - ../story/([^./]+)\.mp3 -> ../story/$1b.mp3
related:
   - ../story/audio_style.md
task_type: GenerateAudio
---