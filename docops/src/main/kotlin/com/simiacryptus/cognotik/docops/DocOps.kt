package com.simiacryptus.cognotik.docops

import com.simiacryptus.cognotik.docops.exec.DocTaskKind
import com.simiacryptus.cognotik.docops.exec.DocTaskKindResolver
import com.simiacryptus.cognotik.docops.exec.DocTaskRunner
import com.simiacryptus.cognotik.docops.exec.DocTaskScheduler
import com.simiacryptus.cognotik.docops.model.DocSpec
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.docops.plan.DocPlanner
import com.simiacryptus.cognotik.docops.plan.RelatedFileCollector
import com.simiacryptus.cognotik.docops.plan.ResolveContext
import com.simiacryptus.cognotik.docops.plan.TaskBuilder
import com.simiacryptus.cognotik.docops.plan.policy.ContextMessageComposer
import com.simiacryptus.cognotik.docops.plan.policy.RootPolicy
import com.simiacryptus.cognotik.docops.plan.policy.TaskConfigPolicy
import com.simiacryptus.cognotik.docops.plan.policy.TaskDescriptionComposer
import com.simiacryptus.cognotik.docops.plan.policy.TaskKindPolicy
import com.simiacryptus.cognotik.docops.plan.policy.UpdateModePolicy
import com.simiacryptus.cognotik.docops.resolve.CompositeResourceResolver
import com.simiacryptus.cognotik.docops.resolve.FileResourceResolver
import com.simiacryptus.cognotik.docops.resolve.ResourceResolver
import com.simiacryptus.cognotik.docops.resolve.UrlCache
import com.simiacryptus.cognotik.docops.resolve.UrlResourceResolver
import com.simiacryptus.cognotik.docops.spec.DocSpecLoader
import com.simiacryptus.cognotik.docops.spec.MarkdownDocSpecLoader
import com.simiacryptus.cognotik.docops.spec.TemplateEngine
import com.simiacryptus.cognotik.docops.status.DocStatusStore
import com.simiacryptus.cognotik.docops.status.JsonFileDocStatusStore
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursivelyBy
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single public entry point of the doc-ops engine, replacing `DocProcessorBase`.
 *
 * Hosts no longer subclass anything: they supply a [DocOpsConfig] (data) and a [DocOpsHost]
 * (three platform bindings). Every collaborator is injectable, so tests can swap the status
 * store, the HTTP fetcher, the loader or the planner without touching the network or disk.
 *
 * [plan] is pure — it never mutates the workspace. All destructive side effects (deleting a
 * target, writing status) happen inside [run].
 *
 * NOTE: every injectable collaborator is resolved **lazily**. Hosts commonly construct
 * `DocOps(host = this)` from a property initializer, which means the host is only partially
 * constructed at that point (`taskKinds` and friends may still be null). Deferring the default
 * wiring to first use makes `DocOps` immune to host field-declaration order.
 */
class DocOps<K : DocTaskKind, S : Any>(
  val config: DocOpsConfig,
  val host: DocOpsHost<K, S>,
  statusStore: DocStatusStore,
  loader: DocSpecLoader? = null,
  resources: ResourceResolver? = null,
  planner: DocPlanner<K>? = null,
  runner: DocTaskRunner<K, S>? = null,
) {
  private val statusStoreOverride: DocStatusStore = statusStore
  private val loaderOverride: DocSpecLoader? = loader
  private val resourcesOverride: ResourceResolver? = resources
  private val plannerOverride: DocPlanner<K>? = planner
  private val runnerOverride: DocTaskRunner<K, S>? = runner
  val statusStore: DocStatusStore by lazy {
    statusStoreOverride ?: JsonFileDocStatusStore(config.root)
  }
  val loader: DocSpecLoader by lazy {
    loaderOverride ?: MarkdownDocSpecLoader(config.templateVarOverrides)
  }
  val resources: ResourceResolver by lazy {
    resourcesOverride ?: defaultResources(config)
  }
  val planner: DocPlanner<K> by lazy {
    plannerOverride ?: defaultPlanner(config, hostTaskKinds())
  }
  val runner: DocTaskRunner<K, S> by lazy {
    runnerOverride ?: DocTaskRunner(config, host, statusStore)
  }

  /**
   * Reads [DocOpsHost.taskKinds] defensively: hosts that leak `this` from a field initializer
   * can (and did) hand us a null here, which used to surface as an opaque Kotlin intrinsic
   * failure ("Parameter specified as non-null is null: ... parameter kinds").
   */
  @Suppress("USELESS_ELVIS")
  private fun hostTaskKinds(): DocTaskKindResolver<K> = host.taskKinds
    ?: throw IllegalStateException(
      "DocOpsHost.taskKinds is null for ${host.javaClass.name}. " +
          "This usually means DocOps was constructed from a property initializer that runs " +
          "before 'taskKinds' is assigned - declare 'taskKinds' before the DocOps field, " +
          "make it a 'get()' property, or pass an explicit planner."
    )

  /** Every `*.md` / `*.markdown` file under [DocOpsConfig.docsFolder]. */

  fun markdownFiles(): List<File> {
    val docsFolder = config.docsFolder
    if (!docsFolder.exists()) {
      log.warn("Docs folder does not exist: ${docsFolder.absolutePath} - no targets can be matched")
      return emptyList()
    }
    val all = docsFolder.listFilesRecursivelyBy().filter { it.isFile }
    val (markdown, other) = all.partition { it.extension.lowercase() in config.markdownExtensions }
    log.info(
      "Scanning ${docsFolder.absolutePath}: ${all.size} file(s) -> ${markdown.size} markdown file(s) " +
          "(extensions=${config.markdownExtensions}); ignored ${other.size} non-markdown file(s)"
    )
    if (markdown.isEmpty()) {
      log.warn("No markdown files found under ${docsFolder.absolutePath}; nothing can be planned.")
    } else {
      markdown.forEach { log.info("  doc candidate: ${it.absolutePath}") }
    }
    return markdown
  }

  fun load(files: Iterable<File>): List<DocSpec> {
    val candidates = files.toList()
    val specs = loader.loadAll(candidates)
    log.info("Loaded ${specs.size} doc spec(s) from ${candidates.size} candidate file(s)")
    if (specs.isEmpty() && candidates.isNotEmpty()) {
      log.warn(
        "None of the ${candidates.size} candidate file(s) declared any doc-ops targets " +
            "(specifies:/transforms:/documents:/generates:/folder:) - see per-file messages above."
      )
    }
    specs.forEach { spec ->
      log.info(
        "  spec ${spec.docFile.absolutePath}: baseDir=${spec.baseDir.absolutePath}, " +
            "specifies=${spec.specifies}, " +
            "transforms=${spec.transforms.map { "${it.sourcePattern} -> ${it.destinationPattern}" }}, " +
            "documents=${spec.documents}, generates=${spec.generates.map { it.output }}, " +
            "folder=${spec.targetFolder}, related=${spec.related}, taskType=${spec.taskType ?: "<default>"}"
      )
    }
    return specs
  }

  fun newResolveContext(): ResolveContext = ResolveContext(
    root = config.root,
    resources = resources,
    rawLister = { it: File -> it.listFilesRecursivelyBy() })

  /** Plan every document under `docsFolder` (or an explicit subset). Side-effect free. */
  fun plan(files: Iterable<File> = markdownFiles()): WorkPlan<K> = planSpecs(load(files))

  fun plan(vararg files: File): WorkPlan<K> = plan(files.toList())

  fun planSpecs(specs: List<DocSpec>): WorkPlan<K> {
    log.info(
      "Planning ${specs.size} doc spec(s); root=${config.root.absolutePath}, " +
          "docsFolder=${config.docsFolder.absolutePath}, updateMode=${config.updateMode}"
    )
    val plan = planner.plan(specs, newResolveContext())
    if (plan.isEmpty) {
      log.warn(
        "Plan is EMPTY: 0 task(s) from ${specs.size} spec(s) (skipped=${plan.skipped.size}, failed=${plan.failed.size}). " +
            "Verify the declared patterns resolve to files under ${config.root.absolutePath}."
      )
      plan.skipped.forEach { log.warn("  skipped ${it.target}: ${it.reason}") }
      plan.failed.forEach { log.warn("  failed ${it.target}: ${it.error.message ?: it.error.javaClass.simpleName}") }
    }
    return plan
  }

  /** Seed the status file without executing anything. */
  fun initializeStatus(plan: WorkPlan<K>) = runner.initializeStatus(plan)

  fun run(
    plan: WorkPlan<K> = plan(),
    scheduler: DocTaskScheduler = host.newScheduler(),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (S) -> Unit = { },
  ): List<S> = runner.run(plan, scheduler, cancelFlag, onNewSession)

  companion object {
    private val log = LoggerFactory.getLogger(DocOps::class.java)

    fun defaultResources(config: DocOpsConfig): ResourceResolver = CompositeResourceResolver(
      listOf(
        UrlResourceResolver(UrlCache(config.urlCacheDir, config.urlCacheTtl)),
        FileResourceResolver(),
      )
    )

    fun <K : DocTaskKind> defaultTaskBuilder(
      config: DocOpsConfig,
      kinds: DocTaskKindResolver<K>,
    ): TaskBuilder<K> = TaskBuilder(
      root = config.root,
      updateModePolicy = UpdateModePolicy(config.updateMode),
      taskKindPolicy = TaskKindPolicy(kinds),
      rootPolicy = RootPolicy(config.root),
      taskConfigPolicy = TaskConfigPolicy(),
      descriptions = TaskDescriptionComposer(),
      messages = ContextMessageComposer(),
      related = RelatedFileCollector(config.additionalContext),
    )

    fun <K : DocTaskKind> defaultPlanner(
      config: DocOpsConfig,
      kinds: DocTaskKindResolver<K>,
    ): DocPlanner<K> = DocPlanner(
      taskBuilder = defaultTaskBuilder(config, kinds),
      maxDepth = config.maxPlanningDepth,
    )
  }
}