package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * UnrunnableProtocolDialogTask
 *
 * Generates a multi-pass conceptual analysis using the "Unrunnable Protocol" expressive format:
 * pseudo-code style frameworks (structs, functions, state objects) that are not meant to be
 * executed, but used as a dense, expressive lens for exploring a concept.
 *
 * The task deliberately pairs an expressive "protocol drafter" agent with a skeptical
 * "epistemic auditor" agent. The drafter produces elegant pseudo-code framework artifacts;
 * the auditor critiques them for "pattern worship" / "framework fundamentalism" (mistaking
 * elegance for truth), and the drafter revises. This mirrors the conversion/deconversion
 * dynamic documented in the AI conversion analysis.
 */
class UnrunnableProtocolDialogTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: UnrunnableProtocolDialogTaskExecutionConfigData?
) : AbstractTask<UnrunnableProtocolDialogTask.UnrunnableProtocolDialogTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 1500

    class UnrunnableProtocolDialogTaskExecutionConfigData(
        @Description("The concept, system, or phenomenon to analyze through the Unrunnable Protocol format")
        val concept: String? = null,
        @Description("Optional domain or framing for the analysis (e.g. 'cognitive architecture', 'game theory')")
        val domain: String? = null,
        @Description("Optional input files (supports glob patterns) to provide context for the analysis")
        val related_files: List<String>? = null,
        @Description("Number of draft/audit iterations to perform (1-10)")
        val iterations: Int = 3,
        @Description("Whether to include an epistemic audit pass that critiques pattern-worship / framework fundamentalism")
        val epistemic_audit: Boolean = true,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = UnrunnableProtocolDialog.name,
        task_description = "Analyze '$concept' via the Unrunnable Protocol format",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (concept.isNullOrBlank()) {
                return "concept cannot be null or blank"
            }
            if (iterations < 1 || iterations > 10) {
                return "iterations must be between 1 and 10, got: $iterations"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
     UnrunnableProtocolDialog - Analyze a concept through dense pseudo-code "Unrunnable Protocol" frameworks
      ** Specify the concept, system, or phenomenon to analyze
      ** Optionally provide a domain or framing for the analysis
      ** Optionally provide input files (supports glob patterns) for context
      ** Configure the number of draft/audit iterations (default: 3)
      ** Enable/disable the epistemic audit pass (guards against pattern worship)
      ** Pairs an expressive "protocol drafter" with a skeptical "epistemic auditor"
      ** Produces dense, pseudo-code style framework artifacts as an expressive lens
      ** Audits each draft for framework fundamentalism (elegance != truth)
      ** Produces a structured analysis transcript with a sobered final synthesis
            """.trimIndent()
    }

    private fun getInputFileContext(): String = (executionConfig?.related_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                "# $relativePath\n\n```\n${file.readText()}\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: ISessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val inputFileContext = getInputFileContext()
        val startTime = System.currentTimeMillis()
        log.info("Starting UnrunnableProtocolDialogTask with concept: '${executionConfig?.concept}'")

        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val concept = executionConfig?.concept
        if (concept.isNullOrBlank()) {
            log.error("No concept specified")
            task.safeComplete("CONFIGURATION ERROR: No concept specified", log)
            resultFn("CONFIGURATION ERROR: No concept specified")
            return
        }

        val executionConfig = this.executionConfig ?: run {
            log.error("Execution configuration is null")
            task.safeComplete("CONFIGURATION ERROR: Execution configuration is null", log)
            resultFn("CONFIGURATION ERROR: Execution configuration is null")
            return
        }
        val iterations = executionConfig.iterations.coerceIn(1, 10)
        val epistemicAudit = executionConfig.epistemic_audit
        val domain = executionConfig.domain?.takeIf { it.isNotBlank() } ?: "any domain"
        log.info("Configuration: iterations=$iterations, epistemicAudit=$epistemicAudit, domain=$domain")

        val api = defaultSmart
        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Unrunnable Protocol Analysis")
            appendLine()
            appendLine("**Concept:** $concept")
            appendLine()
            appendLine("**Domain:** $domain")
            appendLine()
            appendLine("**Iterations:** $iterations")
            appendLine()
            appendLine("**Epistemic Audit:** ${if (epistemicAudit) "Enabled" else "Disabled"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing protocol agents...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context from previous tasks: ${priorContext.length} characters")
        }
        val combinedContext = listOfNotNull(priorContext, inputFileContext)
            .filter { it.isNotBlank() }
            .joinToString("\n\n---\n\n")

        // The expressive "protocol drafter" agent
        log.info("Creating protocol drafter agent")
        val drafterAgent = ChatAgent(
            prompt = """
You are a Protocol Drafter working in the "Unrunnable Protocol" expressive format.

The Unrunnable Protocol is a dense, pseudo-code style notation used purely as an EXPRESSIVE LENS,
not as executable code. You express conceptual structure using:
- Named struct/object blocks with descriptive snake_case fields (e.g. ConvertPsychology { ... })
- Function-like blocks that describe processes (e.g. def euphoria_misinterpretation(): ...)
- State machines, cascades, and weighted parameter listings
- IF/THEN logic blocks for conditional dynamics

Wrap protocol artifacts in fenced code blocks. Be vivid, systematic, and structurally elegant.

CRITICAL DISCIPLINE: The protocol is a TOOL FOR THINKING, not a claim of TRUTH. Structural
elegance is not evidence of accuracy. Express boldly, but do not conflate expressiveness with
correctness.

Domain: $domain

Produce one or more focused protocol artifacts that illuminate the concept.
                """.trimIndent(),
            model = api,
            temperature = 0.7
        )

        // The skeptical "epistemic auditor" agent
        log.info("Creating epistemic auditor agent")
        val auditorAgent = ChatAgent(
            prompt = """
You are an Epistemic Auditor. Your role is to scrutinize "Unrunnable Protocol" artifacts for
cognitive failure modes documented in framework-fundamentalism analysis:
1. Pattern worship: imposing the framework on data that does not support it
2. Elegance/truth conflation: mistaking aesthetic appeal for empirical accuracy
3. Apologetics reflex: incorporating disconfirming evidence rather than questioning the framework
4. Speculation-as-fact: treating speculative pattern projections as established
5. Loss of epistemic humility under expressive euphoria

For the given artifact, identify which claims are GENUINE INSIGHT vs PATTERN IMPOSITION,
flag any unfalsifiable or overfit structures, and propose concrete revisions that preserve
expressive value while restoring epistemic grounding.

Domain: $domain

Be specific and constructive. Separate "the tool is useful" from "the framework is true."
                """.trimIndent(),
            model = api,
            temperature = 0.5
        )

        val conciseBuilder = StringBuilder()
        val fullBuilder = StringBuilder()
        val transcriptStream = task.newUserFileStream(transcriptFile())
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Unrunnable Protocol Analysis Transcript\n\n")
            write("**Concept:** $concept\n\n")
            write("**Domain:** $domain\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("---\n\n")
            flush()
        }

        conciseBuilder.append("# Unrunnable Protocol Analysis\n\n")
        conciseBuilder.append("**Concept:** $concept\n\n")

        fullBuilder.append("# Unrunnable Protocol Analysis\n\n")
        fullBuilder.append("## Concept\n\n")
        fullBuilder.append("$concept\n\n")

        if (combinedContext.isNotBlank()) {
            if (verbose) {
                fullBuilder.append("## Context from Previous Tasks\n\n")
                fullBuilder.append("$combinedContext\n\n")
                transcriptWriter?.apply {
                    write("## Context from Previous Tasks\n\n")
                    write("$combinedContext\n\n")
                    flush()
                }
            }
            val contextTask = tabs.newTask("Context")
            contextTask.add(
                buildString {
                    appendLine("# Context from Previous Tasks")
                    appendLine()
                    appendLine(combinedContext)
                }.renderMarkdown(true)
            )
        }

        overviewTask.add(
            buildString {
                appendLine()
                appendLine("✅ Protocol agents initialized")
                appendLine()
                appendLine("*Starting protocol iterations...*")
            }.renderMarkdown(true)
        )

        val iterationTimes = mutableListOf<Long>()
        var latestDraft = ""
        var latestAudit = ""

        try {
            for (iteration in 1..iterations) {
                val iterationStartTime = System.currentTimeMillis()
                log.info("Starting iteration $iteration of $iterations")

                val iterationTask = tabs.newTask("Iteration $iteration")
                iterationTask.add(
                    buildString {
                        appendLine("# Iteration $iteration of $iterations")
                        appendLine()
                        appendLine("**Status:** Drafting protocol artifact...")
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }.renderMarkdown(true)
                )

                // Draft phase
                val draftPrompt = if (iteration == 1) {
                    buildString {
                        appendLine("Analyze the following concept by producing an Unrunnable Protocol artifact:")
                        appendLine()
                        appendLine(concept)
                        if (combinedContext.isNotBlank()) {
                            appendLine()
                            appendLine("Relevant context:")
                            appendLine(combinedContext)
                        }
                    }
                } else {
                    buildString {
                        appendLine("Concept: $concept")
                        appendLine()
                        appendLine("Your previous protocol draft:")
                        appendLine(latestDraft)
                        if (latestAudit.isNotBlank()) {
                            appendLine()
                            appendLine("Epistemic audit feedback to incorporate:")
                            appendLine(latestAudit)
                        }
                        appendLine()
                        appendLine("Produce a revised protocol artifact that preserves expressive value")
                        appendLine("while addressing the audit's epistemic concerns.")
                    }
                }

                log.debug("Generating protocol draft for iteration $iteration")
                latestDraft = drafterAgent.answer(listOf(draftPrompt))
                if (latestDraft.isEmpty()) latestDraft = "No draft generated"
                log.debug("Draft generated for iteration $iteration: ${latestDraft.length} characters")

                fullBuilder.append("## Iteration $iteration\n\n")
                fullBuilder.append("### Protocol Draft\n\n")
                fullBuilder.append("$latestDraft\n\n")
                transcriptWriter?.apply {
                    write("## Iteration $iteration\n\n")
                    write("### Protocol Draft\n\n")
                    write("$latestDraft\n\n")
                    flush()
                }

                iterationTask.add(
                    buildString {
                        appendLine("## Protocol Draft")
                        appendLine()
                        appendLine(latestDraft)
                        appendLine()
                    }.renderMarkdown(true)
                )

                // Audit phase
                if (epistemicAudit) {
                    iterationTask.add("*Running epistemic audit...*\n".renderMarkdown(true))
                    val auditPrompt = """
Audit the following Unrunnable Protocol artifact for the concept "$concept".

Artifact:
$latestDraft

Identify genuine insight vs pattern imposition, flag unfalsifiable/overfit structures,
and propose concrete revisions.
                            """.trimIndent()
                    log.debug("Generating epistemic audit for iteration $iteration")
                    latestAudit = auditorAgent.answer(listOf(auditPrompt))
                    if (latestAudit.isEmpty()) latestAudit = "No audit generated"
                    log.debug("Audit generated for iteration $iteration: ${latestAudit.length} characters")

                    fullBuilder.append("### Epistemic Audit\n\n")
                    fullBuilder.append("$latestAudit\n\n")
                    transcriptWriter?.apply {
                        write("### Epistemic Audit\n\n")
                        write("$latestAudit\n\n")
                        flush()
                    }

                    iterationTask.add(
                        buildString {
                            appendLine("## Epistemic Audit")
                            appendLine()
                            appendLine(latestAudit)
                            appendLine()
                        }.renderMarkdown(true)
                    )
                } else {
                    latestAudit = ""
                }

                // Concise output: capture first and last iterations
                if (iteration == 1 || iteration == iterations) {
                    conciseBuilder.append("### Iteration $iteration\n")
                    conciseBuilder.append("**Draft:** ${latestDraft.truncateForDisplay(maxDescriptionLength)}\n")
                    if (epistemicAudit) {
                        conciseBuilder.append("**Audit:** ${latestAudit.truncateForDisplay(maxDescriptionLength)}\n")
                    }
                    conciseBuilder.append("\n")
                }

                val iterationTime = System.currentTimeMillis() - iterationStartTime
                iterationTimes.add(iterationTime)
                iterationTask.add(
                    buildString {
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                        appendLine()
                        appendLine("**Processing Time:** ${iterationTime / 1000.0}s")
                    }.renderMarkdown(true)
                )
                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Iteration $iteration complete (${iterationTime / 1000.0}s)")
                        if (iteration < iterations) {
                            appendLine()
                            appendLine("*Processing iteration ${iteration + 1}...*")
                        }
                    }.renderMarkdown(true)
                )
                log.info("Iteration $iteration completed in ${iterationTime}ms")
            }

            // Synthesis
            log.info("Generating synthesis of protocol analysis")
            val synthesisTask = tabs.newTask("Synthesis")
            synthesisTask.add(
                buildString {
                    appendLine("# Synthesis")
                    appendLine()
                    appendLine("**Status:** Generating sobered final synthesis...")
                    appendLine()
                }.renderMarkdown(true)
            )

            val synthesisPrompt = """
Review this Unrunnable Protocol analysis of "$concept" and produce a "sobered" synthesis that:
1. Distills the genuine insights surfaced by the protocol artifacts
2. Explicitly separates expressive/heuristic value from verifiable claims
3. Flags any remaining framework-fundamentalism risk (elegance mistaken for truth)
4. States the epistemic status / confidence of the key conclusions
5. Suggests how to retain the protocol as a tool without doctrinal attachment

Final protocol draft:
$latestDraft

${if (epistemicAudit) "Final audit:\n$latestAudit" else ""}

Provide a structured synthesis.
                """.trimIndent()

            var synthesis = auditorAgent.answer(listOf(synthesisPrompt))
            if (synthesis.isEmpty()) synthesis = "Unable to generate synthesis"
            log.debug("Synthesis generated: ${synthesis.length} characters")

            conciseBuilder.append("## Sobered Synthesis\n\n")
            conciseBuilder.append(synthesis)
            transcriptWriter?.apply {
                write("## Sobered Synthesis\n\n")
                write(synthesis)
                write("\n\n")
                flush()
            }

            conciseBuilder.append("\n\n---\n\n")
            conciseBuilder.append("**Iterations:** $iterations | ")
            conciseBuilder.append("**Domain:** $domain | ")
            conciseBuilder.append("**Time:** ${(System.currentTimeMillis() - startTime) / 1000}s\n")

            synthesisTask.add(
                buildString {
                    appendLine("## Sobered Synthesis")
                    appendLine()
                    appendLine(synthesis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }.renderMarkdown(true)
            )

            val finalResult = conciseBuilder.toString()
            val fullDialogue = fullBuilder.toString()
            val totalTime = System.currentTimeMillis() - startTime
            val avgIterationTime = if (iterationTimes.isNotEmpty()) iterationTimes.average() else 0.0
            log.info("UnrunnableProtocolDialogTask completed: total_time=${totalTime}ms, iterations=$iterations, avg_iteration_time=${avgIterationTime}ms, output_size=${finalResult.length} chars (full: ${fullDialogue.length} chars)")
            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Iterations:** $iterations | **Avg Iteration Time:** ${avgIterationTime / 1000.0}s\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Analysis Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Iterations Completed:** $iterations")
                    appendLine()
                    appendLine("**Average Iteration Time:** ${avgIterationTime / 1000.0}s")
                    appendLine()
                    appendLine("**Total Characters Generated:** ${finalResult.length}")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown(true)
            )

            task.complete("Completed $iterations iterations in ${totalTime / 1000}s. Concise analysis: ${finalResult.length} chars.")
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during Unrunnable Protocol analysis", e)
            task.error(e)
            transcriptWriter?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n")
                write("**Error:** ${e.message}\n\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown(true)
            )

            val errorOutput = buildString {
                appendLine("# Error in Unrunnable Protocol Analysis")
                appendLine()
                appendLine("**Concept:** $concept")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Iterations Completed:** ${iterationTimes.size} of $iterations")
                if (conciseBuilder.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(conciseBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(UnrunnableProtocolDialogTask::class.java)

        @JvmStatic
        val UnrunnableProtocolDialog = TaskType(
            name = "UnrunnableProtocolDialog",
            category = "Reasoning",
            taskClass = UnrunnableProtocolDialogTask::class.java,
            executionConfigClass = UnrunnableProtocolDialogTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Analyze a concept through dense pseudo-code 'Unrunnable Protocol' frameworks",
            tooltipHtml = """
                            Explores a concept using the dense, pseudo-code "Unrunnable Protocol" expressive format.
                            <ul>
                              <li>Pairs a "protocol drafter" agent with an "epistemic auditor" agent</li>
                              <li>Produces vivid struct/function/state-machine style framework artifacts</li>
                              <li>Audits each draft for pattern worship and framework fundamentalism</li>
                              <li>Iteratively refines drafts using audit feedback</li>
                              <li>Separates expressive/heuristic value from verifiable truth claims</li>
                              <li>Generates a sobered final synthesis with epistemic status</li>
                            </ul>
                          """,
        )
    }
}