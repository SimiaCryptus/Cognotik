---
transforms: ../inputs/dietary_preferences\.md -> ../processing/nutritional_framework.json
related:
- ../processing/processed_profile.json
task_type: FileModification
---
* Extract dietary restrictions, allergies, and preferences from the input
* Based on health goals (weight loss, energy, muscle gain, etc.), calculate:
- Daily caloric target (estimate based on typical activity level)
- Macronutrient targets (protein %, carbs %, fat %)
- Key micronutrient focuses (iron, calcium, B12, omega-3s, etc.)
* Identify food categories that satisfy both:
- Dietary restrictions and allergies (what's allowed)
- Astrological elemental associations (what resonates symbolically)
* Create a mapping of:
- Allowed proteins (tofu, legumes, dairy, etc. based on vegetarian status)
- Allowed grains and carbs
- Allowed vegetables and fruits (organized by element)
- Allowed fats and oils
- Allowed seasonings and herbs
* Account for budget constraints if provided
* Output a structured JSON object with:
- Daily caloric and macronutrient targets
- Micronutrient focus areas
- Allowed food categories with examples
- Elemental food mapping (which foods align with which elements)
- Budget constraints and cost-per-meal targets
- Meal frequency and timing preferences
* Ensure all recommendations are compatible with stated restrictions