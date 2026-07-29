private fun resolveDocOpsTargets(ctx: FsActionContext): List<ActionOption> {
val cfg = config ?: return emptyList()
val paths = ctx.req.getParameterValues("path").orEmpty().filter { it.isNotBlank() }
if (paths.isEmpty()) return emptyList()
if (!ioLock.tryLock(2, TimeUnit.SECONDS)) return emptyList()
try {
val plan = try {
planDocOpsQuietly(cfg, paths)
} catch (e: Throwable) {
return emptyList()
}
return plan.tasks.map { ActionOption(it.target.relativeToOrAbsolute(cfg.root)) }
} finally {
ioLock.unlock()
}
}
/*
* ------------------------------------------------------------------
* DocOps execution (in-process; never shells out through DocOpsCli)
* ------------------------------------------------------------------
*/
private fun planDocOpsQuietly(cfg: Config, paths: List<String>): WorkPlan<PlatformTaskKind> {
DocOpsSupport.installFileApplicationServices()
val user = DocOpsSupport.defaultUser()
DocOpsSupport.bootstrapPlatform(user)
val smartModelId = cfg.smartModel ?: throw IllegalStateException("no smart model configured")
val models = DocOpsSupport.resolveModels(smartModelId = smartModelId, fastModelId = cfg.fastModel, user = user)
val docProcessor = DocOpsSupport.buildDocProcessor(
root = cfg.root,
docsFolder = cfg.root,
updateMode = UpdateModes.PatchToUpdate,
models = models,
serverless = true,
autoFix = true,
user = user,
)
val docFiles = resolveDocOpsPaths(cfg.root, paths)
return if (docFiles.isEmpty()) docProcessor.getAll() else docProcessor.getAll(*docFiles.toTypedArray())
}
private fun resolveDocOpsPaths(root: File, paths: List<String>): List<File> = paths.flatMap { path ->
val file = File(path).let { if (it.isAbsolute) it else root.resolve(path) }.canonicalFile
when {
!file.exists() -> emptyList()
file.isDirectory -> file.walkTopDown()
.filter { it.isFile && it.extension.lowercase() in setOf("md", "markdown") }
.toList()
else -> listOf(file)
}
}
/** Plans, and for `run` executes, in-process against [DocProcessor] - never through the CLI's stdout. */
private fun runDocOps(
cfg: Config,
command: String,
paths: List<String>,
mode: String?,
targets: List<String>,
vars: Map<String, String>,
): Int {
val root = cfg.root
if (command == "status") {
printDocOpsStatus(JsonFileDocStatusStore(root).read())
return 0
}
DocOpsSupport.installFileApplicationServices()
if (command == "vars") {
val files = if (paths.isNotEmpty()) resolveDocOpsPaths(root, paths) else DocOpsSupport.markdownFiles(root)
val declared = DocProcessor.listTemplateVarKeys(files)
if (declared.isEmpty()) {
println("No template variables declared in ${files.size} document(s).")
} else {
println("Template variables (${declared.size}):")
declared.toSortedMap().forEach { (k, v) -> println("  $k = ${v.ifBlank { "<no default>" }}") }
}
return 0
}
val user = DocOpsSupport.defaultUser()
DocOpsSupport.bootstrapPlatform(user)
if (command == "models") {
println(DocOpsSupport.describeModels(DocOpsSupport.availableModels(user)))
return 0
}
val smartModelId = cfg.smartModel
if (smartModelId == null) {
println("docops: no smart model configured (set COGNOTIK_SMART_MODEL)")
return 1
}
val models = DocOpsSupport.resolveModels(
smartModelId = smartModelId,
fastModelId = cfg.fastModel,
user = user,
)
val updateMode = mode?.let {
UpdateModes.fromName(it) ?: run {
println("docops: unknown mode '$it'")
return 2
}
} ?: UpdateModes.PatchToUpdate
val docProcessor = DocOpsSupport.buildDocProcessor(
root = root,
docsFolder = root,
updateMode = updateMode,
models = models,
serverless = true,
autoFix = true,
user = user,
templateVarOverrides = vars,
)
val docFiles = resolveDocOpsPaths(root, paths)
val rawPlan = if (paths.isEmpty()) docProcessor.getAll() else docProcessor.getAll(*docFiles.toTypedArray())
val plan = DocOpsSupport.applyTargetFilter(rawPlan, root, targets)
println("Plan: ${plan.tasks.size} task(s) in ${plan.queues.size} queue(s) [mode=$updateMode]")
plan.tasks.forEach { planned ->
val target = planned.target.relativeToOrAbsolute(root)
val verb = if (planned.target.file.exists()) "update" else "create"
println("  $verb $target  [${planned.task.taskType.name}]")
}
if (plan.skipped.isNotEmpty()) {
println("Skipped (${plan.skipped.size}):")
plan.skipped.forEach { println("  ${it.target.relativeToOrAbsolute(root)}: ${it.reason}") }
}
if (plan.failed.isNotEmpty()) {
println("Failed to plan (${plan.failed.size}):")
plan.failed.forEach { println("  ${it.target.relativeToOrAbsolute(root)}: ${it.error}") }
}
if (command == "plan") return if (plan.failed.isNotEmpty()) 1 else 0
if (plan.isEmpty) {
println("Nothing to do.")
return 0
}
docProcessor.docOps.initializeStatus(plan)
val cancelFlag = AtomicBoolean(false)
val pool = FixedConcurrencyProcessor(
Executors.newCachedThreadPool { r -> Thread(r, "docops-fsaction").apply { isDaemon = true } },
4,
)
println("Executing ${plan.tasks.size} task(s)...")
val sessions = docProcessor.runAll(plan = plan, pool = pool, cancelFlag = cancelFlag) { session ->
println("  session $session")
}
println("Finished: ${sessions.size} session(s).")
val status = docProcessor.docOps.statusStore.read()
printDocOpsStatus(status)
val failures = status.tasks.values.count { it.status == TaskStatus.FAILED }
return if (failures > 0) 1 else 0
}
private fun printDocOpsStatus(status: DocOpsStatus) {
if (status.tasks.isEmpty()) {
println("No docops status recorded.")
return
}
println("Status (updated ${status.lastUpdated}):")
status.tasks.values.sortedBy { it.target }.forEach { entry ->
val session = entry.sessionId?.let { " session=$it" } ?: ""
val error = entry.error?.let { " error=${it.lines().first()}" } ?: ""
println("  ${entry.status.name.padEnd(9)} ${entry.target}$session$error")
}
}