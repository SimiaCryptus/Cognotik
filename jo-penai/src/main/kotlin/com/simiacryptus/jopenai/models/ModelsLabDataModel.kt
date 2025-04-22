package com.simiacryptus.jopenai.models

class ModelsLabDataModel {

    data class ChatRequest(
        val key: String? = null,
        val model_id: String? = null,
        val chat_id: String? = null,
        val system_prompt: String? = null,
        val prompt: String? = null,
        val max_new_tokens: Int? = null,
        val do_sample: Boolean? = null,
        val temperature: Double? = null,
        val top_k: Int? = null,
        val top_p: Double? = null,
        val no_repeat_ngram_size: Int? = null,
        val seed: Int? = null,
        val temp: Boolean? = null,
        val reset: Boolean? = null,
        val uncensored_system_prompt: Boolean? = null,
        val webhook: String? = null,
        val track_id: String? = null,
    )

    data class ChatResponse(
        val status: String? = null,
        val output: Any? = null,
        val message: String? = null,
        val chat_id: String? = null,
        val meta: Meta? = null,
        val eta: Int? = null,
    )

    data class Meta(
        val chat_id: String? = null,
        val created_at: String? = null,
        val do_sample: String? = null,
        val max_new_tokens: Int? = null,
        val model_id: String? = null,
        val no_repeat_ngram_size: Int? = null,
        val num_return_sequences: Int? = null,
        val pipeline_tag: String? = null,
        val prompt: String? = null,
        val seed: Long? = null,
        val temp: String? = null,
        val temperature: Double? = null,
        val top_k: Int? = null,
        val top_p: Double? = null,
        val updated_at: String? = null
    )

}