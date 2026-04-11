# The System Wizard App

## Overview

The System Wizard app is powerful and dangerous. Sometimes you need to do something with your system — install something, figure out what's using all the disk space, or something else that you might normally go to the command line for. However, with AI and Cognotic, you can have an LLM write the shell script for you.

## Getting Started — The Goal Tab

You start at the **Goal** tab of the System Wizard and type in what you want to do. For this demonstration, let's list your running processes.

After saving the goal, you select your platform. This demo uses **Windows** with **PowerShell**, but the app also supports Shell (Bash) for Linux or Mac OS.

## Settings

In **Settings**, make sure you have a model selected. For this demo, we're using **Gemini 3 Flash Preview**.

## Generating the Script — The Pipeline

Navigate to the **Pipeline** and generate a shell script. The script is generated momentarily.

## Running and Auto-Fixing

Once the script is generated, you can either:

- Use it directly in your own shell, or
- Click **Run and Fix** to test the script

In the **Run and Autofix** session that appears, if an error occurs, the system will analyze the error and attempt to code up a fix. You can then retry the execution from the same interface.

## Results

In this demonstration, the first execution was successful and listed all the running processes. The UI shows that the command succeeded.

> **Note:** The UI does not show the output of the command directly. You have to go into the session link to view that detail.

## Conclusion

That is the System Wizard application. I hope you find it useful.