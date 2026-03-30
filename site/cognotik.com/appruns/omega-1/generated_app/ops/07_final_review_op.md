---
transforms:
- ../processing/meal_plan_draft\.md -> ../outputs/final_meal_plan.md
- ../processing/recipes_detailed\.md -> ../outputs/final_meal_plan.md
- ../outputs/shopping_list\.md -> ../outputs/final_meal_plan.md
- ../processing/astrological_insights\.md -> ../outputs/final_meal_plan.md
related:
- ../processing/processed_profile.json
- ../inputs/meal_plan_request.md
task_type: CodeReview
---
* Validate the complete meal plan for:
- **Nutritional adequacy**: Do all meals combined meet daily macro/micronutrient targets?
- **Dietary compliance**: Are all restrictions and allergies respected throughout?
- **Astrological coherence**: Do meals align with stated astrological themes and user's chart?
- **User preference alignment**: Do meals match cuisine preferences and health goals?
- **Practical feasibility**: Are recipes achievable? Is shopping list realistic?
- **Variety and balance**: Is there sufficient variety across the week? Are elemental themes balanced?
* Flag any conflicts or issues found:
- Nutritional gaps (e.g., insufficient iron for vegetarian)
- Restriction violations
- Astrological misalignments
- Budget overruns
- Ingredient redundancy
* Generate a polished final meal plan markdown document with:
- Executive summary (1-2 paragraphs)
- User profile recap (astrological and dietary)
- Week-at-a-glance overview (7-day meal grid)
- Detailed daily plans (day-by-day with all meals and astrological notes)
- Complete recipe section (all recipes with full details)
- Shopping list (consolidated and organized)
- Meal prep timeline and tips
- Astrological insights and food-element associations
- Nutritional summary statistics
* Create a summary JSON file with:
- Weekly nutrition stats (total calories, macro percentages, key micronutrients)
- Cost breakdown (total, per day, per meal)
- Astrological themes highlighted
- Elemental distribution across meals
- Comparison to health goals (met/not met)
- Quality assurance checklist (all validations passed)
* Ensure final output is polished, professional, and ready for user presentation