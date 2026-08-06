package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream


open class IterativeGraphGenerationTask<T : IterativeGraphGenerationTask.IterativeGraphGenerationTaskExecutionConfigData>(
  orchestrationConfig: OrchestrationConfig,
  planTask: T?
) : AbstractTask<T, IterativeGraphGenerationTask.IterativeGraphGenerationTaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  open class IterativeGraphGenerationTaskExecutionConfigData(
    @Description("The goal or question the graph should answer/represent")
    var goal_prompt: String? = null,

    @Description("Input text to analyze")
    var context_data: String? = null,

    @Description("Input files to analyze")
    var related_files: List<String>? = null,

    @Description("Optional JSON file to initialize graph from")
    var initial_graph_file: String? = null,

    @Description("Maximum number of iterations")
    var max_iterations: Int = 20,

    @Description("Maximum number of nodes")
    var max_nodes: Int = 50,

    @Description("Maximum number of edges")
    var max_edges: Int = 100,

    @Description("Allowed node types (labels)")
    var node_types: List<String> = listOf("Concept", "Entity"),

    @Description("Allowed edge types (labels)")
    var edge_types: List<String> = listOf("RELATES_TO"),

    @Description("Tasks that must complete before this one")
    task_dependencies: List<String>? = null,

    @Description("The current state of the task")
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = IterativeGraphGeneration.name,
    task_description = "Generate knowledge graph for '${goal_prompt ?: "unknown"}'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (goal_prompt.isNullOrBlank()) return "goal_prompt is required"
      if (max_iterations <= 0) return "max_iterations must be positive"
      return null
    }
  }

  class IterativeGraphGenerationTaskTypeConfig(
    @Description("The prompt template used for each iteration of graph expansion")
    var iterationPromptTemplate: String = """
            Goal: {goal_prompt}
            
            Schema:
            Node Types: {node_types}
            Edge Types: {edge_types}
            
            Current Graph State:
            {graph_view}
            
            Context Data ({chunk_index}/{total_chunks}):
            {current_text}
            
            Provide a list of actions to expand the graph based on the context and goal.
            If the graph is complete, set is_finished to true.
            
            For ADD_EDGE, 'from' and 'to' must be maps of properties that uniquely identify existing nodes.
            For ADD_NODE, provide properties that uniquely identify the node (e.g. name).
            For MERGE_NODES, provide 'target' (keep) and 'source' (remove) properties to identify nodes.
        """.trimIndent()
  ) : TaskTypeConfig()

  data class GraphActionList(
    @Description("The reasoning behind the proposed actions")
    var reasoning: String = "",
    @Description("The list of graph operations to perform")
    var actions: List<GraphAction> = emptyList(),
    @Description("Whether the graph generation is complete")
    var is_finished: Boolean = false
  ) : ValidatedObject

  data class GraphAction(
    @Description("Type of action to perform: ADD_NODE, ADD_EDGE, or MERGE_NODES")
    var type: String = "ADD_NODE",
    @Description("Label of the node or edge (REQUIRED for ADD_*)")
    var label: String = "",
    @Description("Properties of the node or edge")
    var properties: Map<String, Any> = emptyMap(),
    @Description("For ADD_EDGE: properties to identify the 'from' node")
    var from: Map<String, Any>? = null,
    @Description("For ADD_EDGE: properties to identify the 'to' node")
    var to: Map<String, Any>? = null,
    @Description("For MERGE_NODES: The properties of the node to keep")
    var target: Map<String, Any>? = null,
    @Description("For MERGE_NODES: The properties of the node to remove (merging into target)")
    var source: Map<String, Any>? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (type != "ADD_NODE" && type != "ADD_EDGE" && type != "MERGE_NODES") return "Invalid action type: $type"
      if ((type == "ADD_NODE" || type == "ADD_EDGE") && label.isBlank()) return "Label is required"
      if (type == "ADD_EDGE") {
        if (from.isNullOrEmpty()) return "From properties are required for ADD_EDGE"
        if (to.isNullOrEmpty()) return "To properties are required for ADD_EDGE"
      }
      if (type == "MERGE_NODES") {
        if (target.isNullOrEmpty()) return "Target properties are required for MERGE_NODES"
        if (source.isNullOrEmpty()) return "Source properties are required for MERGE_NODES"
      }
      return null
    }
  }

  override fun promptSegment(): String {
    return """
IterativeGraphGeneration - Build knowledge graphs incrementally
  * goal_prompt: The goal or question the graph should answer/represent.
  * context_data: Input text to analyze.
  * related_files: Input files to analyze.
  * node_types/edge_types: Allowed labels for nodes and edges.
  * Use this to extract entities and relationships for complex knowledge management and visualization.
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {


    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      try {
        val config = executionConfig!!
        log.info("Starting IterativeGraphGenerationTask for goal: ${config.goal_prompt}")
        transcript?.write("## Iterative Graph Generation\n\n".toByteArray())
        transcript?.write("Goal: **${config.goal_prompt}**\n\n".toByteArray())

        val graph: Graph = TinkerGraph.open()
        if (!config.initial_graph_file.isNullOrBlank()) {
          val file = task.resolveUserFile(config.initial_graph_file!!)
          if (file?.exists() == true) {
            try {
              GraphSONReader.build().create().readGraph(file.inputStream(), graph)
              transcript?.write("Loaded initial graph from `${config.initial_graph_file}`\n".toByteArray())
            } catch (e: Exception) {
              transcript?.write("Error loading initial graph: ${e.message}\n".toByteArray())
            }
          }
        }
        val g: GraphTraversalSource = graph.traversal()

        val tabs = TabbedDisplay(task)
        val mainTask = tabs.newTask("Progress")

        // Load context
        val fileContext = try {
          super.getInputFileContent(config.related_files, root, treatDocumentsAsText = true)
        } catch (e: Exception) {
          log.warn("Failed to load input files: ${e.message}")
          ""
        }

        val fullContext = """
                    ${config.context_data ?: ""}
                    
                    $fileContext
                """.trimIndent()

        val chunkSize = 4000
        val contextChunks = fullContext.chunked(chunkSize).ifEmpty { listOf("") }
        var currentChunkIndex = 0

        var iteration = 0
        val api = defaultSmart.getChildClient(task)

        while (iteration < config.max_iterations && currentChunkIndex < contextChunks.size) {
          iteration++
          val currentText = contextChunks[currentChunkIndex]

          // 1. Serialize State
          val nodeCount = g.V().count().next()
          val edgeCount = g.E().count().next()

          if (nodeCount >= config.max_nodes) {
            transcript?.write("Max nodes reached ($nodeCount). Stopping.\n".toByteArray())
            break
          }
          if (edgeCount >= config.max_edges) {
            transcript?.write("Max edges reached ($edgeCount). Stopping.\n".toByteArray())
            break
          }

          val graphView = if (nodeCount < 50) {
            serializeGraphSimple(g)
          } else {
            "Graph Summary: $nodeCount nodes, $edgeCount edges. (Too large to display full state)"
          }

          mainTask.header("Iteration $iteration", 3)
          mainTask.add("Starting Nodes: $nodeCount, Edges: $edgeCount".renderMarkdown())

          // 2. Agent Decision
          val prompt = (typeConfig?.iterationPromptTemplate ?: "")
            .replace("{goal_prompt}", config.goal_prompt ?: "")
            .replace("{node_types}", config.node_types.toString())
            .replace("{edge_types}", config.edge_types.toString())
            .replace("{graph_view}", graphView)
            .replace("{chunk_index}", (currentChunkIndex + 1).toString())
            .replace("{total_chunks}", contextChunks.size.toString())
            .replace("{current_text}", currentText)

          val agentResponse = ParsedAgent(
            resultClass = GraphActionList::class.java,
            prompt = prompt,
            model = api,
            temperature = 0.2,
            parsingModel = api,
            singleStage = true
          ).answer(listOf("Analyze and update graph"))

          val response = agentResponse.obj
          transcript?.write("### Iteration $iteration\n".toByteArray())
          transcript?.write("<details><summary>Reasoning</summary>\n\n${response.reasoning}\n\n</details>\n".toByteArray())
          mainTask.expandable("Reasoning", response.reasoning.renderMarkdown())

          // 3. Apply Actions
          var actionsApplied = 0
          response.actions.forEach { action ->
            try {
              when (action.type) {
                "ADD_NODE" -> {
                  var traversal = g.V().hasLabel(action.label)
                  action.properties.forEach { (k, v) ->
                    if (k == "name" || k == "id") traversal = traversal.has(k, v)
                  }
                  if (!traversal.hasNext()) {
                    val v = g.addV(action.label).next()
                    action.properties.forEach { (k, valObj) ->
                      v.property(k, valObj)
                    }
                    actionsApplied++
                  }
                }

                "ADD_EDGE" -> {
                  val fromV = findVertex(g, action.from)
                  val toV = findVertex(g, action.to)
                  if (fromV != null && toV != null) {
                    if (!g.V(fromV.id()).out(action.label).where(`__`.hasId<Vertex>(toV.id()))
                        .hasNext()
                    ) {
                      val e = g.addE(action.label).from(fromV).to(toV).next()
                      action.properties.forEach { (k, valObj) ->
                        e.property(k, valObj)
                      }
                      actionsApplied++
                    }
                  }
                }

                "MERGE_NODES" -> {
                  val keepV = findVertex(g, action.target)
                  val removeV = findVertex(g, action.source)
                  if (keepV != null && removeV != null && keepV.id() != removeV.id()) {
                    g.V(removeV.id()).outE().forEachRemaining { e ->
                      g.addE(e.label()).from(keepV).to(e.inVertex()).next()
                    }
                    g.V(removeV.id()).inE().forEachRemaining { e ->
                      g.addE(e.label()).from(e.outVertex()).to(keepV).next()
                    }
                    g.V(removeV.id()).drop().iterate()
                    transcript?.write("Merged node `${removeV.id()}` into `${keepV.id()}`\n".toByteArray())
                    actionsApplied++
                  }
                }

                else -> transcript?.write("Unknown action type: ${action.type}\n".toByteArray())
              }
            } catch (e: Exception) {
              log.warn("Error applying action: $action", e)
              transcript?.write("Error applying action: $action - ${e.message}\n".toByteArray())
            }
          }
          transcript?.write("Applied $actionsApplied actions.\n".toByteArray())

          if (nodeCount > 0) {
            val mermaid = toMermaid(g)
            mainTask.add("```mermaid\n$mermaid\n```".renderMarkdown())
            transcript?.write("<details><summary>Graph Visualization</summary>\n\n```mermaid\n$mermaid\n```\n\n</details>\n".toByteArray())
          }

          if (response.is_finished) {
            transcript?.write("Agent signaled completion.\n".toByteArray())
            break
          }

          if (actionsApplied < 2 || iteration % 3 == 0) {
            currentChunkIndex++
            transcript?.write("Advancing to context chunk ${currentChunkIndex + 1}\n".toByteArray())
          }
        }

        // Export
        val os = ByteArrayOutputStream()
        GraphSONWriter.build().create().writeGraph(os, graph)
        val graphJSON = os.toByteArray()
        val fileUrl = task.saveFile("graph.json", graphJSON)

        val summary = """
                    ## Graph Generation Complete
                    * **Nodes:** ${g.V().count().next()}
                    * **Edges:** ${g.E().count().next()}
                    * **Artifact:** [Download Graph JSON]($fileUrl)
                """.trimIndent()

        mainTask.add(summary.renderMarkdown())
        task.update()
        task.safeComplete(summary, log)
        resultFn(summary)
      } catch (e: Exception) {
        task.error(e)
        log.error("Error in IterativeGraphGenerationTask", e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        throw e
      } finally {
        transcript?.close()
      }
    }
  }

  private fun findVertex(g: GraphTraversalSource, props: Map<String, Any>?): Vertex? {
    if (props == null || props.isEmpty()) return null
    // Try exact match first
    var t = g.V()
    props.forEach { (k, v) -> t = t.has(k, v) }
    if (t.hasNext()) return t.next()

    // Fallback: Case insensitive search for 'name' property
    if (props.containsKey("name")) {
      val nameVal = props["name"].toString().lowercase()
      val candidates = g.V().filter {
        it.get().property<String>("name").orElse("").lowercase() == nameVal
      }
      if (candidates.hasNext()) return candidates.next()
    }

    return null
  }

  private fun toMermaid(g: GraphTraversalSource): String {
    val sb = StringBuilder()
    sb.append("graph TD\n")
    g.V().forEachRemaining { v ->
      val label = v.label()
      val name =
        if (v.property<Any>("name").isPresent) v.property<Any>("name").value().toString() else v.id().toString()
      sb.append("  v${v.id()}[\"$label: ${name.replace("\"", "'")}\"]\n")
    }
    g.E().forEachRemaining { e ->
      sb.append("  v${e.outVertex().id()} -->|\"${e.label()}\"| v${e.inVertex().id()}\n")
    }
    return sb.toString()
  }


  private fun serializeGraphSimple(g: GraphTraversalSource): String {
    val sb = StringBuilder()
    sb.append("Nodes:\n")
    g.V().forEachRemaining { v ->
      sb.append(" - [${v.label()}] ")
      v.properties<Any>().forEachRemaining { p -> sb.append("${p.key()}=${p.value()}, ") }
      sb.append("\n")
    }
    sb.append("Edges:\n")
    g.E().forEachRemaining { e ->
      val from = e.outVertex()
      val to = e.inVertex()
      // Try to get a name property for display
      val fromName = if (from.property<Any>("name").isPresent) from.property<Any>("name").value() else from.id()
      val toName = if (to.property<Any>("name").isPresent) to.property<Any>("name").value() else to.id()
      sb.append(" - $fromName --[${e.label()}]--> $toName\n")
    }
    return sb.toString()
  }

  companion object {
    private val log = LoggerFactory.getLogger(IterativeGraphGenerationTask::class.java)

    @JvmStatic
    val IterativeGraphGeneration = TaskType(
      name = "IterativeGraphGeneration",
      category = "Writing",
      taskClass = IterativeGraphGenerationTask::class.java,
      executionConfigClass = IterativeGraphGenerationTaskExecutionConfigData::class.java,
      taskSettingsClass = IterativeGraphGenerationTaskTypeConfig::class.java,
      description = "Extract structured knowledge from unstructured data by iteratively building an entity-relationship graph.",
      tooltipHtml = """
                      Constructs a knowledge graph by iteratively analyzing context and adding nodes/edges.
                      <ul>
                        <li>Processes large contexts by chunking and iterative refinement</li>
                        <li>Supports custom schemas for nodes and edges</li>
                        <li>Visualizes progress using Mermaid diagrams</li>
                        <li>Allows merging nodes to resolve entities</li>
                        <li>Exports the final graph as GraphSON JSON</li>
                        <li>Ideal for mapping complex domains, research analysis, and knowledge extraction</li>
                      </ul>
                      """.trimIndent(),
    )
  }
}