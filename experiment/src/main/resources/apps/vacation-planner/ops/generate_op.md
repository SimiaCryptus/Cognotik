---
task_type: FileModification
transforms: ../itinerary\.md -> ../vacation_plan.html
related:
   - ../analysis_output.md
   - ../brainstorm_output.md
   - ../research.md
validation_regex: "(?=.*<header>)(?=.*<main>)(?=.*<style>)(?=.*<script>)(?=.*vacation-plan)"
---

# Vacation Brainstorming App UI Generation

You are generating a self-contained HTML/CSS/JS interface for displaying the vacation brainstorming application and results.

## Your Role

Create a professional, responsive, self-contained HTML file that:
- Displays vacation brainstorming results
- Provides interactive navigation between sections
- Supports mobile and desktop viewing
- Includes all necessary styling and functionality
- Follows accessibility best practices

## Design Requirements

### Visual Design
- **Color Scheme**: Dark theme (background: #0f1117, accent: #a78bfa)
- **Typography**: System fonts (Segoe UI, -apple-system)
- **Spacing**: 8px grid system
- **Radius**: 8px border radius
- **Responsive**: Mobile-first, works on 320px+ screens

### Core Sections

1. **Header**
   - App title: "🌴 Vacation Brainstormer"
   - Subtitle: "Plan your perfect weekend getaway"
   - Status indicator (Idle / Planning / Complete)

2. **Input Panel**
   - Text input: "What kind of vacation are you dreaming of?"
   - Placeholder: "e.g., Beach weekend with friends, mountain hiking adventure..."
   - Button: "✨ Brainstorm Ideas"
   - Button: "📊 View Analysis"

3. **Results Panel** (appears after brainstorming)
   - Tabs: Analysis | Perspectives | Data | Itinerary
   - **Analysis Tab**: Display brainstorming analysis
   - **Perspectives Tab**: Display multi-perspective analysis
   - **Data Tab**: Display gathered data with sources
   - **Itinerary Tab**: Display day-by-day itinerary with costs

4. **Concept Cards** (for each vacation concept)
   - Concept name
   - Location
   - Duration
   - Primary activities (3-4 listed)
   - Estimated budget
   - "View Details" button
   - "Select This" button

5. **Itinerary View**
   - Day-by-day breakdown
   - Timeline visualization
   - Cost summary
   - "Export as PDF" button
   - "Share" button

### Interaction Patterns

- **Brainstorm Button**: Triggers analysis, shows loading state
- **Concept Selection**: Highlights selected concept, shows detailed view
- **Tab Navigation**: Smooth transitions between sections
- **Cost Calculator**: Real-time budget tracking
- **Responsive Behavior**: Stacks vertically on mobile

---

## HTML Structure

Generate a complete, self-contained HTML file with:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vacation Brainstormer</title>
    <style>
        [Include comprehensive CSS here]
    </style>
</head>
<body>
    <header>
        <h1>🌴 Vacation Brainstormer</h1>
        <p class="subtitle">Plan your perfect weekend getaway</p>
        <div class="status-badge" id="statusBadge">Ready</div>
    </header>

    <main>
        <section id="inputPanel" class="panel">
            <textarea id="vacationInput" placeholder="What kind of vacation are you dreaming of?"></textarea>
            <button id="brainstormBtn" class="btn btn-primary">✨ Brainstorm Ideas</button>
        </section>

        <section id="resultsPanel" class="panel hidden">
            <div class="tabs">
                <button class="tab-btn active" data-tab="analysis">📊 Analysis</button>
                <button class="tab-btn" data-tab="perspectives">🔍 Perspectives</button>
                <button class="tab-btn" data-tab="data">📍 Data</button>
                <button class="tab-btn" data-tab="itinerary">📅 Itinerary</button>
            </div>

            <div id="analysisTab" class="tab-content active">
                [Analysis content]
            </div>
            <div id="perspectivesTab" class="tab-content">
                [Perspectives content]
            </div>
            <div id="dataTab" class="tab-content">
                [Data content]
            </div>
            <div id="itineraryTab" class="tab-content">
                [Itinerary content]
            </div>
        </section>
    </main>

    <script>
        [Include comprehensive JavaScript here]
    </script>
</body>
</html>
```

---

## CSS Requirements

- **Dark theme** with accent colors
- **Responsive grid** layout (1 column mobile, 2+ columns desktop)
- **Smooth transitions** (0.15s) for interactive elements
- **Accessible contrast** (WCAG AA minimum)
- **Print-friendly** styles for PDF export

---

## JavaScript Requirements

- **Tab switching**: Show/hide content based on selected tab
- **Cost calculation**: Real-time budget tracking
- **Concept selection**: Highlight selected concept
- **Data display**: Render markdown/JSON data as formatted HTML
- **Export functionality**: Generate PDF or shareable link
- **Responsive behavior**: Adapt layout for mobile/tablet/desktop

---

## Output Format

- Single self-contained HTML file
- All CSS embedded in <style> tag
- All JavaScript embedded in <script> tag
- No external dependencies (except optional marked.js for markdown rendering)
- Responsive design that works on 320px+ screens
- Dark theme with accent colors matching design system

---

## Success Criteria

This op file produces high-quality output when:

1. **Completeness**: All required sections are present
2. **Functionality**: All interactive elements work correctly
3. **Responsiveness**: Layout adapts to all screen sizes
4. **Accessibility**: WCAG 2.1 AA compliance
5. **Performance**: Loads quickly, no console errors
6. **Usability**: Clear navigation and intuitive interactions