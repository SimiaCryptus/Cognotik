# User Product Page Guidelines

This document outlines the standards for creating "User Product Pages" for Cognotik tasks. These pages serve as
marketing and documentation hybrids, designed to bridge the gap between technical implementation and user understanding.

## 1. Design Philosophy

* **Aesthetic:** "Future-Professional." Dark mode by default, using deep blues/slates (`#0f172a`, `#1e293b`) with
  high-contrast neon accents (Cyan, Gold, Purple) specific to the task category.
* **Typography:** Clean sans-serif (Inter, System UI) for body text; Serif (Cinzel, Merriweather) allowed for
  narrative/creative tasks.
* **Interactivity:** The page must feel alive. Static documentation is discouraged. Use hover states, tab switching, and
  reactive forms.

## 2. Page Structure

Every product page must contain the following sections in order:

### A. Header & Navigation

* See following section on **Component Design** for `<cognotik-header>` implementation.

### B. Hero Section

* **Title:** The Task Name (e.g., "Neural Network Layer Designer").
* **Subtitle:** A compelling one-paragraph summary derived from the `Summary` and `Description` fields in
  `task_type_docs.md`.
* **Visual:** A high-quality, abstract 3D illustration representing the task concept (e.g., DNA for genetics, glowing
  nodes for networks).
    * *Requirement:* Include the prompt description for this image in an HTML comment at the top of the file.
* **CTA:** A "Try the Simulator" button that scrolls to the Demo section.

### C. Features Grid

* **Layout:** 3-column grid.
* **Content:** Extract 3-6 key points from the "Key features include" list in the task documentation.
* **Icons:** Use SVG icons (Lucide/Feather style) inside a glowing container.

### D. Interactive Simulator (The Core)

This is the most important section. It mocks the `ExecutionConfigData` input and the Task `Output`.

* **Layout:** Split screen. Left side = Configuration; Right side = Visualization/Output.
* **Left Column (Inputs):**
    * Map the `Execution Configuration` table from the docs to HTML form elements.
    * *Strings:* Text inputs or Textareas.
    * *Booleans:* Toggles or Checkboxes.
    * *Lists/Enums:* Select dropdowns.
    * *Numbers:* Range sliders with value displays.
* **Right Column (Outputs):**
    * Use Tabs to organize the output (e.g., "Overview", "Code", "Logs", "Visuals").
    * **Code Blocks:** Use syntax highlighting colors for code outputs.
    * **Visuals:** If the task produces data, use `<canvas>` or CSS-based charts.
    * **Logs:** If the task is a process (like `SelfHealing`), show a terminal-like log window.
* **Functionality:** Write vanilla JavaScript to make the inputs update the outputs (or mock the update process with
  loading states).

### E. Workflow / Process (Optional)

* If the task involves multiple steps (e.g., `NarrativeGeneration` or `SubPlanning`), visualize the pipeline using a
  step-stepper or flow diagram.

### F. Use Cases

* Derive this from the "When to Use" section of the task documentation.
* Format as cards or a list.

## 3. Content Mapping Guide

Use the `task_type_docs.md` to populate the page content:

| Product Page Element | Source in `task_type_docs.md`                     |
|:---------------------|:--------------------------------------------------|
| **Hero Title**       | Task Name                                         |
| **Hero Tagline**     | `Summary` field                                   |
| **Feature Cards**    | Bullet points under "Key features include"        |
| **Simulator Inputs** | `Execution Configuration` Table                   |
| **Simulator Output** | `Output` Section (Mock the format described here) |
| **Use Case Section** | "When to U se" Section                            |## 4. T echnical Implementation Standards

#### Directory Structure

```text
/site
  ├── assets/
  │   ├── data/
  │   │   └── tasks.json          <-- Centralized Metadata
  │   ├── scripts/
  │   │   └── components/
  │   │       └── CognotikHeader.js  <-- Reusable Web Component
  │   └── styles/
  │       └── main.css            <-- Shared variables & layout styles
  ├── AnalysisTask.html
  ├── BrainstormingTask.html
  ├── CrawlerAgentTask.html
  └── FileSearch.html
```

* **Single File:** The output must be a single `.html` file containing HTML, CSS, and JS.
* **CSS Variables:** Define a `:root` block for easy theming.
  ```css
  :root {
      --bg-dark: #0f172a;
      --accent-primary: #38bdf8; /* Change per task type */
      --font-sans: system-ui, ...;
  }
  ```
* **No External Heavy Libs:** Do not require `npm install`. Use CDN links for Fonts (Google Fonts) or Icons (
  FontAwesome) if necessary, but prefer inline SVGs.
* **Responsive:** The Simulator must stack vertically on mobile devices.

---

### 2. Data Design: `tasks.json`

This file serves as the single source of truth for the navigation menu. Adding a new task page in the future will only
require adding an entry here.

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
      "label": "Examples",
      "type": "dropdown",
      "items": [
        {
          "id": "analysis",
          "label": "Analysis Task",
          "url": "/pages/tasks/AnalysisTask.html",
          "description": "Process inquiries without modifying files."
        }
      ]
    }
  ]
}
```

---

### 3. Component Design: `<cognotik-header>`

We will utilize standard **HTML Web Components (Custom Elements API)** to create a header that can be dropped into any
page.

**Key Features:**

1. **Asynchronous Loading:** On `connectedCallback`, the component fetches `tasks.json`.
2. **Active State Highlighting:** The component accepts an attribute (e.g., `current-page="analysis"`) to visually
   highlight the active task in the menu.
3. **Shadow DOM:** Encapsulates the header styles so they don't bleed into the specific page content, while allowing
   global theme variables to pass through.

**Component Interface:**

```html
<!-- Usage in HTML pages -->
<cognotik-header current-page="crawler"></cognotik-header>
```

**Logic Flow (Pseudo-code):**

1. Initialize Shadow DOM.
2. Fetch `/assets/data/tasks.json`.
3. Parse JSON.
4. Generate HTML Template:
    * Create Logo/Brand area.
    * Loop through `navigation` array.
    * If item is `dropdown`, generate a sub-menu.
    * Check `current-page` attribute against JSON `id`s to apply `.active` CSS class.
5. Inject generated HTML + Component-specific CSS into Shadow DOM.

---

### 4. Page Integration Strategy

To integrate the new design into a page, follow these steps to load assets and configure the menubar.

#### Step 1: Load Assets

Include the shared CSS and the Web Component script in the `<head>` of your HTML file. Adjust the paths based on your
file's location relative to the `assets` folder.

```html

<head>
    <!-- ... meta tags ... -->

    <!-- Load Shared Styles -->
    <link rel="stylesheet" href="assets/styles/main.css">

    <!-- Load Component Script -->
    <script type="module" src="assets/scripts/components/CognotikHeader.js"></script>
</head>
```

#### Step 2: Use the Menubar

Insert the `<cognotik-header>` tag at the beginning of the `<body>`.

**Configuration:**

* **Tag:** `<cognotik-header>`
* **Attribute `current-page`:** Set this to the `id` of the task (as defined in `tasks.json`) to highlight the active
  tab.

**Example:**

```html


<body>

<!-- Active state for Analysis Task -->
<cognotik-header current-page="analysis"></cognotik-header>

<!-- Page Specific Content -->
<main class="task-container">
    <h1>Analysis Task</h1>
    <!-- ... -->
</main>

</body>
```
