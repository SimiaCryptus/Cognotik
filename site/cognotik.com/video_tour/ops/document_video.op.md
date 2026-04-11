---
transforms:
  - ../edit/transcripts/([^/\.]+)\.txt -> ../edit/$1.md
  - ../reference/([^/\.]+)\.md -> ../edit/$1.md
related:
  - ../README.md
---

* Based on transcript text and reference documentation for a video segment, write a markdown document documenting the video
* The document should be a formatted companion to the video, describing the content as a written presentation of the video
