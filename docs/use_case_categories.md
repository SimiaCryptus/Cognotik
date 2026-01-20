---
specifies: ../site/cognotik.com/use_case_categories.html
---
# Basic Generative Use Cases

## 0 -> 1: Content Creation

* Generate file from description
* Generate images from text prompts

## 1 -> 1: Content Transformation

* Summarization of articles
* Translation of text between languages
* Edit / feature addition to existing code files

## (A + B + ... ) -> C: Synthesis

* Synthesis by Example: Generate new content based on multiple input examples
* Pattern Extraction: Identify common patterns from multiple inputs to create generalized content
* Mass summarization: Summarize multiple documents into a single cohesive summary

## (A-B+C) -> D: Analogical Generation

* Create new content by combining and modifying existing pieces
* Useful to transfer features from one class/file to another - a sort of transgenic content generation

# Advanced Generative Use Cases

## A → A+δ: Incremental Generation

* Generate additions to existing content
* Basically telling the model to "continue" from where it left off

## A -> (B,C); B -> (D,E); ... : Hierarchical Generation

* Generate complex content through multiple stages of refinement and transformation
* Allows for exponential growth in content volume; LLM output volume from models are generally constrained by input size
* E.G. Spec-driven development

## for(1..5) A -> A: Iterative Editing

* Requires patching strategy: e.g. Full replacement or modification of existing content
* Convergence and Stability should be monitored

## while(failure) A --fix--> A: Error Correction

* Identify and correct errors in generated content using a feedback loop and an external validator
* May require many iterations to achieve desired quality, with periodic interventions

# Combined Use Cases

Some real-world workflows combine patterns:

```
Spec → (Modules)           [Hierarchical]
  ↓
Each Module → Code         [0→1 Creation]
  ↓
Code → Tests               [1→1 Transformation]
  ↓
while(tests fail) → Fix    [Error Correction]
```
