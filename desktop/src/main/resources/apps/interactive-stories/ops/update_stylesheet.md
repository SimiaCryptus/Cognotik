---
specifies: ../style.css
related:
  - ../story/0.md
  - ../story_idea.md
  - ../stylesheet_instructions.md
---

Update the application stylesheet (`style.css`) to improve or extend the visual design of the interactive story app.

When modifying the stylesheet, use the following as **default guidelines** — but always defer to the user's explicit intent. If the user wants something wild, experimental, or deliberately over-the-top, **do it**. This is a creative tool and silly, expressive, or unconventional styling is valid and welcome.

1. **Consistency** — Ensure new or changed rules align with the existing design language:
   - Color palette: indigo/purple gradients (`#1e1b4b`, `#312e81`, `#6366f1`), neutral grays, and semantic accent colors (green, red, yellow).
   - Border radii: `8px` for inputs/buttons, `14px` for cards, `50%` for badges.
   - Typography: system font stack for UI, `ui-monospace` for code/logs/tree.

2. **Responsiveness** — Where sensible, include appropriate `@media` breakpoints. The primary breakpoint is `max-width: 600px`.

3. **Immersive mode** — If adding new UI sections or components that appear inside `#node-card`, also add corresponding `body.immersive #node-card` overrides so they render correctly in fullscreen mode (dark background, translucent surfaces, light text).

4. **Accessibility** — Maintain sufficient color contrast where possible. Focus states (`:focus` with `box-shadow` and `border-color`) must be preserved or extended for any new interactive elements. When a user explicitly requests effects that may reduce readability or cause motion, implement them faithfully and add a `prefers-reduced-motion` fallback as a courtesy — but do not refuse or silently tone down the request.

5. **Animation** — Use the existing `pulse` and `slideIn` keyframes where appropriate. For general improvements, keep animations subtle (duration ≤ 0.3 s for transitions, ≤ 2 s for loops). **When the user explicitly asks for dramatic, chaotic, or exaggerated animation effects, implement them as requested** — the limits above are defaults, not rules.

---

### Instructions

Describe the specific visual change or new component you want to style. Apply the CSS rules required to achieve it faithfully. Prefer extending existing selectors or adding new classes that follow the established naming conventions (e.g., `.btn-*`, `.step-*`, `.tree-*`, `.node-*`), but invent new ones freely when the request calls for it.

**The user's intent is the highest priority.** If they ask for something unconventional, maximalist, or just plain fun — embrace it fully. Do not second-guess, water down, or lecture about design principles unless the user asks for that guidance.

After making changes, verify:
- [ ] No existing selector is unintentionally overridden.
- [ ] New classes are documented with a short inline comment.
- [ ] Immersive-mode overrides are added where needed.
- [ ] The file remains well-organised (sections in the same order as the original).