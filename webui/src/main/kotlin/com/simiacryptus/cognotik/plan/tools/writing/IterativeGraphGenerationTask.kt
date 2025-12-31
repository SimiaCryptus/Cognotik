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
import com.simiacryptus.cognotik.webui.session.newLogStream
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
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
        @Description("Optional JSON file to initialize graph from")
        val initial_graph_file: String? = null,


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
    ) : ValidatedObject
    data class GraphAction(
        @Description("Type of action to perform: ADD_NODE, ADD_EDGE, or MERGE_NODES")
        val type: String = "ADD_NODE", // ADD_NODE, ADD_EDGE, MERGE_NODES
        @Description("Label of the node or edge (REQUIRED for ADD_*)")
        val label: String = "",
        @Description("Properties of the node or edge")
        val properties: Map<String, Any> = emptyMap(),
        @Description("For ADD_EDGE: properties to identify the 'from' node")
        val from: Map<String, Any>? = null,
        @Description("For ADD_EDGE: properties to identify the 'to' node")
        val to: Map<String, Any>? = null,
        @Description("For MERGE_NODES: The properties of the node to keep")
        val target: Map<String, Any>? = null,
        @Description("For MERGE_NODES: The properties of the node to remove (merging into target)")
        val source: Map<String, Any>? = null
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
        val logStream = task.newLogStream("GraphGeneration.log")
        val transcript = OutputStreamWriter(logStream)

        transcript.write("# Iterative Graph Generation\n\n")
        transcript.write("Goal: ${config.goal_prompt}\n\n")
        transcript.flush()

        val graph: Graph = TinkerGraph.open()
        if (!config.initial_graph_file.isNullOrBlank()) {
            val file = task.resolveUserFile(config.initial_graph_file)
            if (file?.exists() == true) {
                try {
                    GraphSONReader.build().create().readGraph(file.inputStream(), graph)
                    transcript.write("Loaded initial graph from ${config.initial_graph_file}\n")
                } catch (e: Exception) {
                    transcript.write("Error loading initial graph: ${e.message}\n")
                }
            }
        }
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
                transcript.write("Max nodes reached ($nodeCount). Stopping.\n")
                break
            }
            if (edgeCount >= config.max_edges) {
                transcript.write("Max edges reached ($edgeCount). Stopping.\n")
                break
            }

            val graphView = if (nodeCount < 50) {
                serializeGraphSimple(g)
            } else {
                "Graph Summary: $nodeCount nodes, $edgeCount edges. (Too large to display full state)"
            }

            mainTask.header("Iteration $iteration", 3)
            mainTask.add("Starting Nodes: $nodeCount, Edges: $edgeCount")

            // 2. Agent Decision
            val prompt = """
                Goal: ${config.goal_prompt}
                
                Schema:
                Node Types: ${config.node_types}
                Edge Types: ${config.edge_types}
                
                Current Graph State:
                $graphView
                
                Context Data (${currentChunkIndex + 1}/${contextChunks.size}):
                $currentText
                
                Provide a list of actions to expand the graph based on the context and goal.
                If the graph is complete, set is_finished to true.
                
                For ADD_EDGE, 'from' and 'to' must be maps of properties that uniquely identify existing nodes.
                For ADD_NODE, provide properties that uniquely identify the node (e.g. name).
                For MERGE_NODES, provide 'target' (keep) and 'source' (remove) properties to identify nodes.
            """.trimIndent()

            val agentResponse = ParsedAgent(
                resultClass = GraphActionList::class.java,
                prompt = prompt,
                model = api,
                temperature = 0.2,
                parsingChatter = api,
                singleStage = true
            ).answer(listOf("Analyze and update graph"))

            val response = agentResponse.obj
            transcript.write("Reasoning: ${response.reasoning}\n")
            mainTask.expandable("Reasoning", "<pre>${response.reasoning}</pre>")

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
                        "MERGE_NODES" -> {
                            val keepV = findVertex(g, action.target)
                            val removeV = findVertex(g, action.source)
                            if (keepV != null && removeV != null && keepV.id() != removeV.id()) {
                                // Move outgoing edges
                                g.V(removeV.id()).outE().forEachRemaining { e ->
                                    g.addE(e.label()).from(keepV).to(e.inVertex()).next()
                                }
                                // Move incoming edges
                                g.V(removeV.id()).inE().forEachRemaining { e ->
                                    g.addE(e.label()).from(e.outVertex()).to(keepV).next()
                                }
                                // Drop the old node
                                g.V(removeV.id()).drop().iterate()
                                transcript.write("Merged node ${removeV.id()} into ${keepV.id()}\n")
                                actionsApplied++
                            }
                        }


                        else -> {
                            transcript.write("Unknown action type: ${action.type}\n")
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Error applying action: $action", e)
                    transcript.write("Error applying action: $action - ${e.message}\n")
                }
            }
            transcript.write("Applied $actionsApplied actions.\n")
            transcript.flush()

            if (nodeCount > 0) mainTask.add("```mermaid\n${toMermaid(g)}\n```".renderMarkdown)

            if (response.is_finished) {
                transcript.write("Agent signaled completion.\n")
                break
            }

            if (actionsApplied < 2 || iteration % 3 == 0) {
                currentChunkIndex++
                transcript.write("Advancing to context chunk ${currentChunkIndex + 1}\n")
            }
        }

        // Export
        val os = ByteArrayOutputStream()

        GraphSONWriter.build().create().writeGraph(os, graph)

        val graphJSON = os.toByteArray()
        val fileUrl = task.saveFile("graph.json", graphJSON)

        val summary = "Graph generation complete. Nodes: ${g.V().count().next()}, Edges: ${
            g.E().count().next()
        }."
        mainTask.add(summary)
        mainTask.add("<a href='$fileUrl'>Download Graph JSON</a>")
        task.update()

        task.safeComplete(summary, log)
        resultFn(summary)
        transcript.close()
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