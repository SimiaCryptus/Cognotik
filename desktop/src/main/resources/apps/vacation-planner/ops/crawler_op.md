---
task_type: CrawlerAgent
transforms: ../brainstorm_output\.md -> ../data/crawler_latest\.json
related:
  - ../user_preferences.md
  - ../analysis_output.md
validation_regex: "(?=.*\"destination\")(?=.*\"accommodation\")(?=.*\"activities\")(?=.*\"transportation\")(?=.*\"dining\")(?=.*\"confidence_score\")"
---

# Vacation Data Gathering & Validation

You are gathering real-time data to validate and enrich the vacation concepts generated in the brainstorming stage.

## Your Role

Your task is to gather current, verified data for each vacation concept including:
- Destination information (weather, visa requirements, best time to visit)
- Accommodation options (availability, pricing, ratings)
- Activities & attractions (pricing, availability, booking requirements)
- Transportation (flight costs, ground transportation)
- Dining options (price ranges, recommendations)

All data must be current (within 30 days), sourced from authoritative sources, and include confidence scores.

## Data Gathering Mission

For each vacation concept from the brainstorming output, gather the following data:

### Required Data Points (All Mandatory)

For each concept, you MUST gather:

#### 1. Destination Overview
- Official tourism website URL
- Current weather forecast (7-day)
- Best time to visit (season/months)
- Visa/entry requirements for [USER_COUNTRY]
- Current travel advisories or warnings
- Time zone and flight duration

#### 2. Accommodation Options
- 3 specific hotels/Airbnbs in [BUDGET_TIER] range
- Average nightly rate (current)
- Guest ratings (Booking.com, Airbnb, TripAdvisor)
- Availability for [TRAVEL_DATES]
- Cancellation policy
- Amenities (WiFi, parking, breakfast, etc.)

#### 3. Activities & Attractions
- 5 specific activities matching user preferences
- Operating hours and current status (open/closed/seasonal)
- Estimated cost per activity
- Booking requirements (advance reservation needed?)
- Accessibility information
- User reviews and ratings

#### 4. Transportation
- Flight cost estimate (from [USER_LOCATION])
- Ground transportation options (rental car, public transit, taxi)
- Estimated total transportation cost
- Travel time from airport to accommodation
- Parking costs (if applicable)

#### 5. Dining & Costs
- Average meal cost (budget, mid-range, upscale)
- Specific restaurant recommendations (3 per tier)
- Dietary accommodation availability
- Local food specialties
- Reservation requirements

### Data Validation Rules

For EACH data point, you MUST:

1. **Source**: Cite the specific source (URL, date accessed)
   - Example: "Source: booking.com (accessed 2026-03-19)"

2. **Confidence**: Rate confidence 1-5
   - 5 = Verified from official source, current within 7 days
   - 4 = Verified from authoritative source, current within 30 days
   - 3 = From reliable source, may be 30-60 days old
   - 2 = Estimated based on similar data
   - 1 = Rough estimate only

3. **Recency**: Note if data is current
   - "Current as of 2026-03-19"
   - "Last updated 2026-02-15 (34 days old)"

4. **Conflicts**: Flag any conflicting information found
   - "Source A: $X, Source B: $Y - using average"

### Data Quality Standards

- **Reject**: Information older than 90 days
- **Flag**: Information from non-authoritative sources
- **Verify**: Cross-reference prices across 2+ sources
- **Note**: Seasonal variations that affect data accuracy

---

## Output Format

Generate a JSON file with the following structure:

```json
{
  "metadata": {
    "generated_date": "2026-03-19T14:30:00Z",
    "data_gathering_duration_minutes": 45,
    "concepts_analyzed": 5,
    "overall_confidence_score": 3.8
  },
  "concepts": [
    {
      "concept_name": "[Concept Name]",
      "destination": "[Location]",
      "data_completeness": "5/5",
      "overall_confidence": 4,
      "destination_overview": {
        "tourism_website": "[URL]",
        "weather_forecast": {
          "current_conditions": "[Description]",
          "7_day_forecast": "[Summary]",
          "best_season": "[Months]",
          "source": "[URL]",
          "confidence": 5,
          "last_updated": "2026-03-19"
        },
        "visa_requirements": {
          "required": true/false,
          "details": "[Visa details]",
          "processing_time": "[Days]",
          "source": "[URL]",
          "confidence": 5
        },
        "travel_advisories": {
          "level": "[SAFE/CAUTION/WARNING/DO_NOT_TRAVEL]",
          "details": "[Any warnings]",
          "source": "[URL]",
          "confidence": 5
        },
        "flight_duration": "[Hours]",
        "time_zone": "[Timezone]"
      },
      "accommodation": {
        "options": [
          {
            "name": "[Hotel/Airbnb Name]",
            "type": "[Hotel/Airbnb/Resort/etc]",
            "nightly_rate": "$[X]",
            "rating": "[4.5/5]",
            "review_count": "[Number]",
            "available_for_dates": true/false,
            "cancellation_policy": "[Free/Moderate/Strict]",
            "amenities": ["WiFi", "Parking", "Breakfast"],
            "booking_url": "[URL]",
            "source": "[Booking.com/Airbnb/etc]",
            "confidence": 4,
            "last_checked": "2026-03-19"
          }
        ],
        "average_nightly_rate": "$[X]",
        "total_accommodation_cost": "$[X] for [Y] nights"
      },
      "activities": [
        {
          "name": "[Activity Name]",
          "category": "[Category]",
          "description": "[Brief description]",
          "cost": "$[X] per person",
          "duration": "[Hours]",
          "status": "[OPEN/CLOSED/SEASONAL]",
          "booking_required": true/false,
          "accessibility": "[Fully/Mostly/Limited]",
          "rating": "[4.5/5]",
          "review_count": "[Number]",
          "booking_url": "[URL]",
          "source": "[Viator/GetYourGuide/etc]",
          "confidence": 4,
          "last_checked": "2026-03-19"
        }
      ],
      "transportation": {
        "flights": {
          "estimated_cost": "$[X] round-trip per person",
          "duration": "[Hours]",
          "sources_checked": ["Google Flights", "Kayak", "Airline websites"],
          "confidence": 4,
          "note": "[Any relevant notes]"
        },
        "ground_transportation": {
          "options": [
            {
              "type": "[Rental car/Public transit/Taxi/etc]",
              "estimated_cost": "$[X]",
              "details": "[Description]",
              "source": "[URL]",
              "confidence": 3
            }
          ],
          "airport_to_accommodation": {
            "distance": "[Miles/KM]",
            "estimated_time": "[Minutes]",
            "estimated_cost": "$[X]"
          }
        },
        "parking": {
          "airport_parking": "$[X] per day",
          "accommodation_parking": "[Included/Free/$X per day]",
          "source": "[URL]"
        }
      },
      "dining": {
        "meal_costs": {
          "budget": {
            "average_cost": "$[X] per meal",
            "examples": ["[Restaurant 1]", "[Restaurant 2]", "[Restaurant 3]"],
            "source": "[URL]",
            "confidence": 3
          },
          "mid_range": {
            "average_cost": "$[X] per meal",
            "examples": ["[Restaurant 1]", "[Restaurant 2]", "[Restaurant 3]"],
            "source": "[URL]",
            "confidence": 3
          },
          "upscale": {
            "average_cost": "$[X] per meal",
            "examples": ["[Restaurant 1]", "[Restaurant 2]", "[Restaurant 3]"],
            "source": "[URL]",
            "confidence": 3
          }
        },
        "dietary_accommodations": "[Description of how dietary restrictions are handled]",
        "local_specialties": ["[Specialty 1]", "[Specialty 2]", "[Specialty 3]"],
        "reservation_requirements": "[Most restaurants require/don't require reservations]"
      },
      "cost_summary": {
        "flights": "$[X]",
        "accommodation": "$[X]",
        "activities": "$[X]",
        "meals": "$[X]",
        "transportation": "$[X]",
        "total_per_person": "$[X]",
        "total_for_group": "$[X] (assuming [Y] people)"
      },
      "data_quality_assessment": {
        "overall_confidence": 4,
        "confidence_breakdown": {
          "destination_info": 5,
          "accommodation": 4,
          "activities": 4,
          "transportation": 4,
          "dining": 3
        },
        "recency": "All data current within 7 days",
        "conflicts_found": "[None / List conflicts]",
        "gaps": "[Missing data points]",
        "verification_notes": "[Any notes about data verification]"
      }
    }
  ],
  "summary": {
    "most_affordable_concept": "[Concept Name] at $[X] per person",
    "most_expensive_concept": "[Concept Name] at $[X] per person",
    "best_value_concept": "[Concept Name] - [Rationale]",
    "data_gathering_challenges": ["[Challenge 1]", "[Challenge 2]"],
    "recommendations_for_next_stage": [
      "[Recommendation 1]",
      "[Recommendation 2]",
      "[Recommendation 3]"
    ]
  }
}
```

---

## Handling Missing Data

If you cannot find required data:

1. **Note the Gap**: "Activity pricing not available"
2. **Provide Estimate**: "Estimated $X based on similar activities in region"
3. **Flag Confidence**: "Confidence: 2/5 (estimate only)"
4. **Suggest Source**: "Recommend contacting [venue] directly"
5. **Mark as Incomplete**: Include in output but flag as needing verification

**Example**:
```json
{
  "name": "Zip-lining Tour",
  "cost": "$75 per person (estimated)",
  "confidence": 2,
  "note": "Specific pricing not available online. Estimate based on similar zip-line tours in region. Recommend contacting operator directly for current pricing.",
  "booking_url": "[Operator website]"
}
```

---

## Handling Conflicting Data

If sources disagree:

1. **List All Versions**: "Source A: $X, Source B: $Y"
2. **Identify Most Reliable**: "Most authoritative: [Source]"
3. **Recommend Value**: "Use $X as planning estimate"
4. **Flag for Verification**: "Verify current pricing before booking"

**Example**:
```json
{
  "nightly_rate": "$120",
  "note": "Booking.com shows $120, Airbnb shows $135. Using Booking.com rate as it's more recent (updated today). Recommend verifying current rate before booking.",
  "sources": [
    {
      "source": "Booking.com",
      "rate": "$120",
      "last_updated": "2026-03-19"
    },
    {
      "source": "Airbnb",
      "rate": "$135",
      "last_updated": "2026-03-15"
    }
  ]
}
```

---

## Data Freshness & Confidence Scoring

### Confidence Score Guidelines

**5 = Verified from Official Source, Current Within 7 Days**
- Direct from official tourism website
- Current booking confirmation
- Official pricing from venue
- Example: "Weather from weather.gov (updated today)"

**4 = Verified from Authoritative Source, Current Within 30 Days**
- Major booking site (Booking.com, Airbnb, Viator)
- Established travel guide (Lonely Planet, Fodor's)
- Recent review (within 30 days)
- Example: "Hotel pricing from Booking.com (updated 5 days ago)"

**3 = From Reliable Source, May Be 30-60 Days Old**
- Travel blog or guide
- User reviews (older than 30 days)
- Estimated based on historical data
- Example: "Activity pricing estimated from 2025 data"

**2 = Estimated Based on Similar Data**
- Extrapolated from similar destinations
- Average of multiple sources
- Rough estimate
- Example: "Meal costs estimated at $15-20 based on similar beach towns"

**1 = Rough Estimate Only**
- Guess based on general knowledge
- No verification
- Use only as last resort
- Example: "Estimated $50 for taxi ride (not verified)"

---

## Example Output

Here's an example of what good output looks like:

```json
{
  "metadata": {
    "generated_date": "2026-03-19T14:30:00Z",
    "data_gathering_duration_minutes": 45,
    "concepts_analyzed": 5,
    "overall_confidence_score": 3.8
  },
  "concepts": [
    {
      "concept_name": "Caribbean Party Weekend",
      "destination": "Cancun, Mexico",
      "data_completeness": "5/5",
      "overall_confidence": 4,
      "destination_overview": {
        "tourism_website": "https://www.cancun.com",
        "weather_forecast": {
          "current_conditions": "Sunny, 82°F, light breeze",
          "7_day_forecast": "Sunny and warm, highs 80-85°F, lows 75-78°F",
          "best_season": "November-April (dry season)",
          "source": "weather.gov (accessed 2026-03-19)",
          "confidence": 5,
          "last_updated": "2026-03-19"
        },
        "visa_requirements": {
          "required": false,
          "details": "US citizens do not require visa for Mexico. Tourist card issued on arrival.",
          "processing_time": "Issued at airport",
          "source": "travel.state.gov",
          "confidence": 5
        },
        "travel_advisories": {
          "level": "CAUTION",
          "details": "Exercise normal precautions. Avoid certain areas at night. Cancun tourist zone is generally safe.",
          "source": "travel.state.gov",
          "confidence": 5
        },
        "flight_duration": "4-5 hours from US",
        "time_zone": "Central Time (same as US Central)"
      },
      "accommodation": {
        "options": [
          {
            "name": "Grand Palladium Cancun",
            "type": "All-Inclusive Resort",
            "nightly_rate": "$180",
            "rating": "4.3/5",
            "review_count": 2341,
            "available_for_dates": true,
            "cancellation_policy": "Free cancellation up to 7 days",
            "amenities": ["All-inclusive meals", "Beach access", "Multiple pools", "WiFi", "Entertainment"],
            "booking_url": "https://www.booking.com/...",
            "source": "Booking.com",
            "confidence": 4,
            "last_checked": "2026-03-19"
          },
          {
            "name": "Cancun Beachfront Airbnb",
            "type": "Airbnb",
            "nightly_rate": "$150",
            "rating": "4.7/5",
            "review_count": 156,
            "available_for_dates": true,
            "cancellation_policy": "Moderate (50% refund up to 5 days)",
            "amenities": ["Kitchen", "Beach view", "WiFi", "Washer/Dryer"],
            "booking_url": "https://www.airbnb.com/...",
            "source": "Airbnb",
            "confidence": 4,
            "last_checked": "2026-03-19"
          },
          {
            "name": "Oasis Cancun Hotel",
            "type": "Hotel",
            "nightly_rate": "$120",
            "rating": "4.0/5",
            "review_count": 892,
            "available_for_dates": true,
            "cancellation_policy": "Free cancellation up to 3 days",
            "amenities": ["Pool", "Restaurant", "WiFi", "Beach access"],
            "booking_url": "https://www.booking.com/...",
            "source": "Booking.com",
            "confidence": 4,
            "last_checked": "2026-03-19"
          }
        ],
        "average_nightly_rate": "$150",
        "total_accommodation_cost": "$300 for 2 nights"
      },
      "activities": [
        {
          "name": "Xcaret Park",
          "category": "Theme Park",
          "description": "Large eco-archaeological park with cenotes, beach, and cultural shows",
          "cost": "$89 per person",
          "duration": "6-8 hours",
          "status": "OPEN",
          "booking_required": true,
          "accessibility": "Mostly accessible",
          "rating": "4.5/5",
          "review_count": 3421,
          "booking_url": "https://www.xcaret.com",
          "source": "Xcaret official website",
          "confidence": 5,
          "last_checked": "2026-03-19"
        },
        {
          "name": "Snorkeling Tour",
          "category": "Water Sports",
          "description": "Guided snorkeling at coral reefs and cenotes",
          "cost": "$65 per person",
          "duration": "4 hours",
          "status": "OPEN",
          "booking_required": true,
          "accessibility": "Limited (requires swimming ability)",
          "rating": "4.6/5",
          "review_count": 2156,
          "booking_url": "https://www.viator.com/...",
          "source": "Viator",
          "confidence": 4,
          "last_checked": "2026-03-19"
        },
        {
          "name": "Cancun Nightclub Tour",
          "category": "Nightlife",
          "description": "VIP access to top nightclubs with drinks included",
          "cost": "$75 per person",
          "duration": "4 hours",
          "status": "OPEN",
          "booking_required": true,
          "accessibility": "Fully accessible",
          "rating": "4.3/5",
          "review_count": 1234,
          "booking_url": "https://www.getyourguide.com/...",
          "source": "GetYourGuide",
          "confidence": 4,
          "last_checked": "2026-03-19"
        },
        {
          "name": "Cenote Swimming",
          "category": "Nature",
          "description": "Swim in natural underground cenote pools",
          "cost": "$45 per person",
          "duration": "3 hours",
          "status": "OPEN",
          "booking_required": false,
          "accessibility": "Mostly accessible",
          "rating": "4.7/5",
          "review_count": 892,
          "booking_url": "https://www.viator.com/...",
          "source": "Viator",
          "confidence": 4,
          "last_checked": "2026-03-19"
        },
        {
          "name": "Playa del Carmen Day Trip",
          "category": "Beach",
          "description": "Day trip to nearby beach town with shopping and dining",
          "cost": "$0 (self-guided) or $35 (guided tour)",
          "duration": "Full day",
          "status": "OPEN",
          "booking_required": false,
          "accessibility": "Fully accessible",
          "rating": "4.4/5",
          "review_count": 1567,
          "booking_url": "https://www.getyourguide.com/...",
          "source": "GetYourGuide",
          "confidence": 4,
          "last_checked": "2026-03-19"
        }
      ],
      "transportation": {
        "flights": {
          "estimated_cost": "$250 round-trip per person",
          "duration": "4-5 hours",
          "sources_checked": ["Google Flights", "Kayak", "Southwest Airlines"],
          "confidence": 4,
          "note": "Prices vary by departure city. Estimate based on flights from major US hubs."
        },
        "ground_transportation": {
          "options": [
            {
              "type": "Rental Car",
              "estimated_cost": "$40-60 per day",
              "details": "Economy car rental from airport",
              "source": "Hertz.com",
              "confidence": 4
            },
            {
              "type": "Taxi",
              "estimated_cost": "$50-70 from airport to hotel",
              "details": "Official airport taxis available",
              "source": "Cancun airport website",
              "confidence": 4
            },
            {
              "type": "Shuttle Service",
              "estimated_cost": "$30-40 per person",
              "details": "Shared shuttle from airport",
              "source": "Cancun airport website",
              "confidence": 4
            }
          ],
          "airport_to_accommodation": {
            "distance": "20 miles",
            "estimated_time": "30-45 minutes",
            "estimated_cost": "$50-70 (taxi) or $30-40 (shuttle)"
          }
        },
        "parking": {
          "airport_parking": "$15-20 per day",
          "accommodation_parking": "Included at most hotels/resorts",
          "source": "Cancun airport website"
        }
      },
      "dining": {
        "meal_costs": {
          "budget": {
            "average_cost": "$8-12 per meal",
            "examples": ["Taco stands", "Local taquerias", "Street food"],
            "source": "TripAdvisor reviews",
            "confidence": 3
          },
          "mid_range": {
            "average_cost": "$15-25 per meal",
            "examples": ["La Habichuela", "Señor Frogs", "Coco Bongo"],
            "source": "TripAdvisor",
            "confidence": 4
          },
          "upscale": {
            "average_cost": "$40-60 per meal",
            "examples": ["Palazzo", "Lorenzillo's", "The Surfin Burro"],
            "source": "TripAdvisor",
            "confidence": 4
          }
        },
        "dietary_accommodations": "Good vegetarian and vegan options. Seafood allergies easily accommodated.",
        "local_specialties": ["Ceviche", "Cochinita Pibil", "Huevos Rancheros", "Fresh tropical fruits"],
        "reservation_requirements": "Upscale restaurants recommend reservations. Casual dining first-come, first-served."
      },
      "cost_summary": {
        "flights": "$250",
        "accommodation": "$300",
        "activities": "$275",
        "meals": "$150",
        "transportation": "$100",
        "total_per_person": "$1,075",
        "total_for_group": "$4,300 (assuming 4 people)"
      },
      "data_quality_assessment": {
        "overall_confidence": 4,
        "confidence_breakdown": {
          "destination_info": 5,
          "accommodation": 4,
          "activities": 4,
          "transportation": 4,
          "dining": 3
        },
        "recency": "All data current within 7 days",
        "conflicts_found": "None",
        "gaps": "None",
        "verification_notes": "All major data points verified from official sources or major booking platforms."
      }
    }
  ],
  "summary": {
    "most_affordable_concept": "Budget Beach Getaway at $450 per person",
    "most_expensive_concept": "Island Hopping Adventure at $1,050 per person",
    "best_value_concept": "California Coastal Escape - Good experience quality at mid-range price",
    "data_gathering_challenges": [
      "Flight prices vary significantly by departure city",
      "Activity availability depends on specific dates",
      "Weather forecasts are estimates for future dates"
    ],
    "recommendations_for_next_stage": [
      "Verify flight prices from user's specific departure city",
      "Confirm accommodation availability for exact travel dates",
      "Contact activity operators directly for current pricing and availability"
    ]
  }
}
```

---

## Validation Checklist

Your output is valid if:

- [ ] JSON is valid and well-formed
- [ ] All 5 concepts have complete data
- [ ] All required data points are present for each concept
- [ ] Confidence scores are assigned (1-5 scale)
- [ ] Sources are cited for all data points
- [ ] Recency is documented (dates of last updates)
- [ ] Conflicts are identified and resolved
- [ ] Missing data is noted and explained
- [ ] Cost summary is accurate (sums match components)
- [ ] Validation regex matches (all required fields present)

---

## Success Criteria

This op file produces high-quality output when:

1. **Completeness**: All required data points are gathered for all concepts
2. **Accuracy**: Data is verified from authoritative sources
3. **Currency**: Data is current (within 30 days)
4. **Transparency**: Sources and confidence scores are documented
5. **Actionability**: Data directly informs planning decisions
6. **Honesty**: Gaps and uncertainties are acknowledged