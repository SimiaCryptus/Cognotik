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
import com.intellij.util.xmlb.XmlSerializerUtil
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.log
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import kotlin.random.Random


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
    var useScratchesSystemPath: Boolean = false,

    /* Model Settings */
    var smartModel: ApiChatModel? = null,
    var fastModel: ApiChatModel? = null,
    var imageChatModel: ApiChatModel? = null,
    var transcriptionModel: String? = null,
    var imageModel: ApiImageModel? = null,
    /* Embedding Model Settings */
    var embeddingModel: EmbeddingModel? = null,
    var processor: PatchProcessor = PatchProcessors.Fuzzy,

    /* AWS Settings */
    var awsProfile: String? = null,
    var awsRegion: String? = null,
    var awsBucket: String? = null,

    /* System Configuration */
    var analyticsEnabled: Boolean = false,
    var diffLoggingEnabled: Boolean = false,
    var listeningPort: Int = Random.nextInt(3000, 9000),
    var listeningEndpoint: String = "localhost",
    var apiThreads: Int = 4,
    var modalTasks: Boolean = false,
    var suppressErrors: Boolean = false,
    var devActions: Boolean = false,
    var disableAutoOpenUrls: Boolean = false,
    var preferredBrowser: String? = null,
    var greetedVersion: String = "",
    var shellCommand: String = getDefaultShell(),
    var feedbackRequested: Boolean = false,
    var feedbackOptOut: Boolean = false,

    /* Recent Activity Helpers */
    var savedPlanConfigs: MutableMap<String, String>? = mutableMapOf(),
    val savedCommandConfigsJson: MutableMap<String, String>? = mutableMapOf(),
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
    val imageChatClient: ChatInterface
        get() = imageChatModel?.instance() ?: throw IllegalStateException("Image chat model not configured")


    @get:JsonIgnore
    val imageClient: com.simiacryptus.cognotik.image.ImageClientInterface?
        get() = imageModel?.instance()


    @get:JsonIgnore
    val embeddingClient: com.simiacryptus.cognotik.embedding.Embedder? get() = embeddingModel?.instance()

    @JsonIgnore
    override fun getState() = SimpleEnvelope(toJson(this))

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

        /* Copy savedCommandConfigsJson */
//        savedCommandConfigsJson?.clear()
        fromJson.savedCommandConfigsJson?.forEach { (key, value) ->
            savedCommandConfigsJson?.set(key, value)
        }
        /* Copy savedPlanConfigs */
//        savedPlanConfigs?.clear()
        fromJson.savedPlanConfigs?.forEach { (key, value) ->
            savedPlanConfigs?.set(key, value)
        }
        /* Copy recentCommandsJson */
//        recentCommandsJson?.clear()
        fromJson.recentCommandsJson?.forEach { (key, value) ->
            recentCommandsJson?.set(key, value)
        }
        /* Copy recentArguments */
//        recentArguments?.clear()
        fromJson.recentArguments?.forEach { argument ->
            recentArguments?.add(argument)
        }
        /* Copy recentWorkingDirs */
//        recentWorkingDirs?.clear()
        fromJson.recentWorkingDirs?.forEach { workingDir ->
            recentWorkingDirs?.add(workingDir)
        }
        notifySettingsLoaded()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppSettingsState
        if (selectedMicLine != other.selectedMicLine) return false
        if (talkTime != other.talkTime) return false
        if (memorySeconds != other.memorySeconds) return false
        if (lookbackSeconds != other.lookbackSeconds) return false
        if (minRMS != other.minRMS) return false
        if (minIEC61672 != other.minIEC61672) return false
        if (minSpectralEntropy != other.minSpectralEntropy) return false
        if (minimumTalkSeconds != other.minimumTalkSeconds) return false
        if (rmsLevel != other.rmsLevel) return false
        if (iec61672Level != other.iec61672Level) return false
        if (spectralEntropyLevel != other.spectralEntropyLevel) return false
        if (sampleRate != other.sampleRate) return false
        if (sampleSize != other.sampleSize) return false
        if (channels != other.channels) return false
        if (temperature != other.temperature) return false
        if (useScratchesSystemPath != other.useScratchesSystemPath) return false
        if (smartModel != other.smartModel) return false
        if (fastModel != other.fastModel) return false
        if (imageChatModel != other.imageChatModel) return false
        if (transcriptionModel != other.transcriptionModel) return false
        if (imageModel != other.imageModel) return false
        if (embeddingModel != other.embeddingModel) return false
        if (processor != other.processor) return false
        if (awsProfile != other.awsProfile) return false
        if (awsRegion != other.awsRegion) return false
        if (awsBucket != other.awsBucket) return false
        if (analyticsEnabled != other.analyticsEnabled) return false
        if (diffLoggingEnabled != other.diffLoggingEnabled) return false
        if (listeningPort != other.listeningPort) return false
        if (listeningEndpoint != other.listeningEndpoint) return false
        if (apiThreads != other.apiThreads) return false
        if (modalTasks != other.modalTasks) return false
        if (suppressErrors != other.suppressErrors) return false
        if (devActions != other.devActions) return false
        if (disableAutoOpenUrls != other.disableAutoOpenUrls) return false
        if (preferredBrowser != other.preferredBrowser) return false
        if (greetedVersion != other.greetedVersion) return false
        if (shellCommand != other.shellCommand) return false
        if (savedPlanConfigs != other.savedPlanConfigs) return false
        if (savedCommandConfigsJson != other.savedCommandConfigsJson) return false
        if (recentCommandsJson != other.recentCommandsJson) return false
        if (recentArguments != other.recentArguments) return false
        if (recentWorkingDirs != other.recentWorkingDirs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = selectedMicLine?.hashCode() ?: 0
        result = 31 * result + talkTime.hashCode()
        result = 31 * result + memorySeconds.hashCode()
        result = 31 * result + lookbackSeconds.hashCode()
        result = 31 * result + minRMS.hashCode()
        result = 31 * result + minIEC61672.hashCode()
        result = 31 * result + minSpectralEntropy.hashCode()
        result = 31 * result + minimumTalkSeconds.hashCode()
        result = 31 * result + rmsLevel
        result = 31 * result + iec61672Level
        result = 31 * result + spectralEntropyLevel
        result = 31 * result + sampleRate
        result = 31 * result + sampleSize
        result = 31 * result + channels
        result = 31 * result + temperature.hashCode()
        result = 31 * result + useScratchesSystemPath.hashCode()
        result = 31 * result + smartModel.hashCode()
        result = 31 * result + fastModel.hashCode()
        result = 31 * result + (imageChatModel?.hashCode() ?: 0)
        result = 31 * result + (transcriptionModel?.hashCode() ?: 0)
        result = 31 * result + (imageModel?.hashCode() ?: 0)
        result = 31 * result + (embeddingModel?.hashCode() ?: 0)
        result = 31 * result + processor.hashCode()
        result = 31 * result + (awsProfile?.hashCode() ?: 0)
        result = 31 * result + (awsRegion?.hashCode() ?: 0)
        result = 31 * result + (awsBucket?.hashCode() ?: 0)
        result = 31 * result + analyticsEnabled.hashCode()
        result = 31 * result + diffLoggingEnabled.hashCode()
        result = 31 * result + listeningPort
        result = 31 * result + listeningEndpoint.hashCode()
        result = 31 * result + apiThreads
        result = 31 * result + modalTasks.hashCode()
        result = 31 * result + suppressErrors.hashCode()
        result = 31 * result + devActions.hashCode()
        result = 31 * result + disableAutoOpenUrls.hashCode()
        result = 31 * result + (preferredBrowser?.hashCode() ?: 0)
        result = 31 * result + greetedVersion.hashCode()
        result = 31 * result + shellCommand.hashCode()
        result = 31 * result + (savedPlanConfigs?.hashCode() ?: 0)
        result = 31 * result + (savedCommandConfigsJson?.hashCode() ?: 0)
        result = 31 * result + (recentCommandsJson?.hashCode() ?: 0)
        result = 31 * result + (recentArguments?.hashCode() ?: 0)
        result = 31 * result + (recentWorkingDirs?.hashCode() ?: 0)
        return result
    }

    companion object {
        var lastEvent: AnActionEvent? = null
        val log = LoggerFactory.getLogger(AppSettingsState::class.java)
        var auxiliaryLog: File? = null

        val localUser: User = com.simiacryptus.cognotik.platform.model.defaultUser
        const val WELCOME_VERSION: String = "2.0.8"

        @JvmStatic
        val instance: AppSettingsState by lazy {
            require(APIProvider.values().isNotEmpty()) { "No API providers registered" }
            ApplicationManager.getApplication()?.getService(AppSettingsState::class.java) ?: AppSettingsState()
        }

        fun getDefaultShell() = if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"

        @JsonIgnore
        var onSettingsLoadedListeners = mutableListOf<() -> Unit>()
        fun notifySettingsLoaded() {
            onSettingsLoadedListeners.forEach { it() }
        }

        val currentSession = Session.Companion.newGlobalID()
      val workPool = ApplicationServices.threadPoolManager.getPool(currentSession, AppSettingsState.localUser)
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

}


fun ApiChatModel.instance(): ChatInterface? {
    val usageManager = ApplicationServices.fileApplicationServices(AppSettingsState.Companion.pluginHome).usageManager
    val model = model
    if (model == null) {
        log.warn("Model not configured for ${provider?.provider?.name}")
        return null
    }
    return model.instance(
        key = provider?.key ?: throw IllegalArgumentException("API key is not set"),
        base = provider?.provider?.base
            ?: throw IllegalArgumentException("API base for ${provider?.provider?.name} is not set"),
        workPool = AppSettingsState.workPool,
        temperature = AppSettingsState.instance.temperature,
        scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
            AppSettingsState.currentSession,
          AppSettingsState.localUser
        ),
        onUsage = { model, usage ->
            usageManager.incrementUsage(
                AppSettingsState.currentSession,
              AppSettingsState.localUser,
                model,
                usage
            )
        },
    )
}

data class ApiImageModel(
    val model: ImageModel,
    val provider: ApiData?
)

fun ApiImageModel.instance(): com.simiacryptus.cognotik.image.ImageClientInterface? {
    val model = model
    if (model == null) {
        log.warn("Model not configured for ${provider?.provider?.name}")
        return null
    }
    return provider?.provider?.getImageClient(
        key = provider.key ?: throw IllegalArgumentException("API key is not set"),
        base = provider.apiBase,
        workPool = AppSettingsState.workPool,
        scheduledPool = ApplicationServices.threadPoolManager.getScheduledPool(
            AppSettingsState.currentSession,
          AppSettingsState.localUser
        ),
    )
}