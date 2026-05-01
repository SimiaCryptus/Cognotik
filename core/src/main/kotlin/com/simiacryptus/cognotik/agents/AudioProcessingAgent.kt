package com.simiacryptus.cognotik.agents
    
    import com.simiacryptus.cognotik.chat.model.ChatInterface
    import com.simiacryptus.cognotik.models.ModelSchema
    import com.simiacryptus.cognotik.models.ModelSchema.AudioInput
    import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
    import com.simiacryptus.cognotik.models.ModelSchema.ContentPart
    import com.simiacryptus.cognotik.util.toContentList
    
    /**
     * Agent that processes text/audio input and generates text/audio output based on the prompt.
     * Can be used for audio transcription, audio generation, audio Q&A, and audio editing tasks.
     */
    open class AudioProcessingAgent(
        prompt: String = "Analyze and respond to the audio based on the user's request",
        name: String? = null,
        model: ChatInterface,
        temperature: Double = 0.3,
    ) : BaseAgent<List<AudioAndText>, AudioAndText>(
        prompt = prompt,
        name = name,
        model = model,
        temperature = temperature,
    ) {
    
        override fun chatMessages(questions: List<AudioAndText>) = arrayOf(
            ChatMessage(
                role = ModelSchema.Role.system,
                content = prompt.toContentList()
            ),
            ChatMessage(
                role = ModelSchema.Role.user,
                content = questions.flatMap { question ->
                    listOf(
                        ContentPart(
                            text = question.text,
                            input_audio = question.audio
                        )
                    )
                }
            )
        )
    
        override fun respond(
            input: List<AudioAndText>,
            vararg messages: ChatMessage
        ): AudioAndText {
            val choices = response(*messages).choices
            val responseMessage = choices.firstOrNull()?.message
            val text = responseMessage?.content ?: ""
            val audio: AudioInput? = responseMessage?.audio_data?.let { audioBytes ->
                AudioInput(
                    data = java.util.Base64.getEncoder().encodeToString(audioBytes),
                    format = responseMessage.audio_format ?: "mp3"
                )
            } ?: run {
                if (responseMessage?.audio_data == null) {
                    log.info("No audio returned in response, falling back to input audio.")
                }
                input.map { it.audio }.firstOrNull()
            }
            return AudioAndText(text = text, audio = audio)
        }
    
        override fun withModel(model: ChatInterface): AudioProcessingAgent = AudioProcessingAgent(
            prompt = prompt,
            name = name,
            model = model,
            temperature = temperature,
        )
    
        companion object {
            private val log = org.slf4j.LoggerFactory.getLogger(AudioProcessingAgent::class.java)
        }
    }