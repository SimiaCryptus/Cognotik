---
transforms: ../outputs/final_meal_plan\.md -> ../outputs/meal_plan_summary.json
related:
- ../processing/processed_profile.json
- ../outputs/shopping_list.md
- ../outputs/meal_prep_schedule.json
task_type: FileModification
---
* Extract and structure key data from the final meal plan into a machine-readable JSON summary
* Include sections for:
- **User Profile**: Name, astrological signs (Sun/Moon/Rising), dietary restrictions, health goals
- **Weekly Statistics**:
- Total calories
- Macronutrient breakdown (protein %, carbs %, fat %)
- Key micronutrients (iron, calcium, B12, omega-3s, etc.)
- Cost (total, per day, per meal)
- **Astrological Summary**:
- Primary themes for the week
- Elemental distribution (% Fire, Earth, Air, Water)
- Current transits affecting meal timing
- Symbolic ingredients highlighted
- **Meal Inventory**:
- Count of unique meals
- Cuisine types represented
- Preparation time ranges
- **Shopping Summary**:
- Total items
- Cost by category
- Estimated budget utilization
- **Prep Timeline**:
- Total prep hours needed
- Optimal prep days
- Astrological timing notes
- **Quality Metrics**:
- Nutritional adequacy score
- Dietary compliance (% restrictions met)
- Astrological alignment score
- User preference match score
* Ensure JSON is properly formatted and valid
* Include metadata (generation date, version, user ID if applicable)