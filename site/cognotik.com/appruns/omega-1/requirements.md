# Astrological Meal Planner - Requirements Document

## App Overview

The Astrological Meal Planner is an AI-powered application that generates personalized meal plans based on astrological profiles and dietary preferences. Users provide their birth chart data, dietary restrictions, and nutritional goals, and the system generates customized weekly meal plans with recipes that align with astrological principles (zodiac elements, planetary influences, seasonal timing). The app combines nutritional science with astrological symbolism to create meaningful, personalized dining experiences.

**Primary Use Case:** A health-conscious individual wants to plan their weekly meals in a way that resonates with their astrological identity and current planetary transits, receiving recipes and meal suggestions that feel personally aligned.

---

## User Inputs

| Filename | Format | Purpose | Example Content |
|----------|--------|---------|-----------------|
| `user_profile.json` | JSON | Core user astrological and dietary data | `{ "name": "Alex", "birth_date": "1990-03-15", "birth_time": "14:30", "birth_location": "New York, NY", "sun_sign": "Pisces", "moon_sign": "Cancer", "rising_sign": "Libra", "dietary_restrictions": ["vegetarian"], "allergies": ["nuts"], "health_goals": ["weight_loss", "energy"], "cuisine_preferences": ["Mediterranean", "Asian"] }` |
| `dietary_preferences.md` | Markdown | Detailed dietary notes and preferences | `# Dietary Preferences\n\n## Restrictions\n- Vegetarian\n- Gluten-free\n\n## Preferences\n- Prefers warm foods\n- Likes spicy flavors\n- Budget: $50/week` |
| `astrological_context.md` | Markdown | Current astrological transits and timing | `# Current Astrological Context\n\n## Week of: March 15-21, 2024\n- Mercury in Pisces (communication)\n- Venus in Aries (relationships)\n- Moon phases: Waxing Gibbous\n- Retrogrades: None` |
| `meal_plan_request.md` | Markdown | Specific request parameters for meal plan | `# Meal Plan Request\n\n## Duration: 7 days\n## Meals per day: 3\n## Special occasions: Birthday dinner on day 3\n## Focus: Grounding, nourishing foods` |

---

## Pipeline Steps

### Stage 1: Profile Analysis & Enrichment
**Reads:** `user_profile.json`, `dietary_preferences.md`  
**Produces:** `processed_profile.json`  
**Task Type:** `FileModification`  
**Description:** Parse and validate user astrological data (birth chart), dietary restrictions, and health goals. Enrich the profile with calculated astrological elements (house placements, aspects, numerology). Normalize and structure all data for downstream processing.

### Stage 2: Astrological Interpretation
**Reads:** `processed_profile.json`, `astrological_context.md`  
**Produces:** `astrological_insights.md`  
**Task Type:** `MultiPerspectiveAnalysis`  
**Description:** Analyze the user's birth chart from multiple astrological perspectives (Vedic, Western, Psychological). Identify key themes, elemental balances, and how current transits interact with the natal chart. Generate symbolic food associations and meal timing recommendations.

### Stage 3: Dietary & Nutritional Planning
**Reads:** `processed_profile.json`, `dietary_preferences.md`  
**Produces:** `nutritional_framework.json`  
**Task Type:** `FileModification`  
**Description:** Create a nutritional framework based on health goals, restrictions, and preferences. Calculate daily macro/micronutrient targets. Identify food categories that align with both dietary needs and astrological elements.

### Stage 4: Meal Plan Generation
**Reads:** `astrological_insights.md`, `nutritional_framework.json`, `meal_plan_request.md`  
**Produces:** `meal_plan_draft.md`  
**Task Type:** `Brainstorming`  
**Description:** Generate creative meal suggestions that balance astrological themes with nutritional requirements. Consider elemental associations (Fire = warming spices, Water = hydrating foods, Air = light/fresh, Earth = grounding/root vegetables). Create a structured 7-day meal plan with breakfast, lunch, dinner, and optional snacks.

### Stage 5: Recipe Development
**Reads:** `meal_plan_draft.md`, `processed_profile.json`  
**Produces:** `recipes_detailed.md`  
**Task Type:** `FileModification`  
**Description:** Expand each meal into full recipes with ingredients, instructions, cooking times, and nutritional information. Add astrological notes explaining the symbolic significance of key ingredients and preparation methods.

### Stage 6: Shopping List & Logistics
**Reads:** `recipes_detailed.md`, `dietary_preferences.md`  
**Produces:** `shopping_list.md`, `meal_prep_schedule.json`  
**Task Type:** `FileModification`  
**Description:** Consolidate all ingredients into an organized shopping list grouped by store section. Create a meal prep timeline with optimal preparation days based on astrological timing (e.g., prep grounding foods on Moon in Earth signs).

### Stage 7: Final Review & Optimization
**Reads:** `meal_plan_draft.md`, `recipes_detailed.md`, `shopping_list.md`, `astrological_insights.md`  
**Produces:** `final_meal_plan.md`, `meal_plan_summary.json`  
**Task Type:** `CodeReview`  
**Description:** Validate the complete meal plan for consistency, nutritional adequacy, astrological coherence, and user preference alignment. Flag any conflicts or improvements needed. Generate a polished final output with summary statistics.

---

### Pipeline Flow Diagram

```
user_profile.json ──┐
                    ├─→ [Stage 1: Profile Analysis] ──→ processed_profile.json
dietary_preferences.md ┘                                        │
                                                                 ├─→ [Stage 2: Astrological Interpretation] ──→ astrological_insights.md
astrological_context.md ──────────────────────────────────────┘                                                    │
                                                                                                                     ├─→ [Stage 4: Meal Plan Generation] ──→ meal_plan_draft.md
nutritional_framework.json ←─ [Stage 3: Dietary Planning] ←─ processed_profile.json                              │
                                                                                                                     ├─→ [Stage 5: Recipe Development] ──→ recipes_detailed.md
meal_plan_request.md ──────────────────────────────────────────────────────────────────────────────────────────┘
                                                                                                                     ├─→ [Stage 6: Shopping & Logistics] ──→ shopping_list.md
                                                                                                                     │                                    meal_prep_schedule.json
                                                                                                                     │
                                                                                                                     └─→ [Stage 7: Final Review] ──→ final_meal_plan.md
                                                                                                                                                    meal_plan_summary.json
```

**Fan-out Points:**
- Stage 1 output (`processed_profile.json`) → feeds into Stages 2 and 3
- Stage 4 output (`meal_plan_draft.md`) → feeds into Stages 5 and 7

**Fan-in Points:**
- Stage 7 consolidates outputs from Stages 4, 5, 6, and 2 into final deliverables

---

## Final Outputs

| Filename | Format | Purpose | User Visibility |
|----------|--------|---------|-----------------|
| `final_meal_plan.md` | Markdown | Complete 7-day meal plan with recipes, timing, and astrological notes | Primary output - displayed prominently |
| `meal_plan_summary.json` | JSON | Structured summary with statistics (calories, macros, cost, astrological themes) | Dashboard/overview display |
| `shopping_list.md` | Markdown | Organized shopping list by category with quantities and estimated costs | Printable/shareable output |
| `meal_prep_schedule.json` | JSON | Day-by-day prep timeline with astrological timing recommendations | Calendar/timeline view |
| `astrological_insights.md` | Markdown | Detailed astrological analysis and food-element associations | Educational/reference material |
| `recipes_detailed.md` | Markdown | Full recipe collection with nutritional info and astrological symbolism | Recipe reference/export |

---

## File Naming Conventions

### Directory Structure

```
astrological-meal-planner/
├── inputs/
│   ├── user_profile.json
│   ├── dietary_preferences.md
│   ├── astrological_context.md
│   └── meal_plan_request.md
├── processing/
│   ├── processed_profile.json
│   ├── astrological_insights.md
│   ├── nutritional_framework.json
│   └── meal_plan_draft.md
├── outputs/
│   ├── final_meal_plan.md
│   ├── meal_plan_summary.json
│   ├── shopping_list.md
│   ├── meal_prep_schedule.json
│   ├── astrological_insights.md
│   └── recipes_detailed.md
└── archive/
    └── meal_plan_[YYYY-MM-DD]_[USER_ID]/
        ├── inputs/
        ├── processing/
        └── outputs/
```

### Naming Rules

- **Input files:** `[purpose].[format]` (e.g., `user_profile.json`, `dietary_preferences.md`)
- **Processing files:** `[stage_name]_[output_type].[format]` (e.g., `processed_profile.json`)
- **Output files:** `[final_]?[content_type].[format]` (e.g., `final_meal_plan.md`, `shopping_list.md`)
- **Archived files:** `[original_filename]_[YYYY-MM-DD]_[USER_ID].[format]`
- **Regex pattern for all files:** `^[a-z_]+\.(json|md|csv|txt)$`

---

## UI Requirements

### Input Editors

1. **User Profile Editor**
   - Form-based input for birth date, time, location
   - Dropdown selectors for zodiac signs (with auto-calculation option)
   - Multi-select checkboxes for dietary restrictions and allergies
   - Tag input for cuisine preferences
   - Health goals multi-select

2. **Dietary Preferences Editor**
   - Rich markdown editor for detailed dietary notes
   - Budget slider ($20-$200/week)
   - Meal frequency selector (2-4 meals/day)
   - Cuisine preference tags

3. **Astrological Context Editor**
   - Date picker for current week/period
   - Display of current planetary positions (read-only, fetched from API)
   - Retrograde status indicators
   - Moon phase visualization

4. **Meal Plan Request Editor**
   - Duration selector (3, 7, 14, 30 days)
   - Meals per day selector
   - Special occasions date picker with event description
   - Focus/theme text input (e.g., "grounding," "energizing")

### Pipeline Step Buttons

1. **"Analyze Profile"** → Runs Stage 1
2. **"Generate Astrological Insights"** → Runs Stage 2
3. **"Calculate Nutrition Framework"** → Runs Stage 3
4. **"Create Meal Plan"** → Runs Stages 4-5
5. **"Generate Shopping List"** → Runs Stage 6
6. **"Finalize & Review"** → Runs Stage 7

### Output Viewers

1. **Meal Plan Viewer**
   - Day-by-day layout with meal cards
   - Expandable recipe details with ingredients and instructions
   - Astrological notes sidebar
   - Print-friendly formatting

2. **Shopping List Viewer**
   - Categorized list with checkboxes
   - Quantity and estimated cost per item
   - Total budget calculation
   - Export to PDF/CSV

3. **Astrological Insights Panel**
   - Birth chart summary
   - Current transit analysis
   - Elemental balance visualization (pie chart)
   - Food-element associations table

4. **Meal Prep Timeline**
   - Calendar view with prep tasks
   - Astrological timing indicators
   - Estimated time per task
   - Drag-and-drop rescheduling

5. **Summary Dashboard**
   - Weekly nutrition stats (calories, macros, micros)
   - Cost breakdown
   - Astrological themes highlighted
   - Comparison to health goals

### Special UI Considerations

- **Multi-round workflow:** Users can regenerate meal plans multiple times; preserve previous versions in history
- **Human-in-the-loop:** Allow users to manually edit meal suggestions before finalizing
- **Real-time validation:** Show conflicts between dietary restrictions and suggested meals immediately
- **Astrological education:** Tooltips explaining astrological concepts and food associations
- **Export options:** PDF meal plan, printable shopping list, calendar integration (iCal)
- **Responsive design:** Mobile-friendly for grocery shopping reference
- **Dark mode:** Optional theme for evening meal planning sessions

---

## Success Criteria

- ✅ User can input complete astrological and dietary profile
- ✅ System generates coherent 7-day meal plans aligned with astrological themes
- ✅ All meals meet nutritional targets and dietary restrictions
- ✅ Shopping list is organized, budgeted, and actionable
- ✅ Astrological insights are educationally valuable and non-prescriptive
- ✅ UI supports iterative refinement of meal plans
- ✅ All outputs are exportable and shareable