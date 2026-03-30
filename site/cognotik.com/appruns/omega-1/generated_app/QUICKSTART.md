
# Quick Start Guide - Astrological Meal Planner

## 5-Minute Setup

### Step 1: Prepare Your Birth Chart Data (2 minutes)

Edit `inputs/user_profile.json`:

```json
{
  "name": "Your Name",
  "birth_date": "1990-03-15",
  "birth_time": "14:30",
  "birth_location": "New York, NY",
  "sun_sign": "Pisces",
  "moon_sign": "Cancer",
  "rising_sign": "Libra",
  "dietary_restrictions": ["vegetarian"],
  "allergies": ["nuts"],
  "health_goals": ["weight_loss", "energy"],
  "cuisine_preferences": ["Mediterranean", "Asian"]
}
```

**Need help finding your signs?**
- Use astro.com or cafeastrology.com
- Enter your birth date, time, and location
- Copy Sun, Moon, and Rising signs

### Step 2: Add Dietary Details (1 minute)

Edit `inputs/dietary_preferences.md`:

```markdown
# Dietary Preferences

## Dietary Restrictions
- Vegetarian
- Gluten-free

## Allergies
- Nuts
- Shellfish

## Preferences
- Warm foods
- Spicy flavors
- Budget: $50/week
```

### Step 3: Set Current Astrological Context (1 minute)

Edit `inputs/astrological_context.md`:

```markdown
# Current Astrological Context

## Week of: 2024-03-15 to 2024-03-21

### Planetary Positions
- Sun: Pisces
- Moon: Cancer
- Mercury: Pisces
- Venus: Aries

### Moon Phase
- Current Phase: Waxing Gibbous

### Retrograde Planets
- None
```

**Where to find current transits?**
- astro.com (free transit calculator)
- cafeastrology.com (daily horoscopes)
- timeanddate.com (moon phase calendar)

### Step 4: Specify Meal Plan Request (1 minute)

Edit `inputs/meal_plan_request.md`:

```markdown
# Meal Plan Request

## Plan Duration
- Duration: 7 days
- Meals per day: 3

## Focus
- Grounding and centering
- Nourishing foods

## Special Occasions
- Birthday dinner on day 3
```

## Run the Pipeline

```bash
docops run --app astrological-meal-planner
```

The pipeline will:
1. Analyze your profile (30 seconds)
2. Generate astrological insights (1 minute)
3. Create nutritional framework (30 seconds)
4. Generate meal suggestions (2 minutes)
5. Develop recipes (2 minutes)
6. Create shopping list (1 minute)
7. Review and finalize (1 minute)

**Total time: ~8 minutes**

## Review Your Outputs

After the pipeline completes, check `outputs/`:

1. **`final_meal_plan.md`** ← Start here! Your complete meal plan
2. **`shopping_list.md`** ← Print this for grocery shopping
3. **`meal_prep_schedule.json`** ← Prep timeline with astrological timing
4. **`astrological_insights.md`** ← Learn about your chart and food associations
5. **`meal_plan_summary.json`** ← Statistics and overview

## Common Customizations

### Change Meal Plan Duration
Edit `inputs/meal_plan_request.md`:
```markdown
## Plan Duration
- Duration: 14 days  # Change from 7 to 14
- Meals per day: 3
```

### Add Special Dietary Needs
Edit `inputs/dietary_preferences.md`:
```markdown
## Health Goals
- Weight loss
- Improved digestion
- Better sleep
```

### Emphasize Specific Elements
Edit `inputs/meal_plan_request.md`:
```markdown
## Astrological Themes
- Fire (warming, energizing)
- Earth (grounding, stabilizing)
```

### Adjust Budget
Edit `inputs/dietary_preferences.md`:
```markdown
## Budget
- Weekly budget: $75  # Increase from $50
```

## Regenerate with Changes

After editing inputs, run the pipeline again:

```bash
docops run --app astrological-meal-planner --update-mode PatchToUpdate
```

This will:
- Preserve any manual edits you made to outputs
- Only regenerate files that depend on changed inputs
- Keep your previous versions in history

## Tips for Best Results

### 1. Accurate Birth Data
- Use exact birth time (not approximate)
- Include birth location (city and state/country)
- Double-check with your birth certificate

### 2. Detailed Preferences
- List all dietary restrictions (not just major ones)
- Include texture and flavor preferences
- Specify cooking time available

### 3. Current Transits
- Update astrological context weekly
- Include moon phase and lunar mansion
- Note any retrograde planets

### 4. Specific Requests
- Mention special occasions or celebrations
- Specify focus (grounding, energizing, etc.)
- Include any ingredients to emphasize or avoid

### 5. Manual Review
- Edit meal suggestions if they don't feel right
- Adjust recipes to your taste
- Customize shopping list for your local stores

## Troubleshooting

### "Pipeline didn't run"
- Check that all 4 input files exist in `inputs/`
- Verify file names match exactly (case-sensitive)
- Check for YAML syntax errors in JSON files

### "Meal plan seems incomplete"
- Verify `astrological_context.md` has current transits
- Check that `dietary_preferences.md` includes all restrictions
- Ensure `meal_plan_request.md` specifies duration and meals/day

### "Nutritional targets not met"
- Review `processing/nutritional_framework.json`
- Check health goals in `user_profile.json`
- Verify dietary restrictions aren't too limiting

### "Astrological insights missing"
- Ensure `user_profile.json` includes Sun, Moon, Rising signs
- Verify `astrological_context.md` has planetary positions
- Check that `processing/processed_profile.json` was created

## Next Steps

1. **Explore the outputs**: Read through your complete meal plan
2. **Try the recipes**: Pick one and cook it this week
3. **Refine your preferences**: Edit inputs based on results
4. **Regenerate**: Run the pipeline again with updated parameters
5. **Share**: Export your meal plan and shopping list to share with others

## Advanced Usage

### Create Multiple Meal Plans
Create separate input directories for different scenarios:
```
inputs_spring/
inputs_summer/
inputs_special_occasion/
```

### Batch Generate Plans
Run the pipeline multiple times with different parameters to compare options.

### Export for Sharing
- Print `final_meal_plan.md` as PDF
- Share `shopping_list.md` with family
- Export `meal_prep_schedule.json` to calendar app

### Integrate with Calendar
Use `meal_prep_schedule.json` to add prep tasks to your calendar app.

## Support

For detailed information:
- See **README.md** for complete documentation
- See **PIPELINE_SUMMARY.md** for technical details
- Review individual op files for specific stage details
- Check **../../docs/PIPELINE.md** for DocOps patterns

## Happy Meal Planning! 🌙✨

Your personalized, astrologically-aligned meal plan is ready to transform your dining experience.