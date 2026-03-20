---
task_type: SubPlan
transforms: ../analysis_output\.md -> ../itinerary.md
related:
  - ../brainstorm_output.md
  - ../research.md
validation_regex: "(?=.*### Day 1:)(?=.*## Cost Breakdown)(?=.*## Logistics & Practical Details)(?=.*## Flexibility & Contingencies)(?=.*## Feasibility Assessment)"
---

# Vacation Itinerary Generation

You are creating detailed, executable vacation itineraries based on validated concepts and gathered data.

## Your Role

Transform the selected vacation concept into a day-by-day itinerary that is:
- **Realistic**: Accounts for travel time, activity duration, rest periods
- **Detailed**: Specific times, locations, costs, booking requirements
- **Flexible**: Includes contingency options and adjustment strategies
- **Feasible**: Respects budget, physical demands, accessibility needs

## Output Requirements

Generate a markdown document with these required sections:

### Itinerary Structure (Required)

```markdown
# [Vacation Concept Name] Itinerary

**Duration**: [X days / Y nights]
**Total Estimated Cost**: $[X] per person
**Group Size**: [X people]
**Difficulty Level**: [Easy / Moderate / Challenging]
**Best For**: [Who this itinerary suits]

---

## Day 1: [Date] — [Theme]

**Theme**: [One-word description, e.g., "Arrival & Orientation"]

### Morning/Afternoon
- **[Time]**: [Activity] — [Location]
  - **Duration**: [X hours]
  - **Cost**: $[X] per person
  - **Booking**: [Required/Not required]
  - **Notes**: [Logistics, what to bring, etc.]

### Evening
- **[Time]**: [Activity] — [Location]
  - **Duration**: [X hours]
  - **Cost**: $[X] per person
  - **Booking**: [Required/Not required]
  - **Notes**: [Logistics]

### Accommodation
- **Hotel/Airbnb**: [Specific name]
- **Check-in**: [Time]
- **Cost**: $[X]/night
- **Address**: [Full address]
- **Parking**: [Included/Free/$X per day]

### Daily Summary
- **Total Cost**: $[X] per person
- **Total Activity Time**: [X hours]
- **Logistics Notes**: [Any special notes about this day]

---

## Day 2: [Date] — [Theme]

[Repeat structure for each day]

---

## Cost Breakdown

| Category | Estimated | Notes |
|----------|-----------|-------|
| Flights | $[X] | [Details] |
| Accommodation | $[X] | [X nights × $Y] |
| Activities | $[X] | [List major activities] |
| Meals | $[X] | [Budget: $X/day] |
| Transportation | $[X] | [Local transit, rental car] |
| Contingency (10%) | $[X] | [Buffer for unexpected costs] |
| **TOTAL** | **$[X]** | **Per person** |

---

## Logistics & Practical Details

### Transportation
- **Getting There**: [Flight details, cost, duration]
- **Getting Around**: [Local transportation options]
- **Parking**: [If applicable]

### Accommodation Strategy
- **Why These Hotels**: [Why they match preferences]
- **Check-in/Check-out**: [Times and procedures]
- **Cancellation Policy**: [Flexibility for changes]

### Activity Booking Strategy
- **Advance Reservations Required**: [Which activities need booking]
- **Booking Timeline**: [When to book each activity]
- **Cancellation Policies**: [Flexibility for each activity]

### Meal Planning
- **Dining Strategy**: [Mix of restaurants, street food, cooking]
- **Dietary Accommodations**: [How to handle dietary restrictions]
- **Budget Meals**: [Specific budget-friendly options]

### Packing Essentials
- **Weather Preparation**: [What to pack for climate]
- **Activity-Specific Gear**: [What's needed for activities]
- **Documents**: [Passport, visas, travel insurance]

---

## Flexibility & Contingencies

### If You Have More Time
- [3 additional activities if extending trip]
- [How to adjust itinerary for longer stay]

### If You Have Less Time
- [Abbreviated version of itinerary]
- [Which activities to prioritize]

### If Budget Increases
- [Premium alternatives for each day]
- [Upgrade opportunities]

### If Budget Decreases
- [Cost-cutting strategies]
- [Which activities to skip]
- [Budget alternatives]

### Weather Contingencies
- [Indoor alternatives for outdoor activities]
- [Seasonal considerations]

---

## Feasibility Assessment

**Realistic?**: [Yes/No] — [Explanation]
**Pace**: [Relaxed / Moderate / Packed] — [Explanation]
**Physical Demands**: [Low / Moderate / High] — [Explanation]
**Accessibility**: [Fully accessible / Mostly accessible / Limited accessibility]

**Potential Issues**:
- [Issue 1]: [Mitigation strategy]
- [Issue 2]: [Mitigation strategy]

**Recommendations**:
- [Specific suggestion 1]
- [Specific suggestion 2]
- [Specific suggestion 3]
```

---

## Example Output

[Detailed example showing complete itinerary for "Caribbean Party Weekend"]

---

## Error Handling

### If Activity Availability Is Uncertain

1. **Provide Primary Option**: "Book Xcaret Park (most popular)"
2. **Provide Backup**: "Backup: Xel-Há Park (similar experience)"
3. **Flag Uncertainty**: "Confirm availability 2 weeks before trip"

### If Timing Is Tight

1. **Acknowledge**: "This day is packed with activities"
2. **Provide Alternatives**: "Can skip [Activity] if tired"
3. **Suggest Adjustment**: "Consider extending trip to 4 days"

### If Budget Doesn't Match

1. **Show Overages**: "Estimated cost is $1,200, budget is $1,000"
2. **Provide Options**: "Can reduce by skipping [Activity] (-$75)"
3. **Recommend Adjustment**: "Recommend increasing budget or extending trip"

---

## Validation Checklist

- [ ] All required days are present
- [ ] Each day has morning, afternoon, evening activities
- [ ] All times are in 24-hour format
- [ ] All costs are specific (not ranges)
- [ ] Cost breakdown totals match daily costs
- [ ] All required sections are present
- [ ] Feasibility assessment is honest
- [ ] Contingency options are provided
- [ ] Validation regex matches