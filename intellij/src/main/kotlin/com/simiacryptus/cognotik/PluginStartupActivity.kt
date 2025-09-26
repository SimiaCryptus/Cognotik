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
        log.info("Starting Cognotik plugin initialization for project: ${project.name}")
        setLogInfo("org.apache.hc.client5.http")
        setLogInfo("org.eclipse.jetty")
        setLogInfo("com.simiacryptus")
        setLogDebug("com.simiacryptus.cognotik.plan")
        setLogInfo("com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask")
        setLogDebug("com.simiacryptus.cognotik.util.FileSelectionUtils")
        setLogDebug("com.simiacryptus.cognotik.util.FixedConcurrencyProcessor")
        setLogInfo("TRAFFIC.com.simiacryptus.cognotik.webui.chat")

        System.getProperty("cognotik.config")?.let { configFile ->
            try {
                log.debug("Attempting to load config from: $configFile")
                val file = File(configFile)
                if (file.exists()) {
                    if (!file.canRead()) {
                        log.error("Config file $configFile exists but is not readable")
                        return@let
                    }
                    StaticAppSettingsConfigurable().apply {
                        val configContent = file.readText()
                        if (configContent.isBlank()) {
                            log.warn("Config file $configFile is empty")
                            return@let
                        }
                        import(configContent)
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

            com.simiacryptus.cognotik.util.AddApplyFileDiffLinks.loggingEnabled =
                { AppSettingsState.instance.diffLoggingEnabled }

            val currentThread = Thread.currentThread()
            val prevClassLoader = currentThread.contextClassLoader
            log.debug("Setting context class loader for plugin initialization")
            try {
                currentThread.contextClassLoader = PluginStartupActivity::class.java.classLoader
                init(project)
                log.info("Plugin initialization completed successfully")
            } catch (e: Exception) {
                log.error("Error during plugin startup", e)
            } finally {
                currentThread.contextClassLoader = prevClassLoader
            }

            //setupDocumentationTracking(project)

            if (AppSettingsState.instance.showWelcomeScreen || AppSettingsState.instance.greetedVersion != AppSettingsState.WELCOME_VERSION) {
                log.debug("Showing welcome screen - showWelcomeScreen: ${AppSettingsState.instance.showWelcomeScreen}, greetedVersion: ${AppSettingsState.instance.greetedVersion}")
                val welcomeFile = "welcomePage.md"
                val resource = PluginStartupActivity::class.java.classLoader.getResource(welcomeFile)
                if (resource == null) {
                    log.error("Welcome page resource not found: $welcomeFile")
                    return
                }
                var virtualFile = resource?.let { VirtualFileManager.getInstance().findFileByUrl(it.toString()) }
                if (virtualFile == null) try {
                    val path = resource?.toURI()?.let { java.nio.file.Paths.get(it) }
                    virtualFile = path?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
                } catch (e: Exception) {
                    log.debug("Error opening welcome page", e)
                }
                if (virtualFile == null) {
                    try {
                        log.debug("Creating temporary file for welcome page")
                        val tempFile =
                            withContext(Dispatchers.IO) {
                                File.createTempFile(
                                    welcomeFile.substringBefore("."),
                                    "." + welcomeFile.substringAfter(".")
                                )
                            }
                        tempFile.deleteOnExit()
                        resource?.openStream()?.use { input ->
                            if (input == null) {
                                log.error("Failed to open input stream for welcome page resource")
                                return
                            }
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempFile.toPath())
                        log.debug("Welcome page temporary file created: ${tempFile.absolutePath}")
                    } catch (e: Exception) {
                        log.error("Error opening welcome page", e)
                    }
                }
                virtualFile?.let {
                    try {
                        log.debug("Opening welcome page in editor")
                        ApplicationManager.getApplication().invokeLater {
                            FileEditorManager.getInstance(project).openFile(it, true).forEach { editor ->
                                try {
                                    editor::class.declaredMembers.filter { it.name == "setLayout" }.forEach { member ->
                                        member.isAccessible = true
                                        member.call(editor, TextEditorWithPreview.Layout.SHOW_PREVIEW)
                                        log.debug("Successfully set preview layout for welcome page")
                                    }
                                } catch (e: Exception) {
                                    log.warn("Failed to set preview layout for welcome page editor", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error opening welcome page", e)
                    }
                } ?: log.error("Welcome page not found")
                AppSettingsState.instance.greetedVersion = AppSettingsState.WELCOME_VERSION
                AppSettingsState.instance.showWelcomeScreen = false
                log.info("Welcome screen display completed")
            }

        } catch (e: Exception) {
            log.error("Critical error during plugin startup - plugin may not function correctly", e)
        }
    }

    private val isInitialized = AtomicBoolean(false)

    private fun init(project: Project) {
        if (isInitialized.getAndSet(true)) return
        log.info("Initializing ApplicationServices configuration")
        ApplicationServicesConfig.dataStorageRoot = AppSettingsState.instance.pluginHome.resolve(".cognotik")
        if (!ApplicationServicesConfig.dataStorageRoot.exists()) {
            try {
                ApplicationServicesConfig.dataStorageRoot.mkdirs()
                log.info("Created data storage directory: ${ApplicationServicesConfig.dataStorageRoot}")
            } catch (e: Exception) {
                log.error("Failed to create data storage directory: ${ApplicationServicesConfig.dataStorageRoot}", e)
            }
        }
        SimpleDiffApplier.validatorProviders.add(0) { filename ->
            val extension = filename?.split('.')?.lastOrNull()
            if (IntelliJPsiValidator.isLanguageSupported(extension)) {
                IntelliJPsiValidator(project, extension ?: "", filename ?: "")
            } else {
                null
            }
        }
        AppSettingsState.instance.apply {
            log.debug("Configuring AWS platform - profile: $awsProfile, region: $awsRegion, bucket: $awsBucket")
            ApplicationServices.cloud = when {
                awsProfile.isNullOrBlank() -> {
                    log.debug("AWS profile not configured")
                    null
                }

                awsRegion.isNullOrBlank() -> {
                    log.debug("AWS region not configured")
                    null
                }

                awsBucket.isNullOrBlank() -> {
                    log.debug("AWS bucket not configured")
                    null
                }
                else -> AwsPlatform(
                    bucket = awsBucket!!,
                    region = Region.of(awsRegion!!),
                    profileName = awsProfile!!,
                ).also {
                    log.info("AWS platform configured successfully with profile: $awsProfile, region: $awsRegion, bucket: $awsBucket")
                }
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