package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

class SocraticDialogueTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SocraticDialogueTaskExecutionConfigData?
) : AbstractTask<SocraticDialogueTask.SocraticDialogueTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class SocraticDialogueTaskExecutionConfigData(
        @Description("The initial question or hypothesis to explore")
        val initial_question: String? = null,
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
    )

    override fun promptSegment(): String {
        return """
SocraticDialogue - Explore ideas through Socratic questioning
  ** Specify the initial question or hypothesis to explore
  ** Configure maximum dialogue depth (default: 5 exchanges)
  ** Enable/disable assumption challenging
  ** Optionally constrain to specific topics or domains
  ** Creates a dialogue between questioner and responder agents
  ** Explores definitions, assumptions, implications, and contradictions
  ** Produces a structured dialogue transcript with insights
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val initialQuestion = executionConfig?.initial_question
        if (initialQuestion.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No initial question specified")
            return
        }

        val maxDepth = executionConfig.max_depth.coerceIn(1, 20)
        val challengeAssumptions = executionConfig.challenge_assumptions
        val domainConstraints = executionConfig.domain_constraints?.joinToString(", ") ?: "any domain"

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Socratic Dialogue: Exploring the Question
                |
                |**Initial Question:** $initialQuestion
                |
                |**Domain Constraints:** $domainConstraints
                |
                |**Max Depth:** $maxDepth exchanges
                |
                |**Challenge Assumptions:** ${if (challengeAssumptions) "Yes" else "No"}
                """.trimMargin(),
                ui = ui
            )
        )

        val priorContext = getPriorCode(agent.executionState!!)

        // Create the Socratic questioner agent
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
        dialogueBuilder.append("# Socratic Dialogue\n\n")
        dialogueBuilder.append("## Initial Question\n\n")
        dialogueBuilder.append("$initialQuestion\n\n")

        if (priorContext.isNotBlank()) {
            dialogueBuilder.append("## Context from Previous Tasks\n\n")
            dialogueBuilder.append("$priorContext\n\n")
        }

        var currentQuestion = initialQuestion
        var currentResponse = ""

        try {
            for (depth in 1..maxDepth) {
                newTask.add(
                    MarkdownUtil.renderMarkdown(
                        "### Exchange $depth of $maxDepth",
                        ui = ui
                    )
                )

                // Get response to current question
                val responsePrompt = if (depth == 1) {
                    "Consider this question: $currentQuestion\n\nProvide your initial thoughts and response."
                } else {
                    "Previous question: $currentQuestion\n\nYour previous response: $currentResponse\n\nNow respond to this follow-up."
                }

                Retryable(newTask, newTask.ui) { sb ->
                    currentResponse = responderAgent.answer(listOf(responsePrompt))
                    sb.append(currentResponse)
                    sb.toString()
                }
                if (currentResponse.isEmpty()) currentResponse = "No response generated"

                dialogueBuilder.append("## Exchange $depth\n\n")
                dialogueBuilder.append("**Question:** $currentQuestion\n\n")
                dialogueBuilder.append("**Response:** $currentResponse\n\n")

                newTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
                        |**Q:** $currentQuestion
                        |
                        |**A:** $currentResponse
                        """.trimMargin(),
                        ui = ui
                    )
                )

                // Generate next question if not at max depth
                if (depth < maxDepth) {
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

                    Retryable(newTask, newTask.ui) { sb ->
                        currentQuestion = questionerAgent.answer(listOf(nextQuestionPrompt))
                        sb.append(currentQuestion)
                        sb.toString()
                    }
                    if (currentQuestion?.isEmpty() == true) currentQuestion =
                        "What are the implications of this understanding?"
                }
            }

            // Generate synthesis
            newTask.add(
                MarkdownUtil.renderMarkdown(
                    "### Generating Synthesis...",
                    ui = ui
                )
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
            Retryable(newTask, newTask.ui) { sb ->
                synthesis = responderAgent.answer(listOf(synthesisPrompt))
                sb.append(synthesis)
                sb.toString()
            }
            if (synthesis.isEmpty()) synthesis = "Unable to generate synthesis"

            dialogueBuilder.append("## Synthesis\n\n")
            dialogueBuilder.append(synthesis)
            dialogueBuilder.append("\n\n")

            newTask.add(
                MarkdownUtil.renderMarkdown(
                    """
                    |### Synthesis
                    |
                    |$synthesis
                    """.trimMargin(),
                    ui = ui
                )
            )

            val finalResult = dialogueBuilder.toString()

            newTask.complete(
                MarkdownUtil.renderMarkdown(
                    """
                    |## Dialogue Complete
                    |
                    |Explored through $maxDepth exchanges of Socratic questioning.
                    |
                    |Key insights and synthesis have been generated.
                    """.trimMargin(),
                    ui = ui
                )
            )

            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during Socratic dialogue", e)
            newTask.error(e)
            resultFn("ERROR: ${e.message}\n\nPartial dialogue:\n${dialogueBuilder}")
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SocraticDialogueTask::class.java)
        val SocraticDialogue = TaskType(
            "SocraticDialogue",
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