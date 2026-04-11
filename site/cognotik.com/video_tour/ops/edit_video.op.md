---
transforms: ../source/transcripts/([^/\.]+)\.srt -> ../edit_$1.sh
related:
  - files.txt
  - README.md
---

* Create an edit script for the srt file given
* Before creating the edit script, read the srt file and determine the edits needed
* The edit script should contain the ffmpeg commands to make the necessary edits to the video file
* The input video is in the `source/` directory and has the same name as the srt file (but with .mp4 extension)
* The output video file should be named `edit/<original_name>.mp4`
* Make sure to use the transcript to trim the start and end of the video to remove any unnecessary content, such as long pauses, irrelevant sections, or off-topic discussions
* Also write out a `<original_name>.md` file containing the edited transcript formatted as markdown
  * The markdown file should include a title, sections/timestamps, and cleaned-up prose suitable for reading
