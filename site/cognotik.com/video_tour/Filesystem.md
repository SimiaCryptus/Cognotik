# Filesystem Access in Cognotic Sessions

All sessions have a file system backing them that can be accessed.

## Accessing the Session File System

If you go to the URL — here's a previous run, for example, that we generated a comic book from — if we open in a new tab that same URL but remove the web page part so that we just access the root directory of that session, we can access the file system itself for that session.

## File System Features

This comes with a number of interesting features:

### Download as ZIP

We can download the entire directory as a zip file.

### Viewing Files

- **Markdown files** can be viewed in several ways:
  - As raw markdown (which will trigger a download)
  - As dynamically rendered HTML
  - As plain text (handy if you want to view the markdown source without triggering a download in your browser)
  - As dynamically rendered PDFs
- **HTML files** can be viewed directly in the browser

## Built-in Git Support

The file system has built-in Git support. You can view the current status and commit the directory. Git is a version control system for file systems — it's basically like a time machine so that you can track all of the file changes and, if needed, roll back.

## A Powerful Interface

Accessing the root file system for any given session via this interface gives you a powerful backdoor into the system functionality and provides a good level of hackability and transparency for any Cognotic applications.

## Physical File Location

The physical location of the file system is shown in the interface. If you want, you can mount this with a development environment, open it in the file system explorer, or do whatever you need.
