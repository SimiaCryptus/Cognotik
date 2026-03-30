# Astrological Meal Planner

A DocOps-powered application that generates personalized, astrologically-aligned meal plans based on user birth charts, dietary preferences, and nutritional goals.

## Overview

The Astrological Meal Planner combines nutritional science with astrological symbolism to create meaningful, personalized dining experiences. Users provide their astrological profile and dietary information, and the system generates customized weekly meal plans with recipes that align with astrological principles (zodiac elements, planetary influences, seasonal timing).

## Pipeline Architecture

The application uses a 7-stage pipeline that transforms user inputs into a complete meal plan with recipes, shopping list, and astrological insights.

### Data Flow

```
INPUTS (user provides)
├── user_profile.json (birth chart, zodiac signs, dietary restrictions)
├── dietary_preferences.md (detailed dietary notes, budget, preferences)
├── astrological_context.md (current transits, moon phase)
└── meal_plan_request.md (duration, meals per day, special occasions)
│
▼
[Stage 1: Profile Analysis]
│
▼
processed_profile.json (validated, enriched astrological data)
│
├─────────────────────────────────────┐
▼                                       ▼
[Stage 2: Astrological Interpretation]  [Stage 3: Nutritional Planning]
│                                       │
▼                                       ▼
astrological_insights.md              nutritional_framework.json
│                                       │
└─────────────────┬─────────────────────┘
▼
[Stage 4: Meal Plan Generation]
│
▼
meal_plan_draft.md
│
▼
[Stage 5: Recipe Development]
│
▼
recipes_detailed.md
│
├──────────────────────────┐
▼                          ▼
[Stage 6: Shopping & Logistics]    [Stage 7: Final Review]
│                          │
├──────────────┬───────────┤
▼              ▼           ▼
shopping_list.md    final_meal_plan.md
meal_prep_schedule.json    meal_plan_summary.json

OUTPUTS (user receives)
├── final_meal_plan.md (complete 7-day plan with recipes)
├── meal_plan_summary.json (statistics and overview)
├── shopping_list.md (organized, budgeted shopping list)
├── meal_prep_schedule.json (prep timeline with astrological timing)
├── astrological_insights.md (detailed astrological analysis)
└── recipes_detailed.md (full recipe collection)
```

## Pipeline Stages

### Stage 1: Profile Analysis (`01_profile_analysis_op.md`)
- **Input**: `user_profile.json`, `dietary_preferences.md`
- **Output**: `processed_profile.json`
- **Task Type**: FileModification
- **Purpose**: Parse, validate, and enrich user astrological and dietary data

### Stage 2: Astrological Interpretation (`02_astrological_interpretation_op.md`)
- **Input**: `processed_profile.json`, `astrological_context.md`
- **Output**: `astrological_insights.md`
- **Task Type**: MultiPerspectiveAnalysis
- **Purpose**: Analyze birth chart from Western, Vedic, and Psychological perspectives; create food-element associations

### Stage 3: Nutritional Planning (`03_nutritional_planning_op.md`)
- **Input**: `dietary_preferences.md`, `processed_profile.json`
- **Output**: `nutritional_framework.json`
- **Task Type**: FileModification
- **Purpose**: Calculate nutritional targets and create food category mappings

### Stage 4: Meal Plan Generation (`04_meal_plan_generation_op.md`)
- **Input**: `meal_plan_request.md`, `astrological_insights.md`, `nutritional_framework.json`
- **Output**: `meal_plan_draft.md`
- **Task Type**: Brainstorming
- **Purpose**: Generate creative meal suggestions balancing astrological themes with nutrition

### Stage 5: Recipe Development (`05_recipe_development_op.md`)
- **Input**: `meal_plan_draft.md`, `processed_profile.json`, `dietary_preferences.md`
- **Output**: `recipes_detailed.md`
- **Task Type**: FileModification
- **Purpose**: Expand meals into full recipes with ingredients, instructions, and astrological notes

### Stage 6: Shopping & Logistics (`06_shopping_logistics_op.md`)
- **Input**: `recipes_detailed.md`, `dietary_preferences.md`
- **Output**: `shopping_list.md`, `meal_prep_schedule.json`
- **Task Type**: FileModification
- **Purpose**: Consolidate ingredients into shopping list and create prep timeline

### Stage 7: Final Review (`07_final_review_op.md`)
- **Input**: `meal_plan_draft.md`, `recipes_detailed.md`, `shopping_list.md`, `astrological_insights.md`
- **Output**: `final_meal_plan.md`, `meal_plan_summary.json`
- **Task Type**: CodeReview
- **Purpose**: Validate complete plan and generate polished final outputs

### Stage 8: Summary Generation (`08_summary_generation_op.md`)
- **Input**: `final_meal_plan.md`, `shopping_list.md`, `meal_prep_schedule.json`
- **Output**: `meal_plan_summary.json`
- **Task Type**: FileModification
- **Purpose**: Extract and structure key data into machine-readable summary

## File Organization

```
astrological-meal-planner/
├── ops/                              # Pipeline operation files
│   ├── 01_profile_analysis_op.md
│   ├── 02_astrological_interpretation_op.md
│   ├── 03_nutritional_planning_op.md
│   ├── 04_meal_plan_generation_op.md
│   ├── 05_recipe_development_op.md
│   ├── 06_shopping_logistics_op.md
│   ├── 07_final_review_op.md
│   ├── 08_summary_generation_op.md
│   └── README.md (this file)
├── inputs/                           # User-provided input files
│   ├── user_profile.json
│   ├── dietary_preferences.md
│   ├── astrological_context.md
│   └── meal_plan_request.md
├── processing/                       # Intermediate processing files
│   ├── processed_profile.json
│   ├── astrological_insights.md
│   ├── nutritional_framework.json
│   └── meal_plan_draft.md
│   └── recipes_detailed.md
└── outputs/                          # Final deliverable files
├── final_meal_plan.md
├── meal_plan_summary.json
├── shopping_list.md
├── meal_prep_schedule.json
├── astrological_insights.md
└── recipes_detailed.md
```

## Usage

### 1. Prepare Input Files

Create the four input files in the `inputs/` directory:

- **`user_profile.json`**: User's astrological and dietary data
  ```json
  {
    "name": "Alex",
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

- **`dietary_preferences.md`**: Detailed dietary notes
  ```markdown
  # Dietary Preferences

  ## Restrictions
  - Vegetarian
  - Gluten-free

  ## Preferences
  - Prefers warm foods
  - Likes spicy flavors
  - Budget: $50/week
  ```

- **`astrological_context.md`**: Current astrological transits
  ```markdown
  # Current Astrological Context

  ## Week of: March 15-21, 2024
  - Mercury in Pisces (communication)
  - Venus in Aries (relationships)
  - Moon phases: Waxing Gibbous
  - Retrogrades: None
  ```

- **`meal_plan_request.md`**: Specific meal plan parameters
  ```markdown
  # Meal Plan Request

  ## Duration: 7 days
  ## Meals per day: 3
  ## Special occasions: Birthday dinner on day 3
  ## Focus: Grounding, nourishing foods
  ```

### 2. Run the Pipeline

Execute the DocOps pipeline to process all stages:

```bash
docops run --app astrological-meal-planner
```

The pipeline will execute stages in dependency order:
1. Profile Analysis
2. Astrological Interpretation (parallel with Nutritional Planning)
3. Nutritional Planning
4. Meal Plan Generation
5. Recipe Development
6. Shopping & Logistics (parallel with Final Review)
7. Final Review
8. Summary Generation

### 3. Review Outputs

After pipeline completion, review the generated files in `outputs/`:

- **`final_meal_plan.md`**: Complete meal plan with recipes and astrological notes
- **`meal_plan_summary.json`**: Statistics and overview
- **`shopping_list.md`**: Organized shopping list (printable)
- **`meal_prep_schedule.json`**: Prep timeline with astrological timing
- **`astrological_insights.md`**: Detailed astrological analysis
- **`recipes_detailed.md`**: Full recipe collection

### 4. Iterate and Refine

To regenerate the meal plan with different parameters:

1. Edit the input files in `inputs/`
2. Run the pipeline again with `--update-mode PatchToUpdate` to preserve manual edits
3. Review changes in the output files

## File Naming Conventions

- **Input files**: `[purpose].[format]` (e.g., `user_profile.json`)
- **Processing files**: `[stage_name]_[output_type].[format]` (e.g., `processed_profile.json`)
- **Output files**: `[final_]?[content_type].[format]` (e.g., `final_meal_plan.md`)
- **All files**: Lowercase with underscores, no spaces

## Key Features

### Astrological Integration
- Multi-perspective analysis (Western, Vedic, Psychological)
- Elemental food associations (Fire, Earth, Air, Water)
- Planetary timing recommendations
- Birth chart enrichment with calculated elements

### Nutritional Science
- Macro/micronutrient targeting based on health goals
- Dietary restriction and allergy compliance
- Budget-aware meal planning
- Nutritional adequacy validation

### User Experience
- Iterative refinement (regenerate with different parameters)
- Human-in-the-loop checkpoints (edit suggestions before finalizing)
- Multiple output formats (markdown, JSON, printable)
- Astrological education through symbolic food associations

### Quality Assurance
- Multi-perspective validation (nutritional, astrological, practical)
- Conflict detection (restrictions, budget, preferences)
- Comprehensive summary statistics
- Quality metrics and scoring

## Customization

### Modify Astrological Perspectives
Edit `02_astrological_interpretation_op.md` to change which astrological systems are analyzed or add new perspectives.

### Adjust Nutritional Targets
Edit `03_nutritional_planning_op.md` to modify macro/micronutrient calculations or add new health goal types.

### Change Meal Plan Duration
Edit `04_meal_plan_generation_op.md` to support different plan lengths (3, 14, 30 days) or meal frequencies.

### Add New Task Types
Extend the pipeline with additional stages (e.g., cost optimization, seasonal ingredient sourcing) by adding new op files.

## Troubleshooting

### Pipeline Doesn't Execute
- Verify all input files exist in `inputs/` directory
- Check that input file names match exactly (case-sensitive)
- Ensure YAML frontmatter in op files is properly formatted

### Incomplete Meal Plans
- Check that `astrological_context.md` includes current transit information
- Verify `dietary_preferences.md` includes all restrictions and preferences
- Ensure `meal_plan_request.md` specifies duration and meal frequency

### Nutritional Targets Not Met
- Review `nutritional_framework.json` for calculated targets
- Check that `recipes_detailed.md` includes nutritional information
- Verify health goals in `user_profile.json` are recognized

### Astrological Insights Missing
- Ensure `user_profile.json` includes all zodiac signs (Sun, Moon, Rising)
- Verify `astrological_context.md` includes current planetary positions
- Check that `processed_profile.json` was successfully generated

## Best Practices

1. **Complete User Profile**: Provide accurate birth time and location for precise astrological calculations
2. **Detailed Preferences**: Include specific dietary notes, budget, and cuisine preferences for better results
3. **Current Context**: Update `astrological_context.md` with current transits for timely recommendations
4. **Iterative Refinement**: Run the pipeline multiple times with different parameters to find optimal plans
5. **Manual Review**: Edit meal suggestions before finalizing to ensure personal alignment
6. **Export Outputs**: Save final meal plan and shopping list for reference during grocery shopping

## Support

For issues or questions about the pipeline:
- Review the detailed op file prompts for specific stage requirements
- Check the generated intermediate files to understand data flow
- Refer to the DocOps PIPELINE.md documentation for advanced patterns
