package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import java.io.File
import java.text.SimpleDateFormat

open class TaskHarness<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val taskType: TaskType<T, U>,
    val typeConfig: U,
    val executionConfig: T,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model,session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session,
                    UserSettingsManager.defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val port: Int = 8082,
    val serverless: Boolean = true,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
//    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiFlash_25_Image_Generation,
    val workspace: File? = null,
) {
    val dataDir: File by lazy { createWorkspace() }

    private val harness = object : UnifiedHarness(
        port = port,
        openBrowser = openBrowser,
        serverless = serverless,
        modelInstanceFn = modelInstanceFn,
        fastModel = fastModel,
        smartModel = smartModel,
        imageModel = imageModel
    ) {
        override fun createTempDirectory(prefix: String) = dataDir
    }

    fun run() {
        try {
            harness.start()
            try {
                harness.runTask(
                    taskType = taskType,
                    typeConfig = typeConfig,
                    executionConfig = executionConfig,
                    timeoutMinutes = timeoutMinutes,
                    autoFix = !openBrowser,
                    workspace = workspace
                )
            } finally {
                harness.stop()
            }
        } catch (e: Exception) {
            fix(e)
            throw RuntimeException(e)
        }
    }

    open fun createWorkspace(): File {
        val name = this.taskType.name
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/$name/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TaskHarness::class.java)
        var fix : (Exception) -> Unit = { e ->
            log.error("Error during task execution", e)
        }
    }
}