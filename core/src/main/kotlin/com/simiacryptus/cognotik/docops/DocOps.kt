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
 */
class DocOps<K : DocTaskKind, S : Any>(
  val config: DocOpsConfig,
  val host: DocOpsHost<K, S>,
  val statusStore: DocStatusStore = JsonFileDocStatusStore(config.root),
  val loader: DocSpecLoader = MarkdownDocSpecLoader(config.templateVarOverrides),
  val resources: ResourceResolver = defaultResources(config),
  val planner: DocPlanner<K> = defaultPlanner(config, host.taskKinds),
  val runner: DocTaskRunner<K, S> = DocTaskRunner(config, host, statusStore),
) {

  /** Every `*.md` / `*.markdown` file under [DocOpsConfig.docsFolder]. */
  fun markdownFiles(): List<File> = config.docsFolder.listFilesRecursively()
    .filter { it.isFile && it.extension.lowercase() in config.markdownExtensions }
    .also { log.info("Found ${it.size} markdown file(s) in ${config.docsFolder.absolutePath}") }

  fun load(files: Iterable<File>): List<DocSpec> = loader.loadAll(files)

  fun newResolveContext(): ResolveContext = ResolveContext(config.root, resources)

  /** Plan every document under `docsFolder` (or an explicit subset). Side-effect free. */
  fun plan(files: Iterable<File> = markdownFiles()): WorkPlan<K> = planSpecs(load(files))

  fun plan(vararg files: File): WorkPlan<K> = plan(files.toList())

  fun planSpecs(specs: List<DocSpec>): WorkPlan<K> = planner.plan(specs, newResolveContext())

  /** Seed the status file without executing anything. */
  fun initializeStatus(plan: WorkPlan<K>) = runner.initializeStatus(plan)

  fun run(
    plan: WorkPlan<K> = plan(),
    scheduler: DocTaskScheduler = host.newScheduler(),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (S) -> Unit = { },
  ): List<S> = runner.run(plan, scheduler, cancelFlag, onNewSession)

  /** Declared `{{ VAR }}` defaults across the given docs (useful for prompting a UI). */
  fun templateVarKeys(files: Iterable<File> = markdownFiles()): Map<String, String> =
    TemplateEngine.listKeys(files)

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