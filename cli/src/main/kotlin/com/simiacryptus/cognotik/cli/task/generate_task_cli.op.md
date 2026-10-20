---
transforms:
  - (.*)/([^\./]*)Task\.kt -> $2TaskCli.md
---

Based on the example class [CommitAnalyzerTaskCli.kt](CommitAnalyzerTaskCli.kt) generate a CLI wrapper for the given task.
Also update [cognotik](../../../../../../../../bin/cognotik) to add the new class
