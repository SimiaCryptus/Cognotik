# The Sys Wizard App

## Introduction
*[0:00]*

The Sys Wizard app is powerful and dangerous.

## The Problem
*[0:10]*

Sometimes you need to do something with your system. You need to install something, or figure out what's using all the disk space, or something else that you might normally go to the command line for.

However, with AI and Cognotic, you can have an LLM write the shell script for you.

## Using the System Wizard
*[0:38]*

You start at the Goal tab of the System Wizard and type in what you want to do. Let's, for this demonstration, see your running processes.

We saved the goal. We are using Windows here. This also supports Shell, which is Linux or Mac OS. It specifically uses Bash, but we're going to be using Windows and PowerShell.

## Settings and Pipeline
*[1:13]*

We list running processes in settings. We make sure that we have a model selected. We have Gemini 3 Flash Preview we're going to use in this demo, and then we go to the pipeline and generate a shell script. That will be generated momentarily.

## Running the Script
*[1:49]*

We can either use this directly in our own shell, or we can click "Run and Fix" to test the script.

Now, in this Run and Autofix session that appears, if an error occurs, it will look into the error and then try to code up a fix. If an error were to happen, you would be able to fix it. You would be able to retry the execution in this interface.

## Results
*[2:28]*

However, the first execution was successful and it is listing all the processes that we can view here. The UI shows that the command succeeded. Note that the UI does not show the output of the command. You have to go into the session link to view that detail.

## Conclusion
*[2:47]*

And there you go. That is the System Wizard. That is the System Wizard application. I hope you find it useful.