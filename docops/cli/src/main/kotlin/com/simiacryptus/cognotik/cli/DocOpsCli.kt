private fun applyTargetFilter(
plan: WorkPlan<PlatformTaskKind>,
root: File,
target: String?,
): WorkPlan<PlatformTaskKind> {
if (target.isNullOrBlank()) return plan
val targetFile = (File(target).let { if (it.isAbsolute) it else root.resolve(target) }).canonicalFile
return plan.filter { planned ->
try {
planned.task.data.main_file?.canonicalFile?.endsWith(targetFile) == true
} catch (e: Exception) {
false
}
}
}
  private fun printPlan(plan: WorkPlan<PlatformTaskKind>, root: File, mode: UpdateMode) {