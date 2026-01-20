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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/BaseAction.kt

```
package cognotik.actions

/**
 * Base action class providing common functionality for AI Coder actions.
 * Handles API client initialization and common UI operations.
 */
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.UITools
import javax.swing.Icon

abstract class BaseAction(
    name: String? = null,
    description: String? = null,
    icon: Icon? = null,
) : AnAction(name, description, icon) {

    private val log by lazy { LoggerFactory.getLogger(javaClass) }

    final override fun update(event: AnActionEvent) {
        val currentThread = Thread.currentThread()
        val scheduledFuture = scheduledPool.schedule({
            if (event.presentation.isEnabledAndVisible) {
                log.warn(
                    "Slow update: ${javaClass.simpleName} took too long; ${currentThread.name}\n\t${
                        currentThread.stackTrace.joinToString(
                            "\n\t"
                        )
                    }"
                )
            }
        }, 1, java.util.concurrent.TimeUnit.SECONDS)
        event.presentation.isEnabledAndVisible = isEnabled(event)
        scheduledFuture.cancel(false)
        super.update(event)
    }

    /**
     * Handle the action event
     * @param e The action event to handle
     */
    abstract fun handle(e: AnActionEvent)

    /** Determines if this action is enabled in the current context */

    final override fun actionPerformed(e: AnActionEvent) {
        UITools.logAction(
            "Action: ${javaClass.simpleName}".trim()
        )
        AppSettingsState.lastEvent = e
        try {
            handle(e)
        } catch (e: IllegalStateException) {
            UITools.error(log, "Invalid state in Action ${javaClass.simpleName}", e)
        } catch (e: IllegalArgumentException) {
            UITools.error(log, "Invalid input in Action ${javaClass.simpleName}", e)
        } catch (e: Throwable) {
            UITools.error(log, "Unexpected error in Action ${javaClass.simpleName}", e)
        }
    }

    open fun isEnabled(event: AnActionEvent): Boolean {
        if (AppSettingsState.instance.smartModel == null) return false
        if (AppSettingsState.instance.fastModel == null) return false
        return true
    }

    companion object {
        val log by lazy { LoggerFactory.getLogger(javaClass) }
        val scheduledPool = java.util.concurrent.Executors.newScheduledThreadPool(1).apply {
            ApplicationManager.getApplication().executeOnPooledThread {
                Runtime.getRuntime().addShutdownHook(Thread {
                    this.shutdown()
                    this.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
                })
            }
        }
    }
}

```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/FileContextAction.kt

```
package cognotik.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.*
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

abstract class FileContextAction<T : Any>(
    private val supportsFiles: Boolean = true,
    private val supportsFolders: Boolean = true,
) : BaseAction() {

    data class SelectionState(
        val selectedFile: File,
        val projectRoot: File,
    )

    abstract fun processSelection(state: SelectionState, config: T?, progress: ProgressIndicator): Array<File>

    final override fun handle(e: AnActionEvent) {
        val config = getConfig(e.project, e)
        if (config == null) {
            log.warn("No configuration found for ${javaClass.simpleName}")
            return
        }
        val virtualFile = e.getSelectedFile() ?: e.getSelectedFolder() ?: run {
            log.warn("No file or folder selected")
            return
        }
        val project = e.project ?: return
        val projectBasePath = project.basePath ?: run {
            log.error("Project base path is null")
            return
        }
        val projectRoot = File(projectBasePath).toPath()
        Thread {
            try {
                e.redoableTask {
                    UITools.run(e.project, templateText!!, true) { progress ->
                        val newFiles = try {
                            processSelection(
                                SelectionState(
                                    selectedFile = virtualFile.toNioPath().toFile(),
                                    projectRoot = projectRoot.toFile(),
                                ), config, progress
                            )
                        } catch (ex: Exception) {
                            log.error("Error processing selection", ex)
                            throw ex
                        } finally {
                            if (progress.isCanceled) throw InterruptedException()
                        }
                        val start = System.currentTimeMillis()
                        val fileSystem = LocalFileSystem.getInstance()
                        val firstFile = newFiles.firstOrNull() ?: throw IllegalStateException("No files were generated")
                        var refreshedFile: VirtualFile? = null
                        while (refreshedFile == null) {
                            if (System.currentTimeMillis() - start > 10000) {
                                throw IllegalStateException("Timeout waiting for file to appear: ${firstFile.absolutePath}")
                            }
                            refreshedFile = fileSystem.refreshAndFindFileByIoFile(firstFile)
                            Thread.sleep(500)
                        }
                        e.writeableFn {
                            val files = newFiles.mapNotNull { file ->
                                val generatedFile = fileSystem.refreshAndFindFileByIoFile(file)
                                if (generatedFile == null) {
                                    log.warn("Generated file not found: ${file.path}")
                                } else {
                                    open(project, file.toPath())
                                }
                                generatedFile
                            }.toTypedArray<VirtualFile?>()
                            Runnable {
                                files.forEach { it?.delete(this@FileContextAction) }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                UITools.error(log, "Error in ${javaClass.simpleName}", e)
            }
        }.start()
    }

    open fun getConfig(project: Project?, e: AnActionEvent): T? = null

    var isDevAction = false
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (isDevAction && !AppSettingsState.instance.devActions) return false
        val virtualFile = event.getSelectedFile() ?: event.getSelectedFolder() ?: return false
        return if (virtualFile.isDirectory) supportsFolders else supportsFiles
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileContextAction::class.java)

        fun open(project: Project, outputPath: Path) {
            log.info("Opening file: $outputPath")
            lateinit var function: () -> Unit
            function = {
                val file = outputPath.toFile()
                if (file.exists()) {

                    ApplicationManager.getApplication().invokeLater {
                        val ioFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                        if (false == (ioFile?.let { FileEditorManager.getInstance(project).isFileOpen(it) })) {
                            val localFileSystem = LocalFileSystem.getInstance()

                            val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file)
                            virtualFile?.let {
                                FileEditorManager.getInstance(project).openFile(it, true)
                            } ?: scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
                        } else {
                            scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
                        }
                    }
                } else {
                    scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
                }
            }
            scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
        }

    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/README.md

```
# AI Coder Plugin User Guide

This comprehensive guide covers all available actions in the AI Coder plugin, organized by category to help you maximize
your productivity.

## Table of Contents

- [Editor Actions](#editor-actions)
- [Chat Actions](#chat-actions)
- [Git Integration](#git-integration)
- [Code Generation](#code-generation)
- [Markdown Tools](#markdown-tools)
- [Knowledge Management](#knowledge-management)
- [Development Tools](#development-tools)
- [Problem Solving](#problem-solving)
- [Multi-file Operations](#multi-file-operations)

## Editor Actions

### Smart Paste & Fast Paste

**Location**: Right-click menu > AI Coder > Paste

- **Smart Paste**: Intelligently formats clipboard content to match your code style using a powerful model
- **Fast Paste**: Similar to Smart Paste but uses a faster, simpler model for quicker results
- **Usage**: Copy code from any source, then use Smart/Fast Paste to automatically format it to match your current file

### Describe Action

**Location**: Right-click menu > AI Coder > Describe

- Generates descriptive comments for selected code
- Automatically formats comments according to the language (line or block comments)
- Helps document your code with minimal effort

### Custom Edit Action

**Location**: Right-click menu > AI Coder > Custom Edit

- Allows you to specify custom transformations for selected code
- Enter instructions like "Add error handling", "Optimize for performance", etc.
- Your recent edit instructions are saved for quick reuse

### Recent Code Edits

**Location**: Right-click menu > AI Coder > Recent Edits

- Quick access to your most recently used custom edit instructions
- Numbered for easy keyboard access (e.g., _1, _2, etc.)

### Redo Last

**Location**: Right-click menu > AI Coder > Redo Last

- Repeats the last AI Coder action you performed
- Useful for applying the same transformation to different code sections

## Chat Actions

### Code Chat

**Location**: Right-click menu > AI Coder > Chat > Code Chat

- Opens a chat interface focused on the current file
- Ask questions about the code, request explanations, or get suggestions
- The AI has full context of your code for accurate responses

### Diff Chat

**Location**: Right-click menu > AI Coder > Chat > Diff Chat

- Similar to Code Chat but specializes in suggesting code changes
- Changes are presented as diffs that can be applied directly
- Great for refactoring or implementing new features

### Multi-Code Chat

**Location**: Right-click menu > AI Coder > Chat > Multi-Code Chat

- Chat interface that includes multiple selected files for context
- Useful for discussing interactions between components
- Provides a broader view of your codebase to the AI

### Multi-Diff Chat

**Location**: Right-click menu > AI Coder > Chat > Multi-Diff Chat

- Like Multi-Code Chat but with diff application capabilities
- Allows making coordinated changes across multiple files
- Available with or without line numbers for better context

### Generic Chat

**Location**: Right-click menu > AI Coder > Chat > Generic Chat

- General-purpose chat interface without specific code context
- Useful for brainstorming, planning, or general coding questions

## Git Integration

### Chat With Commit

**Location**: Right-click on a commit > AI Coder > Chat With Commit

- Opens a chat interface focused on a specific commit
- Discuss the changes, ask for explanations, or get suggestions
- Helps understand unfamiliar code changes

### Chat With Commit Diff

**Location**: Right-click on a commit > AI Coder > Chat With Commit Diff

- Similar to Chat With Commit but focuses on the diff between the selected commit and HEAD
- Useful for understanding the impact of specific changes

### Chat With Working Copy Diff

**Location**: Right-click in Git tool window > AI Coder > Chat With Working Copy Diff

- Discusses differences between your working copy and the repository
- Helps review changes before committing

### Replicate Commit

**Location**: Right-click on a commit > AI Coder > Replicate Commit

- Analyzes a commit and applies similar changes to different files
- Useful for implementing consistent patterns across your codebase
- Provides step-by-step explanation of changes

## Code Generation

### Create File From Description

**Location**: Right-click on a folder > AI Coder > Generate > Create File From Description

- Generates a new file based on your description
- Automatically determines appropriate file type and content
- Great for quickly scaffolding new components

### Generate Related File

**Location**: Right-click on a file > AI Coder > Generate > Generate Related File

- Creates a companion file based on the selected file
- Examples: generating a test file for a class, creating a header for an implementation
- Understands the relationship between different file types

### Create Image

**Location**: Right-click on a folder > AI Coder > Generate > Create Image

- Generates images based on your code and instructions
- Useful for creating diagrams, icons, or illustrations
- Saves directly to your project

### Generate Documentation

**Location**: Right-click on a folder > AI Coder > Generate > Generate Documentation

- Creates comprehensive documentation for your code
- Options for single file output or multiple files
- Customizable output format and structure

## Markdown Tools

### Markdown List Action

**Location**: Right-click in a Markdown file > AI Coder > Markdown > Extend List

- Automatically extends bullet points or checkbox lists
- Maintains consistent style and context with existing items
- Specify how many new items to generate

### Markdown Implement

**Location**: Right-click on text in a Markdown file > AI Coder > Markdown > Implement as...

- Converts natural language descriptions into code blocks
- Supports multiple programming languages
- Great for documentation with code examples

## Knowledge Management

### Document Data Extractor

**Location**: Right-click on a document > AI Coder > Knowledge > Extract Document Data

- Extracts structured data from documents (PDFs, text files, etc.)
- Configurable extraction parameters
- Useful for analyzing and processing document content

### Save As Query Index

**Location**: Right-click on parsed files > AI Coder > Knowledge > Save As Query Index

- Creates a searchable index from parsed documents
- Enables efficient querying of document content
- Foundation for advanced knowledge management

### Create Projector From Query Index

**Location**: Right-click on index files > AI Coder > Knowledge > Create Projector

- Visualizes document relationships in an interactive 3D space
- Helps identify clusters and connections in your data
- Powerful tool for exploring large document collections

## Development Tools

### Print Tree Action

**Location**: Right-click in editor > AI Coder > Dev > Print Tree

- Prints the PSI tree structure of the current file
- Useful for understanding how the IDE parses your code
- Helpful for plugin development or debugging

### Apply Patch

**Location**: Right-click in editor > AI Coder > Dev > Apply Patch

- Applies a patch to the current file
- Supports standard diff format
- Useful for manually applying changes from other sources

### Line Filter Chat

**Location**: Right-click in editor > AI Coder > Dev > Line Filter Chat

- Chat interface that references code by line numbers
- Makes it easy to discuss specific parts of a file
- Great for code reviews or teaching

## Problem Solving

### Analyze Problem

**Location**: Right-click on a problem in the Problems view > AI Coder > Analyze Problem

- Analyzes compiler errors, warnings, or other issues
- Suggests fixes with explanations
- Helps understand and resolve complex problems

### Test Result Autofix

**Location**: Right-click on a test result > AI Coder > Autofix Test

- Analyzes failed tests and suggests fixes
- Understands test context and requirements
- Accelerates the test-driven development cycle

### Command Autofix

**Location**: Right-click on a folder > AI Coder > Command Autofix

- Runs commands and automatically fixes issues
- Useful for build errors, linting problems, etc.
- Configurable with multiple commands and retry options

### Validate Code

**Location**: Right-click on files > AI Coder > Validate Code

- Performs syntax checking and validation
- Identifies potential issues before compilation
- Suggests improvements for code quality

## Multi-file Operations

### Mass Patch

**Location**: Right-click on multiple files > AI Coder > Mass Patch

- Applies similar changes to multiple files
- Specify transformation instructions once and apply to all selected files
- Great for codebase-wide refactoring

### Documented Mass Patch

**Location**: Right-click on multiple files > AI Coder > Documented Mass Patch

- Similar to Mass Patch but specifically for aligning code with documentation
- Ensures code and documentation stay in sync
- Useful for maintaining API consistency

### Multi-Step Patch (Auto Dev)

**Location**: Right-click on files > AI Coder > Auto Dev

- Breaks down complex changes into manageable steps
- Creates a detailed plan before implementing changes
- Provides explanations for each modification

### Simple Command

**Location**: Right-click on files > AI Coder > Simple Command

- Executes a simple transformation based on your instructions
- Faster than Multi-Step Patch for straightforward changes
- Good for quick, targeted modifications

## Advanced Features

### Web Development Assistant

**Location**: Right-click on a folder > AI Coder > Web Development Assistant

- Creates complete web applications from descriptions
- Generates HTML, CSS, JavaScript, and image files
- Handles both UI and functionality implementation

### Shell Command

**Location**: Right-click on a folder > AI Coder > Shell Command

- Executes shell commands with AI assistance
- Interprets command output and suggests next steps
- Useful for complex command-line operations

### Outline & Enhanced Outline

**Location**: AI Coder menu > Outline / Enhanced Outline

- Creates structured outlines for documents or ideas
- Progressively expands concepts with multiple AI models
- Useful for planning documents, presentations, or projects

### Unified Plan

**Location**: AI Coder menu > Unified Plan

- Comprehensive planning tool with multiple cognitive modes
- Supports single tasks, plan-ahead mode, auto-plan, and graph-based planning
- Configurable with saved templates for different project types

## Tips for Effective Use

1. **Start with the right context**: Select relevant code or files before invoking actions
2. **Be specific in instructions**: Clear, detailed prompts yield better results
3. **Use the appropriate action**: Different tasks benefit from different specialized actions
4. **Save frequent commands**: Most actions remember your recent instructions for reuse
5. **Combine actions**: Use multiple actions in sequence for complex transformations
6. **Review AI suggestions**: Always verify generated code before applying changes
7. **Provide feedback**: The AI learns from your interactions and improves over time

```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/SelectionAction.kt

```
﻿package cognotik.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.util.*

abstract class SelectionAction<T : Any>(
    private val requiresSelection: Boolean = true
) : BaseAction() {

    open fun getConfig(project: Project?): T? = null

    private fun retarget(
        editorState: EditorState,
        selectedText: @NlsSafe String?,
        selectionStart: Int,
        selectionEnd: Int
    ): Pair<Int, Int>? {
        if (selectedText.isNullOrEmpty()) {
            var (start, end) = defaultSelection(editorState, selectionStart)
            if (start >= end && requiresSelection) return null
            start = start.coerceAtLeast(0)
            end = end.coerceAtLeast(start).coerceAtMost(editorState.text.length - 1)
            return Pair(start, end)
        } else {
            var (start, end) = editSelection(editorState, selectionStart, selectionEnd)
            if (start >= end && requiresSelection) return null
            start = start.coerceAtLeast(0)
            end = end.coerceAtLeast(start).coerceAtMost(editorState.text.length - 1)
            return Pair(start, end)
        }
    }

    final override fun handle(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val config = getConfig(e.project)
        val indent = e.getIndent()
        val caretModel = editor.caretModel
        val primaryCaret = caretModel.primaryCaret
        var selectionStart = primaryCaret.selectionStart
        var selectionEnd = primaryCaret.selectionEnd
        var selectedText = primaryCaret.selectedText
        val editorState = editorState(editor)
        val (start, end) = retarget(editorState, selectedText, selectionStart, selectionEnd) ?: return
        val text = editorState.text
        selectedText = text.substring(
            start.coerceIn(0, (text.length - 1).coerceAtLeast(0)),
            end.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        )
        selectionEnd = end.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        selectionStart = start.coerceIn(0, (text.length - 1).coerceAtLeast(0))

        e.redoableTask {
            val document = e.getData(CommonDataKeys.EDITOR)?.document
            var rangeMarker: RangeMarker? = null
            WriteCommandAction.runWriteCommandAction(e.project) {
                rangeMarker = document?.createGuardedBlock(selectionStart, selectionEnd)
            }
            val newText = try {
                processSelection(
                    event = e,
                    SelectionState(
                        selectedText = selectedText,
                        selectionOffset = selectionStart,
                        selectionLength = selectionEnd - selectionStart,
                        entireDocument = editor.document.text,
                        language = LanguageUtils.getComputerLanguage(e),
                        indent = indent,
                        contextRanges = editorState.contextRanges,
                        psiFile = editorState.psiFile,
                        project = e.project,
                        editor = editor,
                    ),
                    config = config
                )
            } finally {
                if (null != rangeMarker)
                    WriteCommandAction.runWriteCommandAction(e.project) {
                        document?.removeGuardedBlock(rangeMarker!!)
                    }
            }
            e.writeableFn {
                log.debug(
                    "Start: $selectionStart; End: $selectionEnd; Selected text: \n\t${selectedText.indent("\t")}; New text: \n\t${
                        newText.indent(
                            "\t"
                        )
                    }"
                )
                editor.document.replaceSubString(selectionStart, selectionEnd, newText)
            }
        }
    }

    data class EditorState(
        val text: @NlsSafe String,
        val cursorOffset: Int,
        val line: Pair<Int, Int>,
        val psiFile: PsiFile?,
        val contextRanges: Array<ContextRange> = arrayOf(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EditorState

            if (cursorOffset != other.cursorOffset) return false
            if (text != other.text) return false
            if (line != other.line) return false
            if (psiFile != other.psiFile) return false
            if (!contextRanges.contentEquals(other.contextRanges)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = cursorOffset
            result = 31 * result + text.hashCode()
            result = 31 * result + line.hashCode()
            result = 31 * result + (psiFile?.hashCode() ?: 0)
            result = 31 * result + contextRanges.contentHashCode()
            return result
        }
    }

    data class ContextRange(
        val name: String,
        val start: Int,
        val end: Int
    ) {
        fun length() = end - start

    }

    private fun editorState(editor: Editor): EditorState {
        val document = editor.document
        val lineNumber = document.getLineNumber(editor.caretModel.offset)
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
        val psiFile = if (virtualFile == null) {
            null
        } else {
            PsiManager.getInstance(editor.project!!).findFile(virtualFile)
        }
        return EditorState(
            text = document.text,
            cursorOffset = editor.caretModel.offset,
            line = Pair(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)),
            psiFile = psiFile,
            contextRanges = contextRanges(psiFile, editor)
        )
    }

    private fun contextRanges(
        psiFile: PsiFile?,
        editor: Editor
    ): Array<ContextRange> {
        val contextRanges = mutableListOf<ContextRange>()
        psiFile?.acceptChildren(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val start = element.textRange.startOffset
                val end = element.textRange.endOffset
                if (start <= editor.caretModel.offset && end >= editor.caretModel.offset) {
                    contextRanges.add(ContextRange(element.javaClass.simpleName, start, end))
                }
                super.visitElement(element)
            }
        })
        return contextRanges.toTypedArray()
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
        if (requiresSelection && editor.caretModel.primaryCaret.selectedText.isNullOrEmpty()) return false
        return isLanguageSupported(LanguageUtils.getComputerLanguage(event))
    }

    data class SelectionState(
        val selectedText: String? = null,
        val selectionOffset: Int = 0,
        val selectionLength: Int? = null,
        val entireDocument: String? = null,
        val language: ComputerLanguage? = null,
        val indent: CharSequence? = null,
        val contextRanges: Array<ContextRange> = arrayOf(),
        val psiFile: PsiFile? = null,
        val project: Project? = null,
        val progress: ProgressIndicator? = null,
        val editor: Editor? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SelectionState

            if (selectionOffset != other.selectionOffset) return false
            if (selectionLength != other.selectionLength) return false
            if (selectedText != other.selectedText) return false
            if (entireDocument != other.entireDocument) return false
            if (language != other.language) return false
            if (indent != other.indent) return false
            if (!contextRanges.contentEquals(other.contextRanges)) return false
            if (psiFile != other.psiFile) return false
            if (project != other.project) return false
            if (progress != other.progress) return false
            if (editor != other.editor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = selectionOffset
            result = 31 * result + (selectionLength ?: 0)
            result = 31 * result + (selectedText?.hashCode() ?: 0)
            result = 31 * result + (entireDocument?.hashCode() ?: 0)
            result = 31 * result + (language?.hashCode() ?: 0)
            result = 31 * result + (indent?.hashCode() ?: 0)
            result = 31 * result + contextRanges.contentHashCode()
            result = 31 * result + (psiFile?.hashCode() ?: 0)
            result = 31 * result + (project?.hashCode() ?: 0)
            result = 31 * result + (progress?.hashCode() ?: 0)
            result = 31 * result + (editor?.hashCode() ?: 0)
            return result
        }
    }

    open fun isLanguageSupported(computerLanguage: ComputerLanguage?): Boolean {
        return true

    }

    open fun defaultSelection(editorState: EditorState, offset: Int) = editorState.line

    open fun editSelection(state: EditorState, start: Int, end: Int) = Pair(start, end)

    open fun processSelection(
        event: AnActionEvent?,
        selectionState: SelectionState,
        config: T?
    ): String {
        return UITools.run(event?.project, templateText ?: "", true) { progress ->
            val result = processSelection(state = selectionState, config = config, progress = progress)
            result
        }
    }

    open fun processSelection(state: SelectionState, config: T?, progress: ProgressIndicator): String {
        throw NotImplementedError()
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/SymbolGraphAction.kt

```
package cognotik.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.BrowseUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.servlet.SymbolGraphServlet
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File

class SymbolGraphAction : BaseAction() {
    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val root = File(project.basePath ?: return)
        Thread {
            try {
                val server = CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                )
                server.context!!.addServlet(ServletHolder(SymbolGraphServlet(SymbolGraphService().apply {
                    load(root.resolve("symbol_graph.json"))
                })), "/symbol_index/*")
                BrowseUtil.browse(server.server.uri.resolve("/symbol_index"))
            } catch (e: Throwable) {
                log.warn("Error launching Symbol Graph", e)
            }
        }.start()
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        val project = event.project ?: return false
        val root = File(project.basePath ?: return false)
        val graphFile = root.resolve("symbol_graph.json")
        if (!graphFile.exists()) return false
        return super.isEnabled(event)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SymbolGraphAction::class.java)
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

Here are the modifications for the `README.md` file in the `cognotik/actions` directory. These changes incorporate the technical details of the action framework and the specific `Symbol Graph` action described in the provided source code.

### intellij/src/main/kotlin/cognotik/actions/README.md
```diff
--- /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/README.md
+++ /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/README.md
@@ -10,6 +10,7 @@
 - [Development Tools](#development-tools)
 - [Problem Solving](#problem-solving)
 - [Multi-file Operations](#multi-file-operations)
+- [Technical Architecture](#technical-architecture)
 
 ## Editor Actions
 
@@ -165,6 +166,13 @@
 - Chat interface that references code by line numbers
 - Makes it easy to discuss specific parts of a file
 - Great for code reviews or teaching
+
+### Symbol Graph
+
+**Location**: Right-click in editor/project view > AI Coder > Dev > Symbol Graph
+
+- Visualizes symbol relationships and dependencies within the project
+- Requires a `symbol_graph.json` file in the project root
+- Launches an interactive web interface for exploring the codebase structure
 
 ## Problem Solving
 
@@ -235,3 +243,33 @@
 6. **Review AI suggestions**: Always verify generated code before applying changes
 7. **Provide feedback**: The AI learns from your interactions and improves over time
+
+## Technical Architecture
+
+The AI Coder plugin uses a hierarchical action system to provide consistent behavior and robust error handling across all features.
+
+### BaseAction
+
+The root class for all plugin actions. It provides:
+- **API Initialization**: Ensures models (Smart and Fast) are configured before execution.
+- **Performance Monitoring**: Detects and logs slow UI updates to ensure a responsive IDE experience.
+- **Error Handling**: Standardized catching and reporting of state, input, and unexpected errors.
+- **Action Logging**: Tracks user interactions for debugging and usage analytics.
+
+### SelectionAction
+
+A specialized base class for editor-based transformations.
+- **Selection Management**: Automatically handles line-based or custom selection logic, including retargeting.
+- **Guarded Blocks**: Protects the document from concurrent modifications during AI processing using range markers.
+- **Context Awareness**: Provides the AI with PSI tree information, language detection, and indentation context.
+- **Undo/Redo Support**: Integrates with the IDE's command system for seamless history management.
+
+### FileContextAction
+
+A base class for operations targeting files and directories.
+- **Background Execution**: Runs long-running tasks (like AI generation) on background threads to keep the UI responsive.
+- **File System Integration**: Handles refreshing the virtual file system after external modifications.
+- **Automatic Opening**: Can automatically open newly generated files in the editor once they appear on disk.
+- **Dev Mode**: Supports developer-only actions that are hidden unless "Dev Actions" is enabled in settings.
```

### Summary of Changes
- **Added Symbol Graph Documentation**: Included a description of the `SymbolGraphAction` under the "Development Tools" section. This action allows users to visualize project dependencies via an interactive web interface, provided a `symbol_graph.json` exists.
- **Added Technical Architecture Section**: Introduced a new section at the end of the document describing the core classes that power the plugin's actions:
    - **`BaseAction`**: Detailed its role in model validation, performance monitoring, and error handling.
    - **`SelectionAction`**: Explained how it manages editor selections, uses guarded blocks for thread safety, and provides PSI context to the AI.
    - **`FileContextAction`**: Described its capabilities for background processing, file system synchronization, and developer-mode filtering.
- **Updated Table of Contents**: Added a link to the new "Technical Architecture" section for easier navigation.
- **Preserved Existing Content**: All previous user-facing documentation for existing actions was maintained while integrating the new technical specifications.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/README.md'>intellij/src/main/kotlin/cognotik/actions/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/README.md'>intellij/src/main/kotlin/cognotik/actions/README.md</a> Updated
