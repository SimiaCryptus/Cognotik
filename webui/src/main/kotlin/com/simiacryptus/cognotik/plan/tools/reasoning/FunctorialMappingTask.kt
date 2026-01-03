package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger

class FunctorialMappingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FunctorialMappingTaskExecutionConfigData?
) : AbstractTask<FunctorialMappingTask.FunctorialMappingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FunctorialMappingTask::class.java)
        val FunctorialMapping = TaskType(
            name = "FunctorialMapping",
            category = "Reasoning",
            taskClass = FunctorialMappingTask::class.java,
            executionConfigClass = FunctorialMappingTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Translate problems from one category to another to solve them using different tools",
            tooltipHtml = """
                          This task implements the logic of Category Theory. It treats domains as "Categories" (collections of objects and arrows/morphisms).
                          The goal is to construct a "Functor"—a bridge that allows you to transport a difficult problem from Domain A to Domain B, solve it there, and transport the solution back.
                          <ul>
                            <li>Formalize source and target domains as Categories</li>
                            <li>Construct a Functor F mapping objects and morphisms</li>
                            <li>Transport the problem statement via F</li>
                            <li>Solve the problem in the target category</li>
                            <li>Inverse transport the solution back to the source</li>
                          </ul>
                        """,
        )
    }

    class FunctorialMappingTaskExecutionConfigData(
        @Description("The specific problem in the Source Category")
        val problem_statement: String? = null,
        @Description("The rules of the current domain (Source Category)")
        val source_category_definition: String? = null,
        @Description("The rules of the destination domain (Target Category)")
        val target_category_definition: String? = null,
        @Description("Constraints on the mapping (e.g., 'covariant', 'contravariant')")
        val functor_properties: String? = "covariant",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = FunctorialMapping.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (problem_statement.isNullOrBlank()) return "problem_statement must not be blank"
            if (source_category_definition.isNullOrBlank()) return "source_category_definition must not be blank"
            if (target_category_definition.isNullOrBlank()) return "target_category_definition must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
FunctorialMapping - Translate problems from one category to another
  ** Specify the problem_statement in the source domain
  ** Define source_category_definition (Objects + Morphisms)
  ** Define target_category_definition (Objects + Morphisms)
  ** Optionally specify functor_properties (default: covariant)
  ** The task will:
     - Formalize domains as Categories
     - Construct a Functor mapping
     - Transport the problem to the target domain
     - Solve it using target domain tools
     - Map the solution back
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val startTime = System.currentTimeMillis()
        log.info("Starting FunctorialMappingTask")

        executionConfig?.validate()?.let { errorMessage ->
            log.error("Configuration validation failed: $errorMessage")
            task.error(RuntimeException("VALIDATION ERROR: $errorMessage"))
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }

        val problem = executionConfig?.problem_statement!!
        val sourceDef = executionConfig?.source_category_definition!!
        val targetDef = executionConfig?.target_category_definition!!
        val properties = executionConfig?.functor_properties ?: "covariant"

        val api = defaultSmart
        val tabs = TabbedDisplay(task)
        val transcript = task.newLogStream("Functorial Mapping Transcript")

        // Overview Tab
        val overviewTask = tabs.newTask("Overview")

        overviewTask.header("Functorial Mapping Task")
        overviewTask.add(
            """
            <b>Problem:</b> $problem<br/>
            <b>Source Category:</b> $sourceDef<br/>
            <b>Target Category:</b> $targetDef<br/>
            <b>Properties:</b> $properties
        """.trimIndent()
        )
        overviewTask.complete()

        try {
            // Step 1: Category Definition
            val step1Task = tabs.newTask("1. Categories")
            step1Task.header("Formalizing Categories...", level = 3)

            val categoryPrompt = """
                You are a Category Theory expert.
                Formalize the following domains as Categories (Objects and Morphisms).
                
                **Source Domain:**
                $sourceDef
                
                **Target Domain:**
                $targetDef
                
                Output a structured description of the Objects and Morphisms for both categories.
                Use mathematical notation where appropriate.
            """.trimIndent()

            val categories = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(categoryPrompt))
            step1Task.add(MarkdownUtil.renderMarkdown(categories, ui = step1Task.ui))
            step1Task.complete()
            transcript.write("\n## Categories\n\n$categories\n".toByteArray())

            // Step 2: Functor Construction
            val step2Task = tabs.newTask("2. Functor")
            step2Task.header("Constructing Functor...", level = 3)

            val functorPrompt = """
                You are a Category Theory expert.
                Based on the category definitions:
                
                $categories
                
                Construct a Functor F from the Source Category to the Target Category.
                Properties: $properties
                
                1. Define how F maps Objects from Source to Target.
                2. Define how F maps Morphisms from Source to Target.
                3. Explain why this mapping is a valid functor (preserves identity and composition).
            """.trimIndent()

            val functor = ChatAgent(
                model = api,
                temperature = 0.4,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(functorPrompt))
            step2Task.add(MarkdownUtil.renderMarkdown(functor, ui = step2Task.ui))
            step2Task.complete()
            transcript.write("\n## Functor\n\n$functor\n".toByteArray())

            // Step 3: Problem Transport
            val step3Task = tabs.newTask("3. Transport")
            step3Task.header("Transporting Problem...", level = 3)

            val transportPrompt = """
                You are a Category Theory expert.
                Using the Functor F defined as:
                
                $functor
                
                Transport the following problem from the Source Category to the Target Category.
                
                **Original Problem:**
                $problem
                
                Express the problem strictly in terms of the Target Category's objects and morphisms.
            """.trimIndent()

            val transportedProblem = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(transportPrompt))
            step3Task.add(MarkdownUtil.renderMarkdown(transportedProblem, ui = step3Task.ui))
            step3Task.complete()
            transcript.write("\n## Transported Problem\n\n$transportedProblem\n".toByteArray())

            // Step 4: Remote Solution
            val step4Task = tabs.newTask("4. Solution")
            step4Task.header("Solving in Target Category...", level = 3)

            val solvePrompt = """
                You are an expert in the Target Domain defined earlier.
                Solve the following problem using tools and reasoning appropriate for this domain.
                
                **Problem (in Target Category):**
                $transportedProblem
                
                Provide a detailed solution and the final result.
            """.trimIndent()

            val targetSolution = ChatAgent(
                model = api,
                temperature = 0.5,
                prompt = "You are an expert in the Target Domain."
            ).answer(listOf(solvePrompt))
            step4Task.add(MarkdownUtil.renderMarkdown(targetSolution, ui = step4Task.ui))
            step4Task.complete()
            transcript.write("\n## Target Solution\n\n$targetSolution\n".toByteArray())

            // Step 5: Inverse Transport
            val step5Task = tabs.newTask("5. Result")
            step5Task.header("Mapping Solution Back...", level = 3)

            val inversePrompt = """
                You are a Category Theory expert.
                We have solved the problem in the Target Category. Now map the solution back to the Source Category.
                
                **Functor:**
                $functor
                
                **Target Solution:**
                $targetSolution
                
                **Original Problem:**
                $problem
                
                Interpret the target solution in the context of the original problem. 
                If the functor is not strictly invertible, provide the best interpretation or adjoint mapping.
                State the final answer clearly.
            """.trimIndent()

            val finalResult = ChatAgent(
                model = api,
                temperature = 0.3,
                prompt = "You are a Category Theory expert."
            ).answer(listOf(inversePrompt))
            step5Task.add(MarkdownUtil.renderMarkdown(finalResult, ui = step5Task.ui))
            step5Task.complete()
            transcript.write("\n## Final Result\n\n$finalResult\n".toByteArray())
            transcript.close()

            task.complete()
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error in FunctorialMappingTask", e)
            task.error(e)
            transcript.close()
            resultFn("ERROR: ${e.message}")
        }
    }

}