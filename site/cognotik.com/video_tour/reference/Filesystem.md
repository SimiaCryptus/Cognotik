---
specifies:
  - ../planscripts/Filesystem.md
documents: 
  - ../Filesystem.md
---

* This file records information specific to the Filesystem Access demo.
* The `planscripts/` files contain the script for the demo, and are used to generate the video.

## Demo Overview

This demo covers **Filesystem Access in Cognotic Sessions**, showcasing how every session is backed by an accessible file system.

## Key Topics Covered

1. **Accessing the Session File System** — Navigating to the root directory of a session URL to access its file system directly.
2. **Download as ZIP** — Downloading the entire session directory as a zip file.
3. **Viewing Files** — Multiple rendering options for files:
    - Markdown files: raw markdown, dynamically rendered HTML, plain text, and dynamically rendered PDFs.
    - HTML files: direct browser viewing.
4. **Built-in Git Support** — Viewing status and committing the directory; version control with rollback capability.
5. **Physical File Location** — The interface displays the physical path, enabling mounting with a development environment or file system explorer.

## Key Message

The root file system interface provides a powerful backdoor into system functionality, offering hackability and transparency for any Cognotic applications.