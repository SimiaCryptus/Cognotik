# 📁 Filesystem Access

Every Cognotik session is backed by an accessible file system. By navigating to the session URL and removing the web page path — leaving just the root directory — you can access the file system directly. This provides a powerful interface for browsing, downloading, and managing session files.

## Accessing the Session File System

Each session has a unique URL. To access the underlying file system, simply strip the page-specific portion of the URL so that you're pointing at the root directory of that session. This opens a browsable file listing with a number of built-in features.

## Downloading Files

The entire session directory can be downloaded as a **ZIP file** directly from the file system interface — useful for archiving or working with session output offline.

## Viewing Files

The file system interface supports multiple rendering modes for different file types:

- **Markdown files** can be:
  - Viewed as raw markdown
  - Dynamically rendered as HTML
  - Displayed as plain text (in a way that won't trigger a browser download)
  - Dynamically rendered as PDF
- **HTML files** can be viewed directly in the browser

This flexibility makes it easy to inspect, review, and share session output in whatever format is most convenient.

## Built-in Git Support

The file system has built-in **Git** version control support. Git acts as a time machine for the file system, tracking all file changes and enabling rollback to previous states when needed. You can view the repository status and manage commits directly through the interface.

## Physical File Location

The interface displays the physical path of the session's file system on disk. This means you can:

- **Mount the directory** in a development environment
- **Open it in a file system explorer**
- **Integrate with external tools** for editing or automation

## Key Takeaway

The root file system interface provides a powerful backdoor into Cognotik's system functionality, offering a high level of **hackability** and **transparency** for any Cognotik application.