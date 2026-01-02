package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * The CognitiveMode interface defines the “cognitive” strategy
 * which handles user input, initial planning, execution and iterative
 * thought updates.
 */
abstract class CognitiveMode<U : CognitiveModeConfig>(
    val orchestrationConfig: OrchestrationConfig,
    val session: Session,
    val user: User,
) {
    val config: U
        get() = orchestrationConfig.cognitiveSettings as? U
            ?: throw IllegalStateException("Cognitive settings not defined")

    /**
     * Initialize the internal cognitive state.
     */
    abstract fun initialize(task : SessionTask)

    /**
     * Handle a user message and trigger the appropriate planning or execution.
     */
    abstract fun handleUserMessage(userMessage: String, task: SessionTask)

    /**
     * Get the context data accumulated during execution.
     * This is useful for sub-planning tasks to collect results.
     */
    abstract fun contextData(): List<String>

    fun transcript(
        task: SessionTask,
        transcriptFile: String = (this.config.type?.name
            ?: this.javaClass.simpleName) + "_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
    ): FileOutputStream? {
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add("[Transcript](${link.removeSuffix(".md")}.html)".renderMarkdown())
        return markdownTranscript
    }
}

class CognitiveModeTypeSerializer : DynamicEnumSerializer<CognitiveModeType<*>>(CognitiveModeType::class.java)
class CognitiveModeTypeDeserializer : DynamicEnumDeserializer<CognitiveModeType<*>>(CognitiveModeType::class.java)