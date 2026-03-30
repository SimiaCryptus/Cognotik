
# Astrological Meal Planner - Pipeline Summary

## Overview

This DocOps pipeline generates personalized, astrologically-aligned meal plans by combining nutritional science with astrological symbolism. The system transforms user inputs (birth chart, dietary preferences, current transits) into a complete meal plan with recipes, shopping list, and astrological insights.

## Pipeline Stages (8 Total)

### Stage 1: Profile Analysis
- **Op File**: `01_profile_analysis_op.md`
- **Input**: `user_profile.json`, `dietary_preferences.md`
- **Output**: `processed_profile.json`
- **Task Type**: FileModification
- **Purpose**: Validate and enrich astrological/dietary data

### Stage 2: Astrological Interpretation
- **Op File**: `02_astrological_interpretation_op.md`
- **Input**: `processed_profile.json`, `astrological_context.md`
- **Output**: `astrological_insights.md`
- **Task Type**: MultiPerspectiveAnalysis
- **Purpose**: Multi-perspective birth chart analysis + food-element associations

### Stage 3: Nutritional Planning
- **Op File**: `03_nutritional_planning_op.md`
- **Input**: `dietary_preferences.md`, `processed_profile.json`
- **Output**: `nutritional_framework.json`
- **Task Type**: FileModification
- **Purpose**: Calculate nutritional targets and food mappings

### Stage 4: Meal Plan Generation
- **Op File**: `04_meal_plan_generation_op.md`
- **Input**: `meal_plan_request.md`, `astrological_insights.md`, `nutritional_framework.json`
- **Output**: `meal_plan_draft.md`
- **Task Type**: Brainstorming
- **Purpose**: Generate creative meal suggestions

### Stage 5: Recipe Development
- **Op File**: `05_recipe_development_op.md`
- **Input**: `meal_plan_draft.md`, `processed_profile.json`, `dietary_preferences.md`
- **Output**: `recipes_detailed.md`
- **Task Type**: FileModification
- **Purpose**: Expand meals into full recipes with astrological notes

### Stage 6: Shopping & Logistics
- **Op File**: `06_shopping_logistics_op.md`
- **Input**: `recipes_detailed.md`, `dietary_preferences.md`
- **Output**: `shopping_list.md`, `meal_prep_schedule.json`
- **Task Type**: FileModification
- **Purpose**: Create shopping list and prep timeline

### Stage 7: Final Review
- **Op File**: `07_final_review_op.md`
- **Input**: `meal_plan_draft.md`, `recipes_detailed.md`, `shopping_list.md`, `astrological_insights.md`
- **Output**: `final_meal_plan.md`, `meal_plan_summary.json`
- **Task Type**: CodeReview
- **Purpose**: Validate and polish final outputs

### Stage 8: Summary Generation
- **Op File**: `08_summary_generation_op.md`
- **Input**: `final_meal_plan.md`, `shopping_list.md`, `meal_prep_schedule.json`
- **Output**: `meal_plan_summary.json`
- **Task Type**: FileModification
- **Purpose**: Extract structured summary data

## Dependency Graph

```
user_profile.json ──┐
├─→ [Stage 1] ──→ processed_profile.json
dietary_preferences.md ┘                    │
├─→ [Stage 2] ──→ astrological_insights.md
astrological_context.md ────────────────────┘                  │
├─→ [Stage 4] ──→ meal_plan_draft.md
nutritional_framework.json ←─ [Stage 3] ←─ processed_profile.json  │
├─→ [Stage 5] ──→ recipes_detailed.md
meal_plan_request.md ──────────────────────────────────────────┘   │
├─→ [Stage 6] ──→ shopping_list.md
│                meal_prep_schedule.json
│
└─→ [Stage 7] ──→ final_meal_plan.md
meal_plan_summary.json
(+ Stage 8)
```

## Key Features

### Astrological Integration
- **Multi-perspective analysis**: Western, Vedic, and Psychological astrology
- **Elemental associations**: Fire, Earth, Air, Water food mappings
- **Planetary timing**: Meal timing recommendations based on transits
- **Birth chart enrichment**: Calculated elements, aspects, numerology

### Nutritional Science
- **Macro/micronutrient targeting**: Based on health goals
- **Dietary compliance**: Restrictions and allergies respected
- **Budget awareness**: Cost-per-meal calculations
- **Nutritional validation**: Adequacy checking across all meals

### User Experience
- **Iterative refinement**: Regenerate with different parameters
- **Human-in-the-loop**: Edit suggestions before finalizing
- **Multiple formats**: Markdown, JSON, printable outputs
- **Educational content**: Astrological symbolism explained

### Quality Assurance
- **Multi-perspective validation**: Nutritional, astrological, practical
- **Conflict detection**: Restrictions, budget, preferences
- **Comprehensive metrics**: Statistics and quality scores
- **Polished outputs**: Professional, ready-to-use deliverables

## File Organization

```
ops/                                    # Pipeline operation files
├── 01_profile_analysis_op.md
├── 02_astrological_interpretation_op.md
├── 03_nutritional_planning_op.md
├── 04_meal_plan_generation_op.md
├── 05_recipe_development_op.md
├── 06_shopping_logistics_op.md
├── 07_final_review_op.md
├── 08_summary_generation_op.md
└── README.md

inputs/                                 # User-provided inputs
├── user_profile.json
├── dietary_preferences.md
├── astrological_context.md
└── meal_plan_request.md

processing/                             # Intermediate files
├── processed_profile.json
├── astrological_insights.md
├── nutritional_framework.json
├── meal_plan_draft.md
└── recipes_detailed.md

outputs/                                # Final deliverables
├── final_meal_plan.md
├── meal_plan_summary.json
├── shopping_list.md
├── meal_prep_schedule.json
├── astrological_insights.md
└── recipes_detailed.md
```

## Execution Flow

1. **User prepares inputs** in `inputs/` directory
2. **Pipeline executes** stages in dependency order:
    - Stage 1 runs first (no dependencies)
    - Stages 2 & 3 run in parallel (both depend on Stage 1)
    - Stage 4 runs after Stages 2 & 3 complete
    - Stage 5 runs after Stage 4 completes
    - Stage 6 runs after Stage 5 completes
    - Stages 7 & 8 run after Stage 6 completes
3. **Outputs generated** in `outputs/` directory
4. **User reviews** final meal plan and supporting documents

## Regex Patterns Used

All patterns use Java regex syntax (not glob syntax):

- `\.` = literal dot (e.g., `user_profile\.json`)
- `(.+)` = capture group (one or more characters)
- `[^/\.]+` = one or more characters that aren't `/` or `.`
- `\.\./` = literal `../` (parent directory)
- `$1` = backreference to first capture group
- `$1+1` = arithmetic on numeric capture groups

## Best Practices

1. **Complete user profile** with accurate birth time and location
2. **Detailed preferences** including budget, restrictions, and cuisine preferences
3. **Current astrological context** with up-to-date planetary positions
4. **Iterative refinement** by running pipeline multiple times
5. **Manual review** of suggestions before finalizing
6. **Export outputs** for reference during shopping and cooking

## Customization Points

- **Astrological perspectives**: Edit Stage 2 to add/modify perspectives
- **Nutritional targets**: Edit Stage 3 to adjust macro/micronutrient calculations
- **Meal plan duration**: Edit Stage 4 to support different durations
- **Task types**: Add new stages with different task types (CrawlerAgent, SubPlan, etc.)
- **Output formats**: Modify stages to produce additional output formats

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Pipeline doesn't execute | Missing input files | Verify all 4 input files exist in `inputs/` |
| Incomplete meal plans | Missing astrological context | Update `astrological_context.md` with current transits |
| Nutritional targets not met | Incomplete dietary preferences | Add detailed restrictions and health goals |
| Astrological insights missing | Incomplete user profile | Provide all zodiac signs (Sun, Moon, Rising) |
| Regex doesn't match files | Pattern syntax error | Use Java regex syntax, escape dots with `\.` |

## Next Steps

1. Review the README.md for detailed usage instructions
2. Prepare input files using the templates in `inputs/`
3. Run the pipeline with `docops run --app astrological-meal-planner`
4. Review outputs in `outputs/` directory
5. Iterate and refine as needed