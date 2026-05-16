# Pipeline

## Stages

### 1. Render
**Operation:** `ops/render_op.md`
**Type:** `SubPlan`
**Config:** `render_project.json`

Renders the software idea (from `idea.md`) into a full project using JavaScript, CSS, and HTML. Produces game design and spec documents before implementation. Outputs to the `code/` folder, including `index.html` and `README.md`.

**Alternatively:** `ops/render_simple_op.md`

A simpler, single-step render (no SubPlan config) that performs the same transformation without a sub-plan configuration file.

---

### 2. Test
**Operation:** `ops/test_op.md`
**Type:** `SeleniumFetch`

Loads `index.html` in a browser and captures:
- Screenshot
- Browser console output (`test.console.log`)
- Network access log (`test.network.log`)
- Fully-rendered HTML (`test.html`)
- A brief `README.md` summarizing the captured artifacts

**Specifies:** `code/test.md`

---

### 3. Review
**Operation:** `ops/review_op.md`

Examines the test artifacts produced in the Test stage and writes a summary of findings — including bugs, issues, and unexpected behaviors — to `notes.md`.

**Inputs:**
- `code/test.console.log`
- `code/test.network.log`
- `code/test.html`

**Specifies:** `notes.md`

---

### 4. Update
**Operation:** `ops/update_op.md`

Reads `notes.md` (user feedback, bug reports, feature requests, design changes) and applies the requested changes to the existing project files in `code/`. Preserves existing functionality not mentioned in the notes and updates `README.md` if warranted.

**Inputs:**
- `notes.md`
- `idea.md`
- `code/**`

---

## Flow

```
idea.md
   │
   ▼
[Render]  ──►  code/  (index.html, README.md, assets, specs)
                │
                ▼
            [Test]  ──►  code/test.console.log
                         code/test.network.log
                         code/test.html
                         code/test.md
                │
                ▼
            [Review]  ──►  notes.md
                │
                ▼
            [Update]  ──►  code/  (patched files)
                │
                └──────────────► (repeat Test → Review → Update as needed)
```

## Operations Reference

| File | Type | Input | Output |
|---|---|---|---|
| `ops/render_op.md` | `SubPlan` (`render_project.json`) | `idea.md` | `code/` |
| `ops/render_simple_op.md` | *(default)* | `idea.md` | `code/` |
| `ops/test_op.md` | `SeleniumFetch` | `code/index.html` | `code/test.*`, `code/test.md` |
| `ops/review_op.md` | *(default)* | `code/test.*` | `notes.md` |
| `ops/update_op.md` | *(default)* | `notes.md`, `idea.md`, `code/**` | `code/` |