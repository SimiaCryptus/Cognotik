# Reusable Prompt Components & Templates

This file contains reusable prompt components that can be included in multiple op files to ensure consistency and reduce duplication.

## Confidence Scoring Template

Use this template in any op file that gathers or validates data:

```markdown
### Confidence Scoring

Rate confidence for each data point using this scale:

- **5 = Verified**: Official source, current within 7 days
- **4 = Authoritative**: Reliable source, current within 30 days
- **3 = Reliable**: Good source, may be 30-90 days old
- **2 = Estimated**: Based on similar data or patterns
- **1 = Rough Estimate**: Limited information available

**Example**:
- Activity pricing: Confidence 4/5 (from official website, updated 2 weeks ago)
- Weather forecast: Confidence 5/5 (from weather service, updated today)
- Accommodation availability: Confidence 3/5 (from booking site, may be outdated)

**Reporting Format**:
```
**Data Point**: [Description]
**Value**: [Specific value]
**Confidence**: [1-5]
**Source**: [URL or source name]
**Date**: [When data was gathered]
**Notes**: [Any caveats or assumptions]
```
```

## Feasibility Assessment Template

Use this template in any op file that generates plans or recommendations:

```markdown
### Feasibility Assessment

Evaluate feasibility using these criteria:

**Realistic?** [Yes/No]
- [Explanation of why this is realistic or not]
- [Any assumptions made]
- [Risks that could affect feasibility]

**Pace**: [Relaxed / Moderate / Packed]
- Relaxed: Plenty of free time, flexible schedule
- Moderate: Balanced activities and rest
- Packed: Full schedule, minimal downtime

**Physical Demands**: [Low / Moderate / High]
- Low: Minimal walking, no strenuous activities
- Moderate: Some walking, light activities
- High: Significant walking, strenuous activities

**Accessibility**: [Fully / Mostly / Partially / Not Accessible]
- Wheelchair accessible
- Dietary accommodations available
- Age-appropriate activities
- Mobility considerations

**Potential Issues**:
1. [Issue]: [Mitigation strategy]
2. [Issue]: [Mitigation strategy]
3. [Issue]: [Mitigation strategy]

**Recommendations**:
- [Specific suggestion 1]
- [Specific suggestion 2]
- [Specific suggestion 3]
```

## Conflict Resolution Template

Use this template in any op file that synthesizes multiple perspectives:

```markdown
### Conflict Resolution

When perspectives disagree:

**Identify the Conflict**:
- Perspective A recommends: [X]
- Perspective B recommends: [Y]
- Perspective C recommends: [Z]

**Analyze the Trade-Off**:
- Choosing A means: [Consequence]
- Choosing B means: [Consequence]
- Choosing C means: [Consequence]

**Resolve the Conflict**:
- **Recommended Choice**: [X]
- **Rationale**: [Why this choice balances the perspectives]
- **Trade-Offs Accepted**: [What we're giving up]
- **Trade-Offs Avoided**: [What we're preserving]

**Fallback Options**:
If the recommended choice doesn't work:
1. [Alternative 1]: [When to use]
2. [Alternative 2]: [When to use]
3. [Alternative 3]: [When to use]
```

## Error Handling Template

Use this template in any op file that might encounter missing or conflicting data:

```markdown
### Error Handling

**If Data Is Missing**:
1. **Note the gap**: "[Specific data] not available"
2. **Provide estimate**: "Estimated $X based on [similar data]"
3. **Flag confidence**: "Confidence: 2/5 (estimate only)"
4. **Suggest source**: "Recommend contacting [venue] directly"

**If Data Conflicts**:
1. **List all versions**: "Source A: $X, Source B: $Y, Source C: $Z"
2. **Identify most reliable**: "Most authoritative: [Source]"
3. **Recommend**: "Use $X as planning estimate"
4. **Flag for user**: "Verify current [data] before booking"

**If Data Is Outdated**:
1. **Note the age**: "Data is [X] days old"
2. **Assess impact**: "This may affect [specific aspect]"
3. **Recommend refresh**: "Recommend gathering fresh data"
4. **Provide fallback**: "Use [alternative data] if current data unavailable"

**Do NOT**:
- Skip sections or leave them blank
- Ignore missing data
- Assume outdated data is still valid
- Provide data without confidence scores
```

## Data Validation Template

Use this template in any op file that validates data:

```markdown
### Data Validation

For each data point, verify:

**Recency Check**:
- [ ] Data is current (within 30 days)
- [ ] Source is up-to-date
- [ ] No seasonal changes since data gathered

**Accuracy Check**:
- [ ] Data matches multiple sources
- [ ] No obvious errors or typos
- [ ] Values are within expected range

**Completeness Check**:
- [ ] All required fields present
- [ ] No missing information
- [ ] All details are specific (not generic)

**Consistency Check**:
- [ ] Data is consistent with other sources
- [ ] No contradictions with related data
- [ ] Follows expected patterns

**Reporting Format**:
```
**Data Point**: [Description]
**Status**: [VALID / INVALID / OUTDATED / INCOMPLETE]
**Issues**: [Any problems found]
**Confidence**: [1-5]
**Recommendation**: [Use / Verify / Replace / Skip]
```
```

## Output Format Template

Use this template to specify output format in any op file:

```markdown
### Output Format

Your output MUST include:

**Required Sections**:
- [ ] Section 1: [Description]
- [ ] Section 2: [Description]
- [ ] Section 3: [Description]

**Required Format**:
- Use markdown headers (##, ###) for structure
- Use bullet points for lists
- Use **bold** for key terms
- Use tables for data comparison
- Use code blocks for examples

**Required Content**:
- Specific examples (not generic descriptions)
- Quantified values (not vague estimates)
- Clear rationale (explain your reasoning)
- Actionable recommendations (not just observations)

**Quality Criteria**:
- ✓ All required sections present
- ✓ All content is specific and concrete
- ✓ All recommendations are actionable
- ✓ All data is validated and sourced
- ✓ All formatting follows markdown standards

**Validation Regex**:
```
[Specific regex pattern to validate output]
```
```

## Checkpoint Decision Template

Use this template in any op file that requires human approval:

```markdown
### Checkpoint Decision

This stage requires human approval before proceeding.

**What the User Sees**:
- [Description of what's presented]
- [Key information highlighted]
- [Decision options available]

**What the User Decides**:
- [ ] Approve: [Proceed to next stage]
- [ ] Reject: [Return to previous stage]
- [ ] Modify: [Allow adjustments and retry]

**Decision Criteria**:
- [Criterion 1]: [How to evaluate]
- [Criterion 2]: [How to evaluate]
- [Criterion 3]: [How to evaluate]

**Timeout Handling**:
- If no response after [X] minutes: [Action]
- Escalation procedure: [How to escalate]
- Reminder procedure: [How to remind user]

**Audit Trail**:
- Record user's decision
- Record timestamp
- Record any modifications made
- Store for compliance/debugging
```

## Quality Assurance Template

Use this template in any op file to define quality criteria:

```markdown
### Quality Assurance

Your output is high-quality if:

**Completeness**:
- ✓ All required sections are present
- ✓ All required fields are filled
- ✓ No sections are skipped or combined

**Accuracy**:
- ✓ All data is verified and sourced
- ✓ All calculations are correct
- ✓ All references are valid

**Clarity**:
- ✓ All descriptions are clear and specific
- ✓ All recommendations are actionable
- ✓ All rationale is explained

**Consistency**:
- ✓ Format matches specification
- ✓ Terminology is consistent
- ✓ Structure is logical

**Validation**:
- ✓ Output passes validation regex
- ✓ All links are working
- ✓ All data is current

**Testing**:
- ✓ Output can be parsed by downstream stages
- ✓ Output produces expected results
- ✓ Output handles edge cases
```

## Example Output Template

Use this template to provide examples in any op file:

```markdown
### Example Output

**Example 1: [Scenario]**

[Show complete example of expected output for this scenario]

**Example 2: [Scenario]**

[Show complete example of expected output for this scenario]

**Example 3: [Scenario]**

[Show complete example of expected output for this scenario]

**What Makes These Examples Good**:
- [Criterion 1]
- [Criterion 2]
- [Criterion 3]

**Common Mistakes to Avoid**:
- ✗ [Mistake 1]: [Why it's wrong]
- ✗ [Mistake 2]: [Why it's wrong]
- ✗ [Mistake 3]: [Why it's wrong]
```

## Testing Template

Use this template to specify testing requirements:

```markdown
### Testing

Test your output with these scenarios:

**Test Case 1: [Scenario]**
- Input: [Specific input]
- Expected Output: [What should happen]
- Validation: [How to verify]

**Test Case 2: [Scenario]**
- Input: [Specific input]
- Expected Output: [What should happen]
- Validation: [How to verify]

**Test Case 3: [Scenario]**
- Input: [Specific input]
- Expected Output: [What should happen]
- Validation: [How to verify]

**Edge Cases**:
- [Edge case 1]: [How to handle]
- [Edge case 2]: [How to handle]
- [Edge case 3]: [How to handle]
```

---

## Usage Guidelines

### When to Use These Templates

1. **Confidence Scoring**: Any op file that gathers, validates, or uses external data
2. **Feasibility Assessment**: Any op file that generates plans or recommendations
3. **Conflict Resolution**: Any op file that synthesizes multiple perspectives
4. **Error Handling**: Any op file that might encounter missing or conflicting data
5. **Data Validation**: Any op file that validates data quality
6. **Output Format**: Every op file (to specify expected output)
7. **Checkpoint Decision**: Any op file that requires human approval
8. **Quality Assurance**: Every op file (to define success criteria)
9. **Example Output**: Every op file (to show expected results)
10. **Testing**: Every op file (to specify validation procedures)

### How to Customize Templates

1. **Replace placeholders**: [Like this] with specific values
2. **Adjust criteria**: Modify criteria to match your specific needs
3. **Add examples**: Include examples relevant to your domain
4. **Extend sections**: Add more items if needed
5. **Simplify**: Remove sections that don't apply

### Consistency Across Op Files

Using these templates ensures:
- ✓ Consistent format across all op files
- ✓ Consistent quality standards
- ✓ Consistent error handling
- ✓ Consistent validation procedures
- ✓ Consistent documentation

---

## Version History

- **v1.0** (2026-03-19): Initial templates created
- **v1.1** (TBD): Add more templates based on feedback
- **v2.0** (TBD): Refactor based on real-world usage