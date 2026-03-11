# Puppy Research Workflow

This repository contains a multi-step pipeline designed to help a user navigate the process of selecting a dog breed and finding reputable breeders.

## Workflow Overview

The process follows a linear progression from high-level requirements to a detailed final summary:

1.  **Brainstorming:** Generates a list of potential breeds based on user requirements.
2.  **Expansion:** Creates detailed profiles for each brainstormed breed.
3.  **Research:** Uses a web crawler to find specific breeders for those breeds.
4.  **Summarization:** Aggregates all research into a final decision-making report.

---

## Operation Files

### 1. Breed Brainstorming (`breed_brainstorm_op.md`)
*   **Input:** `requirements.md`
*   **Output:** `ideas.md`
*   **Task:** Takes the user's initial criteria (e.g., size, energy level, temperament) and brainstorms a list of suitable dog breeds.

### 2. Breed Expansion (`breed_expand_op.md`)
*   **Input:** `ideas.md`
*   **Output:** Multiple `[breed_name]_breed.md` files and `expand_status.md`.
*   **Task:** Breaks down the list of ideas into individual files. Each file contains a deep dive into the specific characteristics of that breed.

### 3. Breeder Research (`breeder_research_op.md`)
*   **Input:** `[breed_name]_breed.md`
*   **Output:** `[breed_name]_breeder_research.md`
*   **Task:** Utilizes a `CrawlerAgent` to search the web for active breeders, health testing standards, and availability for the specific breed.

### 4. Final Summary (`breeder_summary_op.md`)
*   **Input:** All generated `_breed.md` and `_breeder_research.md` files.
*   **Output:** `final_summary.md`
*   **Task:** Consolidates all the gathered information into a single, easy-to-read report to help the user make a final choice.

---

## How to Use

1.  **Create Requirements:** Start by creating a `requirements.md` file in the project directory detailing what you are looking for in a dog (e.g., "I live in an apartment and want a low-shedding dog").
2.  **Run the Pipeline:** Execute the operations in order. The system will automatically detect the input files based on the `transforms` regex defined in the frontmatter of each `.md` file.
3.  **Review Results:** The final output will be generated in `final_summary.md`.

## Technical Requirements

This workflow is designed to be executed by an LLM-based automation engine that supports:
*   **Regex-based file transformations.**
*   **Task types** such as `Brainstorming` and `CrawlerAgent`.
*   **Frontmatter configuration** for pipeline logic.
