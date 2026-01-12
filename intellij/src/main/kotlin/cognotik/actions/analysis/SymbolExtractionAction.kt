package cognotik.actions.analysis

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.util.LoggerFactory
//import org.jetbrains.kotlin.com.intellij.psi.PsiModifier
//import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
//import org.jetbrains.kotlin.psi.KtModifierListOwner
import java.io.File
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
                val service = SymbolGraphService()
                val jsonFile = File(project.basePath, "symbol_graph.json")
                if (jsonFile.exists()) {
                    try {
                        service.load(jsonFile)
                    } catch (e: Exception) {
                        log.warn("Error loading existing symbol graph", e)
                    }
                }
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
                val currentFilePaths = fileList.map { it.path }.toSet()
                val graphFilePaths = service.listFileIds()
                (graphFilePaths - currentFilePaths).forEach {
                    if (verbose) log.info("Removing deleted file from graph: $it")
                    service.removeFile(it)
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
                    val fileId = virtualFile.path
                    val lastModified = virtualFile.timeStamp
                    val storedModified = service.getLastModified(fileId)
                    if (storedModified != null && storedModified == lastModified) {
                        return@forEachIndexed
                    }
                    var fileAnnotation: FileAnnotation? = null
                    try {
                        val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile)
                        fileAnnotation = vcs?.annotationProvider?.annotate(virtualFile)
                    } catch (e: Exception) {
                        if (verbose) log.warn("Error getting VCS annotation for ${virtualFile.path}", e)
                    }


                    ReadAction.run<Throwable> {
                        if (virtualFile.isValid) {
                            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                            if (psiFile != null) {
                                if(verbose) log.debug("Analyzing file: ${virtualFile.path}")
                                

                                service.addFile(fileId, virtualFile.name, lastModified)
                                service.clearOutgoingReferences(fileId)
                                val foundSymbolIds = mutableSetOf<String>()
                                val scopeStack = Stack<String>()

                                psiFile.accept(object : PsiRecursiveElementVisitor() {
                                    override fun visitElement(element: PsiElement) {
                                        var pushed = false
                                        if (element is PsiNamedElement) {
                                            element.name?.let { elementName ->
                                                val nodeId = "$fileId::$elementName"
                                                var startOffset: Int? = null
                                                var endOffset: Int? = null
                                                var line: Int? = null
                                                var symbolLastModified: Long? = null
                                                val range = element.textRange
                                                if (range != null) {
                                                    startOffset = range.startOffset
                                                    endOffset = range.endOffset
                                                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                                                    if (document != null) {
                                                        line = document.getLineNumber(range.startOffset) + 1
                                                        if (fileAnnotation != null) {
                                                            try {
                                                                symbolLastModified = fileAnnotation?.getLineDate(line!! - 1)?.time
                                                            } catch (e: Exception) {
                                                                // ignore
                                                            }
                                                        }
                                                    }
                                                }
                                                var visibility: String? = null
                                                var modifiersStr: String? = null
                                                var annotationsStr: String? = null
//                                                if (element is KtModifierListOwner) {
//                                                    element.modifierList?.let { modList ->
//                                                        visibility = when {
//                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("public")) -> "public"
//                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("private")) -> "private"
//                                                            modList.hasModifier(KtModifierKeywordToken.keywordModifier("internal")) -> "internal"
//                                                            else -> "package"
//                                                        }
//                                                        val modifiers = listOf(PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.ABSTRACT, PsiModifier.SYNCHRONIZED)
//                                                            .filter { m -> modList.hasModifier(KtModifierKeywordToken.keywordModifier(m.lowercase())) }
//                                                        if (modifiers.isNotEmpty()) modifiersStr = modifiers.joinToString(",")
//                                                        val annotations = modList.annotations.mapNotNull { a -> a.name }
//                                                        if (annotations.isNotEmpty()) annotationsStr = annotations.joinToString(",")
//                                                    }
//                                                }

                                                service.addSymbol(nodeId, elementName, fileId, startOffset, endOffset, line, visibility, modifiersStr, annotationsStr, symbolLastModified)
                                                foundSymbolIds.add(nodeId)
                                                scopeStack.push(nodeId)
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
                                                            val sourceId = if (scopeStack.isNotEmpty()) scopeStack.peek() else fileId
                                                            service.addReference(sourceId, targetId, name, resolvedFile)
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
                                service.pruneRemovedSymbols(fileId, foundSymbolIds)
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
                    
                    service.save(jsonFile.absolutePath)
                    

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