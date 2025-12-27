package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

open class IterativeGraphGenerationTask<T : IterativeGraphGenerationTask.IterativeGraphGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class IterativeGraphGenerationTaskExecutionConfigData(
        @Description("The goal or question the graph should answer/represent")
        val goal_prompt: String? = null,

        @Description("Input text to analyze")
        val context_data: String? = null,

        @Description("Input files to analyze")
        val input_files: List<String>? = null,

        @Description("Maximum number of iterations")
        val max_iterations: Int = 20,

        @Description("Maximum number of nodes")
        val max_nodes: Int = 50,

        @Description("Maximum number of edges")
        val max_edges: Int = 100,

        @Description("Allowed node types (labels)")
        val node_types: List<String> = listOf("Concept", "Entity"),

        @Description("Allowed edge types (labels)")
        val edge_types: List<String> = listOf("RELATES_TO"),

        task_dependencies: List<String>? = null,
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

    data class GraphActionList(
        val reasoning: String = "",
        val actions: List<GraphAction> = emptyList(),
        val is_finished: Boolean = false
    )

    data class GraphAction(
        @Description("Type of action to perform: ADD_NODE or ADD_EDGE")
        val type: String = "ADD_NODE", // ADD_NODE, ADD_EDGE
        @Description("Label of the node or edge")
        val label: String = "",
        @Description("Properties of the node or edge")
        val properties: Map<String, Any> = emptyMap(),
        @Description("For ADD_EDGE: properties to identify the 'from' node")
        val from: Map<String, Any>? = null,
        @Description("For ADD_EDGE: properties to identify the 'to' node")
        val to: Map<String, Any>? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (type != "ADD_NODE" && type != "ADD_EDGE") return "Invalid action type: $type"
            if (label.isBlank()) return "Label is required"
            if (type == "ADD_EDGE") {
                if (from.isNullOrEmpty()) return "From properties are required for ADD_EDGE"
                if (to.isNullOrEmpty()) return "To properties are required for ADD_EDGE"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
IterativeGraphGeneration - Build knowledge graphs incrementally
  ** Specify goal_prompt and context
  ** Define schema (node_types, edge_types)
  ** Iteratively observes graph state and adds nodes/edges
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val config = executionConfig!!
        val transcript = task.transcript("GraphGeneration")?.let { OutputStreamWriter(it) }

        transcript?.write("# Iterative Graph Generation\n\n")
        transcript?.write("Goal: ${config.goal_prompt}\n\n")

        val graph: Graph = TinkerGraph.open()
        val g: GraphTraversalSource = graph.traversal()

        val tabs = TabbedDisplay(task)
        val mainTask = task.ui.newTask(false)
        tabs["Progress"] = mainTask.placeholder

        // Load context
        val fileContext = try {
            super.getInputFileContent(config.input_files, root, treatDocumentsAsText = true)
        } catch (e: Exception) {
            log.warn("Failed to load input files", e)
            ""
        }

        val fullContext = """
            ${config.context_data ?: ""}
            
            $fileContext
        """.trimIndent()

        var iteration = 0
        val api = defaultSmart.getChildClient(task)

        while (iteration < config.max_iterations) {
            iteration++

            // 1. Serialize State
            val nodeCount = g.V().count().next()
            val edgeCount = g.E().count().next()

            if (nodeCount >= config.max_nodes) {
                transcript?.write("Max nodes reached ($nodeCount). Stopping.\n")
                break
            }
            if (edgeCount >= config.max_edges) {
                transcript?.write("Max edges reached ($edgeCount). Stopping.\n")
                break
            }

            val graphView = if (nodeCount < 50) {
                serializeGraphSimple(g)
            } else {
                "Graph Summary: $nodeCount nodes, $edgeCount edges. (Too large to display full state)"
            }

            mainTask.add("### Iteration $iteration\nNodes: $nodeCount, Edges: $edgeCount\n".renderMarkdown)
            task.update()

            // 2. Agent Decision
            val prompt = """
                Goal: ${config.goal_prompt}
                
                Schema:
                Node Types: ${config.node_types}
                Edge Types: ${config.edge_types}
                
                Current Graph State:
                $graphView
                
                Context Data:
                ${fullContext.take(5000)} ... (truncated)
                
                Provide a list of actions to expand the graph based on the context and goal.
                If the graph is complete, set is_finished to true.
                
                For ADD_EDGE, 'from' and 'to' must be maps of properties that uniquely identify existing nodes.
                For ADD_NODE, provide properties that uniquely identify the node (e.g. name).
            """.trimIndent()

            val agentResponse = ParsedAgent(
                resultClass = GraphActionList::class.java,
                prompt = prompt,
                model = api,
                temperature = 0.2,
                parsingChatter = api,
            ).answer(listOf("Analyze and update graph"))

            val response = agentResponse.obj
            transcript?.write("## Iteration $iteration\n")
            transcript?.write("Reasoning: ${response.reasoning}\n")

            if (response.is_finished) {
                transcript?.write("Agent signaled completion.\n")
                break
            }

            // 3. Apply Actions
            var actionsApplied = 0
            response.actions.forEach { action ->
                try {
                    when (action.type) {
                        "ADD_NODE" -> {
                            // Check if exists
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
                                // Check if edge exists
                                if (!g.V(fromV.id()).out(action.label).where(`__`.hasId<Vertex>(toV.id())).hasNext()) {
                                    val e = g.addE(action.label).from(fromV).to(toV).next()
                                    action.properties.forEach { (k, valObj) ->
                                        e.property(k, valObj)
                                    }
                                    actionsApplied++
                                }
                            }
                        }

                        else -> {
                            transcript?.write("Unknown action type: ${action.type}\n")
                        }
                    }
                } catch (e: Exception) {
                    transcript?.write("Error applying action: $action - ${e.message}\n")
                }
            }
            transcript?.write("Applied $actionsApplied actions.\n\n")
            transcript?.flush()

            if (actionsApplied == 0 && !response.is_finished) {
                transcript?.write("No actions applied. Stopping to prevent loop.\n")
                break
            }
        }

        // Export
        val os = ByteArrayOutputStream()

        GraphSONWriter.build().create().writeGraph(os, graph)
        val graphJSON = os.toString()

        task.resolveUserFile("graph.json")?.writeText(graphJSON)

        val summary = "Graph generation complete. Nodes: ${g.V().count().next()}, Edges: ${g.E().count().next()}"
        mainTask.add(summary.renderMarkdown)
        task.update()

        task.safeComplete(summary, log)
        resultFn(summary)
        transcript?.close()
    }

    private fun findVertex(g: GraphTraversalSource, props: Map<String, Any>?): Vertex? {
        if (props == null || props.isEmpty()) return null
        var t = g.V()
        props.forEach { (k, v) -> t = t.has(k, v) }
        return if (t.hasNext()) t.next() else null
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
        val IterativeGraphGeneration = TaskType(
            "IterativeGraphGeneration",
            "Writing",
            IterativeGraphGenerationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Iteratively build a knowledge graph",
            "Constructs a knowledge graph by iteratively analyzing context and adding nodes/edges."
        )
    }
}