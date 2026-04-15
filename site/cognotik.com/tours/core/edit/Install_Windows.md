# 💻 Installing Cognotik Desktop on Windows

This guide walks through the full process of downloading, installing, configuring, and testing Cognotik Desktop on a Windows machine.

---

## Downloading the Installer

There are two ways to get the Cognotik Desktop installer:

- **From the website** — Go to [cognotic.com](https://cognotic.com) and click **Download for Windows**.
- **From GitHub** — Navigate to the GitHub repository's **Releases** page to find all available installer artifacts.

Save the installer file when prompted. Because the file may not be commonly downloaded, Windows SmartScreen may flag it — you may need to manually choose to **keep the file** before proceeding.

---

## Installing the Application

Run the downloaded installer and follow the standard Windows installation prompts. The process is straightforward — install it like any typical desktop application.

---

## Launching Cognotik

Cognotik is a **server-based application**. When you launch it from the Start menu, it starts a local server rather than opening a window directly.

1. Open the **Start menu** and find **Cognotik**.
2. Run the application — this starts the background server.
3. Go to the **taskbar**, locate the **Cognotik server icon**, and click it to open the web UI in your browser.

---

## Creating a Local Login

The browser UI requires a local login. This login is used to **secure your API credentials** and is stored locally on your system.

- If you don't already have an account, you can register one directly from the login page.
- **Weak passwords are allowed**, but spaces are **not permitted**.
- After registering, a **confirmation dialogue** will appear to prevent unauthorized users (e.g., someone over the internet) from creating accounts on your local service. Click **Yes** to confirm.

> **Note:** The confirmation dialogue may not always appear in the foreground. If you don't see it, use **Alt+Tab** to find it.

---

## Adding an API Key

Once logged in, you need to configure an AI provider:

1. Navigate to **Settings**.
2. Select **Add Provider**.
3. Cognotik supports multiple providers, but **Anthropic** and **Gemini** are the primarily recommended options.
4. Enter your API key and save.

---

## Testing the Installation

After adding your API key:

1. **Refresh the page** to pick up the new configuration.
2. Verify that models are discovered — for example, you should see models like **Haiku** listed under Anthropic.
3. Send a test message in the chat to confirm everything is working end to end.

Once you receive a response, Cognotik Desktop is **fully installed, configured, and tested** — you're ready to start using the applications.