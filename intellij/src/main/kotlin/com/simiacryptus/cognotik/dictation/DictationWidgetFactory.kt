package com.simiacryptus.cognotik.dictation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.simiacryptus.cognotik.TranscriptionClient
import com.simiacryptus.cognotik.audio.AudioState
import com.simiacryptus.cognotik.audio.DictationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.currentSession
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import icons.MyIcons
import kotlinx.coroutines.CoroutineScope
import org.slf4j.event.Level
import java.awt.event.MouseEvent
import java.io.IOException

class DictationWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = SpeechToTextWidget.ID
    override fun getDisplayName(): String = "AI Speech-to-Text"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = SpeechToTextWidget()
    override fun createWidget(project: Project, scope: CoroutineScope) = createWidget(project)
    override fun canBeEnabledOn(statusBar: StatusBar) = true

    class SpeechToTextWidget : StatusBarWidget,
        StatusBarWidget.IconPresentation {
        companion object {
            var statusBar: StatusBar? = null
            val ID = "AICodingAssistant.SpeechToTextWidget"
            fun toggleRecording() {
                if (DictationState.isRecording) {
                    DictationState.setRecordingState(false)
                    dictationManager.stopRecording()
                } else {
                    DictationState.setRecordingState(true)
                    DictationState.resetState()
                    dictationManager.startRecording()
                }
                statusBar?.updateWidget(ID)
            }
        }

        override fun install(statusBar: StatusBar) {
            dictationManager.onTranscriptionUpdate = DictationState.onTranscriptionUpdate
            dictationManager.handlePacket = DictationState.onPacket
            Companion.statusBar = statusBar
            val project = statusBar.project ?: return
            DictationState.project = project
//            val connection = project.messageBus.connect()
//            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
//                override fun selectionChanged(event: FileEditorManagerEvent) {
//
//                    val editor = FileEditorManager.getInstance(project).selectedTextEditor
//                    val editorHash = editor?.hashCode() ?: return
//                    if (!editorsWithListeners.add(editorHash)) {
//
//                        return
//                    }
//                    editor.document.addDocumentListener(object : DocumentListener {
//                        override fun documentChanged(event: DocumentEvent) {
//
//                            val str = event.document.text.take(1024)
//                            DictationManager.transcriptionProcessor?.prompt = str
//
//                        }
//                    })
//                    editor.selectionModel.addSelectionListener(object : SelectionListener {
//                        override fun selectionChanged(event: SelectionEvent) {
//
//                            val str = editor.selectionModel.selectedText?.take(1024) ?: ""
//                            DictationManager.transcriptionProcessor?.prompt = str
//
//                        }
//                    })
//                    editor.caretModel.addCaretListener(object : CaretListener {
//                        override fun caretPositionChanged(event: CaretEvent) {
//
//                            val caret = event.caret
//                            val offset = caret?.offset
//                            val document = caret?.editor?.document
//                            val str = document?.text?.take(offset ?: 0)?.takeLast(1024)
//                            DictationManager.transcriptionProcessor?.prompt = str ?: ""
//
//                        }
//                    })
//                    DictationManager.discriminator.onModeChanged.addListener {
//                        Companion.statusBar?.updateWidget(ID)
//                    }
//                }
//            })
        }

        override fun ID(): String = ID
        override fun getPresentation() = this
        override fun getIcon() = when (dictationManager.discriminator.currentState) {
            AudioState.QUIET -> when {
                DictationState.isRecording -> MyIcons.micActive
                else -> MyIcons.micInactive
            }

            AudioState.TALKING -> when {
                DictationState.isRecording -> MyIcons.micListening
                else -> MyIcons.micActive
            }
        }

        override fun getTooltipText(): String =
            if (DictationState.isRecording) "Click to stop recording" else "Click to start recording"

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            ApplicationManager.getApplication().invokeLater { toggleRecording() }
        }

    }

    companion object {
        val dictationManager = object : DictationManager() {
            override fun transcriptionClient(): TranscriptionClient {
                val model = AppSettingsState.instance.transcriptionModel.let {
                    findAudioModel(it)
                } ?: throw IOException("Transcription model not configured")
                val apiData =
                  fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.find { it.provider == model.provider }
                return TranscriptionClient(
                    key = apiData?.key?.decrypt ?: throw IOException("API key for ${model.provider} not configured"),
                    apiBase = apiData.apiBase ?: throw IllegalArgumentException("No API found for provider: ${apiData.provider?.name}"),
                    logLevel = Level.DEBUG,
                    logStreams = mutableListOf(),
                    workPool = ApplicationServices.threadPoolManager.getPool(
                        currentSession,
                      AppSettingsState.localUser
                    ),
                    scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
                        currentSession,
                      AppSettingsState.localUser
                    ),
                    provider = model.provider
                )
            }

        }
    }
}

