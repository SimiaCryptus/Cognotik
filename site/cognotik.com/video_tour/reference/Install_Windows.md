---
specifies:
  - ../planscripts/Install_Windows.md
documents: 
  - ../Install_Windows.md
---

* This file records information specific to the Windows installation demo.
* The `planscripts/` files contain the script for the demo, and are used to generate the video.

## Demo Overview

The Windows installation demo video walks through the full process of installing and configuring Cognotic Desktop on Windows. It covers the following steps:

1. **Downloading the Installer** — From [cognotic.com](https://cognotic.com) or from GitHub Releases.
2. **Installing the Application** — Standard desktop application install, with a note about manually keeping the file since it may not be commonly downloaded (Windows SmartScreen).
3. **Configuring Cognotic**
    - Launching from the Start menu (starts the server).
    - Opening the web UI via the taskbar server icon.
    - Creating a local login (used to secure API credentials).
      - Weak passwords are allowed but warned about; spaces are not permitted during registration.
      - A confirmation dialogue is shown to prevent unauthorized remote registration (may need Alt+Tab to find it).
4. **Adding an API Key**
    - Navigate to Settings → Add Provider.
    - Anthropic is the primarily recommended provider, though multiple providers are supported.
    - Enter the API key and save.
5. **Testing the Installation**
    - Refresh the page after adding the API key.
    - Verify that models are discovered (e.g., Haiku from Anthropic).
    - Send a test message to confirm full functionality.

## Notes

* The application is server-based: launching from the Start menu starts a local server, and the UI is accessed through a browser.
* The confirmation dialogue during registration has a known UX issue where it may not come to the foreground automatically.