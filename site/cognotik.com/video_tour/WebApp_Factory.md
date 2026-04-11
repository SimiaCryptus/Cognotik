# Web App Factory: Building a Graphing Calculator

## Overview

A walkthrough of using the Web App Factory to generate a fully functional web application — in this case, a graphing calculator — with just a brief description and a single pipeline step.

---

## Setting Up the Project
*(0:00)*

To generate a web application, you can use the Web App Factory. Simply put in some details of what you want to build. For this demo, we will implement a graphing calculator.

Save your idea and make sure that you have some models selected. We're going to be using Gemini 3 Flash Preview for this demo. You do have to have an image model selected even if you aren't using it — it doesn't have to be actually an image model, you just have to select something.

## Running the Pipeline
*(0:46)*

Then we go to Pipeline and select "Build Web App." This is the only step in this pipeline, but this step is quite involved. It starts by generating a project plan of tasks and then executes them appropriately, keeping in mind all of their dependencies.

Here we see the project plan with five different tasks. It's currently executing the first task. As it proceeds through the plan, the graph will update, and we just have to wait until the project is implemented.

## Viewing the Build Process
*(2:05)*

We can view the tasks themselves. Here it creates the foundational documents for the project — the HTML structure, visual style — and now it's implementing the core logic.

Once it is complete, the web app UI will update.

## Implementation Complete
*(3:14)*

The implementation is done and we can see the README in the project root. At this point we can download the zip if we want.

Also notable is that this application has Git support integrated into it. Git is a version control system that allows you to track and manage changes to your files. The files were saved to Git automatically.

## Launching and Testing the App
*(3:53)*

Now that the implementation is done, we can also launch the app. Here we see we are plotting `sin(x)`. What happens if we say `sin(x) + 10*x`? Here we go — that looks about right.

Or what about divide instead of times? What about times? Oh, power — that looks interesting.

Anyway, it seems to work, but you know what? It's too bright.

## Updating the App
*(4:36)*

Let's ask for some changes. We can update our web app using the updater. We can say: "Implement a dark theme."

Save our notes and run the update. This will update our project with our requested changes. This can also be useful if you see bugs or want a new feature or really any change.

The update is done. Let's go look at our graphing calculator, refresh it, and — we've got a theme button and it works!

## Conclusion
*(5:23)*

So that's how to use the Web App Factory to generate your own applications. We can also click "Usage" and see that this entire project demo cost 11 cents so far.

I hope you find this useful.
