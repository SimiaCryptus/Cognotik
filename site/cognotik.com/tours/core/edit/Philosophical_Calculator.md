# 🧮 Philosophical Calculator

A walkthrough of the Philosophical Calculator app — an AI-powered analytical toolkit that transforms simple input into a richly analyzed, fully illustrated article through multi-perspective reasoning and creative lenses.

## Overview

The Philosophical Calculator takes input content and processes it through a pipeline of AI-driven operations: drafting, analytical lenses, and illustration. This demo follows the complete journey from a short prompt ("a guide to Connect Four") to a fully illustrated article, produced for approximately $0.62 in AI usage costs.

## Input and Setup

The first step is supplying input. The app accepts either a text prompt or uploaded files. For this demo, a simple prompt is used — a brief instruction to write a guide about Connect Four in Act 4 format.

Before running the pipeline, models must be selected. The demo configures:

- **Haiku** — for text generation tasks (drafting, analysis)
- **Gemini Flash Image** — for illustration generation

## The Pipeline

The Philosophical Calculator operates as a sequential pipeline with optional branches. Each stage builds on the output of the previous one.

### Step 1: Draft Article

Since the input is small, the summarization step is skipped entirely. The pipeline proceeds directly to **Draft Article**, which generates a complete article (`content.md`) based on the prompt.

The draft operation runs in the UI. Once complete, the result is displayed in a formatted preview. This is a one-step operation — its session details are straightforward and not explored in depth here.

### Step 2: Lenses

With a draft article in hand, the real power of the Philosophical Calculator comes into play. **Lenses** are analytical operations that examine the article from different angles. The app provides a variety of lenses:

| Lens | Description |
|------|-------------|
| **Brainstorm** | Divergent ideation — generates broad ideas, extensions, and applications |
| **Dialectical** | Thesis/antithesis/synthesis analysis of core themes |
| **Socratic** | Transforms content into a rigorous Socratic dialogue |
| **Perspectives** | Multi-stakeholder perspective analysis |
| **Persuasive** | Reframes content as a compelling persuasive essay |
| **Game Theory** | Analyzes concepts through game theory (players, strategies, payoffs, equilibria) |
| **Narrative** | Dramatizes the content with illustrations |
| **Comic** | Generates a comic book representation of the content |
| **Technical Explanation** | Precise, in-depth technical breakdown with examples and definitions |

For this demo, **Perspective Analysis** is selected.

#### Real-Time Session Monitoring

When a lens is launched, a session link appears in the UI. Clicking it opens a real-time monitoring view — the standard interface for observing AI agents as they work through multi-step reasoning.

The multi-perspective analysis proceeds through several phases:

1. **Individual perspectives** — The system examines the article from distinct stakeholder viewpoints (e.g., the competitive player perspective), generating detailed analysis for each.
2. **Synthesis** — After all perspectives are explored, the system produces a **Synthesis and Recommendations** section that summarizes findings across all viewpoints.

The result is a lengthy, structured analysis document. Each perspective is presented in turn, followed by the consolidated synthesis at the end.

#### Usage Tracking

While lenses are running, the **Usage** tab in the main UI displays real-time token consumption and cost. During this demo, the multi-perspective analysis consumed a few cents as it progressed through each reasoning step.

#### Parallel Execution

Multiple lenses can be run simultaneously if desired. Each lens operates independently on the draft article, so there is no conflict between concurrent runs.

### Step 3: Update Article (Optional)

After lenses complete, the pipeline offers an **Update Article** step. This operation takes the output of all completed lens runs and folds their insights, corrections, and new ideas back into the original draft article — enriching and refining the content based on the analytical results.

This step is skipped in the demo for brevity.

### Step 4: Illustrate Article

The final feature demonstrated is **Illustrate Article**. This operation:

1. Analyzes the article content to design a set of appropriate illustrations
2. Generates each image using the configured image-capable model (Gemini Flash Image)
3. Integrates the generated images directly into the article by inserting image links at relevant positions in the document

Like lenses, this operation provides a session link for real-time monitoring. The system works through each illustration sequentially — designing, generating, and then weaving the images into the document.

## Final Result

The end product is a fully illustrated guide to Connect Four — a complete article with AI-generated images embedded throughout. The entire process, from initial prompt through drafting, multi-perspective analysis, and illustration, cost approximately **$0.62** in total AI usage.

## Key Takeaways

- **Simple input, rich output** — A short text prompt produces a complete, analyzed, and illustrated article
- **Modular pipeline** — Each stage (draft, lenses, update, illustrate) can be run independently or in sequence
- **Real-time observability** — Session links provide live monitoring of multi-step AI reasoning processes
- **Cost transparency** — The Usage tab tracks token consumption and cost throughout the workflow
- **Extensible analysis** — Twelve different analytical lenses offer diverse perspectives on any content