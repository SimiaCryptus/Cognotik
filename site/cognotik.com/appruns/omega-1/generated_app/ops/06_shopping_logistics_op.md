---
transforms:
- ../processing/recipes_detailed\.md -> ../outputs/shopping_list.md
- ../processing/recipes_detailed\.md -> ../outputs/meal_prep_schedule.json
related:
- ../inputs/dietary_preferences.md
- ../processing/processed_profile.json
task_type: FileModification
---
* Consolidate all ingredients from all recipes into a comprehensive shopping list
* Organize shopping list by store section:
- Produce (vegetables, fruits, herbs)
- Grains & Legumes
- Dairy & Alternatives
- Proteins (if applicable)
- Pantry Staples (oils, spices, condiments)
- Frozen Foods
- Other
* For each ingredient, include:
- Quantity needed for the full week
- Unit of measurement
- Estimated cost per unit
- Total cost for that ingredient
- Checkboxes for shopping
* Calculate and display:
- Total estimated cost
- Cost per day
- Comparison to budget (if provided)
* Create a meal prep timeline (JSON) with:
- Optimal prep days based on astrological timing (e.g., prep grounding foods on Moon in Earth signs)
- Day-by-day prep tasks with estimated time
- Ingredient prep instructions (chopping, marinating, cooking components)
- Storage instructions for prepped ingredients
- Astrological timing notes for each prep session
* Output two files:
- **shopping_list.md**: Markdown formatted, printable, organized by store section
- **meal_prep_schedule.json**: Structured JSON with prep timeline and astrological timing
* Ensure shopping list is practical and actionable for grocery shopping