package com.simiacryptus.cognotik

import ch.qos.logback.classic.Level
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.simiacryptus.cognotik.config.AppSettingsComponent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.StaticAppSettingsConfigurable
import com.simiacryptus.cognotik.diff.SimpleDiffApplier
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.AwsPlatform
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.isLocked
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.util.IntelliJPsiValidator
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.showDocument
import software.amazon.awssdk.regions.Region
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class PluginStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        log.info("Starting Cognotik plugin initialization for project: ${project.name}")
        setLogInfo("org.apache.hc.client5.http")
        setLogInfo("org.eclipse.jetty")
        setLogInfo("com.simiacryptus")
//        setLogDebug("com.simiacryptus.cognotik.plan")
//        setLogInfo("com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask")
//        setLogDebug("com.simiacryptus.cognotik.util.FileSelectionUtils")
//        setLogDebug("com.simiacryptus.cognotik.util.FixedConcurrencyProcessor")
//        setLogDebug("com.simiacryptus.cognotik.chat")
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
            AddApplyFileDiffLinks.loggingEnabled =
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
            if (AppSettingsState.instance.showWelcomeScreen || AppSettingsState.instance.greetedVersion != AppSettingsState.WELCOME_VERSION) {
                log.debug("Showing welcome screen - showWelcomeScreen: ${AppSettingsState.instance.showWelcomeScreen}, greetedVersion: ${AppSettingsState.instance.greetedVersion}")
                if (project.showDocument("welcomePage.md")) return
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
        if (isInitialized.getAndSet(true)) return // Prevent double initialization
        dataStorageRoot = AppSettingsState.Companion.pluginHome
        log.info("Initializing ApplicationServices configuration: $dataStorageRoot")
        if (!dataStorageRoot.exists()) {
            try {
                dataStorageRoot.mkdirs()
                log.info("Created data storage directory: $dataStorageRoot")
            } catch (e: Exception) {
                log.error("Failed to create data storage directory: $dataStorageRoot", e)
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