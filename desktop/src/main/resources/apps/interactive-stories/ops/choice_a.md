---
transforms: ../story/([^./]+)\.md -> ../story/$1a.md
---

You have chosen option A. This path will lead you to a different branch of the story, where you will encounter new characters, settings, and challenges.

Write the next node of the story based on this choice, introducing new elements and advancing the plot in a compelling way. 

Remember to keep the narrative engaging and to provide clear options for the user to continue exploring the story.

At the end of this node, present the user with 3 new choice options (labeled A,B,C) that will lead to different branches of the story.

Output format:

```markdown
# Node Title

Node content goes here.

### What shall we do?

* **A** - Option A description
* **B** - Option B description
* **C** - Option C description
```

