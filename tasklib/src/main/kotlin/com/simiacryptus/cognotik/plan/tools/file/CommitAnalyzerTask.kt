package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.PriorityQueue
import java.util.concurrent.TimeUnit

/**
 * Commit Analyzer: explodes a single (large / messy) commit into an ordered DAG of
 * atomic, purpose-specific operations. See idea.md in this package for the design.
 *
 * Hard invariant: applying all operations to the parent reproduces the commit's file
 * contents byte-for-byte. This is guaranteed by construction: change items are
 * zero-context hunks applied in memory, with a whole-file fallback for anything that
 * cannot be reproduced exactly.
 */
class CommitAnalyzerTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: CommitAnalyzerTaskExecutionConfigData?
) : AbstractTask<CommitAnalyzerTask.CommitAnalyzerTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class CommitAnalyzerTaskExecutionConfigData(
    @Description("Path to the git repository (or any directory inside it), relative to the workspace root")
    var repository_path: String? = ".",
   @Description("The commit to explode (any git revision, default HEAD; its first parent is used as the base), or a commit range 'A..B' (tree diff A -> B) / 'A...B' (merge-base(A,B) -> B) whose combined change is exploded")
    var commit: String? = "HEAD",
   @Description("Optional base revision; when set, the combined change from base_commit to commit is exploded (equivalent to commit='base..commit')")
   var base_commit: String? = null,
    @Description("Shell command used to verify that intermediate states build (e.g. './gradlew compileKotlin -q')")
    var build_command: String? = null,
    @Description("Whether to run the build after each operation and repair the graph on failure (requires build_command)")
    var verify_build: Boolean = false,
    @Description("Timeout for a single build run, in seconds")
    var build_timeout_seconds: Int = 600,
    @Description("Maximum number of graph repair attempts during build verification")
    var max_verification_retries: Int = 10,
    @Description("Name of the branch holding the commit DAG: one commit per atomic change item, merge commits joining independent operations (default: commit-analysis/<short sha>)")
    var create_branch: String? = null,
    @Description("Whether to emit one patch file per operation")
    var emit_patches: Boolean = true,
    @Description("Whether to emit a customized, self-contained interactive HTML viewer for the analysis")
    var emit_viewer: Boolean = true,
    @Description("Whether to use LLMs for cluster refinement, naming, semantic ordering and build failure diagnosis")
    var use_llm: Boolean = true,
    @Description("Where mechanical operations (whitespace, reordering, bulk replace) are placed: 'first' or 'last'")
    var mechanical_placement: String = "first",
    @Description("Similarity threshold for clustering change items (lower = coarser operations)")
    var similarity_threshold: Double = 0.5,
    @Description("Minimum number of identical substitutions before they are collapsed into a bulk-replace operation")
    var bulk_replace_min_occurrences: Int = 3,
    @Description("Maximum number of change items in a single deterministic cluster before it is split further")
    var max_cluster_size: Int = 40,
    @Description("Maximum number of operations for which LLM dependency reasoning is performed")
    var max_llm_operations: Int = 200,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = CommitAnalyzer.name,
   task_description = task_description ?: (
       if (!base_commit.isNullOrBlank()) "Explode commit range '$base_commit..${commit ?: "HEAD"}' into an ordered change graph"
       else if ((commit ?: "").contains("..")) "Explode commit range '$commit' into an ordered change graph"
       else "Explode commit '${commit ?: "HEAD"}' into an ordered change graph"
       ),
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (commit.isNullOrBlank()) commit = "HEAD"
     commit = commit?.trim()
     base_commit = base_commit?.trim()?.ifBlank { null }
     if (base_commit != null && commit!!.contains("..")) {
       return "commit must be a single revision when base_commit is set, got range: $commit"
     }
     if (commit!!.split("...").size > 2 || (!commit!!.contains("...") && commit!!.split("..").size > 2)) {
       return "commit is not a valid revision or range: $commit"
     }
      if (repository_path.isNullOrBlank()) repository_path = "."
      mechanical_placement = mechanical_placement.trim().lowercase()
      if (mechanical_placement !in setOf("first", "last")) {
        return "mechanical_placement must be 'first' or 'last', got: $mechanical_placement"
      }
      if (verify_build && build_command.isNullOrBlank()) {
        return "verify_build requires a build_command"
      }
      if (similarity_threshold <= 0.0 || similarity_threshold > 2.0) {
        return "similarity_threshold must be in (0, 2], got: $similarity_threshold"
      }
      bulk_replace_min_occurrences = bulk_replace_min_occurrences.coerceIn(2, 1000)
      max_cluster_size = max_cluster_size.coerceIn(2, 1000)
      build_timeout_seconds = build_timeout_seconds.coerceIn(10, 86400)
      max_verification_retries = max_verification_retries.coerceIn(0, 100)
      max_llm_operations = max_llm_operations.coerceIn(0, 2000)
      create_branch = create_branch?.trim()?.ifBlank { null }
      if (create_branch?.contains(Regex("""[\s~^:?*\[\\]|\.\.""")) == true) {
        return "create_branch is not a valid git branch name: $create_branch"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  // ---------------------------------------------------------------------------
  // LLM result types
  // ---------------------------------------------------------------------------

  data class PlannedOperation(
    @Description("Short imperative name for the operation, suitable as a commit subject (max ~70 chars)")
    var name: String = "",
    @Description("1-3 sentence description of the intent of this operation")
    var description: String = "",
    @Description("One of: refactor, feature, fix, test, docs, config, build, cleanup, formatting, generated")
    var category: String? = null,
    @Description("IDs of the change items (e.g. i12) that belong to this operation")
    var item_ids: List<String> = emptyList(),
    @Description("True if this operation looks unrelated to the rest of the commit or hides a behavioral change inside a refactor/formatting change")
    var suspicious: Boolean = false,
    @Description("Why the operation is suspicious, if it is")
    var suspicion_reason: String? = null
  )

  data class OperationPlan(
    @Description("The operations; every change item id must appear in exactly one operation")
    var operations: List<PlannedOperation> = emptyList()
  )

  data class PlannedEdge(
    @Description("Operation id that must be applied first (e.g. op3)")
    var before: String = "",
    @Description("Operation id that must be applied after 'before'")
    var after: String = "",
    @Description("Short explanation of why this dependency exists")
    var reason: String = ""
  )

  data class DependencyPlan(
    @Description("Semantic dependencies not visible in the syntax")
    var edges: List<PlannedEdge> = emptyList(),
    @Description("A human-friendly order of ALL operation ids (refactor, then feature, then tests, then cleanup)")
    var suggested_order: List<String> = emptyList()
  )

  data class BuildDiagnosis(
    @Description("Ids of later operations that must be applied before the failing operation for the build to succeed")
    var required_operation_ids: List<String> = emptyList(),
    @Description("Short explanation of the failure")
    var explanation: String = ""
  )

  // ---------------------------------------------------------------------------
  // Model
  // ---------------------------------------------------------------------------

  enum class ItemKind(val mechanical: Boolean) {
    EDIT(false),
    WHITESPACE(true),
    REORDER(true),
    BULK_REPLACE(true),
    MOVE(true),
    ADD_FILE(false),
    DELETE_FILE(false),
    REPLACE_FILE(false)
  }

  /** A zero-context hunk. [start] is the 0-based index in the old file where [oldCount] lines are replaced. */
  class Hunk(
    val start: Int,
    val oldCount: Int,
    val newStart: Int,
    val oldLines: List<String>,
    val newLines: List<String>
  )

  class FileChange(
    val index: Int,
    val status: Char,
    val oldPath: String?,
    val newPath: String?,
    val oldContent: String?,
    val newContent: String?
  ) {
    val displayPath: String get() = newPath ?: oldPath ?: "?"
    val hunks = mutableListOf<Hunk>()
    val hunkItems = mutableListOf<ChangeItem>()
    var moveItem: ChangeItem? = null
    var wholeFileItem: ChangeItem? = null
    val generated: Boolean by lazy { isGeneratedPath(displayPath) }
  }

  class ChangeItem(
    val id: String,
    var kind: ItemKind,
    val file: FileChange,
    val hunk: Hunk?,
    val removedText: String,
    val addedText: String
  ) {
    var substitution: Pair<String, String>? = null
    val line: Int get() = hunk?.start ?: 0
    val addedTokens: Set<String> by lazy { tokens(addedText) }
    val removedTokens: Set<String> by lazy { tokens(removedText) }
    val definedAdded: Set<String> by lazy { definitions(addedText) }
    val definedRemoved: Set<String> by lazy { definitions(removedText) }
    val newRefs: Set<String> by lazy { (addedTokens - removedTokens).filter { isSignificant(it) }.toSet() }
    val droppedRefs: Set<String> by lazy { (removedTokens - addedTokens).filter { isSignificant(it) }.toSet() }
    val signature: Set<String> by lazy {
      val diff = newRefs + droppedRefs
      if (diff.isNotEmpty()) diff else (addedTokens + removedTokens).filter { isSignificant(it) }.toSet()
    }
    val fingerprint: String by lazy { sha1(file.displayPath + "\u0000" + removedText + "\u0000" + addedText) }
  }

  class Operation(
    var id: String,
    var name: String,
    var description: String,
    var category: String,
    val items: MutableList<ChangeItem>,
    var rule: String? = null
  ) {
    var suspicious: Boolean = false
    var suspicionReason: String? = null
    var confidence: Double = 0.6
    var verified: Boolean? = null
    val mechanical: Boolean get() = items.isNotEmpty() && items.all { it.kind.mechanical }
    fun definedAdded(): Set<String> = items.flatMap { it.definedAdded }.toSet()
    fun definedRemoved(): Set<String> = items.flatMap { it.definedRemoved }.toSet()
    fun refsAdded(): Set<String> = items.flatMap { it.newRefs }.toSet()
    fun refsRemoved(): Set<String> = items.flatMap { it.droppedRefs }.toSet()
    fun files(): List<String> = items.map { it.file.displayPath }.distinct()
    fun position(): Long = items.minOfOrNull { it.file.index.toLong() * 10_000_000L + it.line } ?: Long.MAX_VALUE
  }

  class Edge(var before: String, var after: String, val kind: String, val reason: String)

  class ChangeGraph(
    val ops: MutableList<Operation> = mutableListOf(),
    val edges: MutableList<Edge> = mutableListOf()
  ) {
    fun op(id: String): Operation? = ops.firstOrNull { it.id == id }

    private fun successors(): Map<String, Set<String>> =
      edges.groupBy { it.before }.mapValues { e -> e.value.map { it.after }.toSet() }

    fun reachable(from: String, to: String): Boolean {
      if (from == to) return true
      val succ = successors()
      val seen = HashSet<String>()
      val queue = ArrayDeque<String>()
      queue.addLast(from)
      while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        if (!seen.add(cur)) continue
        for (n in succ[cur].orEmpty()) {
          if (n == to) return true
          queue.addLast(n)
        }
      }
      return false
    }

    fun addEdge(before: String, after: String, kind: String, reason: String, allowCycle: Boolean = true): Boolean {
      if (before == after) return false
      if (op(before) == null || op(after) == null) return false
      if (edges.any { it.before == before && it.after == after }) return false
      if (!allowCycle && reachable(after, before)) return false
      edges.add(Edge(before, after, kind, reason))
      return true
    }

    fun merge(ids: List<String>, note: String): Operation {
      val target = op(ids.first()) ?: throw IllegalStateException("Unknown operation ${ids.first()}")
      ids.drop(1).distinct().filter { it != target.id }.mapNotNull { op(it) }.forEach { other ->
        target.items.addAll(other.items)
        target.name = "${target.name} + ${other.name}".take(160)
        target.suspicious = target.suspicious || other.suspicious
        if (target.suspicionReason == null) target.suspicionReason = other.suspicionReason
        target.confidence = minOf(target.confidence, other.confidence)
        if (target.category != other.category) target.category = "mixed"
        target.rule = null
        ops.remove(other)
        edges.forEach { e ->
          if (e.before == other.id) e.before = target.id
          if (e.after == other.id) e.after = target.id
        }
      }
      edges.removeIf { it.before == it.after }
      val seen = HashSet<Pair<String, String>>()
      edges.removeIf { !seen.add(it.before to it.after) }
      if (note.isNotBlank()) target.description = (target.description + "\n\n" + note).trim()
      return target
    }

    /** Merges every strongly connected component (dependency cycle) into one operation. */
    fun mergeCycles(): List<List<String>> {
      val merged = mutableListOf<List<String>>()
      while (true) {
        val sccs = stronglyConnected().filter { it.size > 1 }
        if (sccs.isEmpty()) return merged
        sccs.forEach { comp ->
          val sorted = comp.sortedBy { id -> ops.indexOfFirst { it.id == id } }
          val names = sorted.mapNotNull { op(it)?.name }
          merge(sorted, "(Merged because of a dependency cycle between: ${names.joinToString("; ")})")
          merged.add(sorted)
        }
      }
    }

    private fun stronglyConnected(): List<List<String>> {
      val succ = successors()
      var index = 0
      val idx = HashMap<String, Int>()
      val low = HashMap<String, Int>()
      val onStack = HashSet<String>()
      val stack = ArrayDeque<String>()
      val result = mutableListOf<List<String>>()
      fun strong(v: String) {
        idx[v] = index
        low[v] = index
        index++
        stack.addLast(v)
        onStack.add(v)
        for (w in succ[v].orEmpty()) {
          if (w !in idx) {
            strong(w)
            low[v] = minOf(low[v]!!, low[w]!!)
          } else if (w in onStack) {
            low[v] = minOf(low[v]!!, idx[w]!!)
          }
        }
        if (low[v] == idx[v]) {
          val comp = mutableListOf<String>()
          while (true) {
            val w = stack.removeLast()
            onStack.remove(w)
            comp.add(w)
            if (w == v) break
          }
          result.add(comp)
        }
      }
      ops.map { it.id }.forEach { if (it !in idx) strong(it) }
      return result
    }

    fun topologicalOrder(comparator: Comparator<Operation>): List<Operation> {
      val indeg = HashMap<String, Int>()
      ops.forEach { indeg[it.id] = 0 }
      edges.forEach { indeg[it.after] = (indeg[it.after] ?: 0) + 1 }
      val succ = successors()
      val queue = PriorityQueue(comparator)
      ops.filter { indeg[it.id] == 0 }.forEach { queue.add(it) }
      val out = mutableListOf<Operation>()
      while (queue.isNotEmpty()) {
        val o = queue.poll()
        out.add(o)
        succ[o.id].orEmpty().forEach { s ->
          val d = (indeg[s] ?: 1) - 1
          indeg[s] = d
          if (d == 0) op(s)?.let { queue.add(it) }
        }
      }
      if (out.size != ops.size) throw IllegalStateException("Change graph contains a cycle")
      return out
    }
  }

  private class ParsedCommit(val files: List<FileChange>, val items: List<ChangeItem>, val notes: List<String>)
/**
  * The resolved analysis target: the change from [base] to [tip]. For a single commit, [base] is
  * its first parent; for a range, [commits] lists the commits in base..tip (oldest first).
  */
private class ResolvedTarget(
   val base: String,
   val tip: String,
   val isRange: Boolean,
   val symmetric: Boolean,
   val baseIsAncestor: Boolean,
   val commits: List<Pair<String, String>>
)


  private class VerificationReport(
    var status: String = "skipped",
    val steps: MutableList<String> = mutableListOf(),
    val fixes: MutableList<String> = mutableListOf()
  )

  private class SeriesResult(
    val patches: MutableList<Pair<String, String>> = mutableListOf(),
    var headSha: String? = null,
    var treeMatches: Boolean? = null,
    var treeDiff: String = "",
    var branchCreated: String? = null,
    var branchError: String? = null
  ) {
    val itemCommits = mutableListOf<ItemCommit>()
    val itemCommitById = HashMap<String, ItemCommit>()
    val operationCommits = LinkedHashMap<String, String>()

    /** Commit each operation was built on (the parent, a single prerequisite's head, or a merge commit). */
    val operationBases = LinkedHashMap<String, String>()

    /** Prerequisite operations (transitively reduced) whose heads were merged to form an operation's base. */
    val operationParents = LinkedHashMap<String, List<String>>()

    /** Merge commits created to join independent branches of the DAG (including the final head merge). */
    val mergeCommits = mutableListOf<String>()
  }

  /** One atomic commit of the emitted series: a single change item applied on top of its predecessor. */
  private class ItemCommit(
    val item: ChangeItem,
    val commit: String,
    val patch: String
  )

  // ---------------------------------------------------------------------------
  // Git / shell helpers
  // ---------------------------------------------------------------------------

  class GitException(message: String) : RuntimeException(message)

  class Git(val dir: File, private val env: Map<String, String> = emptyMap()) {
    fun exec(args: List<String>, allowFail: Boolean = false, timeoutSeconds: Long = 600): Pair<Int, ByteArray> {
      val t0 = System.currentTimeMillis()
      log.debug("git exec in {}: git {}", dir, args.joinToString(" ").take(500))
      val pb = ProcessBuilder(listOf("git", "-c", "core.quotePath=false") + args).directory(dir)
      pb.environment().putAll(env)
      pb.environment()["GIT_TERMINAL_PROMPT"] = "0"
      val process = pb.start()
      process.outputStream.close()
      val stderr = StringBuilder()
      val errThread = Thread {
        try {
          stderr.append(process.errorStream.bufferedReader().readText())
        } catch (_: Exception) {
        }
      }.apply { isDaemon = true; start() }
      val out = process.inputStream.readBytes()
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        log.error("git {} timed out after {}s", args.joinToString(" ").take(300), timeoutSeconds)
        throw GitException("git ${args.joinToString(" ")} timed out")
      }
      errThread.join(5000)
      val code = process.exitValue()
      log.debug(
        "git {} -> exit={} stdout={}B stderr={}B in {}ms",
        args.firstOrNull(), code, out.size, stderr.length, System.currentTimeMillis() - t0
      )
      if (code != 0 && !allowFail) {
        log.warn("git {} failed ({}): {}", args.joinToString(" ").take(300), code, stderr.toString().take(2000))
        throw GitException("git ${args.joinToString(" ").take(300)} failed ($code): ${stderr.toString().take(2000)}")
      }
      if (code != 0) log.debug(
        "git {} returned {} (allowed): {}",
        args.joinToString(" ").take(300),
        code,
        stderr.toString().take(500)
      )
      return code to out
    }

    fun bytes(vararg args: String): ByteArray = exec(args.toList()).second
    fun text(vararg args: String): String = String(bytes(*args), StandardCharsets.UTF_8).trim()
    fun ok(vararg args: String): Boolean = exec(args.toList(), allowFail = true).first == 0
  }

  private fun runShell(command: String, dir: File, timeoutSeconds: Long): Pair<Boolean, String> {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val cmd = if (isWindows) listOf("cmd", "/c", command) else listOf("sh", "-c", command)
    val outFile = Files.createTempFile("commit-analyzer-build", ".log").toFile()
    try {
      val t0 = System.currentTimeMillis()
      log.info("Running build command in {}: {} (timeout {}s)", dir, command, timeoutSeconds)
      val p = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).redirectOutput(outFile).start()
      p.outputStream.close()
      if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        log.warn("Build command timed out after {}s: {}", timeoutSeconds, command)
        return false to ("TIMEOUT after ${timeoutSeconds}s\n" + outFile.readText().takeLast(6000))
      }
      val code = p.exitValue()
      val output = outFile.readText().takeLast(6000)
      log.info("Build command finished with exit={} in {}ms", code, System.currentTimeMillis() - t0)
      if (code != 0) log.debug("Build output tail:\n{}", output.takeLast(2000))
      return (code == 0) to output
    } finally {
      outFile.delete()
    }
  }

  /** Materializes intermediate trees into a worktree for a given set of applied change items. */
  private class Replayer(val worktree: File, val files: List<FileChange>) {
    private val filesByPath = HashMap<String, MutableList<FileChange>>()
    private val last = HashMap<String, String?>()

    init {
      files.forEach { f ->
        listOfNotNull(f.oldPath, f.newPath).distinct().forEach { filesByPath.getOrPut(it) { mutableListOf() }.add(f) }
      }
      filesByPath.keys.forEach { last[it] = desiredFor(it, emptySet()) }
    }

    private fun desiredFor(path: String, applied: Set<ChangeItem>): String? =
      filesByPath[path].orEmpty().firstNotNullOfOrNull { f ->
        fileState(f, applied)?.takeIf { it.first == path }?.second
      }

    /**
     * Re-synchronizes the bookkeeping after the worktree was checked out at a commit whose tree
     * corresponds to [applied]; nothing is written to disk.
     */
    fun reset(applied: Set<ChangeItem>) {
      filesByPath.keys.forEach { last[it] = desiredFor(it, applied) }
    }


    fun sync(applied: Set<ChangeItem>, touched: Collection<FileChange> = files): List<String> {
      val paths = touched.flatMap { listOfNotNull(it.oldPath, it.newPath) }.toSet()
      val changed = mutableListOf<String>()
      for (p in paths) {
        val d = desiredFor(p, applied)
        if (d != last[p]) {
          val f = File(worktree, p)
          if (d == null) {
            f.delete()
            log.debug("Replayer: deleted {}", p)
          } else {
            f.parentFile?.mkdirs()
            f.writeBytes(d.toByteArray(ISO))
            log.debug("Replayer: wrote {} ({} bytes)", p, d.length)
          }
          last[p] = d
          changed.add(p)
        }
      }
      log.debug(
        "Replayer sync: {} applied item(s), {} candidate path(s), {} changed",
        applied.size,
        paths.size,
        changed.size
      )
      return changed
    }
  }

  // ---------------------------------------------------------------------------
  // Task entry point
  // ---------------------------------------------------------------------------

  override fun promptSegment(): String {
    return """
  CommitAnalyzer - Explode a single large or messy git commit (or the combined change of a commit range) into an ordered DAG of atomic operations
  ** Specify the repository path (relative to workspace) and the commit (default HEAD), or a range via commit='A..B' / 'A...B' or base_commit + commit
  ** Mechanical changes (whitespace, line reordering, bulk replacements, file moves) are normalized into dedicated operations
  ** Remaining hunks are clustered by token similarity and symbol relationships, then refined and named by an LLM
  ** Dependencies are derived (definition-before-use, removal-after-last-use, LLM semantic edges); cycles are merged
  ** Outputs: change graph JSON (with per-item diffs and commit ids), review report, per-item and per-operation patches, and a branch whose commit DAG mirrors the dependency graph (one commit per atomic change item, merge commits joining independent operations)
  ** Optionally verifies that every intermediate state builds (build_command + verify_build) and repairs the graph
   ** The final state always reproduces the original commit's (or range tip's) file contents exactly
  ** Useful for history repolishing, PR review assistance, regression hunting and code archaeology
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val override = executionConfigOverride
    val received = executionConfig
    if (override != null && received != null &&
      (override.repository_path != received.repository_path || override.commit != received.commit)
    ) {
      log.warn(
        "Execution config received by task (repo={}, commit={}) differs from the explicit override " +
            "(repo={}, commit={}); using the override. The harness did not propagate the execution config.",
        received.repository_path, received.commit, override.repository_path, override.commit
      )
    }
    val cfg = override ?: received
    if (cfg == null) {
      val msg = "CONFIGURATION ERROR: Execution configuration is null"
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }
    cfg.validate()?.let { error ->
      val msg = "CONFIGURATION ERROR: $error"
      log.error(msg)
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }
    log.info(
       "CommitAnalyzer starting: repo={}, commit={}, base_commit={}, use_llm={}, verify_build={}, build_command={}, emit_patches={}, " +
          "create_branch={}, mechanical_placement={}, similarity_threshold={}, bulk_min={}, max_cluster_size={}, max_llm_ops={}",
     cfg.repository_path, cfg.commit, cfg.base_commit, cfg.use_llm, cfg.verify_build, cfg.build_command, cfg.emit_patches,
      cfg.create_branch, cfg.mechanical_placement, cfg.similarity_threshold, cfg.bulk_replace_min_occurrences,
      cfg.max_cluster_size, cfg.max_llm_operations
    )
    val startTime = System.currentTimeMillis()
    val tabs = TabbedDisplay(task)
    val overview = tabs.newTask("Overview")
    try {
      val summary = analyze(cfg, agent, task, tabs, overview)
      overview.complete()
      log.info("CommitAnalyzer completed in {}ms", System.currentTimeMillis() - startTime)
      task.safeComplete("Commit analysis completed in ${(System.currentTimeMillis() - startTime) / 1000}s", log)
      resultFn(summary)
    } catch (e: Exception) {
      log.error("Commit analysis failed after {}ms", System.currentTimeMillis() - startTime, e)
      try {
        overview.error(e)
      } catch (_: Exception) {
      }
      val msg = "ERROR: Commit analysis failed - ${e.message}"
      task.safeComplete(msg, log)
      resultFn(msg)
    }
  }

  private fun analyze(
    cfg: CommitAnalyzerTaskExecutionConfigData,
    agent: TaskOrchestrator,
    task: ISessionTask,
    tabs: TabbedDisplay,
    overview: ISessionTask
  ): String {
    fun status(msg: String) {
      log.info("[status] {}", msg.lineSequence().firstOrNull { it.isNotBlank() }?.take(300) ?: "")
      overview.add(md(msg))
      task.update()
    }

    // ---------------- Stage 0: preconditions ----------------
    val startDir = root.resolve(cfg.repository_path ?: ".").toFile().canonicalFile
    require(startDir.isDirectory) { "Repository path does not exist: $startDir" }
    val probe = Git(startDir)
    val top = File(probe.text("rev-parse", "--show-toplevel")).canonicalFile
    val prefix = probe.text("rev-parse", "--show-prefix")
    val git = Git(top)
     val target = resolveTarget(git, cfg.commit ?: "HEAD", cfg.base_commit)
     val commit = target.tip
     val parent = target.base
     val isRange = target.isRange
     // Used in file names, trailers and the default branch name: must not contain "..".
     val shortSha = if (isRange) "${parent.take(8)}-${commit.take(8)}" else commit.take(10)
     val message = if (isRange) rangeMessage(git, target) else git.text("log", "-1", "--format=%B", commit)
     val subject = if (isRange) {
       "Combined changes of ${target.commits.size} commit(s) ${parent.take(10)}..${commit.take(10)}"
     } else {
       message.lineSequence().firstOrNull() ?: ""
     }
    val authorInfo = git.text("log", "-1", "--format=%an%x00%ae%x00%aI", commit).split('\u0000')
     val isMerge = !isRange && git.ok("rev-parse", "--verify", "--quiet", "$commit^2")
    log.info(
       "Resolved repository top={} prefix='{}' commit={} parent={} range={} rangeCommits={} merge={} subject='{}'",
       top, prefix, commit, parent, isRange, target.commits.size, isMerge, subject.take(200)
    )

    overview.header("Commit Analyzer: $shortSha", level = 2)
     if (isRange) {
       status(buildString {
         appendLine("**Repository:** `${top.path}`")
         appendLine()
         appendLine(
           "**Range:** `$parent..$commit` — ${target.commits.size} commit(s)" +
               if (target.symmetric) " (base is the merge-base)" else ""
         )
         appendLine()
         target.commits.take(30).forEach { (sha, subj) -> appendLine("- `${sha.take(10)}` $subj") }
         if (target.commits.size > 30) appendLine("- _...and ${target.commits.size - 30} more_")
         if (!target.baseIsAncestor) {
           appendLine()
           appendLine("⚠️ The base is not an ancestor of the tip; the analysis covers the plain tree diff base → tip.")
         }
       })
     } else {
       status(
         """
         |**Repository:** `${top.path}`
         |
         |**Commit:** `$commit` — ${subject.ifBlank { "(no subject)" }}
         |
         |**Parent:** `$parent`${if (isMerge) " (merge commit: analyzing against first parent)" else ""}
         """.trimMargin()
       )
     }

    // ---------------- Stage 1: parse ----------------
    status("🔄 Stage 1: parsing the commit into change items...")
    val stage1Start = System.currentTimeMillis()
    val parsed = parseCommit(git, parent, commit)
    val files = parsed.files
    val items = parsed.items
    log.info(
      "Stage 1: parsed {} file(s) into {} item(s), {} note(s) in {}ms",
      files.size, items.size, parsed.notes.size, System.currentTimeMillis() - stage1Start
    )
    if (items.isEmpty()) {
      val msg = "Commit $shortSha contains no content changes to analyze."
      status(msg)
      return msg
    }
    classify(items, cfg.bulk_replace_min_occurrences)
    val kindCounts = items.groupingBy { it.kind }.eachCount()
    log.info("Stage 1: item kinds after classification: {}", kindCounts)
    status(
      "✅ Parsed ${files.size} file(s) into ${items.size} change item(s): " +
          kindCounts.entries.joinToString(", ") { "${it.key}=${it.value}" })

    val itemsTab = tabs.newTask("Change Items")
    itemsTab.add(md(buildString {
      appendLine("## Change Items (${items.size})")
      appendLine()
      if (parsed.notes.isNotEmpty()) {
        appendLine("### Notes")
        parsed.notes.forEach { appendLine("- ${disp(it)}") }
        appendLine()
      }
      appendLine("| id | kind | file | line | -/+ |")
      appendLine("|---|---|---|---|---|")
      items.take(500).forEach { item ->
        val h = item.hunk
        appendLine("| ${item.id} | ${item.kind} | `${item.file.displayPath}` | ${h?.let { it.start + 1 } ?: "-"} | ${h?.let { "-${it.oldLines.size}/+${it.newLines.size}" } ?: "-"} |")
      }
      if (items.size > 500) appendLine("\n_...and ${items.size - 500} more_")
    }))
    itemsTab.complete()

    // ---------------- Stage 2: cluster ----------------
    status("🔄 Stage 2: clustering related change items...")
    val mechanicalOps = mechanicalOperations(items)
    val generatedItems = items.filter { !it.kind.mechanical && it.file.generated }
    val generatedOps = if (generatedItems.isEmpty()) emptyList() else listOf(
      Operation(
        "", "Update generated, lock and vendored files",
        "Opaque changes to generated/lock/vendored files (${
          generatedItems.map { it.file.displayPath }.distinct().size
        } file(s)), kept as a single unit.",
        "generated", generatedItems.toMutableList()
      ).apply { confidence = 0.9 }
    )
    val semanticItems = items.filter { !it.kind.mechanical && !it.file.generated }
    val clusters = cluster(semanticItems, cfg.similarity_threshold, cfg.max_cluster_size)
      .sortedBy { c -> c.minOf { it.file.index.toLong() * 10_000_000L + it.line } }
    log.info(
      "Stage 2: {} mechanical op(s), {} generated item(s), {} semantic item(s) -> {} cluster(s) (sizes: {})",
      mechanicalOps.size, generatedItems.size, semanticItems.size, clusters.size,
      clusters.map { it.size }.sortedDescending().take(20)
    )

    val priorContext = try {
      getPriorCode(agent.executionState).take(4000)
    } catch (e: Exception) {
      log.debug("No prior context available: {}", e.message)
      ""
    }
    val commitContext = buildString {
       if (isRange) {
         appendLine("These changes are the combined diff of a range of ${target.commits.size} commit(s).")
         appendLine("Commits in range (oldest first):")
         appendLine(message.take(4000))
       } else {
         appendLine("Commit message:")
         appendLine(message.take(2000))
       }
      if (priorContext.isNotBlank()) {
        appendLine()
        appendLine("Context from previous tasks:")
        appendLine(priorContext)
      }
    }

    val opsTab = tabs.newTask("Operations")
    val semanticOps = if (cfg.use_llm && clusters.isNotEmpty()) {
      opsTab.add(md("🔄 Refining ${clusters.size} deterministic cluster(s) with LLM..."))
      task.update()
      refineClustersWithLlm(clusters, commitContext, opsTab)
    } else {
      clusters.map { deterministicOp(it) }
    }

    val graph = ChangeGraph()
    graph.ops.addAll(mechanicalOps)
    graph.ops.addAll(semanticOps.sortedBy { it.position() })
    graph.ops.addAll(generatedOps)
    graph.ops.forEachIndexed { i, op -> op.id = "op${i + 1}" }
    graph.ops.forEach { op ->
      log.debug("Operation {} [{}] '{}' items={} files={}", op.id, op.category, op.name, op.items.size, op.files().size)
    }
    status("✅ Grouped change items into ${graph.ops.size} operation(s) (${mechanicalOps.size} mechanical)")

    // ---------------- Stage 3: dependencies ----------------
    status("🔄 Stage 3: analyzing order and dependencies...")
    addDeterministicEdges(graph)
    log.info("Stage 3: {} deterministic edge(s)", graph.edges.size)
    val cycleMerges = graph.mergeCycles()
    if (cycleMerges.isNotEmpty()) log.info("Stage 3: merged {} cycle(s): {}", cycleMerges.size, cycleMerges)
    var llmOrder: Map<String, Int> = emptyMap()
    val depsTab = tabs.newTask("Dependencies")
    if (cfg.use_llm && graph.ops.size in 2..cfg.max_llm_operations) {
      depsTab.add(md("🔄 Asking LLM for semantic dependencies and a readable order..."))
      task.update()
      llmOrder = llmDependencies(graph, commitContext, depsTab)
    } else if (cfg.use_llm) {
      log.info("Stage 3: skipping LLM dependency analysis ({} ops, limit {})", graph.ops.size, cfg.max_llm_operations)
    }
    val comparator = operationComparator(cfg.mechanical_placement) { llmOrder[it] }
    var order = graph.topologicalOrder(comparator)
    log.info("Stage 3: topological order: {}", order.joinToString(", ") { it.id })
    status(
      "✅ Built change graph: ${graph.ops.size} operation(s), ${graph.edges.size} edge(s)" +
          if (cycleMerges.isNotEmpty()) ", ${cycleMerges.size} cycle merge(s)" else ""
    )

    // ---------------- Stage 4: verify & emit series ----------------
    var verification = VerificationReport()
    val series = SeriesResult()
    // Every change item becomes a real commit, so the scratch worktree is always needed.
    run {
      val tmpRoot = Files.createTempDirectory("commit-analyzer").toFile()
      val wtDir = File(tmpRoot, "wt")
      try {
        log.info("Stage 4: creating temporary worktree {} at {}", wtDir, parent)
        git.exec(listOf("worktree", "add", "--detach", wtDir.path, parent))
        val replay = Replayer(wtDir, files)
        if (cfg.verify_build) {
          status("🔄 Stage 4: verifying the change graph by building intermediate states...")
          val verifyTab = tabs.newTask("Verification")
          verification = verifyGraph(graph, replay, File(wtDir, prefix), cfg, comparator, verifyTab, commitContext)
          verifyTab.complete()
          order = graph.topologicalOrder(comparator)
          status("Verification: **${verification.status}**")
        }
        status("🔄 Emitting ${order.sumOf { it.items.size }} atomic commit(s) in ${order.size} operation(s)...")
        emitSeries(git, wtDir, replay, graph, order, commit, shortSha, subject, authorInfo, cfg, series)
      } finally {
        try {
          log.info("Stage 4: removing temporary worktree {}", wtDir)
          git.exec(listOf("worktree", "remove", "--force", wtDir.path), allowFail = true)
          git.exec(listOf("worktree", "prune"), allowFail = true)
        } catch (e: Exception) {
          log.warn("Failed to remove worktree $wtDir", e)
        }
        tmpRoot.deleteRecursively()
      }
    }

    // Final invariant check (in-memory exact reconstruction)
    val allApplied = items.toSet()
    val mismatches = files.filter { f ->
      val expected: Pair<String, String>? = if (f.status == 'D') null else f.newPath!! to f.newContent!!
      fileState(f, allApplied) != expected
    }
    if (mismatches.isEmpty()) {
      log.info("Final invariant check: exact reconstruction verified for {} file(s)", files.size)
    } else {
      log.error(
        "Final invariant check: {} file(s) differ: {}",
        mismatches.size,
        mismatches.joinToString { it.displayPath })
    }

    // Display operations & dependencies
    opsTab.add(md(buildString {
      appendLine("## Operations (topological order)")
      appendLine()
      order.forEachIndexed { i, op -> appendLine(operationMarkdown(i + 1, op)) }
    }))
    opsTab.complete()
    depsTab.add(md(buildString {
      appendLine("## Dependency Edges (${graph.edges.size})")
      appendLine()
      if (cycleMerges.isNotEmpty()) {
        appendLine("Merged cycles: ${cycleMerges.joinToString("; ") { it.joinToString(", ") }}")
        appendLine()
      }
      graph.edges.forEach { e ->
        appendLine("- **${e.before}** → **${e.after}** _(${e.kind})_: ${disp(e.reason)}")
      }
    }))
    depsTab.complete()

    // ---------------- Outputs ----------------
    val base = "commit_analysis_$shortSha"
    // Atomic diffs for every change item, written before the JSON so the JSON can reference them.
    val itemLinks: Map<String, String?> = series.itemCommits.associate { ic ->
      ic.item.id to writeArtifact(
        task,
        "${base}_item_${ic.item.id}_${slug(ic.item.file.displayPath)}.patch",
        ic.patch.toByteArray(ISO)
      )
    }
    val json = toJson(
       graphToMap(commit, parent, subject, graph, order, verification, series, itemLinks, mismatches.size, target)
    ).toString()
     val report = buildReport(
       commit, parent, subject, files, items, graph, order, verification, series, parsed.notes, mismatches, target
     )
    val jsonLink = writeArtifact(task, "${base}_graph.json", json.toByteArray(StandardCharsets.UTF_8))
    val reportLink = writeArtifact(task, "${base}_report.md", report.toByteArray(StandardCharsets.UTF_8))
    val viewerLink = if (cfg.emit_viewer) writeViewer(task, base, shortSha, subject, json, top) else null
    val patchLinks = series.patches.mapIndexedNotNull { i, (name, patch) ->
      writeArtifact(task, "${base}_${(i + 1).toString().padStart(4, '0')}_${slug(name)}.patch", patch.toByteArray(ISO))
    }
    log.info(
      "Outputs written: json={}, report={}, viewer={}, patches={}/{}",
      jsonLink != null, reportLink != null, viewerLink != null, patchLinks.size, series.patches.size,
    )

    val outputTab = tabs.newTask("Report")
    outputTab.add(md(report))
    outputTab.complete()

    status(buildString {
      appendLine("## ✅ Analysis complete")
      viewerLink?.let { appendLine("- [Interactive viewer]($it)") }
      jsonLink?.let { appendLine("- [Change graph JSON]($it)") }
      reportLink?.let { appendLine("- [Review report]($it)") }
      if (patchLinks.isNotEmpty()) appendLine("- ${patchLinks.size} patch file(s) written")
      series.branchCreated?.let { appendLine("- Branch `$it` created") }
      if (series.mergeCommits.isNotEmpty()) appendLine("- ${series.mergeCommits.size} merge commit(s) join independent operations")
      series.branchError?.let { appendLine("- ⚠️ Branch creation failed: $it") }
    })

    return buildString {
      appendLine("# Commit Analysis: $shortSha — ${disp(subject)}")
      appendLine()
       if (isRange) {
         appendLine("- Range: `${parent.take(10)}..${commit.take(10)}` (${target.commits.size} commit(s))")
       }
      appendLine("- Files: ${files.size}, change items: ${items.size}, operations: ${order.size}, edges: ${graph.edges.size}")
      appendLine("- Exact reconstruction: ${if (mismatches.isEmpty()) "✅ verified" else "❌ ${mismatches.size} file(s) differ"}")
      appendLine("- Build verification: ${verification.status}")
      series.treeMatches?.let { appendLine("- Commit series tree identical to original: ${if (it) "✅" else "❌"}") }
      series.branchCreated?.let { appendLine("- Branch: `$it`") }
      viewerLink?.let { appendLine("- [Interactive viewer]($it)") }
      jsonLink?.let { appendLine("- [Change graph JSON]($it)") }
      reportLink?.let { appendLine("- [Review report]($it)") }
      appendLine()
      appendLine("## Operations")
      order.take(60).forEachIndexed { i, op ->
        val flag = if (op.suspicious) " ⚠️" else ""
        appendLine("${i + 1}. **${disp(op.name)}** _(${op.category}, ${op.items.size} item(s))_$flag")
      }
      if (order.size > 60) appendLine("...and ${order.size - 60} more (see report)")
    }
  }

  // ---------------------------------------------------------------------------
  // Stage 1: parsing & normalization
  // ---------------------------------------------------------------------------
   /**
    * Resolves the analysis target. Supported forms:
    * - single revision `X`: base is `X^1` (first parent);
    * - `A..B`: base is `A`, tip is `B` (tree diff A -> B);
    * - `A...B`: base is merge-base(A, B), tip is `B`;
    * - explicit [baseRev] with a single revision `B`: same as `baseRev..B`.
    * Empty sides of a range default to HEAD, as in git.
    */
   private fun resolveTarget(git: Git, spec: String, baseRev: String?): ResolvedTarget {
     fun rev(r: String): String {
       val name = r.trim().ifBlank { "HEAD" }
       if (!git.ok("rev-parse", "--verify", "--quiet", "$name^{commit}")) {
         throw IllegalArgumentException("Unknown revision: $name")
       }
       return git.text("rev-parse", "--verify", "$name^{commit}")
     }
     val s = spec.trim().ifBlank { "HEAD" }
     val explicitBase = baseRev?.trim()?.ifBlank { null }
     val (baseSpec, tipSpec, symmetric) = when {
       explicitBase != null -> Triple(explicitBase, s, false)
       s.contains("...") -> Triple(s.substringBefore("..."), s.substringAfter("..."), true)
       s.contains("..") -> Triple(s.substringBefore(".."), s.substringAfter(".."), false)
       else -> Triple(null, s, false)
     }
     val tip = rev(tipSpec)
     if (baseSpec == null) {
       if (!git.ok("rev-parse", "--verify", "--quiet", "$tip^1")) {
         throw IllegalStateException("Commit $tip has no parent (root commit); nothing to explode against")
       }
       val parent = git.text("rev-parse", "$tip^1")
       return ResolvedTarget(parent, tip, isRange = false, symmetric = false, baseIsAncestor = true, commits = emptyList())
     }
     val baseSha = rev(baseSpec)
     val base = if (symmetric) {
       val (code, out) = git.exec(listOf("merge-base", baseSha, tip), allowFail = true)
       if (code != 0) throw IllegalStateException("No merge-base between $baseSpec and $tipSpec")
       String(out, StandardCharsets.UTF_8).trim()
     } else baseSha
     if (base == tip) {
       throw IllegalStateException("Commit range '$s' is empty (base and tip are both $tip)")
     }
     val baseIsAncestor = git.ok("merge-base", "--is-ancestor", base, tip)
     val commits = git.text("log", "--reverse", "--format=%H%x00%s", "$base..$tip")
       .lines().filter { it.isNotBlank() }
       .map { line -> line.split('\u0000', limit = 2).let { it[0] to it.getOrElse(1) { "" } } }
     if (!baseIsAncestor) log.warn("Range base {} is not an ancestor of tip {}; using plain tree diff", base, tip)
     log.info("Resolved range {}..{} ({} commit(s), symmetric={})", base, tip, commits.size, symmetric)
     return ResolvedTarget(base, tip, isRange = true, symmetric = symmetric, baseIsAncestor = baseIsAncestor, commits = commits)
   }
   /** Builds an LLM/context-friendly summary of all commit messages in a range (oldest first). */
   private fun rangeMessage(git: Git, target: ResolvedTarget): String {
     val (code, out) = git.exec(
       listOf("log", "--reverse", "--format=- %h %s%n%w(0,4,4)%b", "${target.base}..${target.tip}"),
       allowFail = true
     )
     val text = if (code == 0) String(out, StandardCharsets.UTF_8).trim() else ""
     return text.ifBlank { target.commits.joinToString("\n") { "- ${it.first.take(10)} ${it.second}" } }
       .lines().filter { it.isNotBlank() }.joinToString("\n")
   }


  private fun parseCommit(git: Git, parent: String, commit: String): ParsedCommit {
    val notes = mutableListOf<String>()
    val raw =
      String(git.bytes("diff", "--name-status", "-z", "-M", "--no-color", parent, commit), StandardCharsets.UTF_8)
    val toks = raw.split('\u0000')
    val entries = mutableListOf<Triple<Char, String, String>>()
    var i = 0
    while (i < toks.size) {
      val st = toks[i]
      if (st.isEmpty()) {
        i++
        continue
      }
      val c = st[0]
      if ((c == 'R' || c == 'C') && i + 2 < toks.size) {
        entries.add(Triple(c, toks[i + 1], toks[i + 2]))
        i += 3
      } else if (i + 1 < toks.size) {
        entries.add(Triple(c, toks[i + 1], toks[i + 1]))
        i += 2
      } else {
        i++
      }
    }
    log.info("parseCommit: {} name-status entries between {} and {}", entries.size, parent, commit)

    val files = mutableListOf<FileChange>()
    val items = mutableListOf<ChangeItem>()
    var nextId = 1
    fun newItem(kind: ItemKind, f: FileChange, hunk: Hunk?, removed: String, added: String): ChangeItem =
      ChangeItem("i${nextId++}", kind, f, hunk, removed, added).also { items.add(it) }

    entries.forEachIndexed { idx, (status, oldPath, newPath) ->
      log.debug("parseCommit: entry {} status={} old={} new={}", idx, status, oldPath, newPath)
      when (status) {
        'A', 'C' -> {
          val content = blob(git, commit, newPath)
          val f = FileChange(idx, 'A', null, newPath, null, content)
          files.add(f)
          f.wholeFileItem = newItem(ItemKind.ADD_FILE, f, null, "", if (isBinary(content)) "" else content)
        }

        'D' -> {
          val content = blob(git, parent, oldPath)
          val f = FileChange(idx, 'D', oldPath, null, content, null)
          files.add(f)
          f.wholeFileItem = newItem(ItemKind.DELETE_FILE, f, null, if (isBinary(content)) "" else content, "")
        }

        else -> {
          val oldContent = blob(git, parent, oldPath)
          val newContent = blob(git, commit, newPath)
          val f = FileChange(idx, if (status == 'R') 'R' else 'M', oldPath, newPath, oldContent, newContent)
          files.add(f)
          if (oldPath != newPath) f.moveItem = newItem(ItemKind.MOVE, f, null, "", "")
          if (oldContent == newContent) {
            if (oldPath == newPath) notes.add("`$newPath`: metadata-only change (e.g. file mode); not reproduced by content replay")
            return@forEachIndexed
          }
          if (status == 'T' || isBinary(oldContent) || isBinary(newContent)) {
            log.debug("parseCommit: {} is binary/type change; whole-file replacement", newPath)
            f.wholeFileItem = newItem(ItemKind.REPLACE_FILE, f, null, "", "")
            return@forEachIndexed
          }
          val hunks = try {
            parseHunks(
              String(
                git.bytes(
                  "diff", "-U0", "--no-color", "--no-ext-diff", "--no-textconv", "--text",
                  "$parent:$oldPath", "$commit:$newPath"
                ), ISO
              )
            )
          } catch (e: Exception) {
            log.warn("Failed to diff $newPath", e)
            null
          }
          val exact = hunks != null && hunks.isNotEmpty() && try {
            applyHunks(oldContent, hunks) == newContent
          } catch (_: Exception) {
            false
          }
          if (!exact || hunks == null) {
            log.warn(
              "parseCommit: {} could not be reproduced from {} hunk(s); using whole-file replacement",
              newPath,
              hunks?.size
            )
            notes.add("`$newPath`: could not be reproduced exactly from hunks; treated as a whole-file replacement")
            f.wholeFileItem = newItem(ItemKind.REPLACE_FILE, f, null, oldContent, newContent)
            return@forEachIndexed
          }
          hunks.forEach { h ->
            f.hunks.add(h)
            f.hunkItems.add(newItem(ItemKind.EDIT, f, h, h.oldLines.joinToString(""), h.newLines.joinToString("")))
          }
        }
      }
    }
    return ParsedCommit(files, items, notes)
  }

  private fun blob(git: Git, rev: String, path: String): String =
    String(git.bytes("cat-file", "blob", "$rev:$path"), ISO)

  private fun classify(items: List<ChangeItem>, minBulk: Int) {
    items.filter { it.kind == ItemKind.EDIT }.forEach { item ->
      val h = item.hunk ?: return@forEach
      when {
        stripWs(item.removedText) == stripWs(item.addedText) -> item.kind = ItemKind.WHITESPACE
        isReorder(h) -> item.kind = ItemKind.REORDER
        else -> item.substitution = substitutionOf(h)
      }
    }
    items.filter { it.kind == ItemKind.EDIT && it.substitution != null }
      .groupBy { it.substitution!! }
      .filter { it.value.size >= minBulk }
      .values.flatten()
      .forEach {
        log.debug("classify: {} -> BULK_REPLACE {}", it.id, it.substitution)
        it.kind = ItemKind.BULK_REPLACE
      }
    if (log.isDebugEnabled) {
      items.filter { it.kind.mechanical }
        .forEach { log.debug("classify: {} {} in {}", it.id, it.kind, it.file.displayPath) }
    }
  }

  private fun mechanicalOperations(items: List<ChangeItem>): List<Operation> {
    val ops = mutableListOf<Operation>()
    val moves = items.filter { it.kind == ItemKind.MOVE }
    if (moves.isNotEmpty()) {
      ops.add(
        Operation(
          "",
          if (moves.size == 1) "Move ${moves[0].file.oldPath} to ${moves[0].file.newPath}" else "Move/rename ${moves.size} files",
          "Pure file moves detected by git rename detection; content edits inside moved files are separate operations.",
          "refactor", moves.toMutableList(),
          rule = moves.joinToString("; ") { "git mv ${it.file.oldPath} ${it.file.newPath}" }
        )
      )
    }
    val ws = items.filter { it.kind == ItemKind.WHITESPACE }
    if (ws.isNotEmpty()) {
      ops.add(
        Operation(
          "", "Whitespace-only changes",
          "Indentation, trailing whitespace, line ending and blank line changes in ${
            ws.map { it.file.displayPath }.distinct().size
          } file(s). No non-whitespace content changes.",
          "formatting", ws.toMutableList(), rule = "whitespace-insensitive equality"
        )
      )
    }
    val reorder = items.filter { it.kind == ItemKind.REORDER }
    if (reorder.isNotEmpty()) {
      ops.add(
        Operation(
          "", "Reorder lines (imports/includes)",
          "Hunks that only permute existing lines in ${reorder.map { it.file.displayPath }.distinct().size} file(s).",
          "formatting", reorder.toMutableList(), rule = "line permutation"
        )
      )
    }
    items.filter { it.kind == ItemKind.BULK_REPLACE }.groupBy { it.substitution!! }.forEach { (sub, its) ->
      ops.add(
        Operation(
          "", "Replace `${disp(sub.first).take(40)}` with `${disp(sub.second).take(40)}`",
          "Bulk textual substitution applied at ${its.size} location(s) in ${
            its.map { it.file.displayPath }.distinct().size
          } file(s).",
          "refactor", its.toMutableList(), rule = "s/${disp(sub.first)}/${disp(sub.second)}/"
        )
      )
    }
    ops.forEach { it.confidence = 0.95 }
    return ops
  }

  // ---------------------------------------------------------------------------
  // Stage 2: clustering
  // ---------------------------------------------------------------------------

  private class UnionFind(n: Int) {
    private val p = IntArray(n) { it }
    fun find(x: Int): Int {
      var r = x
      while (p[r] != r) r = p[r]
      var c = x
      while (p[c] != r) {
        val nx = p[c]
        p[c] = r
        c = nx
      }
      return r
    }

    fun union(a: Int, b: Int) {
      val ra = find(a)
      val rb = find(b)
      if (ra != rb) p[maxOf(ra, rb)] = minOf(ra, rb)
    }
  }

  private fun cluster(items: List<ChangeItem>, threshold: Double, maxSize: Int): List<List<ChangeItem>> {
    val n = items.size
    if (n <= 1) return items.map { listOf(it) }
    val weights = HashMap<Long, Double>()
    fun key(a: Int, b: Int): Long = minOf(a, b).toLong() * n + maxOf(a, b)
    fun add(k: Long, w: Double) {
      weights[k] = (weights[k] ?: 0.0) + w
    }

    // Inverted index over signature tokens (avoids all-pairs comparison)
    val index = HashMap<String, MutableList<Int>>()
    items.forEachIndexed { i, it -> it.signature.forEach { t -> index.getOrPut(t) { mutableListOf() }.add(i) } }
    val maxDf = maxOf(8, n / 20)
    val seen = HashSet<Long>()
    index.values.filter { it.size in 2..maxDf }.forEach { list ->
      for (x in list.indices) for (y in x + 1 until list.size) {
        val a = list[x]
        val b = list[y]
        val k = key(a, b)
        if (seen.add(k)) add(k, jaccard(items[a].signature, items[b].signature))
      }
    }
    // Symbol-level relationships: definition change + reference updates
    items.forEachIndexed { i, item ->
      (item.definedAdded + item.definedRemoved).filter { isSignificant(it) }.forEach { sym ->
        index[sym]?.takeIf { it.size <= 50 }?.forEach { j -> if (j != i) add(key(i, j), 0.5) }
      }
    }
    // Structural locality (weak)
    items.indices.groupBy { items[it].file.index }.values.forEach { idxs ->
      val sorted = idxs.sortedBy { items[it].line }
      for (x in 0 until sorted.size - 1) {
        val ha = items[sorted[x]].hunk
        val hb = items[sorted[x + 1]].hunk
        if (ha != null && hb != null && hb.start - (ha.start + ha.oldCount) <= 3) add(
          key(sorted[x], sorted[x + 1]),
          0.3
        )
      }
    }
    val uf = UnionFind(n)
    weights.forEach { (k, w) -> if (w >= threshold) uf.union((k / n).toInt(), (k % n).toInt()) }
    val groups = items.indices.groupBy { uf.find(it) }.values.map { g -> g.map { items[it] } }
    log.debug(
      "cluster: n={} threshold={} weightedPairs={} -> {} group(s), largest={}",
      n, threshold, weights.size, groups.size, groups.maxOfOrNull { it.size }
    )
    return groups.flatMap { g ->
      when {
        g.size <= maxSize -> listOf(g)
        threshold < 1.5 -> {
          log.debug("cluster: splitting group of {} with raised threshold {}", g.size, threshold + 0.2)
          cluster(g, threshold + 0.2, maxSize)
        }

        else -> {
          log.debug("cluster: chunking group of {} into size {}", g.size, maxSize)
          g.chunked(maxSize)
        }
      }
    }
  }

  private fun refineClustersWithLlm(
    clusters: List<List<ChangeItem>>,
    commitContext: String,
    ui: ISessionTask
  ): List<Operation> {
    val result = mutableListOf<Operation>()
    val batches = mutableListOf<MutableList<List<ChangeItem>>>()
    var current = mutableListOf<List<ChangeItem>>()
    var size = 0
    clusters.forEach { c ->
      val s = c.take(40).sumOf { itemSummary(it).length }
      if (current.isNotEmpty() && size + s > 20000) {
        batches.add(current)
        current = mutableListOf()
        size = 0
      }
      current.add(c)
      size += s
    }
    if (current.isNotEmpty()) batches.add(current)
    log.info("LLM refinement: {} cluster(s) in {} batch(es)", clusters.size, batches.size)

    batches.forEachIndexed { bi, batch ->
      val batchItems = batch.flatten()
      val byId = batchItems.associateBy { it.id }
      val clusterOf = HashMap<ChangeItem, Int>()
      batch.forEachIndexed { ci, c -> c.forEach { clusterOf[it] = ci } }
      val prompt = buildString {
        appendLine("You are analyzing a single git commit that is being decomposed into small, atomic, purpose-specific operations.")
        appendLine("Each operation will become its own commit, so it should express exactly one intent.")
        appendLine()
        appendLine(commitContext)
        appendLine()
        appendLine("Below are candidate clusters of change items produced by deterministic similarity analysis.")
        appendLine("Each change item is a minimal diff hunk (lines prefixed with - were removed, + were added).")
        appendLine()
        appendLine("Your job:")
        appendLine("1. Merge clusters that express the same intent; split clusters that mix multiple intents; move misplaced items.")
        appendLine("2. Every item id listed below must appear in exactly one operation.")
        appendLine("3. Give each operation a short imperative name (commit subject) and a 1-3 sentence description.")
        appendLine("4. Assign a category: refactor, feature, fix, test, docs, config, build, cleanup, formatting, generated.")
        appendLine("5. Mark operations as suspicious if they look unrelated to the rest of the commit or hide a behavioral change inside a refactor/formatting change.")
        appendLine("Prefer operations that would compile on their own; keep a definition change together with tightly coupled call-site updates when they cannot be separated.")
        appendLine()
        appendLine("## Clusters")
        batch.forEachIndexed { ci, c ->
          appendLine("### Cluster ${ci + 1}")
          c.take(40).forEach { append(itemSummary(it)) }
          if (c.size > 40) appendLine(
            "- ...and ${c.size - 40} more items in this cluster: ${
              c.drop(40).joinToString(", ") { it.id }
            }"
          )
          appendLine()
        }
      }
      val t0 = System.currentTimeMillis()
      val plan = try {
        ParsedAgent(
          resultClass = OperationPlan::class.java,
          prompt = prompt,
          model = defaultSmart,
          temperature = 0.1,
          parsingModel = defaultFast
        ).answer(listOf("Group the change items into operations.")).obj
      } catch (e: Exception) {
        log.warn("LLM grouping failed for batch ${bi + 1}", e)
        ui.add(md("⚠️ LLM grouping failed for batch ${bi + 1}: ${e.message}; using deterministic clusters"))
        null
      }
      log.info(
        "LLM refinement batch {} returned {} operation(s) in {}ms",
        bi + 1,
        plan?.operations?.size ?: 0,
        System.currentTimeMillis() - t0
      )
      val assigned = HashSet<String>()
      val batchOps = mutableListOf<Operation>()
      plan?.operations?.forEach { po ->
        val its = po.item_ids.map { it.trim().trim('[', ']') }.mapNotNull { byId[it] }.filter { assigned.add(it.id) }
        if (its.isEmpty()) return@forEach
        batchOps.add(
          Operation(
            "",
            po.name.trim().ifBlank { defaultName(its) }.take(120),
            po.description.trim().ifBlank { defaultDescription(its) },
            normalizeCategory(po.category),
            its.toMutableList()
          ).apply {
            suspicious = po.suspicious
            suspicionReason = po.suspicion_reason?.ifBlank { null }
            confidence = 0.75
          }
        )
      }
      // Validate: items omitted by the LLM go back to the operation holding most of their original cluster
      val unassigned = batchItems.filter { it.id !in assigned }
      if (unassigned.isNotEmpty()) log.info(
        "LLM refinement batch {}: {} item(s) omitted by LLM, reassigning",
        bi + 1,
        unassigned.size
      )
      unassigned.groupBy { clusterOf[it] ?: -1 }.forEach { (ci, leftovers) ->
        val home = batchOps
          .filter { op -> op.items.any { clusterOf[it] == ci } }
          .maxByOrNull { op -> op.items.count { clusterOf[it] == ci } }
        if (home != null) home.items.addAll(leftovers) else batchOps.add(deterministicOp(leftovers))
      }
      result.addAll(batchOps)
      ui.add(md("✅ Batch ${bi + 1}/${batches.size}: ${batch.size} cluster(s) → ${batchOps.size} operation(s)"))
    }
    return result
  }

  private fun deterministicOp(items: List<ChangeItem>): Operation =
    Operation("", defaultName(items), defaultDescription(items), guessCategory(items), items.toMutableList())

  private fun defaultName(items: List<ChangeItem>): String {
    val files = items.map { it.file.displayPath.substringAfterLast('/') }.distinct()
    val where = if (files.size == 1) files[0] else "${files.size} files"
    if (items.all { it.kind == ItemKind.ADD_FILE }) return "Add $where"
    if (items.all { it.kind == ItemKind.DELETE_FILE }) return "Delete $where"
    val sym =
      items.flatMap { it.definedAdded + it.definedRemoved }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?: items.flatMap { it.signature }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    return (if (sym != null) "Update `${disp(sym)}` in $where" else "Edit $where").take(120)
  }

  private fun defaultDescription(items: List<ChangeItem>): String {
    val files = items.map { it.file.displayPath }.distinct()
    return "Changes ${items.size} change item(s) in ${
      files.take(5).joinToString(", ")
    }${if (files.size > 5) " and ${files.size - 5} more" else ""}."
  }

  private fun guessCategory(items: List<ChangeItem>): String {
    val paths = items.map { it.file.displayPath.lowercase() }
    return when {
      paths.all { it.contains("test") } -> "test"
      paths.all { it.endsWith(".md") || it.endsWith(".rst") || it.endsWith(".txt") || it.startsWith("docs/") } -> "docs"
      paths.all { p -> CONFIG_EXTENSIONS.any { p.endsWith(it) } } -> "config"
      else -> "change"
    }
  }

  private fun normalizeCategory(c: String?): String {
    val v = c?.trim()?.lowercase() ?: return "change"
    return if (v in CATEGORIES) v else "change"
  }

  private fun itemSummary(item: ChangeItem, maxLines: Int = 3): String = buildString {
    val h = item.hunk
    append("- [${item.id}] ${item.file.displayPath}")
    if (h != null) append(":${h.start + 1}")
    append(" (${item.kind}")
    if (h != null) append(", -${h.oldLines.size}/+${h.newLines.size}")
    append(")")
    when (item.kind) {
      ItemKind.ADD_FILE -> append(" new file, ${item.addedText.lines().size} lines")
      ItemKind.DELETE_FILE -> append(" deleted file, ${item.removedText.lines().size} lines")
      ItemKind.REPLACE_FILE -> append(" whole-file replacement")
      else -> {}
    }
    appendLine()
    if (h != null) {
      h.oldLines.take(maxLines).forEach { appendLine("    - ${clip(it)}") }
      if (h.oldLines.size > maxLines) appendLine("    - ...")
      h.newLines.take(maxLines).forEach { appendLine("    + ${clip(it)}") }
      if (h.newLines.size > maxLines) appendLine("    + ...")
    } else {
      val defs = (item.definedAdded + item.definedRemoved).take(8)
      if (defs.isNotEmpty()) appendLine("    defines: ${defs.joinToString(", ") { disp(it) }}")
    }
  }

  // ---------------------------------------------------------------------------
  // Stage 3: dependencies
  // ---------------------------------------------------------------------------

  private fun addDeterministicEdges(graph: ChangeGraph) {
    val definers = HashMap<String, MutableSet<String>>()
    val removers = HashMap<String, MutableSet<String>>()
    graph.ops.forEach { op ->
      val added = op.definedAdded().filter { isSignificant(it) }
      added.forEach { definers.getOrPut(it) { mutableSetOf() }.add(op.id) }
      (op.definedRemoved() - added.toSet()).filter { isSignificant(it) }
        .forEach { removers.getOrPut(it) { mutableSetOf() }.add(op.id) }
    }
    graph.ops.toList().forEach { b ->
      val bDefs = b.definedAdded()
      (b.refsAdded() - bDefs).forEach { x ->
        definers[x]?.takeIf { it.size <= 50 }?.forEach { a ->
          if (a != b.id && graph.addEdge(
              a,
              b.id,
              "definition-before-use",
              "`$x` is introduced by $a and referenced by ${b.id}"
            )
          ) {
            log.debug("edge {} -> {} (definition-before-use: {})", a, b.id, x)
          }
        }
      }
      b.refsRemoved().forEach { x ->
        removers[x]?.takeIf { it.size <= 50 }?.forEach { a ->
          if (a != b.id && graph.addEdge(
              b.id,
              a,
              "removal-after-last-use",
              "${b.id} removes references to `$x`, whose definition is deleted by $a"
            )
          ) {
            log.debug("edge {} -> {} (removal-after-last-use: {})", b.id, a, x)
          }
        }
      }
    }
  }

  private fun llmDependencies(graph: ChangeGraph, commitContext: String, ui: ISessionTask): Map<String, Int> {
    val prompt = buildString {
      appendLine("You are ordering the operations of an exploded git commit. Each operation will become its own commit.")
      appendLine()
      appendLine(commitContext)
      appendLine()
      appendLine("## Operations")
      graph.ops.forEach { op ->
        appendLine("- ${op.id} [${op.category}] ${disp(op.name)}: ${disp(op.description).take(300)}")
        appendLine("    files: ${op.files().take(6).joinToString(", ")}")
        val defs = op.definedAdded().take(10)
        if (defs.isNotEmpty()) appendLine("    defines: ${defs.joinToString(", ") { disp(it) }}")
        val refs = op.refsAdded().take(10)
        if (refs.isNotEmpty()) appendLine("    newly references: ${refs.joinToString(", ") { disp(it) }}")
      }
      appendLine()
      appendLine("## Known (syntactic) dependencies")
      if (graph.edges.isEmpty()) appendLine("(none)")
      graph.edges.take(200).forEach { appendLine("- ${it.before} before ${it.after}: ${disp(it.reason)}") }
      appendLine()
      appendLine("## Instructions")
      appendLine("1. Infer semantic dependencies not visible in the syntax (config changes required by code, behavior changes that tests depend on, feature flags). Only list edges you are confident about, each with a reason.")
      appendLine("2. Suggest a human-friendly order of ALL operation ids among independent operations (e.g. refactor, then feature, then tests, then cleanup). It must respect the dependencies.")
    }
    val llmOrder = HashMap<String, Int>()
    val t0 = System.currentTimeMillis()
    log.info(
      "LLM dependency analysis: {} op(s), {} known edge(s), prompt {} chars",
      graph.ops.size,
      graph.edges.size,
      prompt.length
    )
    val plan = try {
      ParsedAgent(
        resultClass = DependencyPlan::class.java,
        prompt = prompt,
        model = defaultSmart,
        temperature = 0.1,
        parsingModel = defaultFast
      ).answer(listOf("Provide semantic dependencies and a suggested order.")).obj
    } catch (e: Exception) {
      log.warn("LLM dependency analysis failed", e)
      ui.add(md("⚠️ LLM dependency analysis failed: ${e.message}"))
      return llmOrder
    }
    var added = 0
    var rejected = 0
    plan.edges.forEach { e ->
      val b = e.before.trim()
      val a = e.after.trim()
      if (graph.op(b) != null && graph.op(a) != null) {
        if (graph.addEdge(
            b,
            a,
            "semantic",
            e.reason.ifBlank { "LLM-inferred semantic dependency" },
            allowCycle = false
          )
        ) {
          added++
          log.debug("semantic edge {} -> {}: {}", b, a, e.reason)
        } else {
          rejected++
          log.debug("rejected semantic edge {} -> {} (duplicate or cycle)", b, a)
        }
      } else {
        log.debug("ignored semantic edge with unknown op(s): {} -> {}", b, a)
      }
    }
    plan.suggested_order.map { it.trim() }.distinct()
      .forEachIndexed { i, id -> if (graph.op(id) != null) llmOrder[id] = i }
    log.info(
      "LLM dependency analysis done in {}ms: added={} rejected={} orderedOps={}",
      System.currentTimeMillis() - t0, added, rejected, llmOrder.size
    )
    ui.add(md("✅ LLM added $added semantic edge(s); rejected $rejected (duplicate or would create a cycle)"))
    return llmOrder
  }

  private fun operationComparator(placement: String, llmIndex: (String) -> Int?): Comparator<Operation> {
    fun rank(op: Operation): Int = when {
      op.mechanical && op.items.all { it.kind == ItemKind.MOVE } -> 0
      op.category == "generated" -> 4
      op.mechanical -> if (placement == "last") 3 else 1
      else -> 2
    }
    return compareBy<Operation>({ rank(it) }, { llmIndex(it.id) ?: Int.MAX_VALUE }, { it.position() }, { it.id })
  }

  // ---------------------------------------------------------------------------
  // Stage 4: verification & commit series
  // ---------------------------------------------------------------------------

  private fun verifyGraph(
    graph: ChangeGraph,
    replay: Replayer,
    buildDir: File,
    cfg: CommitAnalyzerTaskExecutionConfigData,
    comparator: Comparator<Operation>,
    ui: ISessionTask,
    commitContext: String
  ): VerificationReport {
    val report = VerificationReport()
    val cmd = cfg.build_command ?: return report.apply { status = "skipped: no build command" }
    val timeout = cfg.build_timeout_seconds.toLong()
    ui.add(md("Running baseline build on parent: `$cmd`"))
    replay.sync(emptySet())
    val (baseOk, baseOut) = runShell(cmd, buildDir, timeout)
    if (!baseOk) {
      log.warn("Verification: parent build failed; skipping verification")
      report.status = "skipped: parent does not build (assumption A0 violated)"
      ui.add(md("⚠️ Parent build failed; skipping verification.\n\n```\n${baseOut.takeLast(2000)}\n```"))
      return report
    }
    ui.add(md("✅ Parent builds"))
    var attempt = 0
    while (true) {
      val order = graph.topologicalOrder(comparator)
      log.info(
        "Verification attempt {}: {} op(s) in order {}",
        attempt + 1,
        order.size,
        order.joinToString(", ") { it.id })
      graph.ops.forEach { it.verified = null }
      replay.sync(emptySet())
      val applied = HashSet<ChangeItem>()
      var failure: Pair<Int, String>? = null
      // The state after the final operation equals the original commit (assumed to build), so it is not rebuilt.
      for (k in 0 until order.size - 1) {
        val op = order[k]
        applied.addAll(op.items)
        val changed = replay.sync(applied, op.items.map { it.file }.distinct())
        if (changed.isEmpty()) {
          log.debug("Verification: {} produced no file changes; skipping build", op.id)
          op.verified = true
          continue
        }
        log.info(
          "Verification: building after {} ({}/{}), {} file(s) changed",
          op.id,
          k + 1,
          order.size - 1,
          changed.size
        )
        val (ok, out) = runShell(cmd, buildDir, timeout)
        if (ok) {
          op.verified = true
          op.confidence = maxOf(op.confidence, 0.9)
        } else {
          op.verified = false
          log.warn("Verification: build failed after {} ({})", op.id, op.name)
          failure = k to out
          break
        }
      }
      val f = failure
      if (f == null) {
        order.lastOrNull()?.verified = true
        report.status = "passed"
        log.info("Verification passed on attempt {}", attempt + 1)
        report.steps.add("Attempt ${attempt + 1}: all ${order.size} intermediate states build")
        ui.add(md("✅ Attempt ${attempt + 1}: every intermediate state builds"))
        break
      }
      val (k, out) = f
      val failing = order[k]
      report.steps.add("Attempt ${attempt + 1}: build failed after applying ${failing.id} (${disp(failing.name)})")
      ui.add(
        md(
          "❌ Attempt ${attempt + 1}: build failed after **${failing.id}** ${disp(failing.name)}\n\n```\n${
            out.takeLast(
              1500
            )
          }\n```"
        )
      )
      attempt++
      if (attempt > cfg.max_verification_retries) {
        report.status = "failed: retry budget exhausted (best partial result: first $k operation(s) verified)"
        log.warn("Verification: retry budget exhausted after {} attempt(s)", attempt)
        break
      }
      val outTokens = tokens(out)
      val later = order.drop(k + 1)
      var fixed = false
      // Missing symbol introduced by a later operation
      later.filter { op -> op.definedAdded().any { it in outTokens } }.forEach { op ->
        fixed = orderBefore(
          graph,
          op,
          failing,
          "Build failed when ${failing.id} was applied before ${op.id}, which introduces a referenced symbol",
          report
        ) || fixed
      }
      // Failing operation deletes a definition that later operations stop using
      if (!fixed) {
        val removedDefs = failing.definedRemoved().filter { it in outTokens }.toSet()
        later.filter { op -> op.refsRemoved().any { it in removedDefs } }.forEach { op ->
          fixed = orderBefore(
            graph,
            op,
            failing,
            "${op.id} removes remaining uses of a symbol deleted by ${failing.id}",
            report
          ) || fixed
        }
      }
      if (!fixed && cfg.use_llm && later.isNotEmpty()) {
        log.info("Verification: asking LLM to diagnose failure of {}", failing.id)
        val diag = diagnoseBuildFailure(failing, later, out, commitContext)
        log.info(
          "Verification: LLM diagnosis requires {} ({})",
          diag?.required_operation_ids,
          diag?.explanation?.take(200)
        )
        diag?.required_operation_ids?.mapNotNull { id -> later.firstOrNull { it.id == id.trim() } }?.forEach { op ->
          fixed = orderBefore(graph, op, failing, "LLM diagnosis: ${diag.explanation.take(200)}", report) || fixed
        }
      }
      if (!fixed) {
        val partner = later.firstOrNull { graph.op(it.id) != null }
        if (partner == null || graph.op(failing.id) == null) {
          report.status = "failed: no dependency could be identified for the failing state"
          log.warn("Verification: no repair possible for {}", failing.id)
          break
        }
        log.info("Verification: fallback merge of {} into {}", partner.id, failing.id)
        graph.merge(
          listOf(failing.id, partner.id),
          "(Merged during build verification: no separable dependency was found.)"
        )
        report.fixes.add("Merged ${partner.id} into ${failing.id} (no dependency could be identified)")
      }
      graph.mergeCycles().forEach { report.fixes.add("Merged dependency cycle: ${it.joinToString(", ")}") }
      task@ run { ui.add(md("🔧 Applied fixes: ${report.fixes.takeLast(3).joinToString("; ")}")) }
    }
    return report
  }

  private fun orderBefore(
    graph: ChangeGraph,
    first: Operation,
    second: Operation,
    reason: String,
    report: VerificationReport
  ): Boolean {
    if (graph.op(first.id) == null || graph.op(second.id) == null) return false
    return if (graph.reachable(second.id, first.id)) {
      log.info("Verification repair: merging {} into {} (mutual dependency)", first.id, second.id)
      graph.merge(
        listOf(second.id, first.id),
        "(Merged during build verification: mutually dependent with ${first.id}.)"
      )
      report.fixes.add("Merged ${first.id} into ${second.id} (mutual dependency)")
      true
    } else {
      val added = graph.addEdge(first.id, second.id, "build-verification", reason)
      if (added) {
        log.info("Verification repair: edge {} -> {}: {}", first.id, second.id, reason)
        report.fixes.add("Added edge ${first.id} → ${second.id}: $reason")
      }
      added
    }
  }

  private fun diagnoseBuildFailure(
    failing: Operation,
    later: List<Operation>,
    output: String,
    commitContext: String
  ): BuildDiagnosis? {
    val prompt = buildString {
      appendLine("A git commit was exploded into operations that are applied one at a time. After applying the operation below, the build failed.")
      appendLine("Identify which LATER operations must be applied before the failing one for the build to succeed. Do not propose code edits.")
      appendLine()
      appendLine(commitContext.take(1500))
      appendLine()
      appendLine("## Failing operation")
      appendLine("${failing.id}: ${disp(failing.name)} — ${disp(failing.description).take(300)}")
      appendLine("files: ${failing.files().take(8).joinToString(", ")}")
      appendLine()
      appendLine("## Build output (tail)")
      appendLine("```")
      appendLine(output.takeLast(4000))
      appendLine("```")
      appendLine()
      appendLine("## Later operations")
      later.take(100).forEach { op ->
        appendLine(
          "- ${op.id}: ${disp(op.name)}; files: ${
            op.files().take(4).joinToString(", ")
          }; defines: ${op.definedAdded().take(8).joinToString(", ") { disp(it) }}"
        )
      }
    }
    return try {
      ParsedAgent(
        resultClass = BuildDiagnosis::class.java,
        prompt = prompt,
        model = defaultSmart,
        temperature = 0.1,
        parsingModel = defaultFast
      ).answer(listOf("Diagnose the build failure.")).obj
    } catch (e: Exception) {
      log.warn("LLM build diagnosis failed", e)
      null
    }
  }

  /**
   * Emits the change graph as a git commit DAG rather than a linear sequence:
   * - every change item becomes one commit, chained within its operation;
   * - an operation without prerequisites is based on the original parent;
   * - an operation with one (transitively reduced) prerequisite is based on that operation's head;
   * - an operation with several prerequisites is based on a merge commit joining their heads, whose
   *   tree is exactly the state with all ancestor operations applied (hunks never overlap, so any
   *   union of applied items has a well-defined state);
   * - all sink operations are finally joined by a merge commit whose tree equals the original commit.
   */
  private fun emitSeries(
    git: Git,
    wtDir: File,
    replay: Replayer,
    graph: ChangeGraph,
    order: List<Operation>,
    commit: String,
    shortSha: String,
    subject: String,
    authorInfo: List<String>,
    cfg: CommitAnalyzerTaskExecutionConfigData,
    series: SeriesResult
  ) {
    val env = HashMap<String, String>()
    if (authorInfo.size >= 2) {
      env["GIT_AUTHOR_NAME"] = authorInfo[0]
      env["GIT_AUTHOR_EMAIL"] = authorInfo[1]
      if (authorInfo.size >= 3 && authorInfo[2].isNotBlank()) env["GIT_AUTHOR_DATE"] = authorInfo[2]
      val hasIdentity = git.ok("config", "user.name") && git.ok("config", "user.email")
      if (!hasIdentity) {
        env["GIT_COMMITTER_NAME"] = authorInfo[0]
        env["GIT_COMMITTER_EMAIL"] = authorInfo[1]
      }
    }
    val wtGit = Git(wtDir, env)
    val total = order.sumOf { it.items.size }
    log.info(
      "Emitting commit DAG: {} operation(s), {} item commit(s), {} edge(s), branch={}",
      order.size, total, graph.edges.size, cfg.create_branch ?: "commit-analysis/$shortSha"
    )
    val parentSha = wtGit.text("rev-parse", "HEAD")
    val opById = order.associateBy { it.id }
    val orderIndex = order.withIndex().associate { it.value.id to it.index }
    val preds = HashMap<String, MutableSet<String>>()
    graph.edges.forEach { e -> preds.getOrPut(e.after) { linkedSetOf() }.add(e.before) }
    val ancestors = HashMap<String, Set<String>>()
    fun itemsOf(ids: Collection<String>): Set<ChangeItem> = ids.flatMap { opById[it]?.items.orEmpty() }.toSet()
    fun closure(id: String): Set<String> = ancestors[id].orEmpty() + id

    // What is currently checked out in the worktree and which items its tree corresponds to.
    var current = parentSha
    val applied = HashSet<ChangeItem>()
    replay.sync(emptySet())

    fun checkoutState(sha: String, state: Set<ChangeItem>) {
      if (current != sha) {
        wtGit.exec(listOf("checkout", "-q", "--detach", "-f", sha))
        replay.reset(state)
        current = sha
      }
      applied.clear()
      applied.addAll(state)
    }

    fun createMerge(parentOps: List<String>, state: Set<ChangeItem>, msg: String): String {
      val parentShas = parentOps.mapNotNull { series.operationCommits[it] }.distinct()
      if (parentShas.size == 1) {
        checkoutState(parentShas[0], itemsOf(parentOps.flatMap { closure(it) }))
        return parentShas[0]
      }
      checkoutState(parentShas.first(), itemsOf(closure(parentOps.first())))
      val changed = replay.sync(state)
      changed.chunked(200).forEach { chunk -> wtGit.exec(listOf("add", "-A", "--") + chunk) }
      val tree = wtGit.text("write-tree")
      val args = mutableListOf("commit-tree", tree)
      parentShas.forEach { args.addAll(listOf("-p", it)) }
      args.addAll(listOf("-m", msg))
      val sha = String(wtGit.exec(args).second, StandardCharsets.UTF_8).trim()
      wtGit.exec(listOf("reset", "-q", "--soft", sha))
      current = sha
      applied.clear()
      applied.addAll(state)
      series.mergeCommits.add(sha)
      log.debug("Created merge {} of {} ({} file(s) changed vs first parent)", sha, parentOps, changed.size)
      return sha
    }

    var step = 0
    order.forEach { op ->
      val direct = preds[op.id].orEmpty().filter { it in opById && it in series.operationCommits }
      val anc = direct.flatMap { closure(it) }.toSet()
      ancestors[op.id] = anc
      // Transitive reduction: drop prerequisites already contained in another prerequisite's history.
      val reduced = direct
        .filter { d -> direct.none { o -> o != d && d in ancestors[o].orEmpty() } }
        .sortedBy { orderIndex[it] ?: Int.MAX_VALUE }
      val baseState = itemsOf(anc)
      val base = when (reduced.size) {
        0 -> parentSha
        1 -> series.operationCommits[reduced[0]]!!
        else -> createMerge(reduced, baseState, mergeMessage(op, reduced, opById, shortSha))
      }
      checkoutState(base, baseState)
      series.operationBases[op.id] = base
      series.operationParents[op.id] = reduced
      val opStart = base
      op.items.sortedWith(compareBy<ChangeItem>({ it.file.index }, { it.line }, { it.id })).forEach { item ->
        step++
        applied.add(item)
        val changed = replay.sync(applied, listOf(item.file))
        changed.chunked(200).forEach { chunk -> wtGit.exec(listOf("add", "-A", "--") + chunk) }
        // The atomic diff of this item, as git computes it against the previous state.
        val patch = String(wtGit.bytes("diff", "--cached", "--binary", "-M", "--no-color"), ISO)
        wtGit.exec(
          listOf("commit", "-q", "--allow-empty", "--no-verify", "-m", itemMessage(op, item, step, total, shortSha))
        )
        current = wtGit.text("rev-parse", "HEAD")
        val ic = ItemCommit(item, current, patch)
        series.itemCommits.add(ic)
        series.itemCommitById[item.id] = ic
        log.debug("Committed step {}/{} ({} / {}): {} file(s) changed", step, total, op.id, item.id, changed.size)
      }
      val opEnd = current
      series.operationCommits[op.id] = opEnd
      if (cfg.emit_patches) {
        series.patches.add(op.name to String(wtGit.bytes("diff", "--binary", "-M", opStart, opEnd), ISO))
      }
    }
    // Join all sink operations (those that are not an ancestor of any other operation).
    val sinks = order.map { it.id }.filter { id -> order.none { o -> id in ancestors[o.id].orEmpty() } }
    val head = when {
      sinks.isEmpty() -> parentSha
      sinks.size == 1 -> series.operationCommits[sinks[0]]!!
      else -> createMerge(sinks, itemsOf(order.map { it.id }), finalMergeMessage(sinks, shortSha, subject))
    }
    checkoutState(head, itemsOf(sinks.flatMap { closure(it) }))
    series.headSha = head
    val headTree = wtGit.text("rev-parse", "HEAD^{tree}")
    val commitTree = git.text("rev-parse", "$commit^{tree}")
    series.treeMatches = headTree == commitTree
    log.info(
      "Commit DAG head={} tree={} original tree={} match={} merges={} sinks={}",
      head, headTree, commitTree, series.treeMatches, series.mergeCommits.size, sinks
    )
    if (headTree != commitTree) {
      series.treeDiff = String(
        wtGit.exec(listOf("diff", "--stat", "--summary", "HEAD", commit), allowFail = true).second,
        StandardCharsets.UTF_8
      ).take(4000)
    }
    val requested = cfg.create_branch
    var branch = requested ?: "commit-analysis/$shortSha"
    if (requested == null && git.ok("show-ref", "--verify", "--quiet", "refs/heads/$branch")) {
      branch += "-${System.currentTimeMillis() / 1000}"
    }
    val (code, _) = git.exec(listOf("branch", branch, head), allowFail = true)
    if (code == 0) {
      log.info("Created branch {} at {}", branch, head)
      series.branchCreated = branch
    } else {
      log.warn("Failed to create branch {} (exit {})", branch, code)
      series.branchError = "could not create branch '$branch' (does it already exist?)"
    }
  }

  private fun mergeMessage(
    op: Operation,
    parents: List<String>,
    opById: Map<String, Operation>,
    shortSha: String
  ): String = buildString {
    appendLine("Merge prerequisites of ${op.id}: ${disp(op.name)}".lineSequence().first().take(100))
    appendLine()
    parents.forEach { p -> appendLine("- $p: ${disp(opById[p]?.name ?: p).lineSequence().firstOrNull() ?: ""}") }
    appendLine()
    appendLine("Commit-Analyzer-Source: $shortSha")
    append("Commit-Analyzer-Merge-For: ${op.id}")
  }

  private fun finalMergeMessage(sinks: List<String>, shortSha: String, subject: String): String = buildString {
    appendLine("Merge exploded commit $shortSha: ${disp(subject)}".lineSequence().first().take(100))
    appendLine()
    appendLine("Joins the independent operation branches ${sinks.joinToString(", ")};")
    appendLine("the resulting tree is identical to the original commit.")
    appendLine()
    append("Commit-Analyzer-Source: $shortSha")
  }


  private fun itemMessage(
    op: Operation,
    item: ChangeItem,
    step: Int,
    total: Int,
    shortSha: String
  ): String = buildString {
    val subject = "${disp(op.name)}: ${item.kind.name.lowercase()} ${disp(item.file.displayPath)}"
    appendLine(subject.lineSequence().first().take(100))
    appendLine()
    appendLine(disp(op.description).trim())
    op.rule?.let {
      appendLine()
      appendLine("Rule: ${disp(it).take(500)}")
    }
    appendLine()
    appendLine("Commit-Analyzer-Source: $shortSha")
    appendLine("Commit-Analyzer-Operation: ${op.id}")
    appendLine("Commit-Analyzer-Item: ${item.id}")
    append("Commit-Analyzer-Step: $step/$total")
  }

  // ---------------------------------------------------------------------------
  // Outputs
  // ---------------------------------------------------------------------------

  private fun writeArtifact(task: ISessionTask, name: String, content: ByteArray): String? = try {
    val (link, file) = task.createFile(name)
    file?.let {
      it.parentFile?.mkdirs()
      it.writeBytes(content)
      log.debug("Wrote artifact {} ({} bytes) to {}", name, content.size, it.absolutePath)
    }
    link
  } catch (e: Exception) {
    log.warn("Failed to write artifact $name", e)
    null
  }

  /**
   * Extracts the bundled viewer template from the classpath and customizes it for this analysis:
   * default graph source, repository base path (relative to the artifact directory), page title,
   * and the embedded graph JSON so the viewer works without a web server.
   */
  private fun writeViewer(
    task: ISessionTask,
    base: String,
    shortSha: String,
    subject: String,
    graphJson: String,
    repoTop: File
  ): String? = try {
    val template = CommitAnalyzerTask::class.java.getResourceAsStream(VIEWER_RESOURCE)
      ?.use { String(it.readBytes(), StandardCharsets.UTF_8) }
    if (template == null) {
      log.warn("Viewer template {} not found on classpath; skipping viewer", VIEWER_RESOURCE)
      null
    } else {
      val (link, file) = task.createFile("${base}_viewer.html")
      if (file == null) {
        link
      } else {
        val repoBase = try {
          file.absoluteFile.parentFile.canonicalFile.toPath()
            .relativize(repoTop.canonicalFile.toPath()).toString()
            .replace('\\', '/').ifBlank { "." }
        } catch (e: Exception) {
          log.debug("Could not relativize repository root for viewer: {}", e.message)
          "."
        }
        val title = "Commit Analysis: $shortSha — ${disp(subject)}".take(200)
        val config = buildString {
          append("{\n  \"src\": ").append(toJson("${base}_graph.json"))
          append(",\n  \"base\": ").append(toJson(repoBase))
          append(",\n  \"title\": ").append(toJson(title))
          append(",\n  \"data\": ").append(graphJson)
          append("\n}")
        }.replace("<", "\\u003c") // '<' only occurs inside JSON strings; prevents </script> breakouts
        val start = template.indexOf(VIEWER_CONFIG_START)
        val end = template.indexOf(VIEWER_CONFIG_END)
        val html = if (start >= 0 && end > start) {
          template.substring(0, start + VIEWER_CONFIG_START.length) + config + template.substring(end)
        } else {
          log.warn("Viewer template has no config markers; writing it uncustomized")
          template
        }
        file.parentFile?.mkdirs()
        file.writeBytes(html.toByteArray(StandardCharsets.UTF_8))
        log.info("Wrote viewer {} (base={}, {} bytes)", file.absolutePath, repoBase, html.length)
        link
      }
    }
  } catch (e: Exception) {
    log.warn("Failed to write viewer", e)
    null
  }


  private fun graphToMap(
    commit: String,
    parent: String,
    subject: String,
    graph: ChangeGraph,
    order: List<Operation>,
    verification: VerificationReport,
    series: SeriesResult,
    itemLinks: Map<String, String?>,
     mismatchCount: Int,
     target: ResolvedTarget
  ): Map<String, Any?> = linkedMapOf(
    "commit" to commit,
    "parent" to parent,
    "subject" to disp(subject),
     "range" to if (target.isRange) linkedMapOf(
       "base" to target.base,
       "tip" to target.tip,
       "symmetric" to target.symmetric,
       "base_is_ancestor" to target.baseIsAncestor,
       "commits" to target.commits.map { linkedMapOf("sha" to it.first, "subject" to it.second) }
     ) else null,
    "exact_reconstruction" to (mismatchCount == 0),
    "operations" to order.map { op ->
      linkedMapOf(
        "id" to op.id,
        "name" to disp(op.name),
        "description" to disp(op.description),
        "category" to op.category,
        "commit" to series.operationCommits[op.id],
        "base_commit" to series.operationBases[op.id],
        "merge_parents" to series.operationParents[op.id],
        "mechanical" to op.mechanical,
        "rule" to op.rule?.let { disp(it) },
        "confidence" to op.confidence,
        "verified" to op.verified,
        "suspicious" to op.suspicious,
        "suspicion_reason" to op.suspicionReason?.let { disp(it) },
        "files" to op.files(),
        "items" to op.items.map { item ->
          linkedMapOf(
            "id" to item.id,
            "kind" to item.kind.name,
            "file" to item.file.displayPath,
            "old_path" to item.file.oldPath,
            "line_start" to item.hunk?.let { it.start + 1 },
            "old_line_count" to item.hunk?.oldLines?.size,
            "new_line_count" to item.hunk?.newLines?.size,
            "fingerprint" to item.fingerprint,
            "commit" to series.itemCommitById[item.id]?.commit,
            "diff" to itemLinks[item.id]?.split('/')?.lastOrNull(),
            "defines" to (item.definedAdded + item.definedRemoved).map { disp(it) },
            "substitution" to item.substitution?.let { listOf(disp(it.first), disp(it.second)) }
          )
        }
      )
    },
    "edges" to graph.edges.map {
      linkedMapOf("before" to it.before, "after" to it.after, "kind" to it.kind, "reason" to disp(it.reason))
    },
    "order" to order.map { it.id },
    "verification" to linkedMapOf(
      "status" to verification.status,
      "steps" to verification.steps,
      "fixes" to verification.fixes
    ),
    "series" to linkedMapOf(
      "layout" to "dag",
      "head" to series.headSha,
      "tree_matches" to series.treeMatches,
      "branch" to series.branchCreated,
      "branch_error" to series.branchError,
      "item_commits" to series.itemCommits.size,
      "merge_commits" to series.mergeCommits
    )
  )

  private fun operationMarkdown(n: Int, op: Operation): String = buildString {
    val flag = if (op.suspicious) " ⚠️" else ""
    val verified = when (op.verified) {
      true -> " ✅"
      false -> " ❌"
      null -> ""
    }
    appendLine("### $n. ${disp(op.name)}$flag$verified")
    appendLine()
    appendLine(
      "`${op.id}` · **${op.category}**${if (op.mechanical) " · mechanical" else ""} · confidence ${
        "%.2f".format(
          op.confidence
        )
      } · ${op.items.size} item(s)"
    )
    appendLine()
    appendLine(disp(op.description))
    op.rule?.let {
      appendLine()
      appendLine("Rule: `${disp(it).take(300).replace("`", "'")}`")
    }
    if (op.suspicious) {
      appendLine()
      appendLine("> ⚠️ Suspicious: ${disp(op.suspicionReason ?: "flagged by analysis")}")
    }
    appendLine()
    op.items.take(25).forEach { item ->
      val loc = item.hunk?.let { ":${it.start + 1}" } ?: ""
      appendLine("- ${item.id} ${item.kind} `${item.file.displayPath}$loc`")
    }
    if (op.items.size > 25) appendLine("- ...and ${op.items.size - 25} more")
  }

  private fun buildReport(
    commit: String,
    parent: String,
    subject: String,
    files: List<FileChange>,
    items: List<ChangeItem>,
    graph: ChangeGraph,
    order: List<Operation>,
    verification: VerificationReport,
    series: SeriesResult,
    notes: List<String>,
     mismatches: List<FileChange>,
     target: ResolvedTarget
  ): String = buildString {
    appendLine("# Commit Analysis Report")
    appendLine()
     appendLine("- **${if (target.isRange) "Tip" else "Commit"}:** `$commit` — ${disp(subject)}")
     appendLine("- **${if (target.isRange) "Base" else "Parent"}:** `$parent`")
     if (target.isRange) {
       appendLine(
         "- **Range:** `${parent.take(10)}..${commit.take(10)}` (${target.commits.size} commit(s)" +
             (if (target.symmetric) ", base = merge-base" else "") +
             (if (!target.baseIsAncestor) ", ⚠️ base is not an ancestor of tip" else "") + ")"
       )
     }
    appendLine("- **Files changed:** ${files.size}")
    appendLine(
      "- **Change items:** ${items.size} (${
        items.groupingBy { it.kind }.eachCount().entries.joinToString(", ") { "${it.key}: ${it.value}" }
      })"
    )
    appendLine("- **Operations:** ${order.size} (${order.count { it.mechanical }} mechanical)")
    appendLine("- **Dependency edges:** ${graph.edges.size}")
    appendLine("- **Exact reconstruction:** ${if (mismatches.isEmpty()) "✅ verified" else "❌ ${mismatches.joinToString { it.displayPath }}"}")
    appendLine("- **Build verification:** ${verification.status}")
    series.treeMatches?.let { appendLine("- **Commit series tree identical to original:** ${if (it) "✅" else "❌"}") }
    series.branchCreated?.let { appendLine("- **Branch:** `$it`") }
    series.headSha?.let { appendLine("- **Item commits:** ${series.itemCommits.size} (series head `$it`)") }
    if (series.mergeCommits.isNotEmpty()) {
      appendLine("- **Merge commits:** ${series.mergeCommits.size} (commit DAG mirrors the dependency graph)")
    }
    appendLine()
     if (target.isRange && target.commits.isNotEmpty()) {
       appendLine("## Commits in Range (oldest first)")
       target.commits.take(200).forEach { (sha, subj) -> appendLine("- `${sha.take(10)}` $subj") }
       if (target.commits.size > 200) appendLine("- _...and ${target.commits.size - 200} more_")
       appendLine()
     }


    val suspicious = order.filter { it.suspicious }
    if (suspicious.isNotEmpty()) {
      appendLine("## ⚠️ Flagged Operations")
      suspicious.forEach { appendLine("- **${it.id} ${disp(it.name)}**: ${disp(it.suspicionReason ?: "flagged")}") }
      appendLine()
    }

    val mechanical = order.filter { it.mechanical }
    if (mechanical.isNotEmpty()) {
      appendLine("## Mechanical Changes (can be approved in bulk)")
      mechanical.forEach {
        appendLine(
          "- **${it.id} ${disp(it.name)}** — ${it.items.size} item(s)${
            it.rule?.let { r ->
              ", rule: `${
                disp(
                  r
                ).take(120).replace("`", "'")
              }`"
            } ?: ""
          }")
      }
      appendLine()
    }

    appendLine("## Operations in Order")
    appendLine()
    order.forEachIndexed { i, op -> appendLine(operationMarkdown(i + 1, op)) }

    appendLine("## Dependencies")
    appendLine()
    if (graph.edges.isEmpty()) appendLine("_No dependencies detected; operations are independent._")
    graph.edges.forEach { appendLine("- ${it.before} → ${it.after} _(${it.kind})_: ${disp(it.reason)}") }
    appendLine()

    if (verification.steps.isNotEmpty() || verification.fixes.isNotEmpty()) {
      appendLine("## Verification Log")
      verification.steps.forEach { appendLine("- $it") }
      if (verification.fixes.isNotEmpty()) {
        appendLine()
        appendLine("### Graph repairs")
        verification.fixes.forEach { appendLine("- ${disp(it)}") }
      }
      appendLine()
    }

    if (series.treeMatches == false) {
      appendLine("## Tree Differences")
      appendLine("```")
      appendLine(series.treeDiff)
      appendLine("```")
      appendLine()
    }

    if (notes.isNotEmpty()) {
      appendLine("## Notes")
      notes.forEach { appendLine("- ${disp(it)}") }
      appendLine()
    }

    appendLine("## Limitations")
    appendLine("- Change items are zero-context diff hunks; no AST-level splitting yet.")
    appendLine("- Formatter-output detection is not performed; only whitespace, reordering and bulk-replace normalization.")
    appendLine("- File mode (permission) changes are not reproduced by content replay.")
  }

  private fun md(s: String): String = MarkdownUtil.renderMarkdown(s)

  companion object {
    private val log: Logger = LoggerFactory.getLogger(CommitAnalyzerTask::class.java)
    private val ISO = StandardCharsets.ISO_8859_1
    private const val VIEWER_RESOURCE = "/viewers/CommitAnalyzer/viewer.html"
    private const val VIEWER_CONFIG_START = "/*CONFIG_START*/"
    private const val VIEWER_CONFIG_END = "/*CONFIG_END*/"

    /**
     * Explicit execution config supplied by a headless driver (e.g. CommitAnalyzerTaskCli).
     * When set, it takes precedence over the config the harness hands to the task, because
     * some harness paths rebuild a default config instance and silently drop user options
     * (repository_path / commit), causing the wrong repo/commit to be analyzed.
     */
    @Volatile
    @JvmStatic
    var executionConfigOverride: CommitAnalyzerTaskExecutionConfigData? = null


    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private val TOKEN_REGEX = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val DEF_REGEXES = listOf(
      Regex("""\b(?:fun|def|class|interface|object|struct|enum|trait|type|typealias|val|var|let|const|function|func|fn|record|module|namespace|macro)\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)"""),
      Regex("""\b(?:public|private|protected|internal|static|final|override|abstract|virtual)\s+(?:[\w<>\[\],.?]+\s+)*([A-Za-z_]\w*)\s*\(""")
    )
    private val KEYWORDS = setOf(
      "abstract", "and", "as", "assert", "async", "await", "boolean", "break", "byte", "case", "catch", "char",
      "class", "const", "continue", "def", "default", "del", "delete", "do", "double", "elif", "else", "enum",
      "except", "export", "extends", "false", "final", "finally", "float", "for", "from", "fun", "func",
      "function", "global", "if", "implements", "import", "in", "instanceof", "int", "interface", "internal",
      "is", "lambda", "let", "long", "new", "nil", "none", "None", "not", "null", "object", "or", "override",
      "package", "pass", "private", "protected", "public", "raise", "return", "self", "short", "static",
      "string", "String", "struct", "super", "switch", "this", "throw", "throws", "true", "True", "False",
      "try", "type", "typeof", "val", "var", "void", "when", "while", "with", "yield", "Int", "Unit", "Any",
      "List", "Map", "Set", "use", "mut", "pub", "impl", "fn", "the", "end", "then"
    )
    private val CATEGORIES = setOf(
      "refactor",
      "feature",
      "fix",
      "test",
      "docs",
      "config",
      "build",
      "cleanup",
      "formatting",
      "generated",
      "change",
      "mixed"
    )
    private val CONFIG_EXTENSIONS = listOf(
      ".yml",
      ".yaml",
      ".json",
      ".toml",
      ".properties",
      ".xml",
      ".ini",
      ".cfg",
      ".conf",
      ".gradle",
      ".gradle.kts",
      ".editorconfig"
    )
    private val GENERATED_NAMES = setOf(
      "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "Cargo.lock", "go.sum", "poetry.lock",
      "Gemfile.lock", "composer.lock", "Pipfile.lock", "gradle.lockfile", "flake.lock"
    )

    fun isGeneratedPath(p: String): Boolean {
      val name = p.substringAfterLast('/')
      return name in GENERATED_NAMES ||
          name.endsWith(".lock") ||
          name.contains(".generated.") ||
          name.endsWith(".min.js") ||
          name.endsWith(".pb.go") ||
          p.split('/').any { it == "vendor" || it == "node_modules" || it == "generated" }
    }

    fun tokens(text: String): Set<String> = TOKEN_REGEX.findAll(text).map { it.value }.toHashSet()

    fun definitions(text: String): Set<String> {
      if (text.isEmpty()) return emptySet()
      val out = HashSet<String>()
      DEF_REGEXES.forEach { r -> r.findAll(text).forEach { m -> out.add(m.groupValues[1]) } }
      return out.filter { it !in KEYWORDS }.toSet()
    }

    fun isSignificant(t: String): Boolean = t.length >= 3 && t !in KEYWORDS

    fun jaccard(a: Set<String>, b: Set<String>): Double {
      if (a.isEmpty() || b.isEmpty()) return 0.0
      val inter = a.count { it in b }
      return inter.toDouble() / (a.size + b.size - inter)
    }

    fun stripWs(s: String): String = s.filterNot { it.isWhitespace() }

    fun isBinary(s: String): Boolean = s.take(8000).contains('\u0000')

    private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    fun isReorder(h: Hunk): Boolean {
      if (h.oldLines.size != h.newLines.size || h.oldLines.size < 2) return false
      val o = h.oldLines.map { it.trimEnd() }
      val n = h.newLines.map { it.trimEnd() }
      return o != n && o.sorted() == n.sorted()
    }

    /** Detects a single word-bounded literal substitution a -> b that explains every line pair of the hunk. */
    fun substitutionOf(h: Hunk): Pair<String, String>? {
      if (h.oldLines.size != h.newLines.size || h.oldLines.isEmpty()) return null
      val o = h.oldLines[0]
      val n = h.newLines[0]
      var p = 0
      while (p < o.length && p < n.length && o[p] == n[p]) p++
      var s = 0
      while (s < o.length - p && s < n.length - p && o[o.length - 1 - s] == n[n.length - 1 - s]) s++
      while (p > 0 && isIdentChar(o[p - 1])) p--
      while (s > 0 && isIdentChar(o[o.length - s])) s--
      if (p > o.length - s || p > n.length - s) return null
      val a = o.substring(p, o.length - s)
      val b = n.substring(p, n.length - s)
      if (a.isBlank() || a.length > 200 || b.length > 200 || a.contains('\n') || b.contains('\n')) return null
      return if (h.oldLines.indices.all { h.oldLines[it].replace(a, b) == h.newLines[it] }) a to b else null
    }

    /** Parses `git diff -U0` output (decoded as ISO-8859-1) into hunks whose lines keep their terminators. */
    fun parseHunks(diff: String): List<Hunk> {
      val lines = diff.split('\n')
      val hunks = mutableListOf<Hunk>()
      var header: MatchResult? = null
      var old = mutableListOf<String>()
      var new = mutableListOf<String>()
      var lastList: MutableList<String>? = null
      fun finish() {
        val m = header ?: return
        val oldStart = m.groupValues[1].toInt()
        val oldCount = m.groupValues[2].ifEmpty { "1" }.toInt()
        val newStart = m.groupValues[3].toInt()
        val start = if (oldCount == 0) oldStart else oldStart - 1
        hunks.add(Hunk(start, oldCount, newStart, old, new))
      }
      for (line in lines) {
        if (line.startsWith("@@")) {
          finish()
          header = HUNK_HEADER.find(line) ?: throw IllegalStateException("Malformed hunk header: $line")
          old = mutableListOf()
          new = mutableListOf()
          lastList = null
          continue
        }
        if (header == null || line.isEmpty()) continue
        when (line[0]) {
          '-' -> {
            old.add(line.substring(1) + "\n"); lastList = old
          }

          '+' -> {
            new.add(line.substring(1) + "\n"); lastList = new
          }

          '\\' -> lastList?.let { l -> if (l.isNotEmpty()) l[l.size - 1] = l.last().removeSuffix("\n") }
          else -> {}
        }
      }
      finish()
      hunks.forEach { h ->
        if (h.oldLines.size != h.oldCount) throw IllegalStateException("Hunk line count mismatch")
      }
      return hunks
    }

    fun splitKeepingTerminators(s: String): List<String> {
      val out = ArrayList<String>()
      var start = 0
      for (i in s.indices) {
        if (s[i] == '\n') {
          out.add(s.substring(start, i + 1))
          start = i + 1
        }
      }
      if (start < s.length) out.add(s.substring(start))
      return out
    }

    /** Applies any subset of zero-context hunks to [base]; hunks never overlap, so every subset applies cleanly. */
    fun applyHunks(base: String, hunks: List<Hunk>): String {
      if (hunks.isEmpty()) return base
      val lines = splitKeepingTerminators(base)
      val sb = StringBuilder(base.length + 256)
      var pos = 0
      for (h in hunks.sortedBy { it.start }) {
        if (h.start < pos || h.start + h.oldCount > lines.size) throw IllegalStateException("Hunk out of range")
        for (k in pos until h.start) sb.append(lines[k])
        h.newLines.forEach { sb.append(it) }
        pos = h.start + h.oldCount
      }
      for (k in pos until lines.size) sb.append(lines[k])
      return sb.toString()
    }

    /** Returns (path, content) for a file given a set of applied change items, or null if the file is absent. */
    fun fileState(f: FileChange, applied: Set<ChangeItem>): Pair<String, String>? {
      val whole = f.wholeFileItem
      val move = f.moveItem
      return when (f.status) {
        'A' -> if (whole != null && whole in applied) f.newPath!! to f.newContent!! else null
        'D' -> if (whole != null && whole in applied) null else f.oldPath!! to f.oldContent!!
        else -> {
          val path = if (move != null && move in applied) f.newPath!! else f.oldPath!!
          val content = if (whole != null) {
            if (whole in applied) f.newContent!! else f.oldContent!!
          } else {
            applyHunks(f.oldContent!!, f.hunkItems.filter { it in applied }.mapNotNull { it.hunk })
          }
          path to content
        }
      }
    }

    /** Converts byte-preserving ISO-8859-1 text to UTF-8 for display. */
    fun disp(s: String): String = String(s.toByteArray(ISO), StandardCharsets.UTF_8)

    private fun clip(s: String): String = disp(s).trimEnd().take(140)

    fun slug(s: String): String =
      disp(s).lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50).ifBlank { "op" }

    fun sha1(s: String): String =
      MessageDigest.getInstance("SHA-1").digest(s.toByteArray(ISO)).joinToString("") { "%02x".format(it) }

    private fun quote(s: String): String = buildString {
      append('"')
      s.forEach { c ->
        when {
          c == '"' -> append("\\\"")
          c == '\\' -> append("\\\\")
          c == '\n' -> append("\\n")
          c == '\r' -> append("\\r")
          c == '\t' -> append("\\t")
          c < ' ' -> append("\\u%04x".format(c.code))
          else -> append(c)
        }
      }
      append('"')
    }

    fun toJson(v: Any?, sb: StringBuilder = StringBuilder(), indent: String = ""): StringBuilder {
      when (v) {
        null -> sb.append("null")
        is String -> sb.append(quote(v))
        is Double -> sb.append(if (v.isNaN() || v.isInfinite()) "null" else v.toString())
        is Number, is Boolean -> sb.append(v.toString())
        is Map<*, *> -> {
          if (v.isEmpty()) sb.append("{}") else {
            sb.append("{\n")
            v.entries.forEachIndexed { i, (k, value) ->
              sb.append(indent).append("  ").append(quote(k.toString())).append(": ")
              toJson(value, sb, "$indent  ")
              if (i < v.size - 1) sb.append(",")
              sb.append("\n")
            }
            sb.append(indent).append("}")
          }
        }

        is Iterable<*> -> {
          val list = v.toList()
          if (list.isEmpty()) sb.append("[]") else {
            sb.append("[\n")
            list.forEachIndexed { i, value ->
              sb.append(indent).append("  ")
              toJson(value, sb, "$indent  ")
              if (i < list.size - 1) sb.append(",")
              sb.append("\n")
            }
            sb.append(indent).append("]")
          }
        }

        else -> sb.append(quote(v.toString()))
      }
      return sb
    }

    @JvmStatic
    val CommitAnalyzer = TaskType(
      name = "CommitAnalyzer",
      category = "Code",
      taskClass = CommitAnalyzerTask::class.java,
      executionConfigClass = CommitAnalyzerTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Explode a monolithic commit into an ordered graph of atomic operations",
      tooltipHtml = """
                        Decomposes a single large or messy commit into an ordered DAG of atomic operations.
                        <ul>
                          <li>Normalizes whitespace, line reordering, bulk replacements and file moves</li>
                          <li>Clusters related hunks with token similarity and symbol relationships</li>
                          <li>LLM refinement and naming of operations (optional)</li>
                          <li>Definition-before-use and removal-after-last-use dependency detection</li>
                          <li>Optional build verification of every intermediate state with automatic graph repair</li>
                         <li>Emits change graph JSON, review report, patches, an interactive HTML viewer and a commit-series branch</li>
                          <li>Guarantees byte-exact reconstruction of the original commit contents</li>
                        </ul>
                      """,
    )
  }
}