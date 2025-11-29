package cognotik.actions.generate

import cognotik.actions.BaseAction
import cognotik.actions.chat.ImageChatAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.RenderableDocumentReader
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.UITools
import java.io.File
import javax.imageio.ImageIO

class OCRAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val project = event.project ?: return
        val root = File(project.basePath ?: return).toPath()
        val files = ImageChatAction.Companion.getFiles(
            PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext),
            root
        ).map { root.resolve(it).toFile() }
        UITools.runAsync(project, "OCR Processing", true) { progress ->
            files.forEach { file ->
                if (progress.isCanceled) return@forEach
                try {
                    if (!file.exists()) return@forEach
                    file.getDocumentReader().use { reader ->
                        if (reader is PaginatedDocumentReader && reader is RenderableDocumentReader) {
                            val sb = StringBuilder()
                            val pageCount = reader.getPageCount()
                            for (page in 0 until pageCount) {
                                if (progress.isCanceled) break
                                progress.text = "Processing ${file.name} (${page + 1}/$pageCount)"
                                progress.fraction = page.toDouble() / pageCount

                                val image = reader.renderImage(page, 150f)
                                val response = AppSettingsState.Companion.instance.imageChatClient.chat(
                                    listOfNotNull(
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.system,
                                            listOf(ModelSchema.ContentPart("You are an OCR engine. Convert the image to Markdown. Output only the markdown content."))
                                        ),
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.user,
                                            listOf(ModelSchema.ContentPart("Convert this page").apply { this.image = image })
                                        )
                                    )
                                ).choices.first().message?.content ?: ""
                                sb.append(response).append("\n\n")
                            }
                            val outputFile = File(file.parentFile, file.nameWithoutExtension + ".md")
                            outputFile.writeText(sb.toString())
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Error processing ${file.name}", e)
                }
            }
        }
    }

    companion object {
        private val log = Logger.getInstance(OCRAction::class.java)
    }
}