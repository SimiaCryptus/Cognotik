# AI Job Hunter

An automated job search pipeline that uses AI to find, research, and apply for jobs matching your profile.

## Overview

AI Job Hunter is a multi-step pipeline that:
1. **Searches the web** for job opportunities matching your resume and requirements
2. **Extracts and organizes** job matches into structured files
3. **Researches companies** to give you background on potential employers
4. **Generates applications** with tailored cover letters and materials

## Getting Started

### Prerequisites
- A running instance of the AI backend server
- Valid API keys configured for your chosen AI models

### Setup

1. **Configure Models** — Select AI models for different tasks:
   - **Smart Model**: Used for analysis and summarization (e.g., GPT-4, Claude Opus)
   - **Fast Model**: Used for high-volume processing (e.g., GPT-3.5, Claude Haiku)
   - **Image Model**: Optional, for processing visual content

2. **Add Your Resume** — Paste your resume in JSON format into the Resume tab

3. **Set Requirements** — Define what you're looking for in the Requirements tab:
   - Remote/on-site preference
   - Salary range
   - Tech stack preferences
   - Company culture preferences
   - Companies to avoid

4. **Run the Pipeline** — Click "Run Full Pipeline" or execute steps individually

## Pipeline Steps

### 1. Web Research (`job_search.md`)
Crawls the web using Google search to find job postings that match your profile. Configured via `job_search.json`:
- Searches up to 500 pages
- Depth-first crawl up to 3 levels
- Uses `JobMatching` processing strategy

### 2. Copy Matches (`copy_job_matches.md`)
Runs a shell script to extract and copy job matches into the `job_matches/` directory, organizing each opportunity into its own folder.

### 3. Company Research (`company_research.md`)
For each job match, crawls the company's website and public information to generate a background report from the perspective of a prospective employee. Configured via `company_background.json`.

### 4. Generate Applications
Creates tailored cover letters and application materials based on your resume, the job description, and the company research.

## File Structure

```
job-hunter/
├── app.html                  # Main UI
├── app.js                    # Application logic
├── style.css                 # Styles
├── README.md                 # This file
├── resume.json               # Your resume data
├── requirements.md           # Your job search requirements
├── companies_to_avoid.txt    # Companies to skip
├── ops/
│   ├── job_search.md         # Job search task definition
│   ├── job_search.json       # Job search crawler config
│   ├── copy_job_matches.md   # Copy matches task definition
│   ├── copy_job_matches.sh   # Shell script for copying matches
│   ├── company_research.md   # Company research task definition
│   └── company_background.json # Company research crawler config
├── job_matches/              # Generated job match files
│   └── <search-run>/
│       └── <company-job>.md  # Individual job match
│           company_research.md # Company background report
└── utils/
    ├── ui.js
    ├── session.js
    ├── fileIO.js
    ├── docops.js
    ├── models.js
    ├── usage.js
    ├── git.js
    └── sessionLinks.js
```

## Configuration

### Crawler Settings (`job_search.json` / `company_background.json`)

| Setting | Description |
|---|---|
| `seed_method` | How to seed URLs (`GoogleProxy`) |
| `fetch_method` | How to fetch pages (`HttpClient`) |
| `processing_strategy` | How to process content (`JobMatching`, `DefaultSummarizer`) |
| `max_pages_per_task` | Maximum pages to crawl per task |
| `max_depth` | Maximum link-follow depth |
| `concurrent_page_processing` | Parallel page processing threads |
| `max_final_output_size` | Maximum size of final output in characters |
| `respect_robots_txt` | Whether to honor robots.txt |

## Tips

- Use **specific requirements** for better job matches
- **Review results** between pipeline steps and refine as needed
- Add companies you've already applied to in **Companies to Avoid** to prevent duplicates
- Use **Git integration** (🗂️ button) to track changes and maintain history
- Monitor **usage costs** (💰 button) to stay within budget