---
transforms:
  - ../source/transcripts/([^/\.]+)\.srt -> ../review/$1.json
  - ../source/transcripts/([^/\.]+)\.json -> ../review/$1.json
  - ../source/scenes/([^/\.]+)\.scenes.txt -> ../review/$1.json
  - ../source/segments/([^/\.]+)\.srt -> ../review/$1.json
related:
  - scene_data.ts
---

* Create an editing plan based on the data given
* Chronologically analyze the transcript and segment data to identify key moments in the video
* For each key moment, determine the necessary edits (e.g., trim, cut, speed up silences)
* The output should be a json document that describes the editing decisions and structure for the video segment
* The json schema is given in scene_data.ts and should be followed closely to ensure compatibility
* Include transitions, intro/outro, and any other polishing steps to create a cohesive final video
* Trim the video at the start and end according to speech
* Edit out sections as appropriate for implicit cuts
* Edit out mis-spoken dialogue as appropriate
* Some longer periods of silence represent processing time and should not be cut out - instead, time-compress them and mute the audio
* Add an into and outro transition with billboard/thanks to make it pretty
