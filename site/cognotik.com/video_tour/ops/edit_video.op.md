---
transforms:
  - ../review/([^/\.]+)\.md -> ../review/$1.js
---

* Create an edit script for the instructions in the md file
* Implement the script using NodeJS - if packages are needed, note the installation command in the comments at the top of the script
* The script should use the `ffmpeg` command line tool to perform the edits, and should be executable as a shell script
* The input video is in the `source/` directory and has the same name as the srt file (but with .mp4 extension)
* The output video file should be named `edit/<original_name>.mp4`
