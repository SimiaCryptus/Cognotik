---
transforms:
  - ../edit/transcripts/([^/\.]+)\.txt -> ../edit/$1.md
  - ../reference/([^/\.]+)\.md -> ../edit/$1.md
related:
  - ../README.md
---

* Create an edit script for the srt file given
* Before creating the edit script, read the srt file(s) and determine the edits needed
* The edit script should contain the ffmpeg commands to make the necessary edits to the video file
* The input video is in the `source/` directory and has the same name as the srt file (but with .mp4 extension)
* The output video file should be named `edit/<original_name>.mp4`
* Trim the video at the start and end according to speech
* Edit out sections as appropriate for implicit cuts
* Edit out mis-spoken dialogue as appropriate
* Some longer periods of silence represent processing time and should not be cut out - instead, time-compress them and mute the audio
* Add a into and outro transition with billboard/thanks to make it pretty
