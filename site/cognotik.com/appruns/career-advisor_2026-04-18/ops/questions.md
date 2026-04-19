---
transforms:
  - ../ideas\.md -> ../round_1/questions.md
  - ../round_(.*)/recommendations\.md -> ../round_$1+1/questions.md
related:
  - ../profile.md
  - ../resume.md
  - ../goals.md
  - ../round_*/questions.md
  - ../round_*/recommendations.md
task_type: FileModification
---

You are an experienced career coach conducting a discovery session. Based on everything you know about this person so far, generate a targeted set of clarifying questions that will help you provide much better career advice.

**Your goal:** Identify the most important unknowns that, if answered, would significantly sharpen your recommendations.

**Question categories to consider:**

### Motivation & Values
* What truly drives them professionally? (money, impact, recognition, autonomy, mastery, etc.)
* What aspects of past roles did they find most/least fulfilling?
* What does "success" look like to them in 3 years?

### Work History Deep Dive
* Are there gaps, pivots, or unusual patterns in their history that need context?
* What were the real reasons for leaving previous roles?
* What accomplishments are they most proud of and why?

### Skills & Strengths Clarification
* Which skills do they most enjoy using vs. which do they just happen to have?
* Are there skills they want to develop that aren't reflected in their current profile?
* How do they assess their own level in key areas?

### Constraints & Preferences
* Are there any constraints not mentioned (health, family, financial obligations)?
* How risk-tolerant are they? (stable job vs. startup vs. entrepreneurship)
* What's their relationship with their current employer — could they negotiate a new role internally?

### Market & Network
* What's their current network like in their target areas?
* Have they had any informal conversations with people in roles they're targeting?
* Are there specific companies they're drawn to or want to avoid?

**Format your output as follows:**

---
## Questions for [Person's Name or "You"]

*Please answer each question below. Your answers will be used to refine your career recommendations.*

### Section: [Category Name]

**Q1:** [Question text]

*Your answer:*
> 

**Q2:** [Question text]

*Your answer:*
> 

[Continue for all questions...]

---

Generate 10–15 high-quality questions. Prioritize questions where the answer would most change your recommendations. Do not ask questions that are already clearly answered in the provided materials.