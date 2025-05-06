# Cognotik Website (site/cognotik.com)

This directory contains the source code for the official Cognotik project website, hosted at [cognotik.com](https://cognotik.com) (or intended to be). It serves as the primary public-facing interface for the project, providing information, downloads, contribution guidelines, and community links.

This README is intended for developers working on or contributing to the website itself.

## Table of Contents

*   [Purpose](#purpose)
*   [Key Features (Website)](#key-features-website)
*   [Technology Stack](#technology-stack)
*   [Directory Structure](#directory-structure)
*   [Setup and Running Locally](#setup-and-running-locally)
*   [Key Scripts & Functionality Breakdown](#key-scripts--functionality-breakdown)
  *   [main.js](#mainjs)
  *   [github-api.js](#github-apijs)
  *   [download-ui.js](#download-uijs)
  *   [os-detection.js](#os-detectionjs)
  *   [feature-modal.js](#feature-modaljs)
  *   [logo-animation.js](#logo-animationjs)
*   [Styling](#styling)
*   [Assets](#assets)
*   [Deployment](#deployment)
*   [Contributing](#contributing)
*   [License](#license)

## Purpose

The website aims to:

1.  **Introduce Cognotik:** Explain what the project is, its goals, and key features.
2.  **Provide Downloads:** Offer easy access to the latest release builds, automatically detecting the user's OS where possible.
3.  **Engage the Community:** Direct users to GitHub for contributions, discussions, and issue reporting.
4.  **Showcase Functionality:** Demonstrate the tool's capabilities (e.g., via video).
5.  **Inform Stakeholders:** Provide information on monetization, investment, and privacy.
6.  **Maintain Brand Identity:** Present a consistent and professional look and feel for the project.

## Key Features (Website)

*   **Responsive Design:** Adapts to various screen sizes using CSS media queries (`styles/responsive.css`).
*   **Dynamic Download Button:**
  *   Detects user OS (`os-detection.js`).
  *   Fetches latest release info from the GitHub API (`github-api.js`).
  *   Provides the most relevant download asset or links to the releases page (`download-ui.js`).
  *   Includes caching via `localStorage` to reduce API calls.
  *   Provides user feedback during loading states (ARIA attributes updated).
*   **Interactive Logo Animation:** Uses SVG and JavaScript (`logo-animation.js`) to create an animated, multicolored gradient fill for the hero logo that responds to cursor movement using a plasma effect.
*   **Feature Details Modal:** "Read More" buttons on the features section open a modal window (`feature-modal.js`) displaying detailed content. Includes accessibility features like focus trapping and closing via Escape key.
*   **Smooth Scrolling:** Navigation links pointing to page sections (`#hash`) scroll smoothly (`main.js`).
*   **Mobile Navigation:** A toggle button (`.mobile-menu-toggle`) reveals the main navigation on smaller screens (`main.js`). Includes ARIA attributes for accessibility.
*   **Fixed Header:** The navigation bar stays fixed at the top and changes appearance slightly on scroll (`main.js`, `.fixed-top.scrolled`).
*   **Static Content Pages:** Includes dedicated pages for Home (`index.html`), Contributing (`contribute.html`), Monetization/Investment (`monetization.html`), and Privacy (`privacy.html`).
*   **SEO & Meta Tags:** Includes descriptive meta tags for SEO and social media sharing (`og:`, `twitter:`).

## Technology Stack

*   **HTML5:** Semantic HTML structure.
*   **CSS3:** Styling, layout (Flexbox, Grid), custom properties (variables), gradients, transitions, animations.
  *   Uses CSS Variables (`:root` in `styles.css`) for theming.
  *   Responsive design handled in `styles/responsive.css`.
*   **JavaScript (ES6+):** Vanilla JavaScript for all client-side interactivity. No external frameworks (like React, Vue, Angular) are used.
  *   DOM Manipulation
  *   Event Handling
  *   Asynchronous operations (`async`/`await`, `fetch`)
  *   `localStorage` for caching API data.
  *   SVG manipulation for animations.
*   **SVG:** Used for the main logo (`logo.svg`, `assets/favicon.svg`) and potentially inline icons. The logo SVG paths are embedded directly in HTML files and manipulated by JavaScript for animation.
*   **GitHub API:** Used to fetch release information (`https://api.github.com/repos/SimiaCryptus/Cognotik/releases`).
*   **Font Awesome:** Used for icons on some pages (e.g., `contribute.html`), linked via CDN.

## Directory Structure

```
site/cognotik.com/
├── assets/
│   ├── favicon.svg         # SVG Favicon (smaller version of logo)
│   ├── demo.mp4            # Demo video file (referenced in index.html)
│   ├── hero-pattern.svg    # Background pattern for hero section (referenced in styles.css)
│   ├── og-image.png        # Image for social media previews
│   └── video-poster.jpg    # Poster image for the demo video
├── scripts/
│   ├── download-ui.js      # Logic for updating the download button UI
│   ├── feature-modal.js    # Handles the feature details modal popup
│   ├── github-api.js       # Fetches release data from GitHub API, handles caching
│   ├── logo-animation.js   # Controls the interactive SVG logo animation
│   ├── main.js             # Main entry point for JS, initializes features, handles nav/menu
│   └── os-detection.js     # Detects the user's operating system
├── styles/
│   ├── responsive.css      # CSS Media queries for different screen sizes
│   └── styles.css          # Main stylesheet (variables, base styles, components)
├── contribute.html         # Contribution guidelines page
├── index.html              # Main landing page (Homepage)
├── logo.svg                # Main SVG logo file (paths embedded in HTML)
├── monetization.html       # Monetization and investor information page
└── privacy.html            # Privacy policy page
```

## Setup and Running Locally

Since this is a static website (HTML, CSS, JS), you don't need a complex build process. You just need a local web server to serve the files correctly (especially for handling root-relative paths and potentially avoiding CORS issues if any external APIs were called differently).

**Prerequisites:**

*   Git (to clone the repository)
*   A simple local web server. Options include:
  *   Python's built-in HTTP server
  *   Node.js with `http-server` (`npm install -g http-server`)
  *   VS Code with the "Live Server" extension

**Steps:**

1.  **Clone the main Cognotik repository** (if you haven't already):
    ```bash
    git clone https://github.com/SimiaCryptus/Cognotik.git
    cd Cognotik
    ```
2.  **Navigate to the website directory:**
    ```bash
    cd site/cognotik.com
    ```
3.  **Start a local web server** from within the `site/cognotik.com` directory:
  *   **Using Python 3:**
      ```bash
      python -m http.server 8000
      ```
  *   **Using Node.js `http-server`:**
      ```bash
      http-server -p 8000 -c-1 # -c-1 disables caching
      ```
  *   **Using VS Code Live Server:** Right-click on `index.html` and select "Open with Live Server".

4.  **Open your browser** and navigate to `http://localhost:8000` (or the port specified by your server).

## Key Scripts & Functionality Breakdown

### main.js

*   **Entry Point:** Acts as the main coordinator after the DOM is loaded.
*   **Initialization:** Calls initialization functions for other modules if they exist (e.g., `initLogoAnimation`, `updateDownloadUI`).
*   **Smooth Scrolling:** Attaches click listeners to navigation links (`nav a`, `.scroll-link`) with `href` starting with `#` and scrolls smoothly to the target element.
*   **Mobile Menu:** Handles toggling the visibility (`.active` class) of the mobile navigation menu and the toggle button itself. Manages ARIA attributes (`aria-expanded`, `aria-controls`) for accessibility. Closes the menu if a click occurs outside the menu or toggle button.
*   **Navbar Scroll Effect:** Adds/removes a `.scrolled` class to the `.fixed-top` navbar based on the window's scroll position (`window.scrollY > 50`), allowing for CSS styles to change the navbar appearance (e.g., background, shadow).

### github-api.js

*   **Purpose:** Interacts with the GitHub API to fetch release information for Cognotik.
*   **`fetchLatestRelease()`:**
  *   Checks `localStorage` for cached data (`CACHE_KEY`) first. Returns cached data if valid and not expired (`CACHE_EXPIRY`).
  *   If no valid cache, attempts to fetch from `releases/latest` endpoint.
  *   If `/latest` fails (e.g., pre-release is latest), it falls back to fetching the full `/releases` list and takes the first item (usually the most recent).
  *   Caches the fetched release data (the single release object) in `localStorage` with a timestamp.
  *   Returns the release object or `null` on error.
*   **`findDownloadAsset(release, os)`:**
  *   Takes a GitHub release object and a detected OS string ('windows', 'mac', 'linux').
  *   Defines preferred file extensions for each OS (`osExtensions`).
  *   Iterates through preferred extensions, looking for a matching asset (`asset.name.endsWith(ext)`).
  *   If no extension match, falls back to searching for OS keywords (`osKeywords`) within the asset names (`asset.name.toLowerCase().includes(keyword)`).
  *   Returns the first matching asset object or `null`.
*   **`checkCache()`, `cacheData()`:** Helper functions for managing the `localStorage` cache.
*   **`getGitHubReleasesUrl()`:** Returns the URL to the main releases page on GitHub.

### download-ui.js

*   **Purpose:** Manages the user interface elements related to downloading the application.
*   **Initialization (`updateDownloadUI()`):**
  *   Gets references to relevant DOM elements (`#detected-os`, `#latest-version`, `#download-button`, etc.).
  *   Sets initial "Checking..." text and disables the download button, setting appropriate ARIA `aria-busy` states.
  *   Calls `detectOS()` from `os-detection.js`.
  *   Updates the "Detected OS" text.
  *   If OS is 'unknown', sets the button to link directly to the GitHub releases page and exits.
  *   Calls `fetchLatestRelease()` from `github-api.js`.
  *   **On successful fetch:**
    *   Updates the "Latest Version" text.
    *   Calls `findDownloadAsset()` to get the appropriate download link for the OS.
    *   If an asset is found, updates the download button text, `href`, enables it, and sets the `download` attribute and a descriptive `aria-label`.
    *   If no asset is found, sets the button to link to the GitHub releases page.
  *   **On fetch error:** Sets the button to link to GitHub releases and updates text/ARIA attributes to indicate an error.
  *   Removes `aria-busy` attributes as states are resolved.
*   **Auto-initialization:** The `updateDownloadUI` function is called automatically on `DOMContentLoaded`.

### os-detection.js

*   **Purpose:** Detects the user's operating system based on browser `navigator` properties.
*   **`detectOS()`:**
  *   Checks `navigator.userAgent` and `navigator.platform` strings for keywords associated with Windows, macOS, and Linux.
  *   Includes checks for 'win', 'mac', 'darwin' (macOS), 'linux', 'x11'.
  *   Explicitly excludes 'android' from being detected as 'linux'.
  *   Returns 'windows', 'mac', 'linux', or 'unknown'.
*   **`getOSDisplayName()`:** A helper function to return a user-friendly name based on the OS code returned by `detectOS()`. (Note: This function isn't actually used in the provided `download-ui.js`, which has its own `osNames` map).

### feature-modal.js

*   **Purpose:** Handles the interactive modal dialog for displaying feature details.
*   **Initialization:** Runs on `DOMContentLoaded`.
*   **Event Listeners:**
  *   Attaches click listeners to all `.read-more-btn` elements.
  *   When clicked, it finds the corresponding feature title (`h3`), looks up the content in the `featureDetails` object.
  *   Populates the modal (`#modal-title`, `#modal-content`) with the details.
  *   Displays the modal (`modal.style.display = 'block'`) and prevents body scroll.
  *   Stores the previously focused element to restore focus on close.
  *   Sets initial focus to the close button (`.close-modal`).
  *   Adds a `keydown` listener to the modal for focus trapping (`trapFocus`).
*   **Closing the Modal (`closeModalHandler()`):**
  *   Hides the modal.
  *   Restores body scroll.
  *   Restores focus to the element that opened the modal.
  *   Removes the focus trap listener.
*   **Closing Mechanisms:**
  *   Clicking the close button (`.close-modal`).
  *   Clicking outside the modal content area (`window` click listener checking `event.target === modal`).
  *   Pressing the Escape key (`document` keydown listener).
*   **Focus Trapping (`trapFocus()`):**
  *   Listens for Tab key presses within the modal.
  *   Identifies all focusable elements inside the modal.
  *   If Tab is pressed on the last element, focus moves to the first.
  *   If Shift+Tab is pressed on the first element, focus moves to the last.

### logo-animation.js

*   **Purpose:** Creates a complex, interactive animation for the SVG logo in the hero section.
*   **Initialization (`initLogoAnimation()`):**
  *   Finds the `#hero-logo` SVG element.
  *   Injects SVG `<defs>` and a `<radialGradient id="animated-gradient">` if they don't exist.
  *   Defines multiple color stops for the gradient.
  *   Applies this gradient fill (`url(#animated-gradient)`) to all `<path>` elements within the logo SVG.
*   **Plasma Effect:**
  *   Uses a `generatePlasmaGrid()` function (implementing the Diamond-Square algorithm) to create a 2D grid of noise values.
  *   Uses `samplePlasma()` for bilinear interpolation to get a smooth value from the grid at given coordinates.
  *   The `plasma(x, y, z)` function samples this grid, using the `z` (time) parameter to shift the sampling coordinates, creating the animation effect.
*   **Animation Loop (`animateGradient()`):**
  *   Uses `requestAnimationFrame` for smooth animation.
  *   Increments a time variable `t`.
  *   **Auto-Animation (when `isUserInteracting` is false):** Animates the gradient's center (`cx`, `cy`) along a Lissajous curve path.
  *   **Color Animation:** Iterates through the gradient's `<stop>` elements. For each stop, it calculates a `plasmaValue` based on the stop's offset and the current time `t`. This value is mapped to an HSL hue, dynamically updating the `stop-color`.
*   **Mouse Interaction:**
  *   `pointermove`: Updates the gradient's center (`cx`, `cy`) to follow the mouse cursor position relative to the SVG's bounding box. Sets `isUserInteracting` to `true`. Clears the leave timeout.
  *   `pointerleave`: Sets a timeout (`leaveTimeoutId`) before setting `isUserInteracting` back to `false`, allowing the auto-animation to resume smoothly after a short delay.
  *   `pointerenter`: Sets `isUserInteracting` to `true` immediately and clears any pending leave timeout.

## Styling

*   **`styles.css`:** Defines the overall look and feel.
  *   Uses CSS Custom Properties (variables) extensively for colors, fonts, spacing, etc.
  *   Includes base resets and styles for common HTML elements.
  *   Defines styles for major components: header, navigation, hero section, buttons, features, video section, download UI, footer, modal.
  *   Implements visual effects like gradients, shadows, transitions.
  *   Includes `:focus-visible` styles for accessibility.
*   **`styles/responsive.css`:** Contains `@media` queries to adjust layout and styles for different screen sizes (tablets, mobiles). Overrides styles defined in `styles.css` as needed for smaller viewports.

## Assets

*   **`logo.svg`:** The main vector logo source file. Its paths are directly embedded in the HTML for easier JS manipulation (animation).
*   **`assets/favicon.svg`:** A version of the logo used as the site's favicon.
*   **`assets/og-image.png`:** Used for social media link previews.
*   **`assets/video-poster.jpg`:** Displayed before the demo video loads.
*   **`assets/demo.mp4`:** The product demo video.
*   **`assets/hero-pattern.svg`:** A subtle background pattern used in the hero section CSS.

## Deployment

Deployment details are not specified within this codebase. However, as a static site, deployment typically involves:

1.  Copying the contents of the `site/cognotik.com` directory (HTML, CSS, JS, assets) to a web server or static hosting provider.
2.  Common providers include GitHub Pages, Netlify, Vercel, AWS S3, etc.
3.  A CI/CD pipeline could be set up (e.g., using GitHub Actions) to automatically deploy changes merged into the main branch.


