---
task_type: AutoFix
transforms: ../vacation_plan\.html -> ../validation/validation_report\.md
related:
   - ../brainstorm_output.md
   - ../analysis_output.md
   - ../data/crawler_latest.json
   - ../itinerary.md
validation_regex: "(?=.*## Validation Results)(?=.*## Issues Found)(?=.*## Auto-Corrections Applied)(?=.*## Recommendations)"
---

# Generated File Validation & Auto-Correction

You are validating all generated files and applying auto-corrections where possible.

## Your Role

Validate and correct:
- YAML frontmatter syntax in generated op files
- Regex patterns in transforms
- Relative path resolution
- Circular dependency detection
- HTML/CSS/JS syntax
- JSON structure and formatting

## Validation Checks

### YAML Frontmatter Validation

**Check**:
- [ ] Valid YAML syntax
- [ ] Required fields present (task_type, transforms, related)
- [ ] task_type is from approved list
- [ ] transforms pattern is valid Java regex
- [ ] All relative paths are valid
- [ ] No circular references

**Issues Found**:
- [Issue 1]: [File] [Error] [Auto-correction applied: Yes/No]
- [Issue 2]: [File] [Error] [Auto-correction applied: Yes/No]

---

### Regex Pattern Validation

**Check**:
- [ ] All regex patterns use valid Java syntax
- [ ] Dots are properly escaped (\.)
- [ ] Capture groups are properly formed
- [ ] No unescaped special characters
- [ ] Patterns match intended files

**Issues Found**:
- [Issue 1]: [File] [Pattern] [Error] [Correction]
- [Issue 2]: [File] [Pattern] [Error] [Correction]

---

### Relative Path Validation

**Check**:
- [ ] All paths resolve correctly from file location
- [ ] Correct depth (../../ for generated files)
- [ ] No broken references
- [ ] Consistent path naming conventions
- [ ] No circular references

**Issues Found**:
- [Issue 1]: [File] [Path] [Error] [Correction]
- [Issue 2]: [File] [Path] [Error] [Correction]

---

### Circular Dependency Detection

**Check**:
- [ ] No cycles in DAG
- [ ] All dependencies are acyclic
- [ ] Transitive dependencies are valid

**Issues Found**:
- [Issue 1]: [Cycle path] [Files involved] [Recommendation]
- [Issue 2]: [Cycle path] [Files involved] [Recommendation]

---

### HTML/CSS/JS Validation

**Check**:
- [ ] HTML is valid and well-formed
- [ ] CSS has no syntax errors
- [ ] JavaScript has no syntax errors
- [ ] All required elements present
- [ ] No console errors

**Issues Found**:
- [Issue 1]: [File] [Error] [Auto-correction applied: Yes/No]
- [Issue 2]: [File] [Error] [Auto-correction applied: Yes/No]

---

### JSON Validation

**Check**:
- [ ] Valid JSON syntax
- [ ] Required fields present
- [ ] Proper data types
- [ ] No trailing commas
- [ ] Proper escaping

**Issues Found**:
- [Issue 1]: [File] [Error] [Auto-correction applied: Yes/No]
- [Issue 2]: [File] [Error] [Auto-correction applied: Yes/No]

---

## Validation Results

**Total Files Checked**: [Number]
**Files with Issues**: [Number]
**Critical Issues**: [Number]
**Auto-Corrections Applied**: [Number]
**Manual Fixes Required**: [Number]

---

## Issues Found

[List all issues with severity, file, description, and resolution]

---

## Auto-Corrections Applied

[List all auto-corrections with before/after examples]

---

## Recommendations

[Specific recommendations for fixing remaining issues]

---

## Success Criteria

This op file produces high-quality output when:

1. **Completeness**: All files are validated
2. **Accuracy**: Issues are correctly identified
3. **Helpfulness**: Auto-corrections are applied where possible
4. **Clarity**: Issues are clearly documented
5. **Actionability**: Recommendations are specific