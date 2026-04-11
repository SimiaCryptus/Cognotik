---
transforms:
  - ../source/transcripts/([^/\.]+)\.srt -> ../review/$1.md
  - ../source/segments/([^/\.]+)\.srt -> ../review/$1.md
---

* Create an editing plan based on the data given
* Chronologically analyze the transcript and segment data to identify key moments in the video
* For each key moment, determine the necessary edits (e.g., trim, cut, speed up silences)
* The output should be a markdown document that describes the editing decisions and structure for the video segment
* Include transitions, intro/outro, and any other polishing steps to create a cohesive final video
* Trim the video at the start and end according to speech
* Edit out sections as appropriate for implicit cuts
* Edit out mis-spoken dialogue as appropriate
* Some longer periods of silence represent processing time and should not be cut out - instead, time-compress them and mute the audio
* Add a into and outro transition with billboard/thanks to make it pretty
