package com.simiacryptus.cognotik.chat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.OpenAIModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.exceptions.NonRetryableException
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.mcp.MCPContent
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class OpenAIChatClient(
  apiKey: SecureString,
  apiBase: String,
  workPool: ExecutorService,
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.OpenAI,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  scheduledPool = scheduledPool,
  session = session,
) {

  override fun authorize(
    request: HttpRequest,
  ) {
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    request.addHeader("Authorization", "Bearer ${apiKey.decrypt}")
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
   logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
   check(chatRequest.toolProviders.isNullOrEmpty() || !chatRequest.tools.isNullOrEmpty()) {
     TOOL_PROVIDERS_WITHOUT_TOOLS
   }
    return withPerformanceLogging {
     val openAIChatRequest = try {
       buildRequest(chatRequest, model)
     } catch (e: Exception) {
       log.error("Failed to map chat request to OpenAI format", e)
       throw NonRetryableException("Failed to map chat request to OpenAI format: ${e.message}", e)
     }

      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
       .writeValueAsString(openAIChatRequest)

      // Pass the model so AI_ERROR metrics can be broken down per model.
      val rawResponse =
        post("${apiBase}/chat/completions", json, model = model.modelId)
      // ChatClientBase.post only reports embedded API errors; throw the typed exception here.
      checkError(rawResponse, model)
     val response = try {
       require(rawResponse.isNotBlank()) { "Response cannot be blank" }
       toChatResponse(JsonUtil.objectMapper().readValue(rawResponse, OpenAIChatResponse::class.java))
     } catch (e: IOException) {
       throw e
     } catch (e: NonRetryableException) {
       throw e
     } catch (e: Exception) {
       log.error("Failed to parse OpenAI response: $rawResponse", e)
       throw RuntimeException("Failed to parse OpenAI response: ${e.message}", e)
     }

     response.usage?.let { usage ->
       usageHandler.onUsage(
         model,
         usage,
         ModelSchema.UsageData(
           input_text = openAIChatRequest.messages.joinToString("\n\n") { describeMessage(it) },
           output_text = response.choices.joinToString("\n\n") { it.message?.content.orEmpty() },
         ),
       )
      }

      response
    }
  }
private fun buildRequest(chatRequest: ModelSchema.ChatRequest, model: ChatModel): OpenAIChatRequest {
   val chatMessages = chatRequest.messages
   require(chatMessages.isNotEmpty()) { "Messages cannot be empty" }
   require(model.modelId.isNotBlank()) { "Model name cannot be blank" }
   val messages = toOpenAIMessages(chatMessages)
   require(messages.isNotEmpty()) { "Mapped messages cannot be empty" }
   val tools = chatRequest.tools?.takeIf { it.isNotEmpty() }?.map { tool ->
     OpenAITool(
       type = "function",
       function = OpenAIFunctionDef(
         name = tool.name,
         description = tool.description.ifBlank { null },
         parameters = tool.inputSchema,
       ),
     )
   }
   val toolChoice = if (tools != null) openAIToolChoice(chatRequest.tool_choice) else null
   return OpenAIChatRequest(
     model = chatRequest.model ?: model.modelId,
     messages = messages,
     max_tokens = chatRequest.max_tokens,
     temperature = if (model.supportsTemperature) chatRequest.temperature else null,
     tools = tools,
     tool_choice = toolChoice,
   )
}
private fun toOpenAIMessages(chatMessages: List<ModelSchema.ChatMessage>): List<OpenAIMessage> {
   val out = mutableListOf<OpenAIMessage>()
   for (msg in chatMessages) {
     val role = msg.role.toString().lowercase()
     val parts = msg.content.orEmpty()
     when (role) {
       "system", "developer" -> {
         val text = parts.mapNotNull { it.text }.joinToString("\n\n")
         if (text.isNotBlank()) {
           out += OpenAIMessage(role = role, content = text)
         }
       }
       "assistant" -> {
         val toolCalls = parts.mapNotNull { part ->
           part.tool_use?.let { toolUse ->
             val args = if (toolUse.input.isNull) {
               "{}"
             } else {
               JsonUtil.objectMapper().writeValueAsString(toolUse.input)
             }
             OpenAIToolCall(
               id = toolUse.id,
               type = "function",
               function = OpenAIFunctionCall(name = toolUse.name, arguments = args),
             )
           }
         }
         val text = parts.mapNotNull { it.text }.filter { it.isNotBlank() }.joinToString("\n")
         if (toolCalls.isNotEmpty() || text.isNotBlank()) {
           out += OpenAIMessage(
             role = "assistant",
             content = text.ifBlank { null },
             tool_calls = toolCalls.takeIf { it.isNotEmpty() },
           )
         }
       }
       else -> {
         // Emit any tool_result blocks first as individual OpenAI role="tool" messages.
         for (part in parts) {
           part.tool_result?.let { result ->
             out += OpenAIMessage(
               role = "tool",
               tool_call_id = result.tool_use_id,
               content = toolResultText(result),
             )
           }
         }
         val nonToolParts = parts.filter { it.tool_result == null && it.tool_use == null }
         val hasImages = nonToolParts.any { !it.image_url.isNullOrBlank() }
         if (hasImages) {
           val contentParts = nonToolParts.mapNotNull { part ->
             when {
               !part.image_url.isNullOrBlank() ->
                 OpenAIContentPart(type = "image_url", image_url = OpenAIImageUrl(url = part.image_url!!))
               !part.text.isNullOrBlank() ->
                 OpenAIContentPart(type = "text", text = part.text)
               else -> null
             }
           }
           if (contentParts.isNotEmpty()) {
             out += OpenAIMessage(role = if (role == "tool") "user" else role, content = contentParts)
           }
         } else {
           val text = nonToolParts.mapNotNull { it.text }.filter { it.isNotBlank() }.joinToString("\n")
           if (text.isNotBlank()) {
             out += OpenAIMessage(role = if (role == "tool") "user" else role, content = text)
           }
         }
       }
     }
   }
   return out
}
private fun toolResultText(result: ModelSchema.ToolResultBlock): String {
   val text = result.content.joinToString("\n") { (it as? MCPContent.Text)?.text ?: "[${it.javaClass.simpleName}]" }
   val body = text.ifBlank { result.structured?.toString() ?: "(no output)" }
   return if (result.is_error && !body.startsWith("Error", ignoreCase = true)) {
     "Error: $body"
   } else {
     body
   }
}
private fun toChatResponse(response: OpenAIChatResponse): ModelSchema.ChatResponse {
   val choices = response.choices.orEmpty().map { choice ->
     val msg = choice.message
     val blocks = mutableListOf<ModelSchema.ContentPart>()
     val textContent = msg?.content
     if (!textContent.isNullOrEmpty()) {
       blocks += ModelSchema.ContentPart(text = textContent)
     }
     msg?.tool_calls.orEmpty().forEach { toolCall ->
       val fn = toolCall.function
       blocks += ModelSchema.ContentPart(
         tool_use = ModelSchema.ToolUseBlock(
           id = toolCall.id ?: "call_missing_${System.nanoTime()}",
           name = fn?.name.orEmpty(),
           input = parseToolArguments(fn?.arguments),
         )
       )
     }
     val finishReason = if (!msg?.tool_calls.isNullOrEmpty() && choice.finish_reason == null) {
       "tool_calls"
     } else {
       choice.finish_reason
     }
     ModelSchema.ChatChoice(
       message = ModelSchema.ChatMessageResponse(
         content = textContent ?: blocks.mapNotNull { it.text }.joinToString("\n"),
         blocks = blocks,
       ),
       index = choice.index,
       finish_reason = finishReason,
     )
   }
   return ModelSchema.ChatResponse(
     id = response.id,
     choices = choices,
     usage = response.usage?.let { u ->
       ModelSchema.Usage(
         prompt_tokens = u.prompt_tokens ?: 0L,
         completion_tokens = u.completion_tokens ?: 0L,
       )
     },
   )
}
private fun parseToolArguments(rawArgs: String?): JsonNode {
   if (rawArgs.isNullOrBlank()) {
     return JsonUtil.objectMapper().createObjectNode()
   }
   return try {
     val node = JsonUtil.objectMapper().readTree(rawArgs)
     if (node == null || node.isNull) JsonUtil.objectMapper().createObjectNode() else node
   } catch (e: Exception) {
     log.warn("Failed to parse OpenAI tool_call arguments as JSON: {}", rawArgs, e)
     JsonUtil.objectMapper().createObjectNode()
   }
}
private fun describeMessage(msg: OpenAIMessage): String {
   val parts = mutableListOf<String>()
   when (val c = msg.content) {
     is String -> if (c.isNotEmpty()) parts += c
     is List<*> -> c.filterIsInstance<OpenAIContentPart>().forEach { part ->
       when (part.type) {
         "text" -> part.text?.let { parts += it }
         "image_url" -> parts += "[Image]"
       }
     }
   }
   msg.tool_calls?.forEach { tc ->
     parts += "[tool_call ${tc.function?.name}: ${tc.function?.arguments}]"
   }
   msg.tool_call_id?.let { id ->
     if (parts.isNotEmpty()) return "[tool_result $id: ${parts.joinToString("\n")}]"
   }
   return parts.joinToString("\n")
}


  private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
   require(model.modelId.isNotBlank()) { "Model name cannot be blank" }
    require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
  }
@Suppress("DEPRECATION")

  override fun getModels(): List<ChatModel> {
    modelsCache[apiBase]?.let { return it }
    return try {
      val modelsResponse = fetchModels()
      val models = modelsResponse.mapNotNull { modelInfo ->
       val knownModels = OpenAIModels.values.values
          .filter { it.modelId == modelInfo.id }
        if (knownModels.isNotEmpty()) {
          knownModels.first()
       } else if (
         modelInfo.id.startsWith("gpt") ||
         modelInfo.id.startsWith("o1") ||
         modelInfo.id.startsWith("o3") ||
         modelInfo.id.startsWith("o4")
       ) {
          ChatModel(
            name = modelInfo.id,
            modelId = modelInfo.id,
            maxTotalTokens = 128000,
            provider = CoreProviders.OpenAI,
            outputTokenPricePerK = 0.0,
            inputModalities = setOf(ChatMessageModality.TEXT),
           outputModalities = setOf(ChatMessageModality.TEXT),
           supportsInteractiveTools = true,
          )
        } else {
          null
        }
     }.distinctBy { it.modelId }
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.error("Failed to fetch OpenAI models", e)
      emptyList()
    }
  }

  private fun fetchModels(): List<OpenAIModelInfo> {
    val response = get("${apiBase}/models")
    checkError(response)
    val listResponse = JsonUtil.objectMapper().readValue(response, OpenAIListModelsResponse::class.java)
    return listResponse.data
  }

  companion object {
    private val log = getLogger(OpenAIChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()
   private const val TOOL_PROVIDERS_WITHOUT_TOOLS =
     "ChatRequest.toolProviders is set but tools is empty: tool requests must go through ChatInterface " +
         "(McpToolLoop); provider clients do not execute tools"
   private fun openAIToolChoice(choice: String?): Any? =
     when (val c = choice?.trim()?.takeIf { it.isNotEmpty() }) {
       null -> null
       "auto" -> "auto"
       "none" -> "none"
       "any", "required" -> "required"
       else -> OpenAIToolChoice(type = "function", function = OpenAIToolChoiceFunction(name = c))
     }
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIChatRequest(
     val model: String,
     val messages: List<OpenAIMessage>,
     val max_tokens: Int? = null,
     val temperature: Double? = null,
     val tools: List<OpenAITool>? = null,
     val tool_choice: Any? = null,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIMessage(
     val role: String,
     val content: Any? = null,
     val tool_calls: List<OpenAIToolCall>? = null,
     val tool_call_id: String? = null,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIContentPart(
     val type: String,
     val text: String? = null,
     val image_url: OpenAIImageUrl? = null,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIImageUrl(
     val url: String,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAITool(
     val type: String = "function",
     val function: OpenAIFunctionDef,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIFunctionDef(
     val name: String,
     val description: String? = null,
     val parameters: JsonNode,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIToolChoice(
     val type: String = "function",
     val function: OpenAIToolChoiceFunction,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   data class OpenAIToolChoiceFunction(
     val name: String,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIToolCall(
     val id: String? = null,
     val type: String? = "function",
     val function: OpenAIFunctionCall? = null,
   )
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIFunctionCall(
     val name: String? = null,
     val arguments: String? = null,
   )
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIChatResponse(
     val id: String? = null,
     val choices: List<OpenAIChoice>? = null,
     val usage: OpenAIUsage? = null,
   )
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIChoice(
     val index: Int = 0,
     val message: OpenAIResponseMessage? = null,
     val finish_reason: String? = null,
   )
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIResponseMessage(
     val role: String? = null,
     val content: String? = null,
     val tool_calls: List<OpenAIToolCall>? = null,
     val refusal: String? = null,
   )
   @JsonIgnoreProperties(ignoreUnknown = true)
   data class OpenAIUsage(
     val prompt_tokens: Long? = null,
     val completion_tokens: Long? = null,
     val total_tokens: Long? = null,
   )
   @JsonIgnoreProperties(ignoreUnknown = true)

    data class OpenAIModelInfo(
      val id: String,
     val `object`: String = "model",
     val created: Long = 0,
     val owned_by: String = ""
    )
   @JsonIgnoreProperties(ignoreUnknown = true)

    data class OpenAIListModelsResponse(
     val `object`: String = "list",
     val data: List<OpenAIModelInfo> = emptyList()
    )
  }


}