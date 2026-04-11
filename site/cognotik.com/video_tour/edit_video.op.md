---
transforms: transcripts/([^/\.]+)\.srt -> edit_$1.sh
related:
  - files.txt
  - README.md
---

* Create an edit script for the str file given
* Before creating the edit script, read the srt file and determine the edits needed
* The edit script should contain the ffmpeg commands to make the necessary edits to the video file
* The output video file should be named <original_name>_edit.mp4
* Also write out a transcripts/<original_name>_edit.txt file that contains the edited transcript, which should match the edits made to the video file