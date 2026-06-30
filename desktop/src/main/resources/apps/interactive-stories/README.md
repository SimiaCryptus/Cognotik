# 📖 Interactive Stories

**Your story. Your choices. Powered by AI.**

Interactive Stories lets you craft rich, branching narratives — no writing experience required. Describe a premise, hit a button, and watch an AI spin it into a living story tree where every decision opens a new path. Come back anytime to explore the roads not taken.

---

## ✨ What can it do?

- **Generate a full story from a single idea.** Just describe a setting, character, or situation — the AI does the rest.
- **Branch in any direction.** Every scene ends with three choices (A, B, C). Pick one and the story continues down that path. Change your mind? Jump back to any earlier point and try a different route.
- **Illustrate every scene.** Generate a unique AI image for each story node so you can *see* the world you're building.
- **Narrate your story aloud.** Generate ambient audio narration for any scene, or use your browser's built-in text-to-speech with word-by-word highlighting so you can follow along.
- **Make it look the way you want.** Describe a visual style in plain English and the app will update its own stylesheet to match — dark and moody, bright and whimsical, whatever fits your story.

---

## 🚀 Getting started

### 1. Choose your AI models
At the top of the page, pick a **Smart Model** (for rich story writing), a **Fast Model** (for quick tasks), an **Image Model**, and an **Audio Model**. Your choices are saved automatically between visits.

### 2. Write your premise
Type your story idea into the **Story Idea** box. It can be as short as a sentence or as detailed as a paragraph — the AI will work with whatever you give it.

> *"A weary detective in a rain-soaked cyberpunk city receives a mysterious letter from a long-dead colleague…"*

Click **✨ Begin Story** to generate the opening scene.

### 3. Read and choose
Your opening scene appears in the **Current Node** panel. Read it, then click one of the three choice buttons (A, B, or C) to continue the story down that branch. Each new scene is generated fresh by the AI.

### 4. Explore the tree
The **Story Tree** panel shows every scene you've generated so far. Click any node to jump back to it — already-explored branches show a ✓ and load instantly. New branches are generated on demand.

### 5. Add images and audio
- Click **🖼 Generate Image** to create an illustration for the current scene.
- Click **🎙 Generate Audio** to create a narrated audio track.
- Enable **Auto-generate images** or **Auto-generate audio** to have these created automatically every time a new scene loads.

### 6. Read aloud
Click **🔊 Read Aloud** to have the current scene read to you using your browser's text-to-speech. Words are highlighted as they're spoken so you can follow along. You can also pick a preferred voice from the **Voice** dropdown at the top.

### 7. Immersive mode
Click the **⛶** button (or press **F**) to enter full-screen immersive mode — just you and the story, no distractions. Press **Esc** to return.

### 8. Restyle the app
Scroll down to **🎨 Update Stylesheet**, describe the look you want in plain English, and click **🖌 Update Stylesheet**. Reload the page to see your changes applied.

---

## 💡 Tips

- **Your idea is auto-saved** as you type, so you won't lose it if you navigate away.
- **Branches are never overwritten.** Clicking an existing branch just navigates to it. Only the root scene asks for confirmation if you regenerate it.
- **The Activity Log** at the bottom shows what the AI is working on in real time. Use the 🔄 button on the Story Tree to manually refresh if you think a new scene has finished generating.
- **Stories can go deep.** The AI naturally steers toward an ending as your path grows longer, but you can keep branching for as long as you like.
- **Each story is self-contained.** The AI keeps track of characters, locations, and world rules behind the scenes so your story stays consistent across branches.

---

## 📁 What gets saved?

Everything lives in your session folder:

| File | What it is |
|---|---|
| `story_idea.md` | Your saved premise |
| `story/0.md` | The opening scene |
| `story/0a.md`, `story/0b.md`, … | Scenes reached by choosing A, B, or C |
| `story/0ab.md`, `story/0ac.md`, … | Deeper branches, and so on |
| `story/*.png` | Illustrations for each scene |
| `story/*.wav` | Audio narration for each scene |
| `style.css` | The app's visual style (updated by the stylesheet tool) |

The branching path is encoded right in the filename — `0abc` means you chose A from the root, then B, then C — so the entire history is visible at a glance.

---

Enjoy your story. 🌿