# Pipeline

```yaml
pipeline:

  - name: Design
    op: ops/design_op.md
    outputs:
      - design/question_data_schema.ts
      - design/result_schema.ts
      - design/game_flow.md

  - name: Game Data
    op: ops/gamedata_op.md
    inputs:
      - idea.md
      - design/question_data_schema.ts
    outputs:
      - code/gamedata/*.json

  - name: Implement
    op: ops/impl_op.md
    inputs:
      - idea.md
      - design/question_data_schema.ts
      - design/result_schema.ts
      - design/game_flow.md
    outputs:
      - code/index.html
      - code/style.css
      - code/script.js

  - name: Test
    op: ops/test_op.md
    task_type: SeleniumFetch
    inputs:
      - code/index.html
    outputs:
      - code/test.console.log
      - code/test.network.log
      - code/test.html
      - code/README.md

  - name: Review
    op: ops/review_op.md
    task_type: Discussion
    inputs:
      - code/test.console.log
      - code/test.network.log
      - code/test.html
    outputs:
      - notes.md

  - name: Update
    op: ops/update_op.md
    inputs:
      - notes.md
      - idea.md
      - code/**
    outputs:
      - code/index.html
      - code/style.css
      - code/script.js
```