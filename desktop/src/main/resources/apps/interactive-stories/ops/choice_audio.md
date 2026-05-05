---
template_vars:
  CHOICE: a
transforms:
  - ../story/([^./]+){{CHOICE}}\.md -> ../story/$1{{CHOICE}}.wav
  - ../story/([^./]+)\.wav -> ../story/$1{{CHOICE}}.wav
related:
   - ../story/audio_style.md
task_type: GenerateAudio
---