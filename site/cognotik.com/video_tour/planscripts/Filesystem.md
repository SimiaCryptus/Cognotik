# Filesystem Access Demo Script

## Introduction

> **Narration:**
> Every Cognotic session has a file system backing it that can be accessed directly through your browser.

**Action:** Open a browser showing a previous session URL (e.g., a comic book generation run).

---

## Accessing the Session File System

> **Narration:**
> Here's a previous run — for example, one where we generated a comic book. If we open that same URL in a new tab but
> remove the webpage portion, we can access the root directory of that session directly.

**Action:** Copy the session URL, open a new tab, and trim the URL to the root directory path. Press Enter to load the
file system view.

---

## Downloading the Directory

> **Narration:**
> This interface comes with a number of useful features. First, we can download the entire session directory as a zip
> file.

**Action:** Click the "Download as ZIP" button to demonstrate the zip download functionality.

---

## Viewing Files

### Markdown Files

> **Narration:**
> We can view markdown files in several ways. Clicking a markdown file directly will trigger a download of the raw
> markdown. But we can also view markdown files rendered dynamically as HTML.

**Action:** Click on a `.md` file to show the download behavior. Then use the HTML rendering option to display the
markdown as a formatted HTML page.

> **Narration:**
> We can also view them as plain text — this is handy if you want to see the markdown source without triggering a
> download in your browser.

**Action:** Use the "view as text" option to display the raw markdown source inline in the browser.

> **Narration:**
> Finally, we can view markdown files as PDFs. This performs a dynamic rendering of the markdown into PDF format.

**Action:** Use the "view as PDF" option to demonstrate the PDF rendering.

### HTML Files

> **Narration:**
> HTML files can be viewed directly in the browser, of course.

**Action:** Click on an `.html` file to open it in the browser.

---

## Built-in Git Support

> **Narration:**
> The file system also has built-in Git support. We can view the current status of the directory and commit changes. If
> you're not familiar, Git is a version control system — essentially a time machine for your files. It tracks all changes
> and lets you roll back if needed.

**Action:** Navigate to the Git section of the interface. Show the current status view, then demonstrate the commit
functionality.

---

## Physical File Location

> **Narration:**
> The physical location of the file system on disk is displayed in the interface. This means you can mount it in a
> development environment, open it in your file system explorer, or do whatever you need with it directly.

**Action:** Point out the physical path shown in the interface. Optionally demonstrate opening the path in a file
explorer or IDE.

---

## Closing

> **Narration:**
> Accessing the root file system for any session through this interface gives you a powerful backdoor into the system's
> functionality. It provides a great level of hackability and transparency for any Cognotic application. I hope this
> functionality is useful to you.

**Action:** Return to the file system root view for a final overview before ending the demo.