---
task_type: MultiPerspectiveAnalysis
transforms: ../analysis_output\.md -> ../continuous_outputs/dashboard_insights.json
related:
  - ../brainstorm_output.md
  - ../research.md
  - ../itinerary.md
validation_regex: "(?=.*\"summary\")(?=.*\"perspectives\")(?=.*\"data_freshness\")(?=.*\"recommendations\")(?=.*\"confidence_score\")"
---

# Real-Time Dashboard Insights Aggregation

You are aggregating data from multiple pipeline sources into a unified dashboard view that provides real-time insights, data freshness indicators, and actionable recommendations.

## Input Context

You will receive:
- **Brainstorming Analysis**: Initial vacation concepts and dimensions
- **Multi-Perspective Analysis**: Budget, experience, and logistics perspectives
- **Crawler Data**: Real-time activity, pricing, and availability data
- **Itinerary**: Day-by-day plan with costs and logistics
- **User Preferences**: Original vacation request

## Your Task

Aggregate all pipeline outputs into a unified JSON dashboard that:
- **Synthesizes** insights from multiple sources
- **Highlights** key metrics and recommendations
- **Surfaces** data freshness and confidence scores
- **Identifies** conflicts or concerns
- **Provides** actionable next steps

## Output Format

Produce a single JSON file with this structure:

```json
{
  "dashboard_generated": "YYYY-MM-DDTHH:MM:SSZ",
  "vacation_concept": "[Selected Concept Name]",
  "destination": "[Location]",
  
  "summary": {
    "trip_duration": "3 days / 2 nights",
    "group_size": 4,
    "total_estimated_cost": 1500,
    "cost_per_person": 375,
    "difficulty_level": "Moderate",
    "best_for": "[Who this trip suits]",
    "overall_recommendation": "[1-2 sentence summary]"
  },
  
  "perspectives": {
    "budget_optimizer": {
      "recommendation": "[Budget-focused recommendation]",
      "cost_efficiency_score": 4,
      "cost_reduction_opportunities": [
        {
          "opportunity": "[Specific opportunity]",
          "potential_savings": 50,
          "feasibility": "High/Medium/Low"
        }
      ],
      "budget_risks": [
        {
          "risk": "[Specific risk]",
          "likelihood": "High/Medium/Low",
          "mitigation": "[How to mitigate]"
        }
      ]
    },
    
    "experience_maximizer": {
      "recommendation": "[Experience-focused recommendation]",
      "experience_quality_score": 4.5,
      "unique_opportunities": [
        {
          "opportunity": "[Specific experience]",
          "why_unique": "[Why it's memorable]",
          "priority": "High/Medium/Low"
        }
      ],
      "experience_risks": [
        {
          "risk": "[What could diminish experience]",
          "likelihood": "High/Medium/Low",
          "mitigation": "[How to mitigate]"
        }
      ]
    },
    
    "logistics_optimizer": {
      "recommendation": "[Logistics-focused recommendation]",
      "feasibility_score": 4,
      "logistical_challenges": [
        {
          "challenge": "[Specific challenge]",
          "complexity": "High/Medium/Low",
          "mitigation": "[How to handle]"
        }
      ],
      "safety_assessment": {
        "health_considerations": "[Any health risks]",
        "security_assessment": "[Safety of destination]",
        "accessibility_evaluation": "[Accessibility for group]",
        "overall_safety_score": 5
      }
    }
  },
  
  "data_freshness": {
    "crawler_data_age_hours": 2,
    "last_crawler_update": "YYYY-MM-DDTHH:MM:SSZ",
    "data_sources": [
      {
        "source": "[Website/API name]",
        "last_updated": "YYYY-MM-DDTHH:MM:SSZ",
        "confidence_score": 5,
        "status": "Current/Stale/Unavailable"
      }
    ],
    "stale_data_warnings": [
      {
        "data_point": "[What data is stale]",
        "age_days": 5,
        "recommendation": "[Suggest refresh or verification]"
      }
    ],
    "data_gaps": [
      {
        "gap": "[Missing data point]",
        "impact": "High/Medium/Low",
        "recommendation": "[How to fill gap]"
      }
    ]
  },
  
  "cost_analysis": {
    "total_estimated_cost": 1500,
    "cost_per_person": 375,
    "cost_breakdown": {
      "lodging": {
        "amount": 600,
        "percentage": 40,
        "details": "[X nights × $Y per night]"
      },
      "activities": {
        "amount": 450,
        "percentage": 30,
        "details": "[List major activities]"
      },
      "meals": {
        "amount": 300,
        "percentage": 20,
        "details": "[Budget: $X per day × Y days]"
      },
      "transportation": {
        "amount": 150,
        "percentage": 10,
        "details": "[Flights, local transit, rental car]"
      }
    },
    "budget_status": "Within budget / Over budget / Under budget",
    "cost_optimization_opportunities": [
      {
        "opportunity": "[Specific opportunity]",
        "current_cost": 100,
        "optimized_cost": 75,
        "savings": 25,
        "trade_offs": "[What you give up]"
      }
    ]
  },
  
  "activity_analysis": {
    "total_activities": 8,
    "activities_by_type": {
      "adventure": 3,
      "relaxation": 2,
      "cultural": 2,
      "food": 1
    },
    "activities": [
      {
        "name": "[Activity Name]",
        "type": "[Type]",
        "cost_per_person": 65,
        "duration_hours": 3,
        "rating": 4.7,
        "availability": "Available/Limited/Sold Out",
        "booking_status": "Not booked/Booked/Recommended",
        "confidence_score": 5,
        "last_verified": "YYYY-MM-DDTHH:MM:SSZ"
      }
    ],
    "activity_risks": [
      {
        "activity": "[Activity Name]",
        "risk": "[Specific risk]",
        "likelihood": "High/Medium/Low",
        "mitigation": "[How to mitigate]"
      }
    ]
  },
  
  "recommendations": {
    "next_steps": [
      {
        "step": 1,
        "action": "[Specific action to take]",
        "priority": "High/Medium/Low",
        "deadline": "YYYY-MM-DD",
        "reason": "[Why this action is important]"
      }
    ],
    "booking_priorities": [
      {
        "item": "[What to book]",
        "urgency": "High/Medium/Low",
        "deadline": "YYYY-MM-DD",
        "reason": "[Why urgent]"
      }
    ],
    "questions_for_user": [
      {
        "question": "[Clarification needed]",
        "impact": "High/Medium/Low",
        "reason": "[Why this matters]"
      }
    ],
    "concerns_to_address": [
      {
        "concern": "[Specific concern]",
        "severity": "High/Medium/Low",
        "recommendation": "[How to address]"
      }
    ]
  },
  
  "confidence_scores": {
    "overall_confidence": 4.2,
    "data_completeness": 0.95,
    "data_accuracy": 0.90,
    "recommendation_confidence": 0.85,
    "confidence_notes": "[Any caveats or limitations]"
  },
  
  "metadata": {
    "pipeline_stage": "Dashboard Aggregation",
    "generated_by": "dashboard_op",
    "input_sources": [
      "brainstorm_output.md",
      "analysis_output.md",
      "research.md",
      "itinerary.md"
    ],
    "data_sources_used": [
      "[Website 1]",
      "[Website 2]",
      "[Website 3]"
    ]
  }
}
```

## Data Aggregation Rules

### Conflict Resolution

If perspectives provide conflicting recommendations:

1. **Document the conflict**: Show where perspectives disagree
2. **Analyze root cause**: Why do they differ?
3. **Provide integrated recommendation**: How to balance competing priorities
4. **Flag for user decision**: If conflict can't be resolved analytically

### Data Freshness Handling

For each data point:

1. **Check age**: How old is this data?
2. **Assess confidence**: How reliable is this data?
3. **Flag if stale**: If older than 30 days, mark as stale
4. **Suggest refresh**: Recommend re-crawling if data is critical

### Confidence Scoring

Assign confidence scores (1-5) based on:
- **Data source reliability**: Official vs. third-party
- **Data recency**: Current vs. outdated
- **Data consistency**: Verified across multiple sources
- **Completeness**: All required data points present

## Quality Criteria

Your output is high-quality if:
- ✓ All three perspectives are represented
- ✓ Data freshness is clearly indicated
- ✓ Confidence scores are assigned
- ✓ Conflicts are documented
- ✓ Recommendations are specific and actionable
- ✓ JSON output is valid and well-formed
- ✓ All data sources are cited
- ✓ Metadata is complete

## Validation Checklist

- [ ] All required JSON fields present
- [ ] All three perspectives included
- [ ] Data freshness indicators present
- [ ] Confidence scores assigned
- [ ] Recommendations are specific
- [ ] JSON is valid and well-formed
- [ ] All data sources cited
- [ ] Metadata complete