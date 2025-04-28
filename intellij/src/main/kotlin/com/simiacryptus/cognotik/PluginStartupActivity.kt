package com.simiacryptus.cognotik

import ch.qos.logback.classic.Level
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.IdeaChatClient
import com.simiacryptus.cognotik.util.IntelliJPsiValidator
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.AwsPlatform
import com.simiacryptus.cognotik.platform.ClientManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.util.JsonUtil.fromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

class PluginStartupActivity : ProjectActivity {
    private val documentationPageOpenTimes = ConcurrentHashMap<String, Long>()
    private lateinit var messageBusConnection: com.intellij.util.messages.MessageBusConnection
    override suspend fun execute(project: Project) {
        setLogInfo("org.apache.hc.client5.http")
        setLogInfo("org.eclipse.jetty")
        setLogInfo("com.simiacryptus")

        try {

            com.simiacryptus.cognotik.util.AddApplyFileDiffLinks.loggingEnabled = { AppSettingsState.instance.diffLoggingEnabled }

            val currentThread = Thread.currentThread()
            val prevClassLoader = currentThread.contextClassLoader
            try {
                currentThread.contextClassLoader = PluginStartupActivity::class.java.classLoader
                init(project)

                addUserSuppliedModels(AppSettingsState.instance.userSuppliedModels?.mapNotNull {
                    try {
                        fromJson(it, AppSettingsState.UserSuppliedModel::class.java)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList())
            } catch (e: Exception) {
                log.error("Error during plugin startup", e)
            } finally {
                currentThread.contextClassLoader = prevClassLoader
            }

            //setupDocumentationTracking(project)

            if (AppSettingsState.instance.showWelcomeScreen || AppSettingsState.instance.greetedVersion != AppSettingsState.WELCOME_VERSION) {
                val welcomeFile = "welcomePage.md"
                val resource = PluginStartupActivity::class.java.classLoader.getResource(welcomeFile)
                var virtualFile = resource?.let { VirtualFileManager.getInstance().findFileByUrl(it.toString()) }
                if (virtualFile == null) try {
                    val path = resource?.toURI()?.let { java.nio.file.Paths.get(it) }
                    virtualFile = path?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
                } catch (e: Exception) {
                    log.debug("Error opening welcome page", e)
                }
                if (virtualFile == null) {
                    try {
                        val tempFile =
                            withContext(Dispatchers.IO) {
                                File.createTempFile(
                                    welcomeFile.substringBefore("."),
                                    "." + welcomeFile.substringAfter(".")
                                )
                            }
                        tempFile.deleteOnExit()
                        resource?.openStream()?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempFile.toPath())
                    } catch (e: Exception) {
                        log.error("Error opening welcome page", e)
                    }
                }
                virtualFile?.let {
                    try {
                        ApplicationManager.getApplication().invokeLater {
                            FileEditorManager.getInstance(project).openFile(it, true).forEach { editor ->
                                try {
                                    editor::class.declaredMembers.filter { it.name == "setLayout" }.forEach { member ->
                                        member.isAccessible = true
                                        member.call(editor, TextEditorWithPreview.Layout.SHOW_PREVIEW)
                                    }
                                } catch (e: Exception) {
                                    log.error("Error opening welcome page", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error opening welcome page", e)
                    }
                } ?: log.error("Welcome page not found")

                AppSettingsState.instance.greetedVersion = AppSettingsState.WELCOME_VERSION
                AppSettingsState.instance.showWelcomeScreen = false
            }

        } catch (e: Exception) {
            log.error("Error during plugin startup", e)
        }
    }

    private fun setupDocumentationTracking(project: Project) {
        messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    if (isDocumentationFile(file)) {
                        trackDocumentationPageView(file)
                    }
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    if (isDocumentationFile(file)) {
                        trackDocumentationPageClose(file)
                    }
                }
            }
        )
    }

    private fun isDocumentationFile(file: VirtualFile): Boolean {
        return file.path.contains("/docs/") || file.extension == "md"
    }

    private fun trackDocumentationPageView(file: VirtualFile) {
        if (AppSettingsState.instance.analyticsEnabled) {
            val pagePath = file.path
            documentationPageOpenTimes[pagePath] = System.currentTimeMillis()
            mapOf<String, @NonNls String>("page" to pagePath)
        }
    }

    private fun trackDocumentationPageClose(file: VirtualFile) {
        if (AppSettingsState.instance.analyticsEnabled) {
            val pagePath = file.path
            val openTime = documentationPageOpenTimes.remove(pagePath)
            if (openTime != null) {
                val timeSpent = System.currentTimeMillis() - openTime
                mapOf(
                    "page" to pagePath,
                    "time_spent" to TimeUnit.MILLISECONDS.toSeconds(timeSpent)
                )
            }
        }
    }

    private val isInitialized = AtomicBoolean(false)

    private fun init(project: Project) {
        if (isInitialized.getAndSet(true)) return
        ApplicationServicesConfig.dataStorageRoot = AppSettingsState.instance.pluginHome.resolve(".cognotik")
        SimpleDiffApplier.validatorProviders.add(0) { filename ->
            val extension = filename?.split('.')?.lastOrNull()
            if (IntelliJPsiValidator.isLanguageSupported(extension)) {
                IntelliJPsiValidator(project, extension ?: "", filename ?: "")
            } else {
                null
            }
        }
        ApplicationServices.clientManager = object : ClientManager() {
            override fun createChatClient(session: Session, user: User?) =
                IdeaChatClient.instance
        }
        AppSettingsState.instance.apply {
            ApplicationServices.cloud = when {
                awsProfile.isNullOrBlank() -> null
                awsRegion.isNullOrBlank() -> null
                awsBucket.isNullOrBlank() -> null
                else -> AwsPlatform(
                    bucket = awsBucket!!,
                    region = Region.of(awsRegion!!),
                    profileName = awsProfile!!,
                )
            }
        }
        ApplicationServices.authorizationManager = object : AuthorizationInterface {
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: User?,
                operationType: AuthorizationInterface.OperationType
            ) = true
        }
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = null
            override fun putUser(accessToken: String, user: User) = user
            override fun logout(accessToken: String, user: User) {}
        }
        isLocked = true
    }

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(PluginStartupActivity::class.java)

        fun addUserSuppliedModels(userModels: List<AppSettingsState.UserSuppliedModel>) {
            userModels.forEach { model ->
                ChatModel.values[model.displayName] = ChatModel(
                    name = model.displayName,
                    modelName = model.modelId,
                    maxTotalTokens = 4096,

                    provider = model.provider,
                    inputTokenPricePerK = 0.0,

                    outputTokenPricePerK = 0.0

                )
            }
        }

        private fun setLogInfo(name: String) {
            try {
                LoggerFactory.getLogger(name).apply {
                    when (this) {
                        is com.intellij.openapi.diagnostic.Logger -> setLevel(LogLevel.INFO)
                        is ch.qos.logback.classic.Logger -> setLevel(Level.INFO)
                        else -> log.info("Failed to set log level for $name: Unsupported log type (${this::class.java})")
                    }
                }
            } catch (e: Exception) {
                log.error("Error setting log level for $name", e)
            }
        }

        private fun setLogDebug(name: String) {
            try {
                LoggerFactory.getLogger(name).apply {
                    when (this) {
                        is com.intellij.openapi.diagnostic.Logger -> setLevel(LogLevel.DEBUG)
                        is ch.qos.logback.classic.Logger -> setLevel(Level.DEBUG)
                        else -> log.info("Failed to set log level for $name: Unsupported log type (${this::class.java})")
                    }
                }
            } catch (e: Exception) {
                log.error("Error setting log level for $name", e)
            }
        }

        private fun setLogWarn(name: String) {
            try {
                LoggerFactory.getLogger(name).apply {
                    when (this) {
                        is com.intellij.openapi.diagnostic.Logger -> setLevel(LogLevel.WARNING)
                        is ch.qos.logback.classic.Logger -> setLevel(Level.WARN)
                        else -> log.info("Failed to set log level for $name: Unsupported log type (${this::class.java})")
                    }
                }
            } catch (e: Exception) {
                log.error("Error setting log level for $name", e)
            }
        }
    }
}