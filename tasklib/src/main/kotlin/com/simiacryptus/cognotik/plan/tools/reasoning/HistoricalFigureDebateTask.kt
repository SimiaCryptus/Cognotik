package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
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
import com.simiacryptus.cognotik.webui.session.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoricalFigureDebateTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: HistoricalFigureDebateTaskExecutionConfigData?
) : AbstractTask<HistoricalFigureDebateTask.HistoricalFigureDebateTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 1500

    class HistoricalFigureDebateTaskExecutionConfigData(
        @Description("The topic or question the historical figures will debate")
        val debate_topic: String? = null,
        @Description("The list of historical figures who will participate in the debate (at least two)")
        val participants: List<String>? = null,
        @Description("Optional input files (supports glob patterns) to provide context for the debate")
        val related_files: List<String>? = null,
        @Description("Number of debate rounds (each round gives every participant one turn)")
        val rounds: Int = 3,
        @Description("Whether to include a neutral moderator that frames questions and summarizes")
        val include_moderator: Boolean = true,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = HistoricalFigureDebate.name,
        task_description = "Debate '$debate_topic' among historical figures",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (debate_topic.isNullOrBlank()) {
                return "debate_topic cannot be null or blank"
            }
            if (participants == null || participants.size < 2) {
                return "participants must contain at least two historical figures"
            }
            if (participants.any { it.isBlank() }) {
                return "participant names cannot be blank"
            }
            if (rounds < 1 || rounds > 10) {
                return "rounds must be between 1 and 10, got: $rounds"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
     HistoricalFigureDebate - Stage a debate between historical figures
      ** Specify the debate topic or question to explore
      ** Provide a list of at least two historical figures as participants
      ** Optionally provide input files (supports glob patterns) for context
      ** Configure the number of debate rounds (default: 3)
      ** Enable/disable a neutral moderator
      ** Each participant argues in character based on their known views and rhetoric
      ** Produces a structured debate transcript and a moderator synthesis
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
        log.info("Starting HistoricalFigureDebateTask on topic: '${executionConfig?.debate_topic}'")

        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val executionConfig = this.executionConfig ?: run {
            log.error("Execution configuration is null")
            task.safeComplete("CONFIGURATION ERROR: Execution configuration is null", log)
            resultFn("CONFIGURATION ERROR: Execution configuration is null")
            return
        }

        val debateTopic = executionConfig.debate_topic
        if (debateTopic.isNullOrBlank()) {
            log.error("No debate topic specified")
            task.safeComplete("CONFIGURATION ERROR: No debate topic specified", log)
            resultFn("CONFIGURATION ERROR: No debate topic specified")
            return
        }

        val participants = executionConfig.participants ?: emptyList()
        if (participants.size < 2) {
            log.error("Insufficient participants specified")
            task.safeComplete("CONFIGURATION ERROR: At least two participants are required", log)
            resultFn("CONFIGURATION ERROR: At least two participants are required")
            return
        }

        val rounds = executionConfig.rounds.coerceIn(1, 10)
        val includeModerator = executionConfig.include_moderator
        log.info("Configuration: participants=${participants.joinToString(", ")}, rounds=$rounds, includeModerator=$includeModerator")

        val api = defaultSmart

        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Historical Figure Debate")
            appendLine()
            appendLine("**Topic:** $debateTopic")
            appendLine()
            appendLine("**Participants:**")
            participants.forEach { appendLine("- $it") }
            appendLine()
            appendLine("**Rounds:** $rounds")
            appendLine()
            appendLine("**Moderator:** ${if (includeModerator) "Yes" else "No"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing debate participants...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))

        val priorContext = getPriorCode(agent.executionState)
        if (priorContext.isNotBlank()) {
            log.debug("Found prior context from previous tasks: ${priorContext.length} characters")
        }
        val combinedContext = listOfNotNull(priorContext, inputFileContext)
            .filter { it.isNotBlank() }
            .joinToString("\n\n---\n\n")

        // Create a ChatAgent for each participant
        log.info("Creating ${participants.size} participant agents")
        val participantAgents = participants.associateWith { figure ->
            ChatAgent(
                prompt = """
    You are $figure, the historical figure, participating in a structured debate.
    Speak and argue in character, drawing on your documented views, philosophy, rhetorical style, and the
    historical context of your life. Stay true to what is known of your positions and personality.

    The debate topic is: $debateTopic

    Guidelines:
    1. Argue persuasively from your authentic perspective
    2. Reference your own works, ideas, and experiences where relevant
    3. Directly engage with and rebut the arguments of other participants
    4. Maintain your characteristic tone and manner of speaking
    5. Keep each contribution focused and substantive

    Respond only as $figure, without narration or stage directions.
                """.trimIndent(),
                model = api,
                temperature = 0.8
            )
        }

        val moderatorAgent = if (includeModerator) {
            log.info("Creating moderator agent")
            ChatAgent(
                prompt = """
    You are a neutral, knowledgeable debate moderator. Your role is to:
    1. Frame the topic clearly and fairly
    2. Pose focused questions to the participants
    3. Keep the debate balanced and on-topic
    4. Remain impartial, never taking a side
    5. Summarize key points of contention and agreement

    The debate topic is: $debateTopic
    The participants are: ${participants.joinToString(", ")}

    Be concise and even-handed.
                """.trimIndent(),
                model = api,
                temperature = 0.4
            )
        } else null

        val transcriptBuilder = StringBuilder()
        val fullDebateBuilder = StringBuilder()

        val transcriptStream = task.newUserFileStream(transcriptFile())
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Historical Figure Debate Transcript\n\n")
            write("**Topic:** $debateTopic\n\n")
            write("**Participants:** ${participants.joinToString(", ")}\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("---\n\n")
            flush()
        }

        transcriptBuilder.append("# Historical Figure Debate Analysis\n\n")
        transcriptBuilder.append("**Topic:** $debateTopic\n\n")
        transcriptBuilder.append("**Participants:** ${participants.joinToString(", ")}\n\n")

        fullDebateBuilder.append("# Historical Figure Debate\n\n")
        fullDebateBuilder.append("## Topic\n\n")
        fullDebateBuilder.append("$debateTopic\n\n")

        if (combinedContext.isNotBlank()) {
            if (verbose) {
                fullDebateBuilder.append("## Context from Previous Tasks\n\n")
                fullDebateBuilder.append("$combinedContext\n\n")
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
                appendLine("✅ Debate participants initialized")
                appendLine()
                appendLine("*Starting debate rounds...*")
            }.renderMarkdown(true)
        )

        // Track the running conversation each agent can see
        val conversationLog = StringBuilder()
        val roundTimes = mutableListOf<Long>()

        try {
            // Optional moderator opening
            if (moderatorAgent != null) {
                log.info("Generating moderator opening")
                val openingTask = tabs.newTask("Moderator Opening")
                openingTask.add(
                    buildString {
                        appendLine("# Moderator Opening")
                        appendLine()
                        appendLine("*Generating opening remarks...*")
                    }.renderMarkdown(true)
                )
                val openingPrompt = buildString {
                    appendLine("Open the debate on the topic: $debateTopic")
                    appendLine("Introduce the participants: ${participants.joinToString(", ")}.")
                    if (combinedContext.isNotBlank()) {
                        appendLine()
                        appendLine("Relevant context:")
                        appendLine(combinedContext)
                    }
                    appendLine()
                    appendLine("Provide a brief framing and a focused opening question for the participants.")
                }
                var opening = moderatorAgent.answer(listOf(openingPrompt))
                if (opening.isEmpty()) opening = "Let us begin the debate on: $debateTopic"
                conversationLog.append("Moderator: $opening\n\n")
                fullDebateBuilder.append("## Moderator Opening\n\n$opening\n\n")
                transcriptWriter?.apply {
                    write("## Moderator Opening\n\n$opening\n\n")
                    flush()
                }
                transcriptBuilder.append("## Moderator Opening\n\n")
                transcriptBuilder.append("${opening.truncateForDisplay(maxDescriptionLength)}\n\n")
                openingTask.add(
                    buildString {
                        appendLine("# Moderator Opening")
                        appendLine()
                        appendLine(opening)
                    }.renderMarkdown(true)
                )
            }

            for (round in 1..rounds) {
                val roundStartTime = System.currentTimeMillis()
                log.info("Starting round $round of $rounds")

                val roundTask = tabs.newTask("Round $round")
                roundTask.add(
                    buildString {
                        appendLine("# Round $round of $rounds")
                        appendLine()
                        appendLine("**Status:** In progress...")
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }.renderMarkdown(true)
                )

                fullDebateBuilder.append("## Round $round\n\n")
                transcriptWriter?.apply {
                    write("## Round $round\n\n")
                    flush()
                }

                for (figure in participants) {
                    log.debug("Generating statement for $figure in round $round")
                    val statementPrompt = buildString {
                        appendLine("The debate topic is: $debateTopic")
                        if (combinedContext.isNotBlank()) {
                            appendLine()
                            appendLine("Relevant context:")
                            appendLine(combinedContext)
                        }
                        appendLine()
                        if (conversationLog.isNotBlank()) {
                            appendLine("Debate so far:")
                            appendLine(conversationLog.toString())
                        }
                        appendLine()
                        appendLine("This is round $round of $rounds.")
                        appendLine("As $figure, present your argument for this round, responding to points already made.")
                    }

                    var statement = participantAgents[figure]!!.answer(listOf(statementPrompt))
                    if (statement.isEmpty()) statement = "[No statement generated]"
                    log.debug("Statement generated for $figure: ${statement.length} characters")

                    conversationLog.append("$figure: $statement\n\n")
                    fullDebateBuilder.append("**$figure:** $statement\n\n")
                    transcriptWriter?.apply {
                        write("**$figure:** $statement\n\n")
                        flush()
                    }

                    // Only include first and last round detail in concise output
                    if (round == 1 || round == rounds) {
                        transcriptBuilder.append("### Round $round — $figure\n")
                        transcriptBuilder.append("${statement.truncateForDisplay(maxDescriptionLength)}\n\n")
                    }

                    roundTask.add(
                        buildString {
                            appendLine("## $figure")
                            appendLine()
                            appendLine(statement)
                            appendLine()
                        }.renderMarkdown(true)
                    )
                }

                val roundTime = System.currentTimeMillis() - roundStartTime
                roundTimes.add(roundTime)
                roundTask.add(
                    buildString {
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                        appendLine()
                        appendLine("**Processing Time:** ${roundTime / 1000.0}s")
                    }.renderMarkdown(true)
                )

                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Round $round complete (${roundTime / 1000.0}s)")
                        if (round < rounds) {
                            appendLine()
                            appendLine("*Processing round ${round + 1}...*")
                        }
                    }.renderMarkdown(true)
                )
                log.info("Round $round completed in ${roundTime}ms")
            }

            // Synthesis / moderator closing
            log.info("Generating debate synthesis")
            val synthesisTask = tabs.newTask("Synthesis")
            synthesisTask.add(
                buildString {
                    appendLine("# Synthesis")
                    appendLine()
                    appendLine("**Status:** Generating debate synthesis...")
                    appendLine()
                }.renderMarkdown(true)
            )

            val synthesisPrompt = buildString {
                appendLine("Review the following debate on the topic: $debateTopic")
                appendLine("Participants: ${participants.joinToString(", ")}")
                appendLine()
                appendLine("Debate transcript:")
                appendLine(conversationLog.toString())
                appendLine()
                appendLine("Provide a structured synthesis that:")
                appendLine("1. Summarizes each participant's core position")
                appendLine("2. Identifies key points of agreement and disagreement")
                appendLine("3. Highlights the strongest arguments raised")
                appendLine("4. Notes unresolved tensions or open questions")
                appendLine("5. Offers a balanced, impartial conclusion")
            }

            val synthesizer = moderatorAgent ?: ChatAgent(
                prompt = "You are an impartial analyst summarizing a debate fairly and objectively.",
                model = api,
                temperature = 0.4
            )
            var synthesis = synthesizer.answer(listOf(synthesisPrompt))
            if (synthesis.isEmpty()) synthesis = "Unable to generate synthesis"
            log.debug("Synthesis generated: ${synthesis.length} characters")

            transcriptBuilder.append("## Synthesis\n\n")
            transcriptBuilder.append(synthesis)
            transcriptWriter?.apply {
                write("## Synthesis\n\n")
                write(synthesis)
                write("\n\n")
                flush()
            }

            transcriptBuilder.append("\n\n---\n\n")
            transcriptBuilder.append("**Rounds:** $rounds | ")
            transcriptBuilder.append("**Participants:** ${participants.size} | ")
            transcriptBuilder.append("**Time:** ${(System.currentTimeMillis() - startTime) / 1000}s\n")

            synthesisTask.add(
                buildString {
                    appendLine("## Synthesis")
                    appendLine()
                    appendLine(synthesis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }.renderMarkdown(true)
            )

            val finalResult = transcriptBuilder.toString()
            val fullDebate = fullDebateBuilder.toString()
            val totalTime = System.currentTimeMillis() - startTime
            val avgRoundTime = if (roundTimes.isNotEmpty()) roundTimes.average() else 0.0
            log.info("HistoricalFigureDebateTask completed: total_time=${totalTime}ms, rounds=$rounds, avg_round_time=${avgRoundTime}ms, output_size=${finalResult.length} chars (full: ${fullDebate.length} chars)")

            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Rounds:** $rounds | **Avg Round Time:** ${avgRoundTime / 1000.0}s\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Debate Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Rounds Completed:** $rounds")
                    appendLine()
                    appendLine("**Average Round Time:** ${avgRoundTime / 1000.0}s")
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

            task.complete("Completed $rounds rounds in ${totalTime / 1000}s. Concise analysis: ${finalResult.length} chars.")
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during historical figure debate", e)
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
                appendLine("# Error in Historical Figure Debate")
                appendLine()
                appendLine("**Topic:** $debateTopic")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Rounds Completed:** ${roundTimes.size} of $rounds")
                if (transcriptBuilder.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(transcriptBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HistoricalFigureDebateTask::class.java)

        @JvmStatic
        val HistoricalFigureDebate = TaskType(
            name = "HistoricalFigureDebate",
            category = "Reasoning",
            taskClass = HistoricalFigureDebateTask::class.java,
            executionConfigClass = HistoricalFigureDebateTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Stage a debate between historical figures on a topic",
            tooltipHtml = """
                            Stages an in-character debate between historical figures.
                            <ul>
                              <li>Each figure argues from their documented views and rhetorical style</li>
                              <li>Configurable participants, rounds, and optional moderator</li>
                              <li>Participants directly engage and rebut one another</li>
                              <li>Optional neutral moderator frames and summarizes</li>
                              <li>Produces a full debate transcript and impartial synthesis</li>
                            </ul>
                          """,
        )
    }
}