---
template_vars:
  CHOICE: a
  CHOICE_LABEL: A
transforms: ../story/([^./]+)\.md -> ../story/$1{{CHOICE}}.md
related:
  - ../story/world_facts.md
---

You have chosen option {{CHOICE_LABEL}}. This path will lead you to a different branch of the story, where you will encounter new
characters, settings, and challenges.

Write the next node of the story based on this choice, introducing new elements and advancing the plot in a compelling
way.

Remember to keep the narrative engaging and to provide clear options for the user to continue exploring the story.

Consult `world_facts.md` before writing to ensure consistency with already-established facts.

---

## End State Guidance

This node **may be a story ending** rather than a continuation. Use the following rules to decide:

### When to end the story

Evaluate the current node's filename depth (e.g. `start.md` = depth 0, `starta.md` = depth 1,
`startab.md` = depth 2, etc.) and the tone of the chosen path:

| Condition                                                                                                | End Probability                           |
|----------------------------------------------------------------------------------------------------------|-------------------------------------------|
| Depth 3–4                                                                                                | ~15% chance of ending                     |
| Depth 5–6                                                                                                | ~35% chance of ending                     |
| Depth 7–8                                                                                                | ~60% chance of ending                     |
| Depth 9+                                                                                                 | ~85% chance of ending                     |
| This choice was aggressive, reckless, or violent                                                         | +25% to above                             |
| This choice was wise, diplomatic, or heroic                                                              | +10% to above (but skewed toward triumph) |
| A game-breaking status has been reached (legendary item, fulfilled prophecy, defeated final enemy, etc.) | Force a **triumphant ending**             |
| The hero has suffered catastrophic loss, mortal injury, or irreversible doom                             | Force a **doom ending**                   |

### Types of endings

- **Triumphant Ending** — The hero achieves something legendary. The world is changed. Write a rich, satisfying
  conclusion. No choices follow.
- **Doom Ending** — The hero falls, fails, or is consumed. This is not forbidden — it is part of the world. Write it
  with weight and meaning. No choices follow.
- **Bittersweet Ending** — The hero survives but at great cost, or wins something small in a vast darkness. Poignant and
  complete. No choices follow.

Endings should feel **earned and creative** — they are not failures of the game, they are its highest expression.

---

## Output Rules

Produce **two file outputs**, clearly separated as shown below.

**If this is a continuing node**, present the user with 3 new choice options (labeled A, B, C) that will lead to
different branches of the story. At least one option should carry narrative risk.

**If this is an ending node**, write a complete and evocative conclusion. Do not present choices. The story node should
close with a final line or epitaph that gives the ending its identity.

---

Output format:

FILE: story node (the transformed output file)

```markdown
# Node Title

Node content goes here.

<!-- If continuing: -->

### What shall we do?

* **Choice A** - Option A description
* **Choice B** - Option B description
* **Choice C** - Option C description

<!-- If ending: -->
*[Epitaph or closing line — e.g. "And so the age of the Ember Crown came to its end."]*
```

FILE: ../story/world_facts.md

```markdown
(full updated contents of world_facts.md, adding any new persistent facts introduced in this node —
such as new characters, locations, factions, or world rules. Do NOT record plot events or timeline-specific details.)
```