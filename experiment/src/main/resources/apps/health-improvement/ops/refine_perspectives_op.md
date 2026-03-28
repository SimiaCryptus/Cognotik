---
transforms:
  - ../round_(.*)/questions_for_patient\.md -> ../round_$1/perspectives.md
  - ../round_(.*)/brainstorm\.md -> ../round_$1/perspectives.md
  - ../round_(.*)/perspectives\.md -> ../round_$1+1/perspectives.md
related:
  - ../symptoms.md
  - ../notes.json
  - ../round_.*/brainstorm.md
task_type: MultiPerspectiveAnalysis
---

* With the patient's answers to the follow-up questions now available, re-analyze from multiple perspectives:
* Incorporate any new candidate diagnoses surfaced by supplemental brainstorming rounds.

- **Diagnostician**: How does the new information narrow or reshape the differential diagnosis?
- **Specialist**: Given the refined picture, what specific conditions move up or down in likelihood?
- **Preventive Medicine**: Are there lifestyle or environmental factors revealed that point to root causes?
- **Patient Experience**: How do the patient's responses inform concerns about quality of life, anxiety, or treatment
  preferences?

* Produce a refined, ranked differential diagnosis
* Identify what further information (lab work, imaging, specialist referral) would be most valuable
* Note any conditions that can now be reasonably ruled out
* Explicitly address any new candidates from supplemental brainstorming — include, rank, or rule them out with rationale