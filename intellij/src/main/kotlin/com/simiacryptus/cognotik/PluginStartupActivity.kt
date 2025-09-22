package com.simiacryptus.cognotik

import ch.qos.logback.classic.Level
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsComponent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.StaticAppSettingsConfigurable
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.AwsPlatform
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.IntelliJPsiValidator
import com.simiacryptus.cognotik.util.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.regions.Region
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        setLogInfo("org.apache.hc.client5.http")
        setLogInfo("org.eclipse.jetty")
        setLogInfo("com.simiacryptus")
        setLogDebug("com.simiacryptus.cognotik.plan")
        setLogDebug("com.simiacryptus.cognotik.util.FileSelectionUtils")
        setLogInfo("TRAFFIC.com.simiacryptus.cognotik.webui.chat")

        System.getProperty("cognotik.config")?.let { configFile ->
            try {
                val file = File(configFile)
                if (file.exists()) {
                    StaticAppSettingsConfigurable().apply {
                        import(file.readText())
                        write(AppSettingsState.instance, AppSettingsComponent())
                    }
                    AppSettingsState.Companion.notifySettingsLoaded()
                    log.info("Loaded config from $configFile")
                } else {
                    log.warn("Config file $configFile does not exist")
                }
            } catch (e: Exception) {
                log.error("Error loading config file from $configFile", e)
            }
        }
        try {

            com.simiacryptus.cognotik.util.AddApplyFileDiffLinks.loggingEnabled = { AppSettingsState.instance.diffLoggingEnabled }

            val currentThread = Thread.currentThread()
            val prevClassLoader = currentThread.contextClassLoader
            try {
                currentThread.contextClassLoader = PluginStartupActivity::class.java.classLoader
                init(project)
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
        val log = LoggerFactory.getLogger(PluginStartupActivity::class.java)

        private fun setLogInfo(name: String) {
            try {
                LoggerFactory.getLogger(name).apply {
                    when (this) {
                        is Logger -> setLevel(LogLevel.INFO)
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
                        is Logger -> setLevel(LogLevel.DEBUG)
                        is ch.qos.logback.classic.Logger -> setLevel(Level.DEBUG)
                        else -> log.info("Failed to set log level for $name: Unsupported log type (${this::class.java})")
                    }
                }
            } catch (e: Exception) {
                log.error("Error setting log level for $name", e)
            }
        }

    }
}