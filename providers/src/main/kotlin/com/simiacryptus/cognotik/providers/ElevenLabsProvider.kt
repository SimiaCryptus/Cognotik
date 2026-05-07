package com.simiacryptus.cognotik.providers

    import com.google.common.util.concurrent.ListeningScheduledExecutorService
    import com.simiacryptus.cognotik.audio.AudioModels
    import com.simiacryptus.cognotik.chat.ChatClientInterface
    import com.simiacryptus.cognotik.chat.ElevenLabsChatClient
    import com.simiacryptus.cognotik.chat.model.ChatModel
    import com.simiacryptus.cognotik.chat.model.ElevenLabsModels
    import com.simiacryptus.cognotik.models.APIProvider
    import com.simiacryptus.cognotik.util.SecureString
    import org.apache.hc.core5.http.HttpRequest
    import org.slf4j.LoggerFactory
    import org.slf4j.event.Level
    import java.io.BufferedOutputStream
    import java.util.concurrent.ExecutorService

    class ElevenLabsProvider : APIProvider(
        name = "ElevenLabs",
        base = "https://api.elevenlabs.io"
    ) {
        private val log = LoggerFactory.getLogger(ElevenLabsProvider::class.java)

        override fun getChatClient(
            key: SecureString,
            base: String,
            workPool: ExecutorService,
            logLevel: Level,
            logStreams: MutableList<BufferedOutputStream>,
            scheduledPool: ListeningScheduledExecutorService
        ): ChatClientInterface {
            log.debug("Creating ElevenLabs chat client (TTS-only) for base={}", base)
            return ElevenLabsChatClient(
                apiKey = key,
                apiBase = base,
                workPool = workPool,
                logLevel = logLevel,
                logStreams = logStreams,
                scheduledPool = scheduledPool
            )
        }

        override fun getTranscriptionModels(key: SecureString, baseUrl: String): List<AudioModels> {
            // ElevenLabs supports speech-to-text via Scribe model
            return emptyList()
        }

    }