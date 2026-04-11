# System Wizard Demo Script

## Introduction

**Action:** Show the System Wizard app open in Cognotic.

**Narration:**
The System Wizard app is powerful — and potentially dangerous. Sometimes you need to do something with your system:
install software, figure out what's using all your disk space, or perform another task you'd normally go to the command
line for. With AI and Cognotic, you can have an LLM write the shell script for you.

---

## Setting the Goal

**Action:** Navigate to the **Goal** tab of the System Wizard.

**Narration:**
You start at the Goal tab of the System Wizard and type in what you want to do.

**Action:** Type `List running processes` into the goal field and save.

**Narration:**
For this demonstration, let's list the running processes. Save the goal.

---

## Selecting the Platform and Model

**Action:** Show the platform selector — **Windows (PowerShell)** is selected.

**Narration:**
We are using Windows here, so we'll be using PowerShell. The System Wizard also supports Shell, which covers Linux and
macOS using Bash.

**Action:** Navigate to the **Settings** tab and confirm the model selection.

**Narration:**
In Settings, make sure you have a model selected. For this demo we're using Gemini 2.5 Flash Preview.

---

## Generating the Script

**Action:** Navigate to the **Pipeline** tab and click **Generate** to produce the shell script.

**Narration:**
Now go to the Pipeline tab and generate the shell script.

**Action:** Wait for the script to appear in the output area.

**Narration:**
And here we go. You can either copy this script and use it directly in your own shell, or you can click **Run and Fix**
to test the script right here.

---

## Running and Auto-Fixing

**Action:** Click the **Run and Fix** button.

**Narration:**
Click Run and Fix to start an auto-fix session. If an error occurs during execution, the System Wizard will analyze the
error and attempt to generate a corrected script. You would then be able to retry the execution from this same
interface.

**Action:** Show the session completing successfully.

**Narration:**
In this case, the first execution was successful. The UI shows that the command succeeded.

---

## Viewing the Output

**Action:** Point out that the main UI does not display command output directly.

**Narration:**
Note that the UI does not show the output of the command inline. To see the full output, you need to go into the session
log.

**Action:** Open the session log and scroll through the list of running processes.

**Narration:**
Here you can see all the running processes listed — exactly what we asked for.

---

## Closing

**Action:** Return to the main System Wizard view.

**Narration:**
And there you go — that is the System Wizard application. I hope you find it useful.