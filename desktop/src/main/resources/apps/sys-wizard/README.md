# 🧙 System Wizard

**Describe what you want your computer to do — the wizard writes and runs the script for you.**

System Wizard turns plain-language goals into working shell scripts, automatically fixes any errors, and shows you the results — no coding required.

---

## What Can It Do?

Ever wished you could just *tell* your computer what to do? System Wizard makes that possible. Simply describe your goal in everyday language, and the AI will:

- **Write a shell script** tailored to your exact goal
- **Run it automatically** on your system
- **Fix any errors** it encounters — on its own, without you lifting a finger
- **Show you the results** in a clean, easy-to-read format

Whether you want to clean up Docker containers, analyze log files, back up important folders, or gather system information, System Wizard handles the technical details so you don't have to.

---

## How to Use It

### Step 1 — Describe Your Goal
Open the **Goal** tab and type what you want to accomplish. Be as specific as you like — the more detail you provide, the better the result.

> **Example:** *"Find all log files in /var/log older than 30 days, compress them, and save a summary to /tmp/log-cleanup.txt"*

Not sure where to start? Click one of the **example prompts** (Docker cleanup, log analysis, backup, system info) to get going instantly.

### Step 2 — Run the Pipeline
Click **▶ Run Entire Pipeline** and let the wizard do its thing. You'll see a live progress log as it:
1. Saves your goal
2. Generates a shell script
3. Runs the script and automatically fixes any issues

### Step 3 — Review Your Results
Switch to the **Results** tab to see:
- The final shell script that was generated
- A full execution log showing what happened (and any fixes that were made)
- Your original goal for reference

You can **copy** or **download** the script directly from the Results tab.

---

## Tabs at a Glance

| Tab | What it's for |
|---|---|
| 📋 **Goal** | Describe what you want to accomplish |
| ⚙️ **Pipeline** | Generate, review, and run your script step by step |
| 📊 **Results** | View the final script and execution output |
| 💰 **Usage** | See how many AI tokens were used and estimated cost |
| 🔧 **Settings** | Choose which AI models to use |

---

## Tips for Great Results

- **Be specific** — mention file paths, service names, or exact commands when you know them
- **Describe the output** — say where you want results saved or how they should be displayed
- **Add safety notes** — if you want a dry run or don't want anything deleted, say so
- **One goal at a time** — complex multi-step tasks work best as a single, clear goal
- **Already have a script?** — Switch to *Paste / Edit* mode in the Pipeline tab to use your own script and skip AI generation entirely

---

## Advanced: Running Steps Individually

Prefer more control? The **Pipeline** tab lets you run each step on its own:

1. **Generate** — Have the AI write the script, then review it before running
2. **Run & Fix** — Execute the script (and auto-fix errors) only when you're ready

You can also **paste or edit your own script** and use the auto-fix feature without AI generation.

---

## Keeping an Eye on Costs

The **Usage** tab shows a real-time breakdown of AI token usage and estimated costs for your session — broken down by model. Use a faster, cheaper model for the *Fast Model* setting to keep costs low, and reserve a more powerful model for script generation.

---

## Settings

Before running the pipeline for the first time, visit the **Settings** tab to choose your AI models:

- **Smart Model** — Used to write your script (pick something capable, like GPT-4o or Claude Sonnet)
- **Fast Model** — Used for quick fixes and parsing (a smaller, faster model works great here)

Your choices are saved in your browser and reused automatically next time.