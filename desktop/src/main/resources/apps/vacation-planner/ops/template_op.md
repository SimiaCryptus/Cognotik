---
<!-- NOTE: This is a TEMPLATE for creating new op files. Do NOT include in pipeline execution. -->
task_type: [Brainstorming|MultiPerspectiveAnalysis|CrawlerAgent|SubPlan|FileModification|CodeReview|AutoFix]
transforms: ../[input_file]\.md -> ../[output_file]\.md
related:
   - ../[context_file].md
   - ../[context_file].json
validation_regex: "(?=.*### Required Section 1)(?=.*### Required Section 2)"
---

# [Op Name]

## Purpose

[One-sentence description of what this op does]

## Input

- **Primary Input**: [input_file.md]
- **Context Files**: [list of related files]
- **User Preferences**: [what user preferences are needed]

## Output

- **Primary Output**: [output_file.md]
- **Format**: [Markdown|JSON|HTML]
- **Required Sections**: [list of required sections]

## Process

[Detailed description of what this op does, 2-3 paragraphs]

### Step 1: [First step]

[Description of first step]

### Step 2: [Second step]

[Description of second step]

### Step 3: [Third step]

[Description of third step]

## Output Format

[Exact specification of output structure, including examples]

### Required Sections

- **Section 1**: [Description]
- **Section 2**: [Description]
- **Section 3**: [Description]

### Example Output

```markdown
### Section 1: [Example]

[Example content]

### Section 2: [Example]

[Example content]
```

## Error Handling

**If [error condition 1]:**
1. [Action 1]
2. [Action 2]
3. [Action 3]

**If [error condition 2]:**
1. [Action 1]
2. [Action 2]
3. [Action 3]

## Validation

This op's output is valid if:
- [ ] All required sections are present
- [ ] Format matches specification
- [ ] No circular dependencies
- [ ] All relative paths resolve
- [ ] Validation regex pattern matches

## Quality Criteria

Your output is high-quality if:
- ✓ All required sections present
- ✓ Format matches specification
- ✓ Examples provided where helpful
- ✓ Error handling specified
- ✓ Validation regex matches output
- ✓ No placeholder text remains

## Validation Checklist

Before submitting, verify:
- [ ] All required sections present
- [ ] Format matches specification
- [ ] Validation regex pattern matches output
- [ ] No placeholder text remains
- [ ] All relative paths correct
- [ ] No circular dependencies