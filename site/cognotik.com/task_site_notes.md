### 1. Architectural Overview

The core of this redesign is the separation of concerns:
*   **Data Layer:** A JSON file defining the available tasks.
*   **Logic Layer:** A Custom Web Component (`<cognotik-header>`) that fetches data and renders the navigation.
*   **Presentation Layer:** A shared CSS file for consistent branding and the individual HTML pages acting as content containers.

#### Proposed Directory Structure
```text
/site/task_docs/
  ├── assets/
  │   ├── data/
  │   │   └── tasks.json          <-- Centralized Metadata
  │   ├── scripts/
  │   │   └── components/
  │   │       └── CognotikHeader.js  <-- Reusable Web Component
  │   └── styles/
  │       └── main.css            <-- Shared variables & layout styles
  └── pages/
      └── tasks/
          ├── AnalysisTask.html
          ├── BrainstormingTask.html
          ├── CrawlerAgentTask.html
          └── FileSearch.html
```

---

### 2. Data Design: `tasks.json`

This file serves as the single source of truth for the navigation menu. Adding a new task page in the future will only require adding an entry here.

**Structure:**
```json
{
  "siteName": "Cognotik",
  "navigation": [
    {
      "label": "Home",
      "url": "/index.html",
      "type": "link"
    },
    {
      "label": "Simulators",
      "type": "dropdown",
      "items": [
        {
          "id": "analysis",
          "label": "Analysis Task",
          "url": "/pages/tasks/AnalysisTask.html",
          "description": "Process inquiries without modifying files."
        },
        {
          "id": "brainstorming",
          "label": "Brainstorming",
          "url": "/pages/tasks/BrainstormingTask.html",
          "description": "Generate diverse solution options."
        },
        {
          "id": "crawler",
          "label": "Crawler Agent",
          "url": "/pages/tasks/CrawlerAgentTask.html",
          "description": "Search and analyze web content."
        },
        {
          "id": "filesearch",
          "label": "File Search",
          "url": "/pages/tasks/FileSearch.html",
          "description": "Regex search across project files."
        }
      ]
    }
  ]
}
```

---

### 3. Component Design: `<cognotik-header>`

We will utilize standard **HTML Web Components (Custom Elements API)** to create a header that can be dropped into any page.

**Key Features:**
1.  **Asynchronous Loading:** On `connectedCallback`, the component fetches `tasks.json`.
2.  **Active State Highlighting:** The component accepts an attribute (e.g., `current-page="analysis"`) to visually highlight the active task in the menu.
3.  **Shadow DOM:** Encapsulates the header styles so they don't bleed into the specific page content, while allowing global theme variables to pass through.

**Component Interface:**
```html
<!-- Usage in HTML pages -->
<cognotik-header current-page="crawler"></cognotik-header>
```

**Logic Flow (Pseudo-code):**
1.  Initialize Shadow DOM.
2.  Fetch `/assets/data/tasks.json`.
3.  Parse JSON.
4.  Generate HTML Template:
    *   Create Logo/Brand area.
    *   Loop through `navigation` array.
    *   If item is `dropdown`, generate a sub-menu.
    *   Check `current-page` attribute against JSON `id`s to apply `.active` CSS class.
5.  Inject generated HTML + Component-specific CSS into Shadow DOM.

---

### 4. Page Integration Strategy

We need to refactor the existing HTML files to remove the hardcoded headers and import the new system.

#### Step A: Shared CSS (`main.css`)
Define CSS Variables to ensure the header matches the body content.
```css
:root {
    --primary-color: #000; /* Cognotik Black */
    --accent-color: #007bff; /* Example Blue */
    --text-color: #333;
    --font-family: 'Inter', sans-serif;
}
```

#### Step B: Refactoring Individual Pages
(Example: `AnalysisTask.html`)

**Before:**
```html
<!-- Old Header -->
<div class="header">
    <span>Cognotik</span>
    <a href="#">Features</a>
    <a href="#">Simulator</a>
    ...
</div>
<h1>Analysis Task</h1>
```

**After:**
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Analysis Task - Cognotik</title>

    <!-- 1. Load Shared Styles -->
    <link rel="stylesheet" href="../../assets/styles/main.css">

    <!-- 2. Load Component Script -->
    <script type="module" src="../../assets/scripts/components/CognotikHeader.js"></script>
</head>
<body>

    <!-- 3. Implement Component with ID -->
    <cognotik-header current-page="analysis"></cognotik-header>

    <!-- Page Specific Content Starts Here -->
    <main class="task-container">
        <h1>Analysis Task</h1>
        <!-- ... rest of existing content ... -->
    </main>

</body>
</html>
```

### 5. Benefits of this Design

1.  **Maintainability:** If you change the name of the "Crawler Agent" to "Web Spider," you only edit `tasks.json`. All pages update instantly.
2.  **Consistency:** The header layout, spacing, and mobile responsiveness are defined once in `CognotikHeader.js`.
3.  **Scalability:** Adding a 5th or 6th task type is trivial and requires no HTML changes to existing pages.
4.  **Performance:** The browser caches the `.js` and `.json` files, making navigation between tasks snappy.