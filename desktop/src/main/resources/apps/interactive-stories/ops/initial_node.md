---
specifies: ../story/0.md
related:
  - ../story_idea.md
   - ../story/world_facts.md
---

Given the user idea, initialize the story by creating the first node. 
This node should introduce the main character and the setting of the story, as well as hint at the central conflict or goal that will drive the narrative forward. 
The node should be engaging and compelling, encouraging the user to continue exploring the story.

At the end of the node, present the user with 3 choice options (labeled A,B,C) that will lead to different branches of the story.
Produce three file outputs, clearly separated as shown below.

Output format:

FILE: ../story/0.md
```markdown
## Node Title

Node content goes here.

### Custom choice prompt here?

* **Choice A** - Option A description
* **Choice B** - Option B description
* **Choice C** - Option C description
```

FILE: ../story/world_facts.md
```markdown
(full contents of world_facts.md, recording foundational facts established in this node —
such as character names and traits, the setting, factions, or world rules. Do NOT record plot events or choices made; only record persistent, universe-level facts.)
```

FILE: ../story/image_style.md
```markdown
(A concise description of the visual style to be used for all images generated in this story —
such as art style, color palette, mood, lighting, and any recurring visual motifs. This should
be consistent with the tone and setting of the story and will be referenced by all image generation operations.)
```

FILE: ../story/audio_style.md
```markdown
(A concise description of the audio/sonic style to be used for all audio generated in this story —
such as ambient soundscape, musical genre/instrumentation, mood, tempo, and any recurring sonic motifs.
This should be consistent with the tone and setting of the story and will be referenced by all audio
generation operations. Keep it brief — a few sentences at most.)
```