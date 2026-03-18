# 🔮 Omega — DocOps App Factory

A meta-application that designs and produces other DocOps applications. Describe the app you want in plain language, and Omega will analyze your idea, design a complete pipeline architecture, generate all op files, build the UI, write documentation, and review the result for quality.

## Features

- **Idea Analysis** — AI-powered decomposition of your app concept into pipeline stages
- **Pipeline Design** — Multi-perspective architectural design with DAG validation
- **Code Generation** — Complete op files with proper frontmatter, regex transforms, and detailed prompts
- **UI Generation** — Self-contained HTML/CSS/JS interface following DocOps conventions
- **Documentation** — Comprehensive README with usage instructions and architecture diagrams
- **Quality Review** — Multi-perspective review checking pipeline correctness, prompt quality, UI implementation, and documentation completeness

## How It Works

```
idea.md
   │
   ▼
[analyze_op]  ──►  analysis.md          (Brainstorming)
   │
   ▼
[design_pipeline_op]  ──►  pipeline_design.md  (MultiPerspectiveAnalysis)
   │
   ├──────────────────────────────┐
   ▼                              ▼
[generate_ops_op]           [generate_ui_op]
   │ (SubPlan)                    │ (FileModification)
   ▼                              ▼
generated_app/ops/*.md      generated_app/index.html
   │
   ▼
[generate_readme_op]  ──►  generated_app/README.md
   │
   ▼
[review_op]  ──►  review.md  (MultiPerspectiveAnalysis)
```

## Getting Started

1. **Open Omega** in your browser at its session URL
2. **Describe your app** in the idea editor — be specific about purpose, inputs, outputs, and any special requirements
3. **Click "Generate App"** to run the full pipeline
4. **Monitor progress** via the step indicators in the pipeline bar
5. **Review outputs** in the tabbed output panel:
   - **Analysis**: Structured breakdown of your app concept
   - **Pipeline Design**: Complete architectural blueprint
   - **Generated Files**: Browse all generated op files, UI, and config
   - **Review**: Quality assessment with specific improvement recommendations
6. **Open the generated app** by clicking the "Open App" button

## Pipeline Architecture

| Op File | Task Type | Input | Output | Purpose |
|---------|-----------|-------|--------|---------|
| `analyze_op.md` | Brainstorming | `idea.md` | `analysis.md` | Decompose idea into pipeline stages |
| `design_pipeline_op.md` | MultiPerspectiveAnalysis | `analysis.md` | `pipeline_design.md` | Design the complete DAG architecture |
| `generate_ops_op.md` | SubPlan | `pipeline_design.md` | `generated_app/ops/*.md` | Generate all op files and configs |
| `generate_ui_op.md` | FileModification | `pipeline_design.md` | `generated_app/index.html` | Build the HTML UI |
| `generate_readme_op.md` | FileModification | `pipeline_design.md` | `generated_app/README.md` | Write documentation |
| `review_op.md` | MultiPerspectiveAnalysis | All generated files | `review.md` | Quality review |

## Tips for Best Results

- **Be specific** about what your app should do — vague ideas produce vague apps
- **Mention task types** if you know you want web research (CrawlerAgent), multi-round iteration, or human-in-the-loop checkpoints
- **Describe the user experience** — what should the user see and do?
- **Specify output formats** — what should the final deliverables look like?
- **Review the pipeline design** before the generation step — you can edit `pipeline_design.md` to refine the architecture

## Iterative Refinement

After the initial generation:

1. Review the **Review** tab for identified issues
2. Edit `idea.md` with clarifications or additional requirements
3. Re-run the pipeline to regenerate with improvements
4. Manually edit generated op files if needed for fine-tuning

## Disclaimer

> ⚠️ Generated applications are AI-produced and should be reviewed before use. Pipeline architectures, regex patterns, and prompts may need manual adjustment. Always validate that generated op files have correct frontmatter syntax and that the pipeline DAG is well-formed.