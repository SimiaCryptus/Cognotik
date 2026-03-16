# 🏥 Medical AI Diagnostic Pipeline

An interactive, multi-round AI-assisted health analysis tool that guides users through a structured diagnostic reasoning process — from initial symptom intake to personalized action plans and clinical handoff documents.

> ⚠️ **Disclaimer:** This tool is **not a substitute for professional medical advice, diagnosis, or treatment.** Always consult a qualified healthcare provider for medical concerns.

## Overview

This application implements a multi-stage diagnostic pipeline that mirrors how a medical team might approach a complex case. It uses AI-powered document operations ("doc ops") to brainstorm differential diagnoses, analyze them from multiple clinical perspectives, conduct web research, and produce both patient-facing and clinician-facing reports.

### Key Features

- **Structured Patient Intake** — Symptom descriptions in Markdown and structured demographic/history data in JSON
- **Multi-Round Analysis** — Iterative refinement through brainstorming, multi-perspective analysis, and follow-up questioning
- **Web Research Integration** — Automated search of medical literature and clinical guidelines via a crawler agent
- **Four Final Reports:**
  - 🩺 **Clinical Handoff Report** — Professional summary for a healthcare provider
  - 💚 **Patient Action Plan** — Warm, supportive guidance written for the patient
  - 🏃 **Lifestyle Plan** — Personalized diet, exercise, and habit recommendations
  - 🧘 **Inner Development Plan** — Psychotherapeutic, spiritual, and motivational guidance

## Pipeline Architecture

```
┌─────────────────┐
│  Patient Input   │  symptoms.md + notes.json
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Round 1         │
│  ┌─────────────┐ │
│  │ Brainstorm  │ │  Broad differential diagnosis
│  └──────┬──────┘ │
│         ▼        │
│  ┌─────────────┐ │
│  │ Perspectives│ │  Multi-perspective clinical analysis
│  └──────┬──────┘ │
│         │        │
│    ┌────┴────┐   │
│    ▼         ▼   │
│ Research  Questions│  Parallel: web research + follow-up questions
│    │         │   │
└────┼─────────┼───┘
     │         │
     │         ▼
     │  ┌─────────────┐
     │  │ Patient     │  User answers follow-up questions
     │  │ Answers     │
     │  └──────┬──────┘
     │         │
     │         ▼
┌────┼─────────────────┐
│  Round 2 │           │
│    │  ┌─────────────┐│
│    │  │ Brainstorm 2││  Expanded differential with new info
│    │  └──────┬──────┘│
│    │         ▼       │
│    │  ┌─────────────┐│
│    │  │ Refined     ││  Updated ranked differential
│    │  │ Perspectives││
│    │  └─────────────┘│
└────┼─────────────────┘
     │         │
     ▼         ▼
┌─────────────────────────┐
│  Final Reports           │
│  ┌────────┐ ┌──────────┐│
│  │ Doctor │ │ Patient  ││
│  │ Report │ │ Plan     ││
│  └────────┘ └──────────┘│
│  ┌────────┐ ┌──────────┐│
│  │Lifestyle│ │  Inner   ││
│  │  Plan  │ │  Dev Plan││
│  └────────┘ └──────────┘│
└─────────────────────────┘
```

## File Structure

```
health-improvement/
├── app.html                 # Main application UI
├── app.js                   # Client-side application logic
├── style.css                # Application styles
├── marked.min.js            # Markdown rendering library
├── symptoms.md              # Patient symptom description (user-edited)
├── notes.json               # Structured patient data (user-edited)
├── README.md                # This file
│
├── ops/                     # Doc-op instruction files
│   ├── initial_brainstorm_op.md        # Round 1: broad differential brainstorm
│   ├── initial_perspectives_op.md      # Round 1: multi-perspective analysis
│   ├── opt_research_op.md              # Web research on top differentials
│   ├── opt_generate_questions_op.md    # Generate follow-up questions
│   ├── opt_brainstorm_op.md            # Round 2: supplemental brainstorm
│   ├── refine_perspectives_op.md       # Round 2: refined perspectives
│   ├── final_report_doctor_op.md       # Final: clinical handoff report
│   ├── final_report_patient_op.md      # Final: patient action plan
│   ├── plan_lifestyle_op.md            # Final: lifestyle plan
│   └── plan_inner_development_op.md    # Final: inner development plan
│
├── round_1/                 # Generated Round 1 outputs
│   ├── brainstorm.md
│   ├── perspectives.md
│   └── research.md
│
├── round_2/                 # Generated Round 2 outputs
│   ├── questions_for_patient.md   # Also edited by user with answers
│   ├── brainstorm.md
│   └── perspectives.md
│
└── plan/                    # Generated final reports
    ├── doctor.md
    ├── patient.md
    ├── lifestyle.md
    └── inner.md
```

## Usage Guide

### Step 1: Patient Input

1. Navigate to the **📝 Patient Input** tab
2. Fill in **symptoms.md** with a description of symptoms including onset, duration, severity, and character
3. Fill in **notes.json** with structured data: demographics, medical history, medications, vitals, lifestyle factors, and social context
4. Save both files

### Step 2: Run Round 1

Switch to the **⚙️ Pipeline** tab and either run steps individually or click **▶ Run All Round 1**:

1. **Initial Brainstorm** — Generates a broad differential diagnosis organized by body system
2. **Multi-Perspective Analysis** — Analyzes from Primary Care, Specialist, Emergency Medicine, and Patient Advocate viewpoints
3. **Web Research** *(parallel)* — Searches medical literature for the top differential diagnoses
4. **Generate Follow-Up Questions** *(parallel)* — Creates targeted questions to narrow the differential

### Step 3: Answer Follow-Up Questions

1. Click **📥 Load Questions** to load the generated questions into the editor
2. Add your answers directly below each question in the editor
3. Click **💾 Save Answers**

### Step 4: Run Round 2

Click **▶ Run All Round 2** to:

1. **Supplemental Brainstorm** — Expand the differential based on new patient information
2. **Refined Perspectives** — Produce an updated, ranked differential diagnosis

### Step 5: Generate Final Reports

Click **▶ Run All Final Reports** to generate all four output documents, then review them in the **📊 Results** tab.

## Doc-Op System

Each operation is defined by a Markdown file in the `ops/` directory with YAML frontmatter that specifies:

- **`transforms`** — Input-to-output file mapping patterns (supports regex and round number arithmetic like `$1+1`)
- **`related`** — Additional context files included in the AI prompt
- **`task_type`** — The type of AI task (e.g., `Brainstorming`, `MultiPerspectiveAnalysis`, `CrawlerAgent`)

The body of each op file contains the detailed instructions for the AI agent.

## Safety Features

- **Red Flag Screening** — Every brainstorm and perspective round explicitly checks for emergency symptoms
- **Pertinent Negatives** — The doctor report documents absent symptoms that help rule out dangerous conditions
- **No Medication Recommendations** — The patient-facing report deliberately avoids recommending medications or clinical treatments
- **Prominent Disclaimers** — All reports include caveats that they do not replace professional medical advice
- **Confidence Levels** — Reports note the confidence level and limitations of the AI-generated assessment

## Technical Details

- **Frontend:** Vanilla HTML/CSS/JavaScript (no framework dependencies)
- **Markdown Rendering:** Uses [marked.js](https://github.com/markedjs/marked) with a minimal fallback parser
- **Backend Communication:**
  - File I/O via `GET`/`PUT` requests to a file-index servlet
  - Doc-op execution via `POST` to a `/docops` endpoint
  - Async task tracking via `docops.status.json` polling
- **Session-Based:** Each user session gets an isolated file workspace identified by a session ID in the URL path
- **Responsive Design:** Fully functional on mobile and desktop viewports

## Extending the Pipeline

To add a new analysis round or report:

1. Create a new op file in `ops/` with appropriate `transforms` and `related` frontmatter
2. Add a corresponding step in `app.html` with run/view buttons
3. Wire up the button in `app.js` (or add it to a batch execution group)
4. The pipeline will automatically resolve file dependencies based on the transform patterns