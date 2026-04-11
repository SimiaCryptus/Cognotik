# Philosophical Calculator — Demo Script

## Scene 1: Introduction & Input Setup
**[0:00–0:28]**

> **Narration:**
> One of my favorite apps is the Philosophical Calculator. Like a normal calculator, the first step is to give it some input. Here, we're going to supply a simple prompt about Connect Four.

**Actions:**
1. Open the Philosophical Calculator app in the UI.
2. Navigate to the input section.
3. Type a short prompt (e.g., a request for a guide to Connect Four).
4. Note: The app also supports file uploads, but for this demo we use a simple text prompt.

---

## Scene 2: Model Selection
**[0:28–0:39]**

> **Narration:**
> The first step in using the Philosophical Calculator is to make sure we have some models selected. We'll go with Haiku for text and Gemini Flash Image for illustrations.

**Actions:**
1. Open the model selection panel.
2. Select **Haiku** (text model).
3. Select **Gemini Flash Image** (image-capable model).
4. Confirm selections are visible in the UI.

---

## Scene 3: Draft Article
**[0:39–1:22]**

> **Narration:**
> Now we start the pipeline. Since our input is fairly small, we're going to skip the "Summarize Notes" step and go straight to "Draft Article." We run that in the UI and wait for it to complete. This processing session is a one-step operation, so its details aren't particularly interesting — we'll look at session monitoring in depth when we run lenses next.

**Actions:**
1. Open the pipeline view.
2. Skip the **Summarize** step (point it out but don't click it).
3. Click **Draft Article** to run `draft_article_op`.
4. Wait for the operation to complete.
5. Show the completed draft article in the formatted preview pane.

> **Narration:**
> And here we have the completed draft article, shown in a formatted preview.

---

## Scene 4: Lenses — Multi-Perspective Analysis
**[1:22–2:55]**

> **Narration:**
> Now that we have the draft article, we can proceed to Lenses. This is where it gets really interesting. The Philosophical Calculator provides a variety of analytical operations you can perform on your draft article — brainstorming, multi-perspective analysis, dialectical reasoning, even writing a comic book about the article.

**Actions:**
1. Navigate to the **Lenses** section of the UI.
2. Slowly scroll through the available lenses so the viewer can see the full list:
   - Brainstorm, Dialectical, Socratic, Perspectives, Persuasive, Game Theory, Narrative, Comic, Technical Explanation.

> **Narration:**
> For demonstration purposes, let's go with Perspective Analysis. I'll click "Run," and in a moment a session link will appear. This session link lets us monitor the multi-perspective analysis task in real time as it proceeds through multi-step reasoning.

**Actions:**
3. Select **Perspectives** lens.
4. Click **Run**.
5. When the session link appears, click it to open the session monitoring view.
6. Show the real-time session output updating as perspectives are generated.

> **Narration:**
> The session monitoring UI lets us watch the agent work through each perspective in real time. This will take a moment because it analyzes the content from a number of different viewpoints and then synthesizes the results.

---

## Scene 5: Monitoring Usage
**[2:55–3:30]**

> **Narration:**
> While the analysis runs, we can go over to the Usage tab and monitor token consumption. So far we've spent about four cents on this process. As the multi-perspective dialogue continues, you can see the cost ticking up — it just used another penny.

**Actions:**
1. Switch to the **Usage** tab in the main UI.
2. Point out the token count and cost display.
3. Show the cost incrementing as the lens operation progresses.

> **Narration:**
> Multiple lenses can be run in parallel if you want, depending on what analysis you feel is appropriate. When they complete, we can fold the results back into the article.

---

## Scene 6: Reviewing Lens Results
**[4:54–6:03]**

> **Narration:**
> The lens run is now complete. We can see the detailed output in the UI. Here's the multi-perspective analysis transcript, which details each perspective in turn.

**Actions:**
1. Return to the **Lenses** section.
2. Click on the completed **Perspectives** lens result.
3. Scroll through the output, pausing briefly on a specific perspective (e.g., the "Competitive Player" perspective).

> **Narration:**
> For example, here's the Competitive Player perspective. The analysis goes through each perspective — it's quite thorough — and at the end you'll find the Synthesis and Recommendations section.

**Actions:**
4. Scroll to the bottom of the lens output to show the **Synthesis and Recommendations** section.

> **Narration:**
> Lenses generally produce a detailed set of analyses and then conclude with a summary. These summaries can be folded back into the original article using the "Update Article" step in the pipeline. However, for brevity, we'll skip that and demonstrate one last feature.

---

## Scene 7: Illustrate Article
**[6:03–8:12]**

> **Narration:**
> The Illustrate Article feature is one of the most entertaining. It takes your article content, designs a number of illustrations, generates each image using an image-capable model, and then edits the document to weave the images in.

**Actions:**
1. Return to the pipeline view.
2. Click **Illustrate Article**.
3. When the session link appears, click it to open the session monitor.

> **Narration:**
> Again, we can monitor this in real time. First it comes up with the image ideas, and then it generates each image one by one.

**Actions:**
4. Show the session monitor as image concepts are listed.
5. Wait as images are generated (this takes a minute or so).
6. Show the integration step where images are inserted into the document.

> **Narration:**
> After generating each image, it integrates them into the original document by editing the markdown to include image links.

---

## Scene 8: Final Result
**[8:12–end]**

> **Narration:**
> And here we have our article — now fully illustrated. For about sixty-two cents, we have a complete, illustrated guide to Connect Four.

**Actions:**
1. Show the final article in the formatted preview pane.
2. Scroll through slowly so the viewer can see the illustrations embedded in the article.

> **Narration:**
> This was just a demo, of course. I hope you enjoy using this tool to explore your own ideas.

**Actions:**
3. Pause on the final view for a few seconds before ending.

---

## Production Notes

- **Total demo length:** ~9 minutes
- **Demo cost:** ~$0.62 in API usage
- **Models used:** Haiku (text), Gemini Flash Image (illustrations)
- **Example topic:** A guide to Connect Four
- **Key features highlighted:**
  - Simple prompt input (file upload also supported)
  - Pipeline with skip-able steps (Summarize → Draft → Lenses → Update → Illustrate)
  - 12 available analytical lenses
  - Real-time session monitoring via session links
  - Usage/cost tracking during processing
  - Parallel lens execution capability
  - AI image generation and automatic document integration