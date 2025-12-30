package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

open class SessionMode(
    override val task: SessionTask,
    override val orchestrationConfig: OrchestrationConfig,
    override val session: Session,
    override val user: User,
) : CognitiveMode {
    private val log = LoggerFactory.getLogger(SessionMode::class.java)
    private var activeToolConfig: TaskExecutionConfig? = null
    private var activeToolRunner: Any? = null
    private val history = mutableListOf<String>()
    private val isRunning = AtomicBoolean(false)

    override fun initialize() {
        log.debug("Initializing SessionMode")
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        if (activeToolConfig == null) {
            selectTool(userMessage, task)
        } else {
            if (isRunning.get()) {
                history.add("User: $userMessage")
                task.echo("Message added to session history.".renderMarkdown)
            } else {
                startSessionLoop(userMessage, task)
            }
        }
    }

    private fun selectTool(userMessage: String, task: SessionTask) {
        try {
            val (reasoning, chosenTask) = ConversationalMode.requestToTask(
                orchestrationConfig.defaultSmart.getChildClient(task),
                orchestrationConfig.defaultFast.getChildClient(task),
                userMessage,
                orchestrationConfig,
                prompt = "Select a tool to open a session with. This tool will be used continuously.",
                singleStage = true
            )
            activeToolConfig = chosenTask
            task.echo("Selected tool: ${activeToolConfig?.task_description ?: "Unknown"}".renderMarkdown)
            
            activeToolRunner = TaskType.getImpl(orchestrationConfig, activeToolConfig!!)
            
            startSessionLoop(userMessage, task)
        } catch (e: Exception) {
            task.error(e)
            activeToolConfig = null
        }
    }

    private fun startSessionLoop(goal: String, task: SessionTask) {
        isRunning.set(true)
        task.ui.pool.submit {
            try {
                var currentGoal = goal
                var iteration = 0
                val maxIterations = 50
                
                if (history.isEmpty() || history.last() != "User: $goal") {
                    history.add("User: $goal")
                }

                while (iteration++ < maxIterations && isRunning.get()) {
                    val nextStep = planNextStep(currentGoal, history, task)
                    
                    if (nextStep.isComplete) {
                        task.complete("Session goal completed: ${nextStep.reasoning}".renderMarkdown)
                        break
                    }
                    
                    val command = nextStep.command
                    if (command == null) {
                        task.complete("No command generated. Stopping.".renderMarkdown)
                        break
                    }
                    
                    task.echo("Executing: `$command`".renderMarkdown)
                    history.add("AI: $command")
                    
                    val result = executeCommand(activeToolRunner!!, command, task)
                    
                    history.add("Tool: $result")
                    task.echo("Result:\n```\n$result\n```".renderMarkdown)
                }
            } catch (e: Exception) {
                task.error(e)
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun executeCommand(runner: Any, command: String, task: SessionTask): String {
        val runMethod = runner::class.java.methods.find { it.name == "run" }
            ?: throw IllegalStateException("Run method not found on ${runner::class.java}")

        val resultRef = AtomicReference<String>("")
        val semaphore = Semaphore(0)
        
        val resultFn: (String) -> Unit = { 
            resultRef.set(it)
            semaphore.release()
        }

        val agent = TaskOrchestrator(
            user = user,
            session = session,
            dataStorage = task.ui.dataStorage!!,
            root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                ?: task.ui.dataStorage!!.getSessionDir(user, session).toPath() ?: File(".").toPath()
        )
        
        runMethod.invoke(runner, agent, listOf(command), task, resultFn, orchestrationConfig)
        
        semaphore.acquire()
        return resultRef.get()
    }

    data class SessionStep(
        val command: String? = null,
        val isComplete: Boolean = false,
        val reasoning: String? = null
    )

    private fun planNextStep(goal: String, history: List<String>, task: SessionTask): SessionStep {
        val describer = TaskContextYamlDescriber(orchestrationConfig)
        val agent = ParsedAgent(
            name = "SessionOperator",
            resultClass = SessionStep::class.java,
            prompt = """
                You are an operator for a stateful tool.
                Your goal is: $goal
                
                Review the history of interactions.
                If the goal is achieved, set isComplete to true.
                Otherwise, provide the next command to execute in the 'command' field.
                Do not create new tasks, just provide the input for the tool.
            """.trimIndent(),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
            temperature = orchestrationConfig.temperature,
            describer = describer
        )
        
        val context = history.takeLast(20)
        return agent.answer(context + listOf("Current Goal: $goal")).obj
    }

    override fun contextData(): List<String> = history
    
    companion object {
        val inputCnt = 1
    }
}