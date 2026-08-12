Trying to analyze packages reports "no packages" (after requesting http://127.0.0.1:8081/files/root/.review/_files.json
 - this check is incorrect. it should query the available targets for the docops file)
note: this is maybe due to "Files to review" see below

We need to be able to customize, and autoconfigure, the file extensions currently hardcoded in the docops. this requires the index.html file to edit the docops files when needed.

"Files to review" should be a treeview of checkboxes driven by the file listing.
Add a similar "Packages to review" treeview, combined with the above via a tabbed pane


