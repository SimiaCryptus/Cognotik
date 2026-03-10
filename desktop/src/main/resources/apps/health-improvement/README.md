# 🏥 Medical AI Diagnostic Pipeline

A structured, multi-round AI-assisted medical analysis system that transforms patient-reported symptoms into
comprehensive clinical reports, personalized wellness plans, and actionable next steps — for both healthcare
professionals and patients.
> ⚠️ **Disclaimer:** This tool is **not a substitute for professional medical advice, diagnosis, or treatment.** Always
> consult a qualified healthcare provider for medical concerns. This system is designed to assist, organize, and inform —
> not to replace clinical judgment.
---

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Pipeline Architecture](#pipeline-architecture)
- [Directory Structure](#directory-structure)
- [Getting Started](#getting-started)
- [1. Set Up a New Case](#1-set-up-a-new-case)
- [2. Fill In Patient Information](#2-fill-in-patient-information)
- [3. Run the Pipeline](#3-run-the-pipeline)
- [4. Answer Follow-Up Questions (Optional Rounds)](#4-answer-follow-up-questions-optional-rounds)
- [5. Review Final Reports](#5-review-final-reports)
- [File Reference](#file-reference)
- [Input Files](#input-files)
- [Operation Files (Ops)](#operation-files-ops)
- [Output Files](#output-files)
- [Pipeline Stages in Detail](#pipeline-stages-in-detail)
- [Stage 1: Initial Brainstorm](#stage-1-initial-brainstorm)
- [Stage 2: Multi-Perspective Analysis](#stage-2-multi-perspective-analysis)
- [Stage 3: Follow-Up Question Generation](#stage-3-follow-up-question-generation)
- [Stage 4: Web Research](#stage-4-web-research)
- [Stage 5: Supplemental Brainstorm (Rounds 2+)](#stage-5-supplemental-brainstorm-rounds-2)
- [Stage 6: Refined Perspectives (Rounds 2+)](#stage-6-refined-perspectives-rounds-2)
- [Stage 7: Final Reports](#stage-7-final-reports)
- [Stage 8: Lifestyle & Inner Development Plans](#stage-8-lifestyle--inner-development-plans)
- [Multi-Round Flow](#multi-round-flow)
- [Customization Guide](#customization-guide)
- [Example Walkthrough](#example-walkthrough)
- [Design Philosophy](#design-philosophy)
- [Limitations](#limitations)
- [FAQ](#faq)
- [License](#license)

---

## Overview

This project provides a **template-driven, iterative diagnostic reasoning pipeline** powered by AI. It mimics the
structured thinking process a medical team would use:

1. **Gather** symptoms and patient context
2. **Brainstorm** a broad differential diagnosis
3. **Analyze** from multiple clinical perspectives
4. **Research** medical literature and guidelines
5. **Refine** through follow-up questions and additional rounds
6. **Produce** two final deliverables:

- A **clinical handoff report** for a doctor
- A **patient-friendly wellness and action plan**
  The system is designed to be thorough, transparent, and safe — always flagging red-flag symptoms, noting confidence
  levels, and emphasizing that professional consultation is essential.

---

## How It Works

The pipeline is built around **operation files** (`.md` files in the `ops/` directory) that define transformations
between documents. Each operation file contains:

- **Frontmatter** with `transforms` (input → output mappings), `related` files for context, and an optional `task_type`
- **Instructions** that guide the AI on what to produce
  The `transforms` field uses a pattern like:

```
../symptoms.md -> ../round_1/brainstorm.md
```

This means: "Take `symptoms.md` as input and produce `round_1/brainstorm.md` as output."
Some transforms use regex capture groups for multi-round support:

```
../round_(.*)/perspectives.md -> ../round_$1+1/perspectives.md
```

This allows the same operation template to work across `round_1`, `round_2`, `round_3`, etc.
---

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PATIENT INPUT                                │
│                  symptoms.md  +  notes.json                         │
└──────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
                ┌────────────────────────┐
                │   ROUND 1: BRAINSTORM  │
                │  (initial_brainstorm)  │
                └───────────┬────────────┘
                            │
                            ▼
                ┌────────────────────────┐
                │  ROUND 1: PERSPECTIVES │
                │ (initial_perspectives) │
                └──────┬─────────┬───────┘
                        │         │
              ┌────────┘         └────────┐
              ▼                           ▼
      ┌──────────────────┐      ┌──────────────────┐
      │  RESEARCH (web)  │      │  GENERATE FOLLOW │
      │ (opt_research)   │      │  UP QUESTIONS    │
      └──────────────────┘      │ (opt_gen_ques)   │
│                └────────┬─────────┘
│                         │
│                         ▼
│                ┌──────────────────┐
│                │  ⏸ PATIENT       │
│                │  ANSWERS QUESTIONS│
│                └────────┬─────────┘
│                         │
│               ┌─────────┘
│               ▼
│    ┌────────────────────────┐
│    │ ROUND 2+: BRAINSTORM   │
│    │  (opt_brainstorm)      │
│    └───────────┬────────────┘
│                │
│                ▼
│    ┌────────────────────────┐
│    │ ROUND 2+: REFINED      │
│    │ PERSPECTIVES           │
│    │ (refine_perspectives)  │
│    └──────┬─────────┬───────┘
│           │         │
│    (loop back for    │
│     more rounds)     │
│                      │
└──────────┬───────────┘
          │
          ▼
┌───────────────────────────┐
│      FINAL OUTPUTS        │
├───────────────────────────┤
│ • final_report_doctor.md  │
│ • plan/patient.md         │
│ • plan/lifestyle.md       │
│ • plan/inner.md           │
└───────────────────────────┘
```

---

## Directory Structure

```
medical/
└── template/
├── README.md                          # (You are here)
├── symptoms.md                        # Patient-reported symptoms (INPUT)
├── notes.json                         # Structured patient data (INPUT)
│
├── ops/                               # Operation definitions (pipeline logic)
│   ├── initial_brainstorm_op.md       # Stage 1: Broad differential brainstorm
│   ├── initial_perspectives_op.md     # Stage 2: Multi-perspective analysis
│   ├── opt_generate_questions_op.md   # Stage 3: Follow-up questions for patient
│   ├── opt_research_op.md             # Stage 4: Web/literature research
│   ├── opt_brainstorm_op.md           # Stage 5: Supplemental brainstorm (round 2+)
│   ├── refine_perspectives_op.md      # Stage 6: Refined perspectives (round 2+)
│   ├── final_report_doctor_op.md      # Stage 7a: Clinical handoff report
│   ├── final_report_patient_op.md     # Stage 7b: Patient-facing action plan
│   ├── plan_lifestyle_op.md           # Stage 8a: Lifestyle & wellness plan
│   └── plan_inner_development_op.md   # Stage 8b: Psycho-spiritual development plan
│
├── round_1/                           # Generated during pipeline execution
│   ├── brainstorm.md
│   ├── perspectives.md
│   └── research.md
│
├── round_2/                           # Generated if follow-up questions are used
│   ├── questions_for_patient.md
│   ├── brainstorm.md
│   ├── perspectives.md
│   └── research.md
│
├── final_report_doctor.md             # Final clinical report (OUTPUT)
│
└── plan/                              # Patient-facing plans (OUTPUT)
├── patient.md
├── lifestyle.md
└── inner.md
```

---

## Getting Started

### 1. Set Up a New Case

Copy the entire `template/` directory to create a new patient case:

```bash
cp -r medical/template medical/case_jane_doe_2025
```

### 2. Fill In Patient Information

#### `symptoms.md` — Describe the Symptoms

Open `symptoms.md` and replace the placeholder text with the patient's actual symptoms:

```markdown
## Primary Symptoms

* Persistent headache behind the eyes
* Onset: 3 weeks ago
* Duration: Constant, with spikes lasting 2-4 hours
* Severity: 6/10 baseline, 9/10 during spikes
* Character: Throbbing, pressure-like

## Associated Symptoms

* Mild nausea during headache spikes
* Sensitivity to bright lights
* Difficulty concentrating at work
* Occasional dizziness when standing quickly

## Aggravating/Alleviating Factors

* What makes it worse: Screen time, stress, skipping meals
* What makes it better: Dark room, ibuprofen (partial relief), cold compress
```

#### `notes.json` — Structured Patient Data

Fill in as much structured data as available. Leave fields as `null` or `[]` if unknown:

```json
{
  "patient_demographics": {
    "age": 34,
    "gender": "Female",
    "occupation": "Software engineer"
  },
  "medical_history": {
    "chronic_conditions": [
      "Mild asthma (childhood, resolved)"
    ],
    "past_surgeries": [],
    "family_history": [
      "Mother: migraines",
      "Father: hypertension"
    ]
  },
  "current_medications": [
    "Ibuprofen 400mg PRN"
  ],
  "allergies": [
    "Sulfa drugs"
  ],
  "vitals": {
    "blood_pressure": "118/76",
    "heart_rate": 72,
    "temperature": 98.4,
    "weight_kg": 65
  },
  "recent_labs": [],
  "lifestyle_factors": {
    "activity_level": "Sedentary, desk job, walks 2x/week",
    "substance_use": "1-2 glasses wine on weekends, no tobacco",
    "sleep_patterns": "6 hours average, difficulty falling asleep",
    "stress_levels": "High — project deadline pressure"
  },
  "social_context": {
    "support_system": "Lives with partner, close friends nearby",
    "living_situation": "Urban apartment"
  }
}
```

> **Tip:** The more complete and accurate the input data, the better the analysis. However, the pipeline is designed to
> work with incomplete information — it will flag gaps and ask clarifying questions.

### 3. Run the Pipeline

Execute the pipeline operations in order. The exact execution method depends on your AI orchestration framework.
Conceptually, the stages run as follows:

```bash
# Stage 1: Initial brainstorm from symptoms
run_op ops/initial_brainstorm_op.md
# → Produces: round_1/brainstorm.md
# Stage 2: Multi-perspective analysis
run_op ops/initial_perspectives_op.md
# → Produces: round_1/perspectives.md
# Stage 3 (parallel): Research + Generate follow-up questions
run_op ops/opt_research_op.md
# → Produces: round_1/research.md
run_op ops/opt_generate_questions_op.md
# → Produces: round_2/questions_for_patient.md
```

### 4. Answer Follow-Up Questions (Optional Rounds)

After `questions_for_patient.md` is generated, the pipeline **pauses** and waits for the patient (or user) to provide
answers. Open the generated file, read the questions, and add your responses directly in the file.
Example generated questions might look like:

```markdown
### Symptom Clarification

1. **[CRITICAL]** Do the headaches wake you from sleep?
2. Have you noticed any visual disturbances (flashing lights, blind spots) before or during the headache?
3. Is the headache always in the same location, or does it move?

### Emergency Screening

4. **[CRITICAL]** Are you experiencing any sudden weakness on one side of your body, difficulty speaking, or the "worst
   headache of your life"?
```

Add your answers below each question, then continue the pipeline:

```bash
# Stage 5: Supplemental brainstorm with new information
run_op ops/opt_brainstorm_op.md
# → Produces: round_2/brainstorm.md
# Stage 6: Refined perspectives
run_op ops/refine_perspectives_op.md
# → Produces: round_2/perspectives.md
# (Optionally repeat stages 3-6 for round_3, round_4, etc.)
```

You can run as many rounds as needed. Each round deepens the analysis with new information.

### 5. Review Final Reports

Once you're satisfied with the depth of analysis (typically 1-3 rounds), generate the final outputs:

```bash
# Final clinical report for the doctor
run_op ops/final_report_doctor_op.md
# → Produces: final_report_doctor.md

# Lifestyle plan
run_op ops/plan_lifestyle_op.md
# → Produces: plan/lifestyle.md

# Inner development plan
run_op ops/plan_inner_development_op.md
# → Produces: plan/inner.md

# Patient-facing action plan
run_op ops/final_report_patient_op.md
# → Produces: plan/patient.md
```

---

## File Reference

### Input Files

| File          | Format   | Purpose                                                                                              |
|---------------|----------|------------------------------------------------------------------------------------------------------|
| `symptoms.md` | Markdown | Free-text description of symptoms, onset, severity, and modifying factors                            |
| `notes.json`  | JSON     | Structured patient demographics, medical history, medications, vitals, lifestyle, and social context |

### Operation Files (Ops)

Each operation file in `ops/` contains YAML frontmatter and markdown instructions:

| File                           | Task Type                | Input(s)                                                    | Output                               |
|--------------------------------|--------------------------|-------------------------------------------------------------|--------------------------------------|
| `initial_brainstorm_op.md`     | Brainstorming            | `symptoms.md`                                               | `round_1/brainstorm.md`              |
| `initial_perspectives_op.md`   | MultiPerspectiveAnalysis | `round_N/brainstorm.md`                                     | `round_N/perspectives.md`            |
| `opt_generate_questions_op.md` | —                        | `round_N/perspectives.md`                                   | `round_N+1/questions_for_patient.md` |
| `opt_research_op.md`           | CrawlerAgent             | `round_N/perspectives.md`                                   | `round_N/research.md`                |
| `opt_brainstorm_op.md`         | Brainstorming            | `round_N/questions_for_patient.md`                          | `round_N+1/brainstorm.md`            |
| `refine_perspectives_op.md`    | MultiPerspectiveAnalysis | `round_N/questions_for_patient.md` +`round_N/brainstorm.md` |                                      |
| `round_N+1/perspectives.md`    |                          |                                                             |                                      |
| `final_report_doctor_op.md`    | —                        | All `perspectives.md` + `research.md`                       | `final_report_doctor.md`             |
| `final_report_patient_op.md`   | —                        | All `perspectives.md` + `research.md`                       | `plan/patient.md`                    |
| `plan_lifestyle_op.md`         | MultiPerspectiveAnalysis | All `perspectives.md`                                       | `plan/lifestyle.md`                  |
| `plan_inner_development_op.md` | MultiPerspectiveAnalysis | All `perspectives.md`                                       | `plan/inner.md`                      |

### Output Files

| File                     | Audience                | Description                                                                                                                        |
|--------------------------|-------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `final_report_doctor.md` | Healthcare professional | Clinical handoff document with ranked differential diagnosis, supporting evidence, recommended next steps, and pertinent negatives |
| `plan/patient.md`        | Patient                 | Warm, plain-language summary with action plan and doctor visit preparation guide                                                   |
| `plan/lifestyle.md`      | Patient                 | Personalized lifestyle plan covering physical activity, nutrition, substance use, and social adjustments                           |
| `plan/inner.md`          | Patient                 | Psychotherapeutic, spiritual, and motivational development plan                                                                    |

---

## Pipeline Stages in Detail

### Stage 1: Initial Brainstorm

**Op:** `initial_brainstorm_op.md`
**Goal:** Cast a wide net of possible diagnoses.

- Immediately screens for **red-flag symptoms** requiring emergency evaluation
- Brainstorms a broad set of possible medical causes grouped by body system
- Prioritizes breadth over precision — avoids premature narrowing
- Identifies symptom clusters that suggest specific conditions
- Flags potentially urgent or life-threatening causes

### Stage 2: Multi-Perspective Analysis

**Op:** `initial_perspectives_op.md`
**Goal:** Analyze the brainstorm from four clinical viewpoints.

| Perspective                | Focus                                            |
|----------------------------|--------------------------------------------------|
| **Primary Care Physician** | Statistical likelihood given the symptom profile |
| **Specialist**             | Nuanced or easily-missed diagnoses               |
| **Emergency Medicine**     | Red flags and time-sensitive conditions          |
| **Patient Advocate**       | Patient concerns and information needs           |

Highlights areas of agreement/disagreement and identifies key uncertainties.

### Stage 3: Follow-Up Question Generation

**Op:** `opt_generate_questions_op.md`
**Goal:** Generate targeted questions to narrow the differential.
Questions are organized into categories:

- **Symptom Clarification** — onset, duration, severity, triggers
- **Medical History** — prior diagnoses, family history
- **Medications & Supplements** — current and recent use
- **Lifestyle & Environment** — diet, exercise, exposures
- **Review of Systems** — targeted rule-in/rule-out questions
- **Emergency Screening** — explicit red-flag checks
  Critical questions are marked for prioritization. **The pipeline pauses here** until the patient provides answers.

### Stage 4: Web Research

**Op:** `opt_research_op.md`
**Task Type:** `CrawlerAgent` (web search)
**Goal:** Ground the analysis in current medical evidence.

- Searches medical literature and clinical guidelines
- Identifies gold-standard diagnostic tests
- Finds applicable clinical decision rules (e.g., Wells' Criteria, PERC rule)
- Locates patient-facing resources from reputable organizations
- Notes recent developments and relevant case studies

### Stage 5: Supplemental Brainstorm (Rounds 2+)

**Op:** `opt_brainstorm_op.md`
**Goal:** Expand the differential based on new patient information.

- Reviews patient responses alongside prior analysis
- Identifies newly revealed symptom clusters and patterns
- Proposes additional conditions (including atypical presentations)
- Re-evaluates previously dismissed possibilities
- Re-screens for newly disclosed red-flag symptoms
- Notes remaining information gaps

### Stage 6: Refined Perspectives (Rounds 2+)

**Op:** `refine_perspectives_op.md`
**Goal:** Produce a refined, ranked differential diagnosis.
Analyzes from updated perspectives:

| Perspective             | Focus                                           |
|-------------------------|-------------------------------------------------|
| **Diagnostician**       | How new information reshapes the differential   |
| **Specialist**          | Conditions moving up or down in likelihood      |
| **Preventive Medicine** | Lifestyle/environmental root causes             |
| **Patient Experience**  | Quality of life, anxiety, treatment preferences |

Explicitly addresses new candidates from supplemental brainstorming and notes conditions that can now be ruled out.

### Stage 7: Final Reports

#### 7a: Doctor Report (`final_report_doctor_op.md`)

A professional clinical handoff document containing:

- **Chief Complaint & Relevant History** — presenting symptoms with onset, duration, severity
- **Differential Diagnosis (Ranked)** — candidates with rationale, distinguishing confirmed from speculative
- **Supporting Evidence** — cited sources, literature concordance/discordance
- **Recommended Next Steps** — diagnostic tests, referrals, triage priority
- **Important Caveats** — limitations, confidence level, red flags, pertinent negatives

#### 7b: Patient Report (`final_report_patient_op.md`)

A warm, supportive patient-facing document containing:

- **What's Going On** — plain-language summary, no jargon
- **Your Action Plan** — concrete lifestyle changes, immediate and long-term
- **Preparing for Your Doctor Visit** — what to bring, questions to ask, self-advocacy tips
- **Important Caveats** — emphasis on professional consultation

> **Note:** The patient report deliberately avoids recommending new medications or clinical treatments.

### Stage 8: Lifestyle & Inner Development Plans

#### 8a: Lifestyle Plan (`plan_lifestyle_op.md`)

Personalized, actionable wellness plan:

- **Physical Activity & Movement** — respects current limitations, includes ramp-up strategy
- **Nutrition & Substance Use** — dietary suggestions, non-judgmental reduction strategies
- **Social & Environmental Adjustments** — community, intellectual engagement
- **Implementation Strategy** — urgency levels, barriers, integration with inner development

#### 8b: Inner Development Plan (`plan_inner_development_op.md`)

Complementary psycho-spiritual plan:

- **Psychotherapeutic Development** — therapeutic modalities, self-directed exercises
- **Spiritual & Existential Development** — meaning-making, contemplative practices
- **Motivational Development** — intrinsic/extrinsic patterns, sustainable progress
- **Integration & Realism** — unified weekly rhythm, realistic for patient's circumstances

---

## Multi-Round Flow

The pipeline supports iterative refinement through multiple rounds:

| Round                                                                                  | What Happens                                                               | Key Files                                           |
|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------|-----------------------------------------------------|
| **Round 1**                                                                            | Initial brainstorm → perspectives → research                               | `round_1/brainstorm.md`, `round_1/perspectives.md`, |
| `round_1/research.md`                                                                  |                                                                            |                                                     |
| **Round 2**                                                                            | Patient answers questions → supplemental brainstorm → refined perspectives |                                                     |
| `round_2/questions_for_patient.md`, `round_2/brainstorm.md`, `round_2/perspectives.md` |                                                                            |                                                     |
| **Round 3+**                                                                           | Additional follow-up → further refinement                                  | `round_3/...`                                       |
| **Final**                                                                              | All rounds synthesized into reports and plans                              | `final_report_doctor.md`, `plan/*.md`               |

**When to add more rounds:**

- The differential is still broad after round 1
- The patient has complex or ambiguous symptoms
- New information significantly changes the picture
- Red-flag symptoms need further clarification
  **When to stop and generate finals:**
- The differential has converged to 2-3 leading candidates
- Further questions would require clinical testing (labs, imaging)
- The patient has provided all available information
- Typically 1-3 rounds is sufficient for most cases

---

## Customization Guide

### Adding Custom Perspectives

Edit `initial_perspectives_op.md` or `refine_perspectives_op.md` to add domain-specific perspectives:

```markdown
- **Occupational Medicine**: Are there workplace exposures contributing to the symptoms?
- **Geriatric Medicine**: How does age-related physiology affect the differential?
- **Pediatric Specialist**: What developmental considerations apply?
```

### Modifying the Notes Schema

Extend `notes.json` with additional fields relevant to your use case:

```json
{
  "reproductive_health": {
    "pregnancy_status": null,
    "menstrual_history": null
  },
  "mental_health_screening": {
    "phq9_score": null,
    "gad7_score": null
  }
}
```

Then reference the new fields in the relevant op files via the `related` frontmatter.

### Adjusting Tone and Scope

- **Doctor report tone:** Controlled in `final_report_doctor_op.md` — currently set to "professional, concise, and
  clinical"
- **Patient report tone:** Controlled in `final_report_patient_op.md` — currently set to "warm, supportive, and
  encouraging"
- **Research depth:** Controlled in `opt_research_op.md` — add or remove search directives as needed

### Skipping Optional Stages

The following stages are optional and can be skipped:

- **Follow-up questions** (`opt_generate_questions_op.md`) — skip if you only need a single-round analysis
- **Web research** (`opt_research_op.md`) — skip if your AI model doesn't support web access
- **Inner development plan** (`plan_inner_development_op.md`) — skip if only clinical output is needed
- **Lifestyle plan** (`plan_lifestyle_op.md`) — skip if only the diagnostic report is needed

---

## Example Walkthrough

Here's a complete example of running the pipeline for a patient with persistent headaches:

### Step 1: Create the case

```bash
cp -r medical/template medical/case_headache_2025
cd medical/case_headache_2025
```

### Step 2: Edit `symptoms.md`

```markdown
## Primary Symptoms

* Persistent headache behind both eyes
* Onset: 3 weeks ago, gradual
* Duration: Constant baseline with 2-4 hour spikes
* Severity: 6/10 baseline, 9/10 spikes
* Character: Throbbing, pressure-like

## Associated Symptoms

* Nausea during spikes
* Photosensitivity
* Difficulty concentrating
* Occasional postural dizziness

## Aggravating/Alleviating Factors

* Worse: Screen time, stress, skipped meals
* Better: Dark room, ibuprofen (partial), cold compress
```

### Step 3: Edit `notes.json`

Fill in demographics, history, vitals, etc. (see [Getting Started](#2-fill-in-patient-information) above).

### Step 4: Run Round 1

```bash
run_op ops/initial_brainstorm_op.md        # → round_1/brainstorm.md
run_op ops/initial_perspectives_op.md      # → round_1/perspectives.md
run_op ops/opt_research_op.md              # → round_1/research.md
run_op ops/opt_generate_questions_op.md    # → round_2/questions_for_patient.md
```

### Step 5: Answer questions in `round_2/questions_for_patient.md`

### Step 6: Run Round 2

```bash
run_op ops/opt_brainstorm_op.md            # → round_2/brainstorm.md
run_op ops/refine_perspectives_op.md       # → round_2/perspectives.md
```

### Step 7: Generate final outputs

```bash
run_op ops/final_report_doctor_op.md       # → final_report_doctor.md
run_op ops/final_report_patient_op.md      # → plan/patient.md
run_op ops/plan_lifestyle_op.md            # → plan/lifestyle.md
run_op ops/plan_inner_development_op.md    # → plan/inner.md
```

### Step 8: Review and share

- Give `final_report_doctor.md` to the healthcare provider
- Give `plan/patient.md`, `plan/lifestyle.md`, and `plan/inner.md` to the patient

---

## Design Philosophy

### Safety First

- Every stage screens for **red-flag symptoms** requiring emergency evaluation
- Final reports always include caveats about AI limitations
- The system never claims to provide a diagnosis
- **Pertinent negatives** are explicitly documented to show what was considered and ruled out

### Breadth Before Depth

- The initial brainstorm deliberately casts a wide net
- Premature narrowing is actively discouraged in early stages
- Multiple perspectives prevent tunnel vision

### Patient-Centered

- Two separate output tracks: one for clinicians, one for patients
- Patient-facing documents avoid jargon and use supportive language
- The patient report empowers self-advocacy without overstepping into clinical advice
- Lifestyle and inner development plans address the whole person, not just symptoms

### Iterative Refinement

- The multi-round structure mirrors real clinical reasoning
- Each round incorporates new information and re-evaluates prior conclusions
- The system explicitly tracks what changed and why

### Transparency

- Confidence levels are noted throughout
- Information gaps are flagged, not hidden
- Sources are cited where applicable
- Areas of agreement and disagreement between perspectives are highlighted

---

## Limitations

| Limitation                        | Impact                                                                                                |
|-----------------------------------|-------------------------------------------------------------------------------------------------------|
| **No physical examination**       | Cannot assess physical signs (e.g., tenderness, reflexes, auscultation)                               |
| **No lab/imaging data**           | Cannot interpret test results unless manually provided in `notes.json`                                |
| **AI hallucination risk**         | Medical facts should be verified against authoritative sources                                        |
| **No real-time monitoring**       | Cannot track symptom progression over time                                                            |
| **Language/cultural bias**        | May not account for culturally specific presentations or health beliefs                               |
| **Not emergency-capable**         | Cannot call 911 or initiate emergency protocols — only flags urgency                                  |
| **Dependent on input quality**    | Vague or incomplete symptom descriptions reduce analysis quality                                      |
| **No medication recommendations** | Patient-facing reports deliberately avoid prescribing; doctor report may suggest classes of treatment |

---

## FAQ

### Q: Can I use this for an actual medical situation?

**A:** This tool can help you organize your thoughts and prepare for a doctor visit, but it is **not a substitute for
professional medical care.** Always consult a qualified healthcare provider.

### Q: How many rounds should I run?

**A:** Most cases benefit from 1-3 rounds. Run additional rounds if the differential is still broad or if new
information significantly changes the picture. Stop when further refinement would require clinical testing.

### Q: Can I skip the follow-up questions and go straight to final reports?

**A:** Yes. After Round 1's perspectives and research are complete, you can proceed directly to final report generation.
The analysis will be less refined but still useful.

### Q: What if I don't have all the information for `notes.json`?

**A:** Leave unknown fields as `null` or `[]`. The pipeline is designed to work with incomplete information and will
flag gaps in its analysis.

### Q: Can I add my own operation files?

**A:** Yes. Create a new `.md` file in `ops/` following the same frontmatter format. Define `transforms` to specify
input/output mappings and write instructions for the AI.

### Q: What does the `task_type` field do?

**A:** It hints to the AI orchestration framework about what kind of processing is needed:

- `Brainstorming` — divergent, creative thinking
- `MultiPerspectiveAnalysis` — structured analysis from multiple viewpoints
- `CrawlerAgent` — web search and research capabilities

### Q: Is my medical data stored or shared?

**A:** This is a local template system. Your data stays in the files you create. However, be aware that running
operations through an AI service may transmit data to that service — check your AI provider's privacy policy.

### Q: Can I use this for veterinary medicine?

**A:
** The template is designed for human medicine, but you could adapt the perspectives and terminology for veterinary use by modifying the op files.
---

## License

Please refer to the project's license file for terms of use. This tool is provided as-is, without warranty. It is not a
medical device and has not been evaluated by any regulatory body.