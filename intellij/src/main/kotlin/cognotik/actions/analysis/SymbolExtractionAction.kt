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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.jetbrains.kotlin.com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtModifierListOwner
import java.io.File
import java.io.FileOutputStream
import java.util.*

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
                val graph = TinkerGraph.open()
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
                fun getOrCreateVertex(id: String, label: String): Vertex {
                    val iter = graph.vertices(id)
                    return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
                }


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
                                val fileV = getOrCreateVertex(fileId, "File")
                                fileV.property(VertexProperty.Cardinality.single, "name", virtualFile.name)
                                val scopeStack = Stack<Vertex>()

                                psiFile.accept(object : PsiRecursiveElementVisitor() {
                                    override fun visitElement(element: PsiElement) {
                                        var pushed = false
                                        if (element is PsiNamedElement) {
                                            element.name?.let { elementName ->
                                                val nodeId = "$fileId::$elementName"
                                                val symbolV = getOrCreateVertex(nodeId, "Symbol")
                                                symbolV.property(VertexProperty.Cardinality.single, "name", elementName)
                                                symbolV.property(VertexProperty.Cardinality.single, "file", fileId)
                                                val range = element.textRange
                                                if (range != null) {
                                                    symbolV.property(VertexProperty.Cardinality.single, "startOffset", range.startOffset)
                                                    symbolV.property(VertexProperty.Cardinality.single, "endOffset", range.endOffset)
                                                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                                                    if (document != null) {
                                                        symbolV.property(VertexProperty.Cardinality.single, "line", document.getLineNumber(range.startOffset) + 1)
                                                    }
                                                }
                                                if (element is KtModifierListOwner) {
                                                    element.modifierList?.let { modList ->
                                                        val visibility = when {
                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("public")) -> "public"
                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("private")) -> "private"
                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("internal")) -> "internal"
                                                            else -> "package"
                                                        }
                                                        symbolV.property(VertexProperty.Cardinality.single, "visibility", visibility)
                                                        val modifiers = listOf(PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.ABSTRACT, PsiModifier.SYNCHRONIZED)
                                                            .filter { m -> modList.hasModifier(KtModifierKeywordToken.keywordModifier(m.lowercase())) }
                                                        if (modifiers.isNotEmpty()) symbolV.property(VertexProperty.Cardinality.single, "modifiers", modifiers.joinToString(","))
                                                        val annotations = modList.annotations.mapNotNull { a -> a.name }
                                                        if (annotations.isNotEmpty()) symbolV.property(VertexProperty.Cardinality.single, "annotations", annotations.joinToString(","))
                                                    }
                                                }

                                                scopeStack.push(symbolV)
                                                pushed = true
                                                if(verbose) log.trace("Found definition: $elementName")
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
                                                            val targetV = getOrCreateVertex(targetId, "Symbol")
                                                            if (!targetV.properties<Any>("name").hasNext()) {
                                                                targetV.property(VertexProperty.Cardinality.single, "name", name)
                                                                targetV.property(VertexProperty.Cardinality.single, "file", resolvedFile)
                                                            }
                                                            val sourceV =
                                                                if (scopeStack.isNotEmpty()) scopeStack.peek() else fileV
                                                            sourceV.addEdge("REFERENCES", targetV)
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
                    
                    val jsonFile = File(project.basePath, "symbol_graph.json")
                    FileOutputStream(jsonFile).use { os ->
                        GraphSONWriter.build().create().writeGraph(os, graph)
                    }
                    

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "Symbol graph saved to ${jsonFile.absolutePath}", "Analysis Complete")
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

    companion object {
        val log = LoggerFactory.getLogger(SymbolExtractionAction::class.java)
    }
}