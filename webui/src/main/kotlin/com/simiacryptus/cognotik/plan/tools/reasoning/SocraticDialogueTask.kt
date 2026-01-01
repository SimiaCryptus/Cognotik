package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SocraticDialogueTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SocraticDialogueTaskExecutionConfigData?
) : AbstractTask<SocraticDialogueTask.SocraticDialogueTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 1500

    class SocraticDialogueTaskExecutionConfigData(
        @Description("The initial question or hypothesis to explore")
        val initial_question: String? = null,
        @Description("Optional input files (supports glob patterns) to provide context for the dialogue")
        val input_files: List<String>? = null,
        @Description("Maximum dialogue depth (number of question-answer exchanges)")
        val max_depth: Int = 5,
        @Description("Whether to challenge assumptions at each level")
        val challenge_assumptions: Boolean = true,
        @Description("Topics or domains to constrain the dialogue")
        val domain_constraints: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = SocraticDialogue.name,
        task_description = "Explore '$initial_question' through Socratic dialogue",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (initial_question.isNullOrBlank()) {
                return "initial_question cannot be null or blank"
            }
            if (max_depth < 1 || max_depth > 20) {
                return "max_depth must be between 1 and 20, got: $max_depth"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
 SocraticDialogue - Explore ideas through Socratic questioning
  ** Specify the initial question or hypothesis to explore
  ** Optionally provide input files (supports glob patterns) for context
  ** Configure maximum dialogue depth (default: 5 exchanges)
  ** Enable/disable assumption challenging
  ** Optionally constrain to specific topics or domains
  ** Creates a dialogue between questioner and responder agents
  ** Explores definitions, assumptions, implications, and contradictions
  ** Produces a structured dialogue transcript with insights
  Available files:
  ${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
  ** Specify the initial question or hypothesis to explore
  ** Configure maximum dialogue depth (default: 5 exchanges)
  ** Enable/disable assumption challenging
  ** Optionally constrain to specific topics or domains
  ** Creates a dialogue between questioner and responder agents
  ** Explores definitions, assumptions, implications, and contradictions
  ** Produces a structured dialogue transcript with insights
        """.trimIndent()
    }

    private fun getInputFileContext(): String = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val inputFileContext = getInputFileContext()
        val startTime = System.currentTimeMillis()
        log.info("Starting SocraticDialogueTask with initial question: '${executionConfig?.initial_question}'")
        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }


        val initialQuestion = executionConfig?.initial_question
        if (initialQuestion.isNullOrBlank()) {
            log.error("No initial question specified")
            task.safeComplete("CONFIGURATION ERROR: No initial question specified", log)
            resultFn("CONFIGURATION ERROR: No initial question specified")
            return
        }

        val maxDepth = executionConfig.max_depth.coerceIn(1, 20)
        val challengeAssumptions = executionConfig.challenge_assumptions
        val domainConstraints = executionConfig.domain_constraints?.joinToString(", ") ?: "any domain"
        log.info("Configuration: maxDepth=$maxDepth, challengeAssumptions=$challengeAssumptions, domainConstraints=$domainConstraints")

        val ui = task.ui
        val api = defaultSmart ?: return
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Socratic Dialogue: Exploring the Question")
            appendLine()
            appendLine("**Initial Question:** $initialQuestion")
            appendLine()
            appendLine("**Domain Constraints:** $domainConstraints")
            appendLine()
            appendLine("**Max Depth:** $maxDepth exchanges")
            appendLine()
            appendLine("**Challenge Assumptions:** ${if (challengeAssumptions) "Yes" else "No"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing dialogue agents...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context from previous tasks: ${priorContext.length} characters")
        }
        val combinedContext = listOfNotNull(priorContext, inputFileContext)
            .filter { it.isNotBlank() }
            .joinToString("\n\n---\n\n")

        // Create the Socratic questioner agent
        log.info("Creating Socratic questioner agent")
        val questionerAgent = ChatAgent(
            prompt = """
You are a Socratic questioner. Your role is to ask probing questions that:
1. Challenge assumptions and definitions
2. Explore implications and consequences
3. Identify contradictions or inconsistencies
4. Seek deeper understanding through clarification
5. Guide toward examining fundamental principles

${if (challengeAssumptions) "Actively challenge assumptions at each step." else "Focus on clarification and exploration."}

Domain constraints: $domainConstraints

Ask one clear, focused question at a time. Build on previous answers to go deeper.
            """.trimIndent(),
            model = api,
            temperature = 0.7
        )

        // Create the responder agent
        log.info("Creating responder agent")
        val responderAgent = ChatAgent(
            prompt = """
You are a thoughtful responder in a Socratic dialogue. Your role is to:
1. Answer questions honestly and thoroughly
2. Acknowledge when you're uncertain
3. Provide clear definitions and reasoning
4. Consider implications of your statements
5. Be open to revising your understanding

Domain constraints: $domainConstraints

Provide substantive, well-reasoned responses that advance the dialogue.
            """.trimIndent(),
            model = api,
            temperature = 0.5
        )

        val dialogueBuilder = StringBuilder()
        val fullDialogueBuilder = StringBuilder()
        // Create transcript file
        val (transcriptLink, transcriptStream) = createTranscriptFile(task)
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Socratic Dialogue Transcript\n\n")
            write("**Initial Question:** $initialQuestion\n\n")
            write("**Domain Constraints:** $domainConstraints\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("---\n\n")
            flush()
        }


        // Concise output for final result
        dialogueBuilder.append("# Socratic Dialogue Analysis\n\n")
        dialogueBuilder.append("**Question:** $initialQuestion\n\n")

        // Full dialogue for UI tabs only
        fullDialogueBuilder.append("# Socratic Dialogue\n\n")
        fullDialogueBuilder.append("## Initial Question\n\n")
        fullDialogueBuilder.append("$initialQuestion\n\n")

        if (combinedContext.isNotBlank()) {
            fullDialogueBuilder.append("## Context from Previous Tasks\n\n")
            fullDialogueBuilder.append("$combinedContext\n\n")
            transcriptWriter?.apply {
                write("## Context from Previous Tasks\n\n")
                write("$combinedContext\n\n")
                flush()
            }
            // Add context tab
            val contextTask = tabs.newTask("Context")
            contextTask.add(
                buildString {
                    appendLine("# Context from Previous Tasks")
                    appendLine()
                    appendLine(combinedContext)
                }.renderMarkdown
            )
        }
        // Update overview with agent initialization complete
        overviewTask.add(
            buildString {
                appendLine()
                appendLine("✅ Dialogue agents initialized")
                appendLine()
                appendLine("*Starting dialogue exchanges...*")
            }.renderMarkdown
        )

        var currentQuestion = initialQuestion ?: ""
        var currentResponse = ""
        val exchangeTimes = mutableListOf<Long>()

        try {
            for (depth in 1..maxDepth) {
                val exchangeStartTime = System.currentTimeMillis()
                log.info("Starting exchange $depth of $maxDepth")

                // Create tab for this exchange
                val exchangeTask = tabs.newTask("Exchange $depth")

                exchangeTask.add(
                    buildString {
                        appendLine("# Exchange $depth of $maxDepth")
                        appendLine()
                        appendLine("**Status:** Processing...")
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }.renderMarkdown
                )

                // Get response to current question
                val responsePrompt = if (depth == 1) {
                    "Consider this question: $currentQuestion\n\nProvide your initial thoughts and response."
                } else {
                    "Previous question: $currentQuestion\n\nYour previous response: $currentResponse\n\nNow respond to this follow-up."
                }
                log.debug("Generating response for exchange $depth")
                exchangeTask.add(
                    buildString {
                        appendLine("## Question")
                        appendLine()
                        appendLine("> $currentQuestion")
                        appendLine()
                        appendLine("*Generating response...*")
                        appendLine()
                    }.renderMarkdown
                )

                currentResponse = responderAgent.answer(listOf(responsePrompt))
                if (currentResponse.isEmpty()) currentResponse = "No response generated"
                log.debug("Response generated for exchange $depth: ${currentResponse.length} characters")

                // Store full dialogue in UI builder
                fullDialogueBuilder.append("## Exchange $depth\n\n")
                fullDialogueBuilder.append("**Question:** $currentQuestion\n\n")
                fullDialogueBuilder.append("**Response:** $currentResponse\n\n")
                transcriptWriter?.apply {
                    write("## Exchange $depth\n\n")
                    write("**Question:** $currentQuestion\n\n")
                    write("**Response:** $currentResponse\n\n")
                    flush()
                }


                // Store only key points in concise output
                if (depth == 1 || depth == maxDepth) {
                    dialogueBuilder.append("### Exchange $depth\n")
                    dialogueBuilder.append("**Q:** ${currentQuestion.truncateForDisplay(maxDescriptionLength)}\n")
                    dialogueBuilder.append("**A:** ${currentResponse.truncateForDisplay(maxDescriptionLength)}\n\n")
                }

                exchangeTask.add(
                    buildString {
                        appendLine("## Response")
                        appendLine()
                        appendLine(currentResponse)
                        appendLine()
                    }.renderMarkdown
                )

                // Generate next question if not at max depth
                if (depth < maxDepth) {
                    log.debug("Generating next question for exchange ${depth + 1}")
                    exchangeTask.add(
                        buildString {
                            appendLine("*Generating next question...*")
                            appendLine()
                        }.renderMarkdown
                    )

                    val nextQuestionPrompt = """
Previous question: $currentQuestion

Response received: $currentResponse

Based on this response, generate the next Socratic question that:
${if (challengeAssumptions) "- Challenges an assumption made in the response" else "- Seeks deeper clarification"}
- Explores implications or consequences
- Identifies potential contradictions
- Moves toward fundamental principles

Provide only the question, without preamble.
                    """.trimIndent()

                    currentQuestion = questionerAgent.answer(listOf(nextQuestionPrompt))
                    if (currentQuestion.isEmpty()) currentQuestion =
                        "What are the implications of this understanding?"
                    log.debug("Next question generated: $currentQuestion")
                    exchangeTask.add(
                        buildString {
                            appendLine("## Next Question")
                            appendLine()
                            appendLine("> $currentQuestion")
                            appendLine()
                        }.renderMarkdown
                    )
                    transcriptWriter?.apply {
                        write("**Next Question:** $currentQuestion\n\n")
                        flush()
                    }

                }
                val exchangeTime = System.currentTimeMillis() - exchangeStartTime
                exchangeTimes.add(exchangeTime)
                // Mark exchange as complete
                exchangeTask.add(
                    buildString {
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                        appendLine()
                        appendLine("**Processing Time:** ${exchangeTime / 1000.0}s")
                    }.renderMarkdown
                )
                // Update overview with progress
                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Exchange $depth complete (${exchangeTime / 1000.0}s)")
                        if (depth < maxDepth) {
                            appendLine()
                            appendLine("*Processing exchange ${depth + 1}...*")
                        }
                    }.renderMarkdown
                )
                log.info("Exchange $depth completed in ${exchangeTime}ms")
            }

            log.info("Generating synthesis of dialogue")
            val synthesisTask = tabs.newTask("Synthesis")

            synthesisTask.add(
                buildString {
                    appendLine("# Synthesis")
                    appendLine()
                    appendLine("**Status:** Generating comprehensive synthesis...")
                    appendLine()
                }.renderMarkdown
            )

            val synthesisPrompt = """
Review this Socratic dialogue and provide a synthesis that:
1. Summarizes key insights discovered
2. Identifies assumptions that were challenged or confirmed
3. Notes any contradictions or tensions revealed
4. Suggests areas for further exploration
5. Draws conclusions about the original question

Dialogue:
$dialogueBuilder

Provide a structured synthesis.
            """.trimIndent()

            var synthesis = ""
            synthesis = responderAgent.answer(listOf(synthesisPrompt))
            if (synthesis.isEmpty()) synthesis = "Unable to generate synthesis"
            log.debug("Synthesis generated: ${synthesis.length} characters")

            dialogueBuilder.append("## Key Insights\n\n")
            dialogueBuilder.append(synthesis)
            transcriptWriter?.apply {
                write("## Synthesis\n\n")
                write(synthesis)
                write("\n\n")
                flush()
            }


            // Add summary statistics
            dialogueBuilder.append("\n\n---\n\n")
            dialogueBuilder.append("**Exchanges:** $maxDepth | ")
            dialogueBuilder.append("**Domain:** $domainConstraints | ")
            dialogueBuilder.append("**Time:** ${(System.currentTimeMillis() - startTime) / 1000}s\n")

            synthesisTask.add(
                buildString {
                    appendLine("## Key Insights")
                    appendLine()
                    appendLine(synthesis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }.renderMarkdown
            )

            val finalResult = dialogueBuilder.toString()
            val fullDialogue = fullDialogueBuilder.toString()
            val totalTime = System.currentTimeMillis() - startTime
            val avgExchangeTime = if (exchangeTimes.isNotEmpty()) exchangeTimes.average() else 0.0
            log.info("SocraticDialogueTask completed: total_time=${totalTime}ms, exchanges=$maxDepth, avg_exchange_time=${avgExchangeTime}ms, output_size=${finalResult.length} chars (full: ${fullDialogue.length} chars)")
            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Exchanges:** $maxDepth | **Avg Exchange Time:** ${avgExchangeTime / 1000.0}s\n")
                flush()
                close()
            }


            // Update overview with completion stats
            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Dialogue Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Exchanges Completed:** $maxDepth")
                    appendLine()
                    appendLine("**Average Exchange Time:** ${avgExchangeTime / 1000.0}s")
                    appendLine()
                    appendLine("**Total Characters Generated:** ${finalResult.length}")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )

            task.complete("Completed $maxDepth exchanges in ${totalTime / 1000}s. Concise analysis: ${finalResult.length} chars.")

            val summaryMessage = buildString {
                appendLine(finalResult)
                appendLine("\n---\n")
                appendLine(
                    "Full dialogue transcript: <a href='$transcriptLink' target='_blank'>$transcriptLink</a> <a href='${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.html' target='_blank'>html</a> <a href='${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf' target='_blank'>pdf</a>"
                )
            }
            resultFn(summaryMessage)

        } catch (e: Exception) {
            log.error("Error during Socratic dialogue", e)
            task.error(e)
            transcriptWriter?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n")
                write("**Error:** ${e.message}\n\n")
                flush()
                close()
            }


            // Update overview with error
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
                }.renderMarkdown
            )

            val errorOutput = buildString {
                appendLine("# Error in Socratic Dialogue")
                appendLine()
                appendLine("**Question:** $initialQuestion")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Exchanges Completed:** ${exchangeTimes.size} of $maxDepth")
                if (dialogueBuilder.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(dialogueBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun createTranscriptFile(task: SessionTask): Pair<String, FileOutputStream?> {
        val transcriptFile =
            this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        )
        return Pair(link, markdownTranscript)
    }

    private fun getAvailableFiles(
        path: Path,
        treatDocumentsAsText: Boolean = false,
    ): List<String> {
        return try {
            listOf(
                FileSelectionUtils.filteredWalkAsciiTree(
                    path.toFile(),
                    20,
                    treatDocumentsAsText = treatDocumentsAsText
                )
            )
        } catch (e: Exception) {
            log.error("Error listing available files", e)
            listOf("Error listing files: ${e.message}")
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocraticDialogueTask::class.java)
        val SocraticDialogue = TaskType(
            "SocraticDialogue",
            "Reasoning",
            SocraticDialogueTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Explore ideas through Socratic questioning",
            """
              Uses Socratic questioning methodology to deeply explore ideas.
              <ul>
                <li>Creates dialogue between questioner and responder agents</li>
                <li>Challenges assumptions and definitions</li>
                <li>Explores implications and consequences</li>
                <li>Identifies contradictions and tensions</li>
                <li>Configurable dialogue depth and constraints</li>
                <li>Generates synthesis of insights discovered</li>
              </ul>
            """
        )
    }
}