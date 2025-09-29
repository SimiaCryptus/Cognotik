package cognotik.actions

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
import com.simiacryptus.cognotik.actors.CodeAgent.Companion.indent
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