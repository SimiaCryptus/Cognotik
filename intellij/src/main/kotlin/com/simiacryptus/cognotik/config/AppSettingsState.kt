package com.simiacryptus.cognotik.config

/**
var transcriptionModel: String = AudioModels.Whisper.modelName
 * Stores and manages plugin configuration settings.
 *
 * This class is responsible for persisting and retrieving the plugin's
 * configuration settings. It uses the IntelliJ Platform's persistence
 * framework to save settings across IDE restarts.
 */
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ImageModels
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import kotlin.random.Random

data class CommandConfig(
    val commands: List<PatchApp.CommandSettings>,
    val exitCodeOption: String,
    val autoFix: Boolean,
    val maxRetries: Int,
    val additionalInstructions: String,
    val includeGitDiffs: Boolean = false,
    val includeLineNumbers: Boolean = false,
    val apiBudget: Double,
)

@State(name = "com.simiacryptus.cognotik.config.AppSettingsState", storages = [Storage("SdkSettingsPlugin.xml")])
data class AppSettingsState(

    /* Audio Settings */
    var selectedMicLine: String? = null,
    var talkTime: Double = 1.0,
    var memorySeconds: Double = 10.0,
    var lookbackSeconds: Double = 5.0,
    var minRMS: Double = 0.5,
    var minIEC61672: Double = 0.5,
    var minSpectralEntropy: Double = 0.5,
    var minimumTalkSeconds: Double = 1.0,
    var rmsLevel: Int = 0,
    var iec61672Level: Int = 0,
    var spectralEntropyLevel: Int = 0,
    var sampleRate: Int = 44100,
    var sampleSize: Int = 16,
    var channels: Int = 1,
    var temperature: Double = 0.1,

    /* Model Settings */
    var smartModel: ApiChatModel? = null,
    var fastModel: ApiChatModel? = null,
    var transcriptionModel: String? = null,
    var mainImageModel: String = "",
    /* Embedding Model Settings */
    var embeddingModel: EmbeddingModel? = null,


    /* AWS Settings */
    var awsProfile: String? = null,
    var awsRegion: String? = null,
    var awsBucket: String? = null,

    /* System Configuration */
    var executables: MutableSet<String>? = mutableSetOf(),
    var analyticsEnabled: Boolean = false,
    var diffLoggingEnabled: Boolean = false,
    var listeningPort: Int = Random.nextInt(3000, 9000),
    var listeningEndpoint: String = "localhost",
    var apiThreads: Int = 4,
    var modalTasks: Boolean = false,
    var suppressErrors: Boolean = false,
    var devActions: Boolean = false,
    var disableAutoOpenUrls: Boolean = false,
    var showWelcomeScreen: Boolean = true,
    var greetedVersion: String = "",
    var shellCommand: String = getDefaultShell(),

    /* Recent Activity Helpers */
    val savedCommandConfigsJson: MutableMap<String, String>? = mutableMapOf(),
    var savedPlanConfigs: MutableMap<String, String>? = mutableMapOf(),
    val recentCommandsJson: MutableMap<String, String>? = mutableMapOf(),
    val recentArguments: MutableList<String>? = mutableListOf(),
    val recentWorkingDirs: MutableList<String>? = mutableListOf(),
) : PersistentStateComponent<SimpleEnvelope> {

    @get:JsonIgnore
    val smartChatClient: ChatInterface
        get() = smartModel?.instance() ?: throw IllegalStateException("Smart model not configured")

    @get:JsonIgnore
    val fastChatClient: ChatInterface
        get() = fastModel?.instance() ?: throw IllegalStateException("Fast model not configured")

    @get:JsonIgnore
    val embeddingClient: com.simiacryptus.cognotik.embedding.Embedder? get() = embeddingModel?.instance()

    @JsonIgnore
    override fun getState() = SimpleEnvelope(toJson(this))

//    private fun handleLegacyKeys(
//        jsonNode: JsonNode
//    ) {
//        // Migrate legacy API keys to UserSettingsManager
//        val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
//        var needsUpdate = false
//
//        // Handle old apiKey field
//        if (jsonNode.has("apiKey")) {
//            val apiKeyNode = jsonNode.get("apiKey")
//            if (apiKeyNode.isObject) {
//                apiKeyNode.fields().forEach { (providerName, keyValue) ->
//                    try {
//                        val provider = APIProvider.valueOf(providerName)
//                        val existingApi = userSettings.apis.find { it.provider == provider }
//                        if (existingApi == null) {
//                            userSettings.apis.add(
//                                ApiData(
//                                    key = keyValue.asText(),
//                                    provider = provider,
//                                    baseUrl = provider.base
//                                ).validate()
//                            )
//                            needsUpdate = true
//                        }
//                    } catch (e: Exception) {
//                        log.warn("Unknown provider in legacy config: $providerName", e)
//                    }
//                }
//            }
//        }
//
//        // Handle apiKeys and apiBase fields
//        if (jsonNode.has("apiKeys") || jsonNode.has("apiBase")) {
//            val apiKeysNode = jsonNode.get("apiKeys")
//            val apiBaseNode = jsonNode.get("apiBase")
//
//            if (apiKeysNode != null && apiKeysNode.isObject) {
//                apiKeysNode.fields().forEach { (providerName, keyValue) ->
//                    try {
//                        val provider = APIProvider.valueOf(providerName)
//                        val baseUrl = apiBaseNode?.get(providerName)?.asText() ?: provider.base
//                        val existingApi = userSettings.apis.find { it.provider == provider }
//                        if (existingApi == null) {
//                            userSettings.apis.add(
//                                ApiData(
//                                    key = keyValue.asText(),
//                                    provider = provider,
//                                    baseUrl = baseUrl
//                                ).validate()
//                            )
//                            needsUpdate = true
//                        }
//                    } catch (e: Exception) {
//                        log.warn("Unknown provider in legacy config: $providerName", e)
//                    }
//                }
//            }
//        }
//        if (needsUpdate) {
//            ApplicationServices.fileApplicationServices().userSettingsManager.updateUserSettings(
//                UserSettingsManager.defaultUser,
//                userSettings
//            )
//        }
//    }

    @JsonIgnore
    fun getRecentCommands(id: String) = recentCommandsJson?.get(id)?.let {
        try {
            fromJson(it, MRUItems::class.java)
        } catch (e: Exception) {
            log.warn("Error loading recent commands: $it", e)
            MRUItems()
        }
    } ?: MRUItems()

    @JsonIgnore
    override fun loadState(state: SimpleEnvelope) {
        state.value ?: return
        val fromJson = try {
            val jsonNode = ObjectMapper().readTree(state.value)
            //handleLegacyKeys(jsonNode)
            try {
                fromJson(ObjectMapper().writeValueAsString(jsonNode), AppSettingsState::class.java)
            } catch (e: Exception) {
                log.warn("Error parsing settings: $jsonNode", e)
                AppSettingsState()
            }
        } catch (e: Exception) {
            log.warn("Error loading settings: ${state.value}", e)
            AppSettingsState()
        }

        XmlSerializerUtil.copyBean(fromJson, this)

        /* Copy executables */
        executables?.clear()
        fromJson.executables?.forEach { executable ->
            executables?.add(executable)
        }
        /* Copy savedCommandConfigsJson */
        savedCommandConfigsJson?.clear()
        fromJson.savedCommandConfigsJson?.forEach { (key, value) ->
            savedCommandConfigsJson?.set(key, value)
        }
        /* Copy savedPlanConfigs */
        savedPlanConfigs?.clear()
        fromJson.savedPlanConfigs?.forEach { (key, value) ->
            savedPlanConfigs?.set(key, value)
        }
        /* Copy recentCommandsJson */
        recentCommandsJson?.clear()
        fromJson.recentCommandsJson?.forEach { (key, value) ->
            recentCommandsJson?.set(key, value)
        }
        /* Copy recentArguments */
        recentArguments?.clear()
        fromJson.recentArguments?.forEach { argument ->
            recentArguments?.add(argument)
        }
        /* Copy recentWorkingDirs */
        recentWorkingDirs?.clear()
        fromJson.recentWorkingDirs?.forEach { workingDir ->
            recentWorkingDirs?.add(workingDir)
        }
        notifySettingsLoaded()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppSettingsState
        if (minRMS != other.minRMS) return false
        if (minIEC61672 != other.minIEC61672) return false
        if (minSpectralEntropy != other.minSpectralEntropy) return false
        if (rmsLevel != other.rmsLevel) return false
        if (iec61672Level != other.iec61672Level) return false
        if (spectralEntropyLevel != other.spectralEntropyLevel) return false
        if (sampleRate != other.sampleRate) return false
        if (sampleSize != other.sampleSize) return false
        if (channels != other.channels) return false
        if (temperature != other.temperature) return false
        if (smartModel != other.smartModel) return false
        if (fastModel != other.fastModel) return false
        if (mainImageModel != other.mainImageModel) return false
        if (listeningPort != other.listeningPort) return false
        if (listeningEndpoint != other.listeningEndpoint) return false
        if (apiThreads != other.apiThreads) return false
        if (modalTasks != other.modalTasks) return false
        if (suppressErrors != other.suppressErrors) return false
        if (devActions != other.devActions) return false
        if (!FileUtil.filesEqual(pluginHome, pluginHome)) return false
        if (recentCommandsJson != other.recentCommandsJson) return false
        if (showWelcomeScreen != other.showWelcomeScreen) return false
        if (greetedVersion != other.greetedVersion) return false
        if (mainImageModel != other.mainImageModel) return false
        if (executables != other.executables) return false
        if (embeddingModel != other.embeddingModel) return false
        if (awsProfile != other.awsProfile) return false
        if (awsRegion != other.awsRegion) return false
        if (awsBucket != other.awsBucket) return false
        if (selectedMicLine != other.selectedMicLine) return false
        return true
    }

    override fun hashCode(): Int {
        var result = temperature.hashCode()
        result = 31 * result + minRMS.hashCode()
        result = 31 * result + minIEC61672.hashCode()
        result = 31 * result + minSpectralEntropy.hashCode()
        result = 31 * result + rmsLevel
        result = 31 * result + iec61672Level
        result = 31 * result + spectralEntropyLevel
        result = 31 * result + sampleRate
        result = 31 * result + sampleSize
        result = 31 * result + channels
        result = 31 * result + smartModel.hashCode()
        result = 31 * result + fastModel.hashCode()
        result = 31 * result + mainImageModel.hashCode()
        result = 31 * result + listeningPort
        result = 31 * result + listeningEndpoint.hashCode()
        result = 31 * result + apiThreads
        result = 31 * result + modalTasks.hashCode()
        result = 31 * result + suppressErrors.hashCode()
        result = 31 * result + devActions.hashCode()
        result = 31 * result + FileUtil.fileHashCode(pluginHome)
        result = 31 * result + recentCommandsJson.hashCode()
        result = 31 * result + showWelcomeScreen.hashCode()
        result = 31 * result + greetedVersion.hashCode()
        result = 31 * result + mainImageModel.hashCode()
        result = 31 * result + executables.hashCode()
        result = 31 * result + (embeddingModel?.hashCode() ?: 0)
        result = 31 * result + (awsProfile?.hashCode() ?: 0)
        result = 31 * result + (awsRegion?.hashCode() ?: 0)
        result = 31 * result + (awsBucket?.hashCode() ?: 0)
        result = 31 * result + (selectedMicLine?.hashCode() ?: 0)
        return result
    }

    companion object {
        var lastEvent: AnActionEvent? = null
        val log = LoggerFactory.getLogger(AppSettingsState::class.java)
        var auxiliaryLog: File? = null
        const val WELCOME_VERSION: String = "2.0.8"

        @JvmStatic
        val instance: AppSettingsState by lazy {
            require(APIProvider.values().isNotEmpty()) { "No API providers registered" }
            ApplicationManager.getApplication()?.getService(AppSettingsState::class.java) ?: AppSettingsState()
        }

        fun getDefaultShell() = if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"

        @JsonIgnore
        var onSettingsLoadedListeners = mutableListOf<() -> Unit>()
        fun notifySettingsLoaded() { onSettingsLoadedListeners.forEach { it() } }

        val currentSession = Session.Companion.newGlobalID()
        val workPool = ApplicationServices.threadPoolManager.getPool(currentSession, UserSettingsManager.defaultUser)
        val pluginHome: File by lazy {
            run {
                var logPath: String? = null
                //if (logPath == null) logPath = System.getProperty("java.io.tmpdir")
                if (logPath == null) logPath = System.getProperty("user.home")
                if (logPath == null) logPath = System.getProperty("idea.plugins.path")
                File(logPath, ".cognotik")
            }
        }
    }

    data class SavedPlanConfig(
        val name: String,
        val temperature: Double,
        val autoFix: Boolean,
        val taskSettings: Map<String, TaskTypeConfig>
    )
}

fun String.imageModel(): ImageModels {
    return ImageModels.entries.firstOrNull {
        it.modelName == this || it.name == this
    } ?: ImageModels.DallE3
}

fun ApiChatModel.instance(): ChatInterface? {
    val usageManager = ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager
    val model = model
    if (model == null) {
        throw RuntimeException("Model not configured for ${provider?.provider?.name}")
    }
    return (model).instance(
        key = provider?.key ?: throw IllegalArgumentException("API key is not set"),
        base = provider?.provider?.base
            ?: throw IllegalArgumentException("API base for ${provider?.provider?.name} is not set"),
        workPool = AppSettingsState.workPool,
        temperature = AppSettingsState.instance.temperature,
        scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
            AppSettingsState.currentSession,
            UserSettingsManager.defaultUser
        ),
        onUsage = { model, usage ->
            usageManager.incrementUsage(
                AppSettingsState.currentSession,
                UserSettingsManager.defaultUser, model, usage
            )
        },
    )
}