package com.simiacryptus.cognotik


import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.startup.ProjectActivity
import com.simiacryptus.cognotik.apps.ResourceApps
import com.simiacryptus.cognotik.config.AppSettingsComponent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.HostedAutoSetup
import com.simiacryptus.cognotik.config.StaticAppSettingsConfigurable
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.mcp.McpClientToolProvider
import com.simiacryptus.cognotik.mcp.McpTransport
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.platform.ChatInterface.Companion.ENABLE_LOGS
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.CognotikConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.CognotikPlatform
import com.simiacryptus.cognotik.platform.ServiceKey

import com.simiacryptus.cognotik.platform.h2.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.AuthorizationInterface
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.text.validate.FileValidators
import com.simiacryptus.cognotik.util.IntelliJPsiValidator
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.PlanHarness.Companion.initDynamicEnums
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PluginStartupActivity : ProjectActivity {

    init {
        require(null != CodeRuntimes.GroovyRuntime) { "Groovy runtime not initialized" } // Force DynamicEnum initialization
        DatabaseFacet.root = File(System.getProperty("user.home")).resolve(".cognotik2").absolutePath
        OrchestrationConfig.instanceFn =
            { model, user -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ServiceKey.AUTHORIZATION_MANAGER.factory = {
            object : AuthorizationInterface {
                override fun isAuthorized(
                    resource: ResourceRef?,
                    principal: Principal,
                    operationType: OperationType
                ): Boolean {
                    return true
                }
            }
        }
        ServiceKey.AUTHENTICATION.factory = {
            object : AuthenticationInterface {
                override fun getUser(accessToken: String?) = CognotikConfig.localUser
                override fun putUser(accessToken: String, user: User) = user
                 /* In hosted mode the hosted session token identifies the user to the backend (balance, usage). */
                 override fun listTokens(user: User): List<AuthenticationInterface.TokenMetadata> =
                     HostedAutoSetup.hostedToken()?.let {
                         listOf(AuthenticationInterface.TokenMetadata(token = it, userId = user.email ?: ""))
                     } ?: emptyList()
            }
        }
        ServiceKey.USER_RESOLVER.factory = {
            object : UserProvider {
                override fun authenticate(request: HttpServletRequest) = CognotikConfig.localUser
            }
        }
        //ResourceApps("/apps/disabled_apps.json").init()
        CoreProviders.init()
        CoreTasks.init()
        initDynamicEnums()
        CognotikPlatform.init()
        try {
            ResourceApps("apps/apps.json").init()
        } catch (e: Exception) {
            log.error("Failed to load apps.json", e)
        }
    }

    override suspend fun execute(project: Project) {
        log.info("Starting Cognotik plugin initialization for project: ${project.name}")
        val extFile = project.getExternalConfigurationDir()?.toFile()
        if (!DatabaseFacet.isInitialized() && null != extFile) {
            DatabaseFacet.root = extFile.resolve(".cognotik").absolutePath
        }
        try {
            ServiceKey.PLUGIN_MANAGER.get().getLoadedPlugins() // Force plugin loading to ensure classloader is initialized
        } catch (e: Exception) {
            log.error("Error loading plugins", e)
        }
        ENABLE_LOGS = true // TODO: Make this configurable via system property or plugin settings
        configLogging()
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
                    AppSettingsState.notifySettingsLoaded()
                    log.info("Loaded config from $configFile")
                } else {
                    log.warn("Config file $configFile does not exist")
                }
            } catch (e: Exception) {
                log.error("Error loading config file from $configFile", e)
            }
        }
        try {
            val currentThread = Thread.currentThread()
            val prevClassLoader = currentThread.contextClassLoader
            log.debug("Setting context class loader for plugin initialization")
            try {
                currentThread.contextClassLoader = PluginStartupActivity::class.java.classLoader
                init(project)
                 registerMcpLifecycleHooks(project)
                 refreshMcpLifecycle()
                 try {
                     if (HostedAutoSetup.isHosted()) HostedAutoSetup.enableHostedUsage()
                 } catch (e: Exception) {
                     log.warn("Could not enable hosted usage tracking", e)
                 }
                log.debug("Plugin initialization completed successfully")
            } catch (e: Exception) {
                log.error("Error during plugin startup", e)
            } finally {
                currentThread.contextClassLoader = prevClassLoader
            }
        } catch (e: Exception) {
            log.error("Critical error during plugin startup - plugin may not function correctly", e)
        }
    }

    private val isInitialized = AtomicBoolean(false)

    private fun init(project: Project) {
        if (isInitialized.getAndSet(true)) return // Prevent double initialization
        dataStorageRoot = AppSettingsState.pluginHome
        log.info("Initializing ApplicationServices configuration: $dataStorageRoot")
        if (!dataStorageRoot.exists()) {
            try {
                dataStorageRoot.mkdirs()
                log.info("Created data storage directory: $dataStorageRoot")
            } catch (e: Exception) {
                log.error("Failed to create data storage directory: $dataStorageRoot", e)
            }
        }
        FileValidators.validatorProviders.add(0) { filename ->
            val extension = filename?.split('.')?.lastOrNull()
            if (IntelliJPsiValidator.isLanguageSupported(extension)) {
                IntelliJPsiValidator(project, extension ?: "", filename ?: "")
            } else {
                null
            }
        }
        require(TaskType.values().isNotEmpty())
        AppSettingsState.instance.apply {
            log.debug("Configuring AWS platform - profile: $awsProfile, region: $awsRegion, bucket: $awsBucket")
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(PluginStartupActivity::class.java)
         private const val EMBEDDED_MCP_SERVER_NAME = "cognotik-embedded-mcp"
         private data class McpClientSpec(
             val name: String,
             val command: List<String>,
             val env: Map<String, String> = emptyMap(),
             val workingDir: String? = null,
             val timeoutSeconds: Long = 120L,
         )
         private data class ManagedMcpClient(
             val spec: McpClientSpec?,
             val provider: McpClientToolProvider,
         )
         private data class EmbeddedServerConfig(
             val path: String,
             val requireAuth: Boolean,
             val endpoint: String,
             val port: Int,
             val tools: List<MCPTool>,
         )
         private data class EmbeddedServerRegistration(
             val config: EmbeddedServerConfig,
             val loopbackProvider: McpClientToolProvider,
         )
         private val mcpLock = Any()
         private val mcpActive = AtomicBoolean(false)
         private val settingsListenerRegistered = AtomicBoolean(false)
         private val appListenersRegistered = AtomicBoolean(false)
         private val shutdownHookRegistered = AtomicBoolean(false)
         private val openProjects = ConcurrentHashMap.newKeySet<Project>()
         private val managedClients = ConcurrentHashMap<String, ManagedMcpClient>()
         private val managedTransports = CopyOnWriteArrayList<McpTransport>()
         private val managedCloseables = CopyOnWriteArrayList<AutoCloseable>()
         private val embeddedTools = CopyOnWriteArrayList<MCPTool>()
         @Volatile
         private var embeddedServerRegistration: EmbeddedServerRegistration? = null
         fun getManagedMcpClients(): Map<String, McpClientToolProvider> =
             managedClients.mapValues { it.value.provider }
         fun registerMcpClient(name: String, provider: McpClientToolProvider) {
             synchronized(mcpLock) {
                 val previous = managedClients.put(name, ManagedMcpClient(spec = null, provider = provider))
                 if (previous != null && previous.provider !== provider) {
                     closeQuietly("MCP client '$name'") { previous.provider.close() }
                 }
             }
         }
         fun unregisterMcpClient(name: String) {
             synchronized(mcpLock) {
                 managedClients.remove(name)?.let { removed ->
                     closeQuietly("MCP client '$name'") { removed.provider.close() }
                 }
             }
         }
         fun registerManagedTransport(transport: McpTransport) {
             managedTransports.add(transport)
         }
         fun registerManagedCloseable(closeable: AutoCloseable) {
             managedCloseables.add(closeable)
         }
         fun setEmbeddedMcpTools(tools: List<MCPTool>) {
             embeddedTools.clear()
             embeddedTools.addAll(tools)
             if (mcpActive.get()) {
                 refreshMcpLifecycleAsync()
             }
         }
         private fun registerMcpLifecycleHooks(project: Project) {
             openProjects.add(project)
             mcpActive.set(true)
             if (settingsListenerRegistered.compareAndSet(false, true)) {
                 AppSettingsState.onSettingsLoadedListeners.add {
                     if (mcpActive.get()) {
                         refreshMcpLifecycleAsync()
                     }
                 }
             }
             if (appListenersRegistered.compareAndSet(false, true)) {
                 ApplicationManager.getApplication()?.let { app ->
                     val connection = app.messageBus.connect()
                     connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                         override fun projectClosing(project: Project) {
                             onProjectClosingOrClosed(project)
                         }
                         override fun projectClosed(project: Project) {
                             onProjectClosingOrClosed(project)
                         }
                     })
                     connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
                         override fun appWillBeClosed(isRestart: Boolean) {
                             shutdownMcpLifecycle()
                         }
                         override fun appClosing() {
                             shutdownMcpLifecycle()
                         }
                     })
                 }
             }
             if (shutdownHookRegistered.compareAndSet(false, true)) {
                 runCatching {
                     Runtime.getRuntime().addShutdownHook(
                         Thread({ shutdownMcpLifecycle() }, "cognotik-mcp-shutdown")
                     )
                 }.onFailure {
                     log.debug("Could not register MCP JVM shutdown hook", it)
                 }
             }
         }
         private fun onProjectClosingOrClosed(project: Project) {
             if (openProjects.remove(project) && openProjects.isEmpty()) {
                 shutdownMcpLifecycle()
             }
         }
         private fun refreshMcpLifecycleAsync() {
             val task = Runnable {
                 val currentThread = Thread.currentThread()
                 val prevClassLoader = currentThread.contextClassLoader
                 try {
                     currentThread.contextClassLoader = PluginStartupActivity::class.java.classLoader
                     refreshMcpLifecycle()
                 } catch (e: Exception) {
                     log.warn("Error refreshing MCP lifecycle on settings change", e)
                 } finally {
                     currentThread.contextClassLoader = prevClassLoader
                 }
             }
             val app = ApplicationManager.getApplication()
             if (app != null) {
                 app.executeOnPooledThread(task)
             } else {
                 AppSettingsState.workPool.submit(task)
             }
         }
         fun refreshMcpLifecycle() {
             synchronized(mcpLock) {
                 if (!mcpActive.get()) return
                 refreshManagedClientsLocked()
                 refreshEmbeddedServerHostLocked()
             }
         }
         private fun refreshManagedClientsLocked() {
             val desiredSpecs = loadConfiguredMcpSpecs()
             val toRemove = managedClients.entries
                 .filter { (_, entry) ->
                     entry.spec != null && desiredSpecs[entry.spec.name] != entry.spec
                 }
                 .map { it.key }
             toRemove.forEach { name ->
                 managedClients.remove(name)?.let { removed ->
                     log.info("Closing stale or removed MCP client '{}'", name)
                     closeQuietly("MCP client '$name'") { removed.provider.close() }
                 }
             }
             desiredSpecs.forEach { (name, spec) ->
                 val existing = managedClients[name]
                 if (existing != null) {
                     return@forEach
                 }
                 var provider: McpClientToolProvider? = null
                 try {
                     provider = McpClientToolProvider.stdio(
                         name = spec.name,
                         command = spec.command,
                         env = spec.env,
                         workingDir = spec.workingDir?.let(::File),
                         requestTimeout = Duration.ofSeconds(spec.timeoutSeconds),
                     )
                     provider.connect()
                     managedClients[name] = ManagedMcpClient(spec = spec, provider = provider)
                     log.info("Initialized managed MCP client '{}'", name)
                 } catch (e: Exception) {
                     log.warn("Failed to initialize MCP client '{}': {}", name, e.message, e)
                     provider?.let { closeQuietly("failed MCP client '$name'") { it.close() } }
                 }
             }
         }
         private fun refreshEmbeddedServerHostLocked() {
             val settings = AppSettingsState.instance
             val current = embeddedServerRegistration
             if (!settings.mcpServerEnabled) {
                 if (current != null) {
                     log.info("Disabling embedded MCP server host registration")
                     closeQuietly("embedded MCP server host") { current.loopbackProvider.close() }
                     embeddedServerRegistration = null
                 }
                 return
             }
             val desired = EmbeddedServerConfig(
                 path = settings.mcpServerPath,
                 requireAuth = settings.mcpRequireAuth,
                 endpoint = settings.listeningEndpoint,
                 port = settings.listeningPort,
                 tools = embeddedTools.toList(),
             )
             if (current?.config == desired) {
                 return
             }
             if (current != null) {
                 closeQuietly("previous embedded MCP server host") { current.loopbackProvider.close() }
                 embeddedServerRegistration = null
             }
             var loopback: McpClientToolProvider? = null
             try {
                 loopback = McpClientToolProvider.loopback(
                     name = EMBEDDED_MCP_SERVER_NAME,
                     tools = desired.tools,
                     session = AppSettingsState.currentSession,
                     user = CognotikConfig.localUser,
                 ).connect()
                 embeddedServerRegistration = EmbeddedServerRegistration(desired, loopback)
                 log.info(
                     "Registered embedded MCP server host (path={}, requireAuth={}, endpoint={}:{}, tools={})",
                     desired.path,
                     desired.requireAuth,
                     desired.endpoint,
                     desired.port,
                     desired.tools.size
                 )
             } catch (e: Exception) {
                 log.warn("Failed to register embedded MCP server host: {}", e.message, e)
                 loopback?.let { closeQuietly("failed embedded MCP server host") { it.close() } }
             }
         }
         fun shutdownMcpLifecycle() {
             synchronized(mcpLock) {
                 mcpActive.set(false)
                 embeddedServerRegistration?.let { registration ->
                     log.info("Shutting down embedded MCP server host registration")
                     closeQuietly("embedded MCP server host") { registration.loopbackProvider.close() }
                     embeddedServerRegistration = null
                 }
                 val clientNames = managedClients.keys.toList()
                 clientNames.forEach { name ->
                     managedClients.remove(name)?.let { entry ->
                         log.info("Shutting down managed MCP client '{}'", name)
                         closeQuietly("MCP client '$name'") { entry.provider.close() }
                     }
                 }
                 val transports = managedTransports.toList()
                 managedTransports.clear()
                 transports.forEach { transport ->
                     closeQuietly("MCP transport") { transport.close() }
                 }
                 val closeables = managedCloseables.toList()
                 managedCloseables.clear()
                 closeables.forEach { closeable ->
                     closeQuietly("MCP resource") { closeable.close() }
                 }
             }
         }
         private fun loadConfiguredMcpSpecs(): Map<String, McpClientSpec> {
             val specs = linkedMapOf<String, McpClientSpec>()
             val candidateFiles = listOfNotNull(
                 File(AppSettingsState.pluginHome, "mcp.json"),
                 System.getProperty("cognotik.mcp.config")?.takeIf { it.isNotBlank() }?.let(::File),
                 System.getProperty("cognotik.config")?.takeIf { it.isNotBlank() }?.let(::File),
             )
             val mapper = JsonUtil.objectMapper()
             for (file in candidateFiles) {
                 if (!file.exists() || !file.isFile || !file.canRead()) continue
                 try {
                     val content = file.readText()
                     if (content.isBlank()) continue
                     val root = mapper.readTree(content)
                     val serversNode = root.get("mcpServers")?.takeIf { it.isObject } ?: continue
                     serversNode.fields().forEach { (name, node) ->
                         parseMcpClientSpec(name, node)?.let { specs[name] = it }
                     }
                 } catch (e: Exception) {
                     log.warn("Failed to parse MCP configuration from {}: {}", file.absolutePath, e.message)
                 }
             }
             return specs
         }
         private fun parseMcpClientSpec(name: String, node: JsonNode): McpClientSpec? {
             if (!node.isObject) return null
             if (node.path("disabled").asBoolean(false) || !node.path("enabled").asBoolean(true)) {
                 return null
             }
             val commandNode = node.get("command") ?: return null
             val command: List<String> = when {
                 commandNode.isArray -> commandNode.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }
                 commandNode.isTextual -> {
                     val args = node.get("args")?.takeIf(JsonNode::isArray)
                         ?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }
                         ?: emptyList()
                     listOf(commandNode.asText()) + args
                 }
                 else -> emptyList()
             }
             if (command.isEmpty()) return null
             val env = linkedMapOf<String, String>()
             node.get("env")?.takeIf(JsonNode::isObject)?.fields()?.forEach { (k, v) ->
                 if (!v.isNull) env[k] = v.asText()
             }
             val workingDir = (node.get("cwd") ?: node.get("workingDir"))
                 ?.takeIf { it.isTextual && it.asText().isNotBlank() }
                 ?.asText()
             val timeoutSeconds = (node.get("timeoutSeconds") ?: node.get("requestTimeoutSeconds"))
                 ?.takeIf { it.isNumber }
                 ?.asLong()
                 ?.takeIf { it > 0L }
                 ?: 120L
             return McpClientSpec(
                 name = name,
                 command = command,
                 env = env,
                 workingDir = workingDir,
                 timeoutSeconds = timeoutSeconds,
             )
         }
         private inline fun closeQuietly(label: String, action: () -> Unit) {
             try {
                 action()
             } catch (e: Exception) {
                 log.warn("Error closing {}: {}", label, e.message, e)
             }
         }


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

        fun configLogging() {
            setLogInfo("org.apache.hc.client5.http")
            setLogInfo("org.eclipse.jetty")
            setLogInfo("com.simiacryptus")
            setLogDebug("com.simiacryptus.cognotik.chat")
            //        setLogDebug("com.simiacryptus.cognotik.plan")
            //        setLogInfo("com.simiacryptus.cognotik.plan.tools.online.CrawlerAgent)
            //        setLogDebug("com.simiacryptus.cognotik.util.FileSelectionUtils")
            //        setLogDebug("com.simiacryptus.cognotik.util.FixedConcurrencyProcessor")
            //        setLogDebug("com.simiacryptus.cognotik.chat")
            //        setLogInfo("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
        }
    }
}