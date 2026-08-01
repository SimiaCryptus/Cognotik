ActionParam("mode", required = false, label = "Update mode", description = "e.g. PatchToUpdate"),
ActionParam(
"target", required = false, label = "Target",
description = "only run the task producing this output file",
/* Enumerated live from the current selection; see resolveDocOpsTargets. */
dynamic = true
),
ActionParam("var", required = false, description = "template variable override NAME=VALUE (repeatable)"),
),