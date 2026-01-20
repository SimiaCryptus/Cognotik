# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/analysis/SymbolExtractionAction.kt

```
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
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new README file for the `SymbolExtractionAction` component.

### intellij/src/main/kotlin/cognotik/actions/analysis/README.md
```markdown
# Symbol Extraction Action

The `SymbolExtractionAction` is an IntelliJ IDEA action responsible for analyzing the project's source code to build and maintain a comprehensive symbol graph. This graph maps definitions and references across the codebase, providing a foundation for advanced code navigation and analysis features.

## Features

- **Incremental Analysis**: The action tracks file timestamps and only re-processes files that have changed since the last analysis, significantly improving performance on large projects.
- **Symbol Definition Tracking**: Identifies named elements (classes, methods, variables, etc.) using the IntelliJ PSI (Program Structure Interface).
- **Reference Resolution**: Resolves cross-references between symbols to build a directed graph of dependencies.
- **VCS Integration**: Leverages version control information to associate specific symbols with their last modification dates.
- **Background Processing**: Runs as a background task with a progress indicator, ensuring the IDE remains responsive during analysis.
- **Persistence**: Serializes the resulting symbol graph to a `symbol_graph.json` file located in the project's base directory.

## Implementation Details

### Core Components
- **SymbolGraphService**: The underlying service used to manage the graph data structure, handle serialization, and perform pruning of stale data.
- **PsiRecursiveElementVisitor**: Used to traverse the PSI tree of each file to extract symbols and their references.
- **ReadAction**: Ensures that code analysis is performed safely on the IntelliJ read thread.

### Workflow
1. **Initialization**: Loads the existing `symbol_graph.json` if present.
2. **Cleanup**: Identifies and removes files from the graph that no longer exist in the project.
3. **Scanning**: Iterates through all content source roots.
4. **Processing**: For each modified file:
    - Clears old outgoing references.
    - Visits every PSI element to find definitions (`PsiNamedElement`).
    - Resolves references to determine target symbols.
    - Captures metadata such as line numbers, offsets, and VCS timestamps.
5. **Pruning**: Removes symbols from the graph that are no longer present in the updated files.
6. **Serialization**: Saves the updated graph back to disk.

## Usage

This action is typically triggered via the IDE's action system (e.g., via a menu item or keyboard shortcut). Upon completion, a notification is displayed indicating the location of the saved symbol graph.

## Potential Impacts

- **Disk I/O**: Writing the `symbol_graph.json` file can be intensive for very large projects.
- **Memory Usage**: The symbol graph is held in memory during the analysis process.
- **VCS Performance**: Requesting annotations for every file can be slow depending on the VCS provider (e.g., Git, SVN).
```

### Summary of Changes
- **New File Created**: `intellij/src/main/kotlin/cognotik/actions/analysis/README.md`.
- **Content**: Documented the purpose, features, and technical implementation of the `SymbolExtractionAction` class.
- **Context**: Explained how the action interacts with the IntelliJ PSI, the `SymbolGraphService`, and the project's file system.
- **Rationale**: Provides maintainers and users with a clear understanding of how the symbol extraction process works and where the output is stored.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/analysis/README.md'>intellij/src/main/kotlin/cognotik/actions/analysis/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/analysis/README.md'>intellij/src/main/kotlin/cognotik/actions/analysis/README.md</a> Updated
