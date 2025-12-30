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

Include the shared CSS and the Web Component script in the
`<head><link href="logo.svg" rel="icon" type="image/svg+xml">` of your HTML file. Adjust the paths based on your
file's location relative to the `assets` folder.

```html

<head>
    <link href="../site/cognotik.com/logo.svg" rel="icon" type="image/svg+xml">
    <!-- ... meta tags ... -->

    <!-- Load Shared Styles -->
    <link rel="stylesheet" href="../site/cognotik.com/assets/styles/main.css">

    <!-- Load Component Script -->
    <script type="module" src="../site/cognotik.com/assets/scripts/components/CognotikHeader.js"></script>
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

Here is a breakdown of the design system and HTML structure required to utilize `main.css`.

### **1. Design Overview**
*   **Theme:** Dark Mode / Sci-Fi / Tech.
*   **Colors:** Dark Slate background (`#0f172a`), White/Grey text, Purple (`#8b5cf6`) primary accents, Cyan (`#06b6d4`) secondary accents.
*   **Typography:**
    *   **Headings:** Serif (`Cinzel`) — *You must import the 'Cinzel' font.*
    *   **Body:** Sans-serif (`Inter` or system fonts).
    *   **Code:** Monospace.

---

### **2. Required HTML Structure by Section**

Here is how you need to structure your HTML to match the specific sections defined in the CSS.

#### **A. Hero Section (`.hero`)**
A centered, high-impact introduction with a radial gradient background.
```html
<section class="hero">
    <!-- H1 has a text-gradient effect automatically applied -->
    <h1>Cognotik AI</h1>
    <p class="subtitle">Advanced cognitive simulation for the modern web.</p>
    <button class="cta-button">Get Started</button>
</section>
```

#### **B. Features Grid (`.features`)**
A responsive grid of cards.
```html
<section class="features">
    <h2>Key Features</h2>
    <div class="features-grid">
        <!-- Card 1 -->
        <div class="feature-card">
            <!-- CSS expects an <i> tag for icons (e.g., FontAwesome) -->
            <i class="fa-solid fa-brain"></i>
            <h3>Neural Mapping</h3>
            <p>Description of the feature goes here.</p>
        </div>
        <!-- Card 2... -->
    </div>
</section>
```

#### **C. Modes Section (`.mode-grid`)**
Cards with a distinct left-border accent color.
```html
<div class="modes-section">
    <h2>System Modes</h2>
    <div class="mode-grid">
        <div class="mode-card">
            <div class="mode-title">Autonomous Mode</div>
            <p>System operates without intervention.</p>
        </div>
    </div>
</div>
```

#### **D. The Simulator (`.simulator`)**
This is the most complex UI component. It is a split-pane interface (Controls on left, Output on right) that stacks vertically on mobile.

**Note:** You will need JavaScript to handle the Tab switching logic (toggling the `.active` class).

```html
<section class="simulator">
    <h2>Simulation Engine</h2>

    <div class="simulator-container">
        <!-- Left Column: Controls -->
        <div class="sim-controls">
            <div class="control-group">
                <label>Input Parameters</label>
                <input type="text" placeholder="Enter value...">
            </div>
            <div class="control-group">
                <label>Model Selection</label>
                <select>
                    <option>GPT-4</option>
                    <option>Claude 3</option>
                </select>
            </div>
            <button class="run-button">Run Simulation</button>
        </div>

        <!-- Right Column: Output with Tabs -->
        <div class="sim-output">
            <!-- Tab Headers -->
            <div class="tabs">
                <button class="tab active">Logs</button>
                <button class="tab">Tree View</button>
                <button class="tab">Plan</button>
            </div>

            <!-- Tab Contents -->
            <div class="tab-content active">
                <!-- Helper classes for content: -->
                <div class="chat-log ai">AI: Initializing sequence...</div>
                <div class="chat-log">User: Confirm.</div>
            </div>

            <div class="tab-content">
                <div class="plan-step">Step 1: Analyze Data</div>
                <div class="tree-node">Node: Root -> Child A</div>
            </div>
        </div>
    </div>
</section>
```

---

### **3. Global Components & Utilities**

#### **Layout Wrapper**
For standard content pages (like documentation or blog posts), wrap your content in `.task-container` to handle max-width and padding automatically.
```html
<div class="task-container">
    <!-- Content goes here -->
</div>
```

#### **Buttons**
*   **Standard Button:** `<button>` or `<a class="btn">` (Purple background).
*   **CTA Button:** `<button class="cta-button">` (Used in Hero, has glow effect).
*   **Run Button:** `<button class="run-button">` (Used in Simulator, Cyan background, uppercase).

#### **Forms**
Standard `<input>`, `<textarea>`, and `<select>` elements are automatically styled with dark backgrounds and purple focus borders. No special classes are needed, just ensure they are inside a block container.

#### **Code Blocks**
Use standard HTML5 tags.
```html
<pre><code>const ai = new Cognotik();
ai.init();</code></pre>
```

### **4. External Dependencies**

To make the fonts work as intended, ensure you include these in your
`<head><link href="logo.svg" rel="icon" type="image/svg+xml">`:
1.  **Inter** (Sans-serif)
2.  **Cinzel** (Serif - *Critical for Headings*)
3.  **Icon Library** (The CSS references `<i>` tags in feature cards, likely FontAwesome or similar).
