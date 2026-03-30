---
transforms: ../inputs/user_profile\.json -> ../processing/processed_profile.json
related:
- ../inputs/dietary_preferences.md
task_type: FileModification
---
* Parse and validate the user's astrological birth chart data (birth date, time, location)
* Extract and normalize zodiac signs (Sun, Moon, Rising) and any provided house placements
* Validate dietary restrictions and allergies against a standard taxonomy
* Normalize health goals and cuisine preferences into structured categories
* Calculate derived astrological elements:
- Elemental balance (Fire, Earth, Air, Water distribution)
- Modality distribution (Cardinal, Fixed, Mutable)
- Numerological significance of birth date
* Enrich the profile with symbolic associations (ruling planets, element colors, seasonal alignment)
* Output a structured JSON object with:
- Validated user metadata (name, birth details)
- Normalized astrological profile with calculated elements
- Structured dietary data (restrictions, allergies, preferences)
- Health goals with priority ranking
- Elemental and modality percentages
* Ensure all data is properly typed and ready for downstream processing