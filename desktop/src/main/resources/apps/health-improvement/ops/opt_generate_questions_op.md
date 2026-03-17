---
transforms: ../round_(.*)/perspectives\.md -> ../round_$1+1/questions_for_patient.md
related:
  - ../symptoms.md
  - ../notes.json
  - ../round_.*/perspectives.md
---

* Based on the multi-perspective analysis and identified uncertainties, generate a prioritized list of follow-up
  questions for the patient
* Organize questions into categories:

- **Symptom Clarification**: Onset, duration, severity, triggers, relieving factors
- **Medical History**: Prior diagnoses, surgeries, family history relevant to suspected causes
- **Medications & Supplements**: Current and recent medications, allergies, over-the-counter use
- **Lifestyle & Environment**: Diet, exercise, sleep, occupational exposures, travel history
- **Review of Systems**: Targeted questions to rule in or rule out leading differential diagnoses

* Keep questions clear, non-technical, and patient-friendly
* Mark which questions are most critical for narrowing the differential
* **This file requires patient/user responses before the pipeline continues**

- **Emergency Screening**: Explicitly check for red flags (e.g., "Are you experiencing any shortness of breath or sudden
  weakness?")