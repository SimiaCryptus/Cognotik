We need to be able to resize the code window/terminal split - and collapse terminal especially when no terminal is open.

"autofix" should appear on all items "run" appears on

http://127.0.0.1:8081/files/root/.fsapi/v1/file?path=%2Ftest.html
cannot be opened in a new tab - it results in a file download
the ui should serve html files, especially in "open in new tab", as the legacy format e.g. `http://127.0.0.1:8081/files/root/test.html` since that format uses non-query paths and is more user/seo friendly



# Fileserver

can only resize the terminal area when it is open. if no terminal is open, this area should be auto-collapsed


# Agent UI

need themeing sync with fileserver ui via theme ui parameter

BUG: all messages from the server currently have a reply ui appended to them (input textbox and send button) - remove these! when needed, input dialog elements are injected by the websocket server messages.
The collapse button to minimize the input ui does not work
We need support for the pending progress status (spinner when server is processing) which is a property of the messages sent (ie `<div class="spinner-border" role="status"><span class="sr-only">Loading...</span></div>`)

