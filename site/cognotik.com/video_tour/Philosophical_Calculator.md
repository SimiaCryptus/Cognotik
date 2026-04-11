# The Philosophical Calculator

A walkthrough of the Philosophical Calculator app — a tool that takes input, drafts articles, runs multi-perspective analysis "lenses," and even illustrates your content with AI-generated images.

---

## Introduction
*[0:00]*

One of my favorite apps is the Philosophical Calculator. Like a normal calculator, the first step is to give it some input. Here, we're going to supply a simple prompt. We could also supply files, but in this case we'll go with a simple prompt.

## Setting Up the Pipeline
*[0:34]*

The first step in using the Philosophical Calculator is to make sure we have some models selected. We're going with Haiku and Gemini Flash Image.

Then we start the pipeline. Since our input is fairly small, we're going to skip the "Summarize Notes" part and go straight to "Draft Article." We can run that in the UI and wait for it to complete.

We could view the session to monitor it in real time, but this processing session is a one-step operation — its details aren't particularly interesting. We'll view session detail in depth when we execute lenses, which is next.

## Draft Article Complete
*[1:22]*

We now have the completed draft article, shown in a formatted preview. With the draft article in hand, we can proceed to lenses. Here's where it gets really interesting.

## Lenses: Multi-Perspective Analysis
*[1:34]*

The Philosophical Calculator provides a variety of operations that you can perform on your draft article. It performs different types of analysis — brainstorming, multi-perspective analysis, or even writing a comic book about the article.

For demonstration purposes, let's go with **Perspective Analysis**. After clicking "Run," a session link pops up. This session link allows us to monitor the multi-perspective analysis task in real time as it proceeds through a multi-step reasoning process.

The user interface you're seeing here is the standard interface for real-time agents and analysis. This will take a moment because it goes through a number of different perspectives, and then finally it synthesizes the results — basically summarizing all of the perspectives.

## Monitoring Usage
*[2:55]*

In the main UI, we can go over to "Usage" and monitor the token usage as it proceeds. So far, we have spent about 4 cents on this process. The multi-perspective dialogue is ongoing, so it just used another penny.

When it completes, the UI will update. We're only going to run this one lens to demonstrate this module, but you could run each of these in parallel, depending on what you felt was appropriate.

## Reviewing Lens Results
*[4:54]*

When the lenses are completed, we can go back to the pipeline and update the article, which takes the output of all the lens runs and tries to fold them back into the article — picking up insights, new ideas, and perhaps correcting issues.

Looking at the lens output, we can see the detailed results for a given lens run in the UI. Here we see the multi-perspective analysis transcript, which details each perspective in turn. For example, the competitive player perspective. It goes through each perspective, so it's quite a long analysis.

At the end, you'll find the **Synthesis and Recommendations** section. The lenses generally involve a very long set of analysis and then conclude with a summary. Those summaries and analysis can be folded back into the original article using the pipeline.

## Illustrate Article
*[6:03]*

For brevity's sake, we'll skip the article update and demonstrate one last feature: the **Illustrate Article** feature, which I think is one of the most entertaining.

This takes your content and designs a number of illustrations that it can weave into the article. A session link is shown, and again we can monitor this in real time. It comes up with image ideas and then generates each image. After generating all of the images, it integrates them into the original document by editing the document and adding in image links.

## Final Result
*[8:12]*

And now we see our article, full of illustrations. For 62 cents, we have a fully illustrated guide to Connect Four.

This of course is merely a demo. I hope you enjoy this tool and exploring your own ideas.