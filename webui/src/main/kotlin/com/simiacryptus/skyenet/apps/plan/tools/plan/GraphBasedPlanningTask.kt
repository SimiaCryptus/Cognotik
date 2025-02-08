 package com.simiacryptus.skyenet.apps.plan.tools.plan
 
 import com.simiacryptus.jopenai.ChatClient
 import com.simiacryptus.jopenai.OpenAIClient
 import com.simiacryptus.jopenai.describe.Description
 import com.simiacryptus.skyenet.apps.graph.SoftwareNodeType
 import com.simiacryptus.skyenet.apps.plan.*
 import com.simiacryptus.skyenet.core.actors.ParsedActor
 import com.simiacryptus.skyenet.webui.session.SessionTask
 import com.simiacryptus.util.JsonUtil
 import org.slf4j.LoggerFactory
 import java.io.File
 
 class GraphBasedPlanningTask(
   planSettings: PlanSettings,
   planTask: GraphBasedPlanningTaskConfigData?
 ) : AbstractTask<GraphBasedPlanningTask.GraphBasedPlanningTaskConfigData>(planSettings, planTask) {
 
   data class GraphBasedPlanResult(
     @Description("The task breakdown generated based on the software graph")
     val tasksByID: Map<String, TaskConfigBase>? = null,
     @Description("The rationale for the task breakdown and dependencies")
     val planningRationale: String? = null
   )
 
   class GraphBasedPlanningTaskConfigData(
     @Description("The path to the input software graph JSON file")
     val input_graph_file: String,
     @Description("The instruction or goal to be achieved")
     val instruction: String,
     @Description("Whether to execute the generated plan immediately")
     val execute_plan: Boolean = true,
     task_description: String? = null,
     task_dependencies: List<String>? = null,
     state: TaskState? = null
   ) : TaskConfigBase(
     task_type = TaskType.GraphBasedPlanning.name,
     task_description = task_description,
     task_dependencies = task_dependencies,
     state = state
   )
 
   private val graphPlanningActor by lazy {
     ParsedActor(
       name = "GraphBasedPlanning",
       resultClass = GraphBasedPlanResult::class.java,
       prompt = """
         Analyze the provided software graph and instruction to generate a detailed task breakdown.
         Consider the graph structure, dependencies, and relationships when planning tasks.
         
         For each task:
         1. Identify relevant nodes from the graph that will be affected
         2. Determine proper task ordering based on graph relationships
         3. Specify clear input/output dependencies between tasks
         4. Choose appropriate task types based on the required operations
         
         Provide clear rationale for:
         - Task selection and ordering
         - Dependencies between tasks
         - How the plan utilizes the graph structure
         
         Format the response as a valid GraphBasedPlanResult JSON structure.
       """.trimIndent(),
       model = planSettings.getTaskSettings(TaskType.GraphBasedPlanning).model ?: planSettings.defaultModel,
       parsingModel = planSettings.parsingModel,
       temperature = planSettings.temperature,
       describer = planSettings.describer()
     )
   }
 
   override fun promptSegment() = """
     GraphBasedPlanning - Generate and execute task plans based on software graph structure
       ** Specify the input graph file path
       ** Provide the instruction or goal to be achieved
       ** Optionally set execute_plan=false to only generate the plan without execution
   """.trimIndent()
 
   override fun run(
     agent: PlanCoordinator,
     messages: List<String>,
     task: SessionTask,
     api: ChatClient,
     resultFn: (String) -> Unit,
     api2: OpenAIClient,
     planSettings: PlanSettings
   ) {
     // Load the software graph
     val inputFile = File(taskConfig?.input_graph_file ?: throw IllegalArgumentException("Input graph file not specified"))
     if (!inputFile.exists()) throw IllegalArgumentException("Input graph file does not exist: ${inputFile.absolutePath}")
     val graph = JsonUtil.fromJson<SoftwareNodeType.SoftwareGraph>(inputFile.readText(), SoftwareNodeType.SoftwareGraph::class.java)
 
     // Generate the plan based on the graph
     val response = graphPlanningActor.respond(
       messages = graphPlanningActor.chatMessages(
         messages + listOf(
           "Software Graph:\n```json\n${JsonUtil.toJson(graph)}\n```",
           "Instruction: ${taskConfig?.instruction}"
         )
       ),
       input = messages,
       api = api
     )
 
     // Format the planning result
     val planSummary = buildString {
       appendLine("# Graph-Based Planning Result")
       appendLine()
       appendLine("## Planning Rationale")
       appendLine(response.obj.planningRationale)
       appendLine()
       appendLine("## Generated Plan")
       appendLine("```json")
       appendLine(JsonUtil.toJson(response.obj.tasksByID!!))
       appendLine("```")
     }
 
     // Execute the plan if requested
     if (taskConfig.execute_plan) {
       val plan = response.obj.tasksByID ?: emptyMap()
       val planProcessingState = agent.executePlan(
         plan = plan,
         task = task,
         userMessage = taskConfig.instruction ?: "",
         api = api,
         api2 = api2
       )
       
       // Add execution summary
       val executionSummary = buildString {
         appendLine("## Plan Execution Summary")
         appendLine("- Completed Tasks: ${planProcessingState.completedTasks.size}")
         appendLine("- Failed Tasks: ${plan.size - planProcessingState.completedTasks.size}")
         appendLine()
         appendLine("### Task Results:")
         planProcessingState.taskResult.forEach { (taskId, result) ->
           appendLine("#### $taskId")
           appendLine("```")
           appendLine(result.take(500)) // Limit output size
           appendLine("```")
         }
       }
       resultFn(planSummary + "\n\n" + executionSummary)
     } else {
       resultFn(planSummary)
     }
   }
 
   companion object {
     private val log = LoggerFactory.getLogger(GraphBasedPlanningTask::class.java)
   }
 }