package com.simiacryptus.cognotik.util

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.FormBuilder
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.config.StaticAppSettingsConfigurable
import com.simiacryptus.cognotik.exceptions.ModerationException
import com.simiacryptus.cognotik.txt.IndentedText
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Type
import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

object UITools {
    val log: Logger = getLogger(UITools::class.java)

    /** Redo/retry handlers keyed by document. Weakly keyed and thread-safe. */
    val retry: MutableMap<Document, Runnable> = Collections.synchronizedMap(WeakHashMap())

    private const val MAX_ERROR_HISTORY = 20
    private const val MAX_ACTION_HISTORY = 100
    private const val ERROR_HISTORY_IN_REPORT = 5
    private const val GITHUB_NEW_ISSUE_URL = "https://github.com/SimiaCryptus/intellij-cognotik/issues/new"

    private val errorLog = ArrayDeque<Pair<String, Throwable>>()
    private val actionLog = ArrayDeque<String>()

    private fun daemonFactory(nameFormat: String) =
        ThreadFactoryBuilder().setNameFormat(nameFormat).setDaemon(true).build()

    val pool: ListeningExecutorService by lazy {
        val threads = AppSettingsState.instance.apiThreads.coerceAtLeast(1)
        MoreExecutors.listeningDecorator(
            ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(), daemonFactory("API Thread %d")
            )
        )
    }

    val scheduledPool: ListeningScheduledExecutorService by lazy {
        MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1, daemonFactory("UITools Scheduler %d")))
    }

    private val errorDialogExecutor = Executors.newSingleThreadExecutor(daemonFactory("Error Dialog %d"))

    // ---------------------------------------------------------------------------------------------
    // EDT helpers
    // ---------------------------------------------------------------------------------------------

    /** Runs [block] on the EDT (immediately if already there), without waiting. */
    fun runOnEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        when {
            app == null -> SwingUtilities.invokeLater { block() }
            app.isDispatchThread -> block()
            else -> app.invokeLater({ block() }, ModalityState.any())
        }
    }

    /** Runs [block] on the EDT and waits for its result. Must not be called while holding a read lock. */
    fun <T> computeOnEdt(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        val onEdt = app?.isDispatchThread ?: SwingUtilities.isEventDispatchThread()
        if (onEdt) return block()
        val result = AtomicReference<Result<T>>()
        val runnable = Runnable { result.set(runCatching(block)) }
        if (app != null) app.invokeAndWait(runnable, ModalityState.any()) else SwingUtilities.invokeAndWait(runnable)
        return result.get().getOrThrow()
    }

    fun showError(project: Project?, message: String, title: String = "Error") {
        runOnEdt { Messages.showErrorDialog(project, message, title) }
    }

    fun showWarning(project: Project?, message: String, title: String = "Warning") {
        runOnEdt { Messages.showWarningDialog(project, message, title) }
    }

    // ---------------------------------------------------------------------------------------------
    // Task execution
    // ---------------------------------------------------------------------------------------------

    fun runAsync(
        project: Project?,
        title: String?,
        canBeCancelled: Boolean = true,
        task: (ProgressIndicator) -> Unit,
    ) {
        val name = title ?: "Background Task"
        val job = Runnable {
            try {
                run(project, name, canBeCancelled, task)
            } catch (e: Throwable) {
                if (e.isCancellation()) log.info("Task '$name' was cancelled")
                else error(log, "Error running task '$name'", e)
            }
        }
        val app = ApplicationManager.getApplication()
        if (app != null) app.executeOnPooledThread(job) else thread(name = name, isDaemon = true) { job.run() }
    }

    fun <T : Any> run(
        project: Project?,
        title: String?,
        canBeCancelled: Boolean = true,
        task: (ProgressIndicator) -> T,
    ): T {
        if (project == null || project.isDisposed) return task(AbstractProgressIndicatorBase())
        val t = if (AppSettingsState.instance.modalTasks) ModalTask(project, title ?: "", canBeCancelled, task)
        else BgTask(project, title ?: "", canBeCancelled, task)
        ProgressManager.getInstance().run(t)
        return t.get()
    }

    fun getRetry(
        event: AnActionEvent,
        request: Supplier<Runnable>,
        undo: Runnable,
    ): Runnable = Runnable {
        Futures.addCallback(
            pool.submit<Runnable> {
                WriteCommandAction.runWriteCommandAction(event.project) { undo.run() }
                request.get()
            }, event.futureCallback(request), pool
        )
    }

    fun <I : Any?, O : Any?> map(
        moderateAsync: ListenableFuture<I>,
        o: com.google.common.base.Function<in I, out O>,
    ): ListenableFuture<O> = Futures.transform(moderateAsync, o::apply, pool)

    // ---------------------------------------------------------------------------------------------
    // Reflection-based UI binding
    // ---------------------------------------------------------------------------------------------

    private fun unwrap(uiVal: Any?): Any? = if (uiVal is JScrollPane) uiVal.viewport.view else uiVal

    /**
     * Copies values from UI components on [component] into same-named mutable properties of [settings].
     * Values that cannot be read or parsed are left unchanged.
     */
    fun <T : Any, R : Any> readKotlinUIViaReflection(
        settings: T,
        component: R,
        componentClass: KClass<*> = component::class,
    ) {
        val uiFields = componentClass.memberProperties.associateBy { it.name }
        for (settingsField in settings.javaClass.kotlin.memberProperties) {
            if (settingsField !is KMutableProperty<*>) continue
            val uiField = uiFields[settingsField.name] ?: continue
            try {
                settingsField.isAccessible = true
                uiField.isAccessible = true
                val uiVal = unwrap(uiField.getter.call(component))
                val newValue = readValue(settingsField.returnType.javaType, uiVal) ?: continue
                settingsField.setter.call(settings, newValue)
            } catch (e: Throwable) {
                throw RuntimeException("Error processing $settingsField", e)
            }
        }
    }

    private fun readValue(type: Type, uiVal: Any?): Any? = when (type.typeName) {
        "java.lang.String" -> when (uiVal) {
            is JTextComponent -> uiVal.text
            is JComboBox<*> -> uiVal.selectedItem?.toString()
            else -> null
        }

        "int", "java.lang.Integer" -> (uiVal as? JTextComponent)?.text?.trim()
            ?.let { if (it.isEmpty()) -1 else it.toIntOrNull() }

        "long", "java.lang.Long" -> (uiVal as? JTextComponent)?.text?.trim()
            ?.let { if (it.isEmpty()) -1L else it.toLongOrNull() }

        "double", "java.lang.Double" -> (uiVal as? JTextComponent)?.text?.trim()
            ?.let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }

        "boolean", "java.lang.Boolean" -> when (uiVal) {
            is AbstractButton -> uiVal.isSelected
            is JTextComponent -> uiVal.text.trim().toBoolean()
            else -> null
        }

        else -> {
            val cls = type as? Class<*>
            if (cls != null && cls.isEnum && uiVal is JComboBox<*>) {
                val item = uiVal.selectedItem
                @Suppress("UNCHECKED_CAST")
                if (cls.isInstance(item)) item
                else item?.toString()?.let { (cls as Class<out Enum<*>?>).findValue(it) }
            } else null
        }
    }

    /** Copies public property values of [settings] into same-named UI components of [component]. */
    fun <T : Any, R : Any> writeKotlinUIViaReflection(
        settings: T,
        component: R,
        componentClass: KClass<*> = component::class,
    ) {
        val uiFields = componentClass.memberProperties.associateBy { it.name }
        for (settingsField in settings.javaClass.kotlin.memberProperties) {
            if (settingsField.visibility != KVisibility.PUBLIC) continue
            val uiField = uiFields[settingsField.name]
            if (uiField == null) {
                log.debug("No UI field for setting: {}", settingsField.name)
                continue
            }
            try {
                settingsField.isAccessible = true
                uiField.isAccessible = true
                val settingsVal = settingsField.get(settings) ?: continue
                writeValue(unwrap(uiField.getter.call(component)), settingsVal)
            } catch (e: Throwable) {
                throw RuntimeException("Error processing $settingsField", e)
            }
        }
    }

    private fun writeValue(uiVal: Any?, value: Any) {
        val isScalar = value is String || value is Number || value is Boolean || value is Enum<*>
        when (uiVal) {
            is AbstractButton -> if (value is Boolean) uiVal.isSelected = value
            is JTextComponent -> if (isScalar) uiVal.text = value.toString()
            is JComboBox<*> -> selectComboItem(uiVal, value)
        }
    }

    private fun selectComboItem(combo: JComboBox<*>, value: Any) {
        val model = combo.model
        val match = (0 until model.size).map { model.getElementAt(it) }
            .firstOrNull { it == value || it?.toString() == value.toString() }
        when {
            match != null -> combo.selectedItem = match
            combo.isEditable -> combo.selectedItem = value.toString()
        }
    }

    /** Adds all [JComponent] properties of [ui] to [formBuilder], in declaration order. */
    fun <T : Any> addKotlinFields(ui: T, formBuilder: FormBuilder, fillVertically: Boolean) {
        val declarationOrder = ui.javaClass.declaredFields.withIndex().associate { it.value.name to it.index }
        val properties = ui.javaClass.kotlin.memberProperties
            .sortedBy { declarationOrder[it.name] ?: Int.MAX_VALUE }
        var first = true
        for (field in properties) {
            val component = try {
                field.isAccessible = true
                field.get(ui) as? JComponent
            } catch (e: Throwable) {
                log.debug("Skipping field ${field.name}", e)
                null
            } ?: continue
            val nameAnnotation = field.annotations.filterIsInstance<Name>().firstOrNull()
            when {
                nameAnnotation == null -> formBuilder.addComponentToRightColumn(component, 1)
                first && fillVertically -> {
                    first = false
                    formBuilder.addLabeledComponentFillVertically(nameAnnotation.value + ": ", component)
                }

                else -> formBuilder.addLabeledComponent(JBLabel(nameAnnotation.value + ": "), component, 1, false)
            }
        }
    }

    fun <T : Any, C : Any> showDialog(
        project: Project?,
        uiClass: Class<T>,
        configClass: Class<C>,
        title: String = "Generate Project",
        onComplete: (C) -> Unit = { _ -> },
    ): C? = computeOnEdt {
        val component = uiClass.getConstructor().newInstance()
        val config = configClass.getConstructor().newInstance()
        val dialog = object : DialogWrapper(project) {
            init {
                this.title = title
                setOKButtonText("Generate")
                setCancelButtonText("Cancel")
                isResizable = true
                init()
            }

            override fun createCenterPanel(): JComponent? = component.buildFormViaReflection()
        }
        if (!dialog.showAndGet()) {
            log.debug("Dialog '{}' cancelled", title)
            return@computeOnEdt null
        }
        readKotlinUIViaReflection(settings = config, component = component, componentClass = component::class)
        onComplete(config)
        config
    }

    // ---------------------------------------------------------------------------------------------
    // Option dialogs
    // ---------------------------------------------------------------------------------------------

    private fun getMaximumSize(factor: Double): Dimension {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return Dimension((screenSize.getWidth() * factor).toInt(), (screenSize.getHeight() * factor).toInt())
    }

    /** Shows a modal option dialog on the EDT and returns the selected option index. */
    private fun showOptionDialog(mainPanel: JPanel?, vararg options: Any, title: String): Int = computeOnEdt {
        val pane = JOptionPane(mainPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.NO_OPTION, null, options, options[0])
        pane.initialValue = options[0]
        val rootFrame = JOptionPane.getRootFrame()
        pane.componentOrientation = rootFrame.componentOrientation
        val dialog = JDialog(rootFrame, title, true)
        dialog.componentOrientation = rootFrame.componentOrientation
        configure(dialog, pane)
        dialog.isVisible = true
        dialog.dispose()
        getSelectedValue(pane, options)
    }

    private fun configure(dialog: JDialog, pane: JOptionPane) {
        val contentPane = dialog.contentPane
        contentPane.layout = BorderLayout()
        contentPane.add(pane, BorderLayout.CENTER)
        if (JDialog.isDefaultLookAndFeelDecorated() && UIManager.getLookAndFeel().supportsWindowDecorations) {
            dialog.isUndecorated = true
            pane.rootPane.windowDecorationStyle = JRootPane.PLAIN_DIALOG
        }
        dialog.isResizable = true
        dialog.maximumSize = getMaximumSize(0.9)
        dialog.pack()
        dialog.setLocationRelativeTo(null as Component?)
        val adapter = windowAdapter(pane, dialog)
        dialog.addWindowListener(adapter)
        dialog.addWindowFocusListener(adapter)
        dialog.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(ce: ComponentEvent) {
                pane.value = JOptionPane.UNINITIALIZED_VALUE
            }
        })
        pane.addPropertyChangeListener { event ->
            if (dialog.isVisible && event.source === pane && event.propertyName == JOptionPane.VALUE_PROPERTY &&
                event.newValue != null && event.newValue !== JOptionPane.UNINITIALIZED_VALUE
            ) {
                dialog.isVisible = false
            }
        }
        pane.selectInitialValue()
    }

    private fun windowAdapter(pane: JOptionPane, dialog: JDialog): WindowAdapter = object : WindowAdapter() {
        private var gotFocus = false
        override fun windowClosing(we: WindowEvent) {
            pane.value = null
        }

        override fun windowClosed(e: WindowEvent) {
            dialog.contentPane.removeAll()
        }

        override fun windowGainedFocus(we: WindowEvent) {
            if (!gotFocus) {
                pane.selectInitialValue()
                gotFocus = true
            }
        }
    }

    private fun getSelectedValue(pane: JOptionPane, options: Array<out Any>): Int {
        val selectedValue = pane.value ?: return JOptionPane.CLOSED_OPTION
        val index = options.indexOfFirst { it == selectedValue }
        return if (index >= 0) index else JOptionPane.CLOSED_OPTION
    }

    fun showInputDialog(
        parentComponent: Component?, message: Any?, title: String?, messageType: Int
    ): Any? = computeOnEdt {
        val pane = JOptionPane(message, messageType, JOptionPane.OK_CANCEL_OPTION, null, null, null)
        pane.wantsInput = true
        val dialog = pane.createDialog(parentComponent, title)
        pane.selectInitialValue()
        dialog.isVisible = true
        dialog.dispose()
        pane.inputValue.takeUnless { it == JOptionPane.UNINITIALIZED_VALUE }
    }

    fun showErrorDialog(errorMessage: String, title: String) {
        computeOnEdt {
            val panel = panel { row { label(errorMessage) } }
            showOptionDialog(panel, "OK", title = title)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Error reporting
    // ---------------------------------------------------------------------------------------------

    fun logAction(message: String) {
        synchronized(actionLog) {
            actionLog.addLast(message)
            while (actionLog.size > MAX_ACTION_HISTORY) actionLog.removeFirst()
        }
    }

    fun error(log: Logger, msg: String, e: Throwable) {
        if (e.isCancellation()) {
            log.info("$msg (cancelled)")
            return
        }
        log.error(msg, e)
        synchronized(errorLog) {
            errorLog.addLast(msg to e)
            while (errorLog.size > MAX_ERROR_HISTORY) errorLog.removeFirst()
        }
        if (AppSettingsState.instance.suppressErrors) return
        errorDialogExecutor.submit {
            try {
                computeOnEdt { showErrorPopup(msg, e) }
            } catch (t: Throwable) {
                log.warn("Unable to display error dialog", t)
            }
        }
    }

    private fun showErrorPopup(msg: String, e: Throwable) {
        when {
            e.matches { it is ModerationException } -> JOptionPane.showMessageDialog(
                null,
                e.get { it is ModerationException }?.message ?: e.message,
                "This request was rejected by moderation",
                JOptionPane.WARNING_MESSAGE
            )

            e.matches { it is IOException && it.message?.contains("Incorrect API key") == true } ->
                showApiKeyErrorDialog()

            else -> showErrorReportDialog(msg, e)
        }
    }

    private fun showApiKeyErrorDialog() {
        val panel = panel {
            row { label("The API key was rejected by the server. Please update it in the plugin settings.") }
            row {
                button("Open Settings") {
                    ShowSettingsUtil.getInstance().editConfigurable(null as Project?, StaticAppSettingsConfigurable())
                }
                button("Open OpenAI Account Page") {
                    browse(URI("https://platform.openai.com/account/api-keys"))
                }
            }
        }
        showOptionDialog(panel, "Dismiss", title = "Invalid API Key")
    }

    private fun showErrorReportDialog(msg: String, e: Throwable) {
        val report = buildErrorReport(msg, e)
        lateinit var suppress: JBCheckBox
        val panel = panel {
            row {
                label("Oops! Something went wrong. An error report has been generated; you can paste it into a new GitHub issue.")
            }
            row {
                scrollCell(JBTextArea(report, 20, 80).apply {
                    isEditable = false
                    caretPosition = 0
                }).align(Align.FILL)
            }.resizableRow()
            row {
                button("Copy Report") { CopyPasteManager.getInstance().setContents(StringSelection(report)) }
                button("Open New Issue on GitHub") { browse(URI(GITHUB_NEW_ISSUE_URL)) }
            }
            row { suppress = checkBox("Suppress future error popups").component }
        }
        showOptionDialog(panel, "Dismiss", title = "Error")
        if (suppress.isSelected) AppSettingsState.instance.suppressErrors = true
    }

    private fun buildErrorReport(msg: String, e: Throwable): String {
        val actions = synchronized(actionLog) { actionLog.toList() }
        val history = synchronized(errorLog) { errorLog.filter { it.second !== e } }.takeLast(ERROR_HISTORY_IN_REPORT)
        return buildString {
            appendLine("Log Message: ${msg.trim()}")
            appendLine("Error Message: ${e.message?.trim()}")
            appendLine("Error Type: ${e.javaClass.name}")
            appendLine()
            appendLine("OS: ${System.getProperty("os.name")} / ${System.getProperty("os.version")} / ${System.getProperty("os.arch")}")
            appendLine("Java: ${System.getProperty("java.version")}")
            appendLine("Locale: ${Locale.getDefault().country} / ${Locale.getDefault().language}")
            appendLine()
            appendLine("Error Details:")
            appendLine("```")
            appendLine(e.toFullString().trim())
            appendLine("```")
            appendLine("Action History:")
            actions.forEach { appendLine("* ${it.trim().replace("\n", "\n  ")}") }
            if (history.isNotEmpty()) {
                appendLine("Error History:")
                history.forEach { (m, t) ->
                    appendLine(m)
                    appendLine("```")
                    appendLine(t.toFullString().trim())
                    appendLine("```")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// AnActionEvent helpers
// -------------------------------------------------------------------------------------------------

private fun AnActionEvent.editorFile(): VirtualFile? =
    PlatformDataKeys.EDITOR.getData(dataContext)?.let { FileDocumentManager.getInstance().getFile(it.document) }

fun AnActionEvent.getSelectedFiles(): List<VirtualFile> {
    PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.let { return it.toList() }
    return listOfNotNull(editorFile())
}

fun AnActionEvent.getSelectedFile(): VirtualFile? =
    PlatformDataKeys.VIRTUAL_FILE.getData(dataContext)?.takeIf { !it.isDirectory }

fun AnActionEvent.getSelectedFolders(): List<VirtualFile> {
    PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.let { files -> return files.filter { it.isDirectory } }
    return listOfNotNull(editorFile()?.parent)
}

fun AnActionEvent.getSelectedFolder(): VirtualFile? {
    PlatformDataKeys.VIRTUAL_FILE.getData(dataContext)?.takeIf { it.isDirectory }?.let { return it }
    return editorFile()?.parent
}

fun AnActionEvent.writeableFn(fn: () -> Runnable): Runnable {
    val runnable = AtomicReference<Runnable>()
    WriteCommandAction.runWriteCommandAction(this.project) { runnable.set(fn()) }
    return runnable.get()
}

fun AnActionEvent.getIndent() = getData(CommonDataKeys.CARET)?.getIndent() ?: ""

fun Caret?.getIndent(): CharSequence {
    if (this == null) return ""
    val document = editor.document
    if (document.lineCount == 0) return ""
    val line = document.getLineNumber(selectionStart.coerceIn(0, document.textLength))
    val lineText = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
    return IndentedText.fromString(lineText).indent
}

fun AnActionEvent.redoableTask(request: Supplier<Runnable>) {
    Futures.addCallback(UITools.pool.submit<Runnable> { request.get() }, futureCallback(request), UITools.pool)
}

fun AnActionEvent.futureCallback(request: Supplier<Runnable>) = object : FutureCallback<Runnable> {
    override fun onSuccess(undo: Runnable) {
        val document = getData(CommonDataKeys.EDITOR)?.document ?: return
        UITools.retry[document] = UITools.getRetry(this@futureCallback, request, undo)
    }

    override fun onFailure(t: Throwable) {
        UITools.error(UITools.log, "Error", t)
    }
}

// -------------------------------------------------------------------------------------------------
// Throwable helpers
// -------------------------------------------------------------------------------------------------

fun Throwable.toFullString(): String {
    val sw = StringWriter()
    PrintWriter(sw).use { printStackTrace(it) }
    return sw.toString()
}

/** The causal chain of this throwable, safe against arbitrary cause cycles. */
fun Throwable.causalChain(): Sequence<Throwable> = sequence {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this@causalChain
    while (current != null && seen.add(current)) {
        yield(current)
        current = current.cause
    }
}

fun Throwable.get(matchFn: (Throwable) -> Boolean): Throwable? = causalChain().firstOrNull(matchFn)

fun Throwable.matches(matchFn: (Throwable) -> Boolean): Boolean = causalChain().any(matchFn)

/** True if this throwable (or any cause) represents user/system cancellation rather than a failure. */
fun Throwable.isCancellation(): Boolean = matches {
    it is InterruptedException || it is ProcessCanceledException || it is CancellationException
}

// -------------------------------------------------------------------------------------------------
// Misc
// -------------------------------------------------------------------------------------------------

fun Class<out Enum<*>?>.findValue(string: String): Enum<*>? =
    enumConstants?.firstOrNull { it?.name?.equals(string, ignoreCase = true) == true }
        ?: enumConstants?.firstOrNull { it?.toString()?.equals(string, ignoreCase = true) == true }

fun <T : Any> T.buildFormViaReflection(
    fillVertically: Boolean = true,
    formBuilder: FormBuilder = FormBuilder.createFormBuilder(),
): JPanel? {
    UITools.addKotlinFields(this, formBuilder, fillVertically)
    return formBuilder.addComponentFillVertically(JPanel(), 0).panel
}

/** Deletes the given range and returns a Runnable that restores it. */
@Suppress("unused")
fun Document.deleteSubString(startOffset: Int, endOffset: Int): Runnable {
    val oldText: CharSequence = getText(TextRange(startOffset, endOffset))
    deleteString(startOffset, endOffset)
    return Runnable {
        insertString(startOffset, oldText)
        UITools.log.debug("REV insertString @ {} ({})", startOffset, oldText.length)
    }
}

/** Replaces the given range and returns a Runnable that verifies and reverts the change. */
fun Document.replaceSubString(startOffset: Int, endOffset: Int, newText: CharSequence): Runnable {
    val oldText: CharSequence = getText(TextRange(startOffset, endOffset))
    replaceString(startOffset, endOffset, newText)
    UITools.log.debug("FWD replaceString {}..{} ({} -> {} chars)", startOffset, endOffset, endOffset - startOffset, newText.length)
    return Runnable {
        val newEnd = startOffset + newText.length
        val verifyTxt = if (newEnd <= textLength) getText(TextRange(startOffset, newEnd)) else null
        if (verifyTxt != newText.toString()) {
            val msg = "The text range from $startOffset to $newEnd does not match the expected text \"$newText\" and is instead \"$verifyTxt\""
            UITools.log.error("Verification failed while reverting replaceString: {}", msg)
            throw IllegalStateException(msg)
        }
        replaceString(startOffset, newEnd, oldText)
        UITools.log.debug("REV replaceString {}..{} ({} -> {} chars)", startOffset, newEnd, newText.length, oldText.length)
    }
}