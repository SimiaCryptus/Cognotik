# Career Advisor

An AI-powered career coaching pipeline that analyzes your professional situation, explores opportunities, and produces a personalized career development plan.

## Overview

Career Advisor is a multi-step, iterative pipeline that:

1. **Generates career path ideas** based on your background and goals
2. **Asks clarifying questions** to deepen understanding of your situation
3. **Reviews your materials** (resume, LinkedIn profile, portfolio)
4. **Revises and refines** recommendations through multiple rounds
5. **Produces an actionable plan** with concrete next steps

## Getting Started

### Prerequisites
- A running instance of the AI backend server
- Valid API keys configured for your chosen AI models

### Setup

1. **Fill in your profile** — Edit `profile.md` with your background, experience, skills, and goals
2. **Add your resume** — Paste your resume content into `resume.md`
3. **Set your goals** — Define what you want in `goals.md` (role, industry, salary, lifestyle)
4. **Run the pipeline** — Click "Run Full Pipeline" or execute steps individually

## Pipeline Steps

### 1. Brainstorm Ideas (`ops/ideas.md`)
Generates a broad set of career path options, opportunities, and strategies based on your profile and goals. Produces `ideas.md`.

### 2. Ask Clarifying Questions (`ops/questions.md`)
Analyzes your profile and the initial ideas to generate targeted questions that will help refine the recommendations. Produces `round_1/questions.md`.

**Human checkpoint:** Answer the questions in `round_1/questions.md` before continuing.

### 3. Review Materials (`ops/review.md`)
Performs a detailed review of your resume and professional materials, identifying strengths, gaps, and improvement opportunities. Produces `round_1/review.md`.

### 4. Revise Recommendations (`ops/revise.md`)
Synthesizes all gathered information — your profile, goals, answered questions, and material review — into refined, actionable career recommendations. Produces `round_1/recommendations.md`.

### 5. Iterative Refinement
Additional rounds of questions and revisions can be triggered by answering questions in `round_N/questions.md`, which produces `round_N+1/questions.md` and updated recommendations.

### 6. Final Plan (`ops/plan.md`)
Synthesizes all rounds into a comprehensive career development plan with prioritized action items. Produces `career_plan.md`.

## File Structure

```
career-advisor/
├── app.html                    # Web UI
├── app.js                      # Application logic
├── style.css                   # Styles
├── README.md                   # This file
├── profile.md                  # Your professional background (INPUT)
├── resume.md                   # Your current resume (INPUT)
├── goals.md                    # Your career goals (INPUT)
├── ideas.md                    # Generated: initial career path ideas
├── career_plan.md              # Generated: final comprehensive plan
├── round_1/
│   ├── questions.md            # Generated: clarifying questions (ANSWER THESE)
│   ├── review.md               # Generated: resume/materials review
│   └── recommendations.md      # Generated: refined recommendations
├── round_2/
│   ├── questions.md            # Generated: follow-up questions (ANSWER THESE)
│   ├── review.md               # Generated: updated review
│   └── recommendations.md      # Generated: further refined recommendations
└── ops/
    ├── ideas.md                # Op: brainstorm career ideas
    ├── questions.md            # Op: generate clarifying questions
    ├── review.md               # Op: review professional materials
    ├── revise.md               # Op: revise recommendations
    └── plan.md                 # Op: generate final career plan
```

## Human-in-the-Loop Checkpoints

The pipeline pauses at question files to incorporate your input:

1. After `round_1/questions.md` is generated, **open the file and answer each question**.
2. Re-run the pipeline — it will detect your answers and proceed to `round_2`.
3. Repeat as needed until you're satisfied with the recommendations.

## Tips

- **Be specific in your profile** — The more detail you provide, the better the recommendations.
- **Answer questions thoroughly** — Your answers directly shape the next round of analysis.
- **Iterate** — Two or three rounds typically produce significantly better results than a single pass.
- **Review intermediate outputs** — Each generated file is worth reading; they contain valuable insights even before the final plan.