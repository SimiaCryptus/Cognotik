package cognotik.actions.analysis

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.io.File
import java.util.Stack

class SymbolExtractionAction : BaseAction() {

    val verbose = false
    override fun isEnabled(event: AnActionEvent): Boolean {
        return true
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            log.warn("Project is null")
            return
        }
        if(verbose) log.info("Starting symbol extraction for project: ${project.name}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Extracting Symbols", true) {
            override fun run(indicator: ProgressIndicator) {
                if(verbose) log.info("Background task started")
                val nodes = mutableMapOf<String, GraphNode>()
                val edges = mutableListOf<GraphEdge>()
                val fileList = mutableListOf<VirtualFile>()

                ReadAction.run<Throwable> {
                    if(verbose) log.info("Collecting source roots")
                    ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
                        if(verbose) log.info("Processing root: ${root.path}")
                        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
                            if (!file.isDirectory) {
                                fileList.add(file)
                            }
                            true
                        }
                    }
                    if(verbose) log.info("Collected ${fileList.size} files")
                }

                indicator.isIndeterminate = false
                val totalFiles = fileList.size

                fileList.forEachIndexed { index, virtualFile ->
                    if (indicator.isCanceled) {
                        if(verbose) log.warn("Task canceled")
                        return
                    }
                    indicator.fraction = index.toDouble() / totalFiles
                    indicator.text = "Processing ${virtualFile.name} ($index/$totalFiles)"

                    ReadAction.run<Throwable> {
                        if (virtualFile.isValid) {
                            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                            if (psiFile != null) {
                                if(verbose) log.debug("Analyzing file: ${virtualFile.path}")
                                
                                val fileId = virtualFile.path
                                nodes[fileId] = GraphNode(fileId, "File", mapOf("name" to virtualFile.name))
                                val scopeStack = Stack<String>()

                                psiFile.accept(object : PsiRecursiveElementVisitor() {
                                    override fun visitElement(element: PsiElement) {
                                        var pushed = false
                                        if (element is PsiNamedElement) {
                                            element.name?.let {
                                                val nodeId = "$fileId::$it"
                                                nodes[nodeId] = GraphNode(nodeId, "Symbol", mapOf("name" to it, "file" to fileId))
                                                scopeStack.push(nodeId)
                                                pushed = true
                                                if(verbose) log.trace("Found definition: $it")
                                            }
                                        }
                                        try {
                                            for (ref in element.references) {
                                                try {
                                                    val resolved = ref.resolve()
                                                    if (resolved is PsiNamedElement) {
                                                        val resolvedFile = resolved.containingFile?.virtualFile?.path
                                                        val name = resolved.name
                                                        if (name != null && resolvedFile != null) {
                                                            val targetId = "$resolvedFile::$name"
                                                            if (!nodes.containsKey(targetId)) {
                                                                nodes[targetId] = GraphNode(
                                                                    targetId,
                                                                    "Symbol",
                                                                    mapOf("name" to name, "file" to resolvedFile)
                                                                )
                                                            }
                                                            val sourceId =
                                                                if (scopeStack.isNotEmpty()) scopeStack.peek() else fileId
                                                            edges.add(GraphEdge(sourceId, targetId, "REFERENCES"))
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    if (verbose) log.warn(
                                                        "Error resolving reference in ${virtualFile.name}",
                                                        e
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            if (verbose) log.warn(
                                                "Error processing element in ${virtualFile.name}",
                                                e
                                            )
                                        }
                                        super.visitElement(element)
                                        if (pushed) {
                                            scopeStack.pop()
                                        }
                                    }
                                })
                            } else {
                                if(verbose) log.warn("PsiFile not found for ${virtualFile.path}")
                            }
                        } else {
                            if(verbose) log.warn("VirtualFile is invalid: ${virtualFile.path}")
                        }
                    }
                }

                try {
                    if(verbose) log.info("Serializing result")
                    
                    val json = toJson(nodes.values, edges)
                    val jsonFile = File(project.basePath, "symbol_graph.json")
                    jsonFile.writeText(json)
                    
                    val graphml = toGraphML(nodes.values, edges)
                    val graphmlFile = File(project.basePath, "symbol_graph.graphml")
                    graphmlFile.writeText(graphml)

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "Symbol graph saved to ${jsonFile.absolutePath} and ${graphmlFile.absolutePath}", "Analysis Complete")
                    }
                } catch (e: Exception) {
                    log.error("Error saving analysis", e)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Error saving analysis: ${e.message}", "Error")
                    }
                }
            }
        })
    }

    private fun toJson(nodes: Collection<GraphNode>, edges: List<GraphEdge>): String {
        val sb = StringBuilder()
        sb.append("{\n  \"nodes\": [\n")
        nodes.forEachIndexed { i, node ->
            if (i > 0) sb.append(",\n")
            sb.append("    { \"id\": \"${escape(node.id)}\", \"label\": \"${escape(node.label)}\", \"properties\": { ")
            val props = node.properties.entries.joinToString(", ") { "\"${escape(it.key)}\": \"${escape(it.value.toString())}\"" }
            sb.append(props).append(" } }")
        }
        sb.append("\n  ],\n  \"edges\": [\n")
        edges.forEachIndexed { i, edge ->
            if (i > 0) sb.append(",\n")
            sb.append("    { \"source\": \"${escape(edge.source)}\", \"target\": \"${escape(edge.target)}\", \"label\": \"${escape(edge.label)}\" }")
        }
        sb.append("\n  ]\n}")
        return sb.toString()
    }
    private fun toGraphML(nodes: Collection<GraphNode>, edges: List<GraphEdge>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n")
        sb.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n")
        sb.append("     http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n")
        sb.append("  <key id=\"label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n")
        sb.append("  <key id=\"name\" for=\"node\" attr.name=\"name\" attr.type=\"string\"/>\n")
        sb.append("  <key id=\"file\" for=\"node\" attr.name=\"file\" attr.type=\"string\"/>\n")
        sb.append("  <graph id=\"G\" edgedefault=\"directed\">\n")
        nodes.forEach { node ->
            sb.append("    <node id=\"${escapeXML(node.id)}\">\n")
            sb.append("      <data key=\"label\">${escapeXML(node.label)}</data>\n")
            node.properties.forEach { (k, v) ->
                sb.append("      <data key=\"${escapeXML(k)}\">${escapeXML(v.toString())}</data>\n")
            }
            sb.append("    </node>\n")
        }
        edges.forEach { edge ->
            sb.append("    <edge source=\"${escapeXML(edge.source)}\" target=\"${escapeXML(edge.target)}\" label=\"${escapeXML(edge.label)}\"/>\n")
        }
        sb.append("  </graph>\n</graphml>")
        return sb.toString()
    }
    
    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun escapeXML(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    data class GraphNode(val id: String, val label: String, val properties: Map<String, Any>)
    data class GraphEdge(val source: String, val target: String, val label: String)

    companion object {
        val log = LoggerFactory.getLogger(SymbolExtractionAction::class.java)
    }
}