package com.simiacryptus.cognotik.proxy

import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.ApiModel.ChatMessage
import com.simiacryptus.cognotik.models.ApiModel.ChatRequest
import com.simiacryptus.cognotik.chat.model.Chatter
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.toContentList

open class ChatProxy<T : Any>(
    clazz: Class<out T>,
    val api: ChatClientInterface,
    var model: Chatter,
    temperature: Double = 0.5,
    private val moderated: Boolean = false,
    val deserializerRetries: Int = 2,
    validation: Boolean = true
) : LLMProxyBase<T>(clazz, temperature, validation, deserializerRetries) {

    override fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String {
        log.info("Starting completion with prompt: {}", prompt.toString())
        var request = ChatRequest()
        val exampleMessages = examples.flatMap {
            listOf(
                ChatMessage(
                    ApiModel.Role.user,
                    argsToString(it.argList).toContentList()
                ),
                ChatMessage(
                    ApiModel.Role.assistant,
                    it.response.toContentList()
                )
            )
        }
        request = request.copy(
            messages = ArrayList(
                listOf(
                    ChatMessage(
                        ApiModel.Role.system, ("""
                          You are a JSON-RPC Service
                          Responses are in JSON format
                          Do not include explaining text outside the JSON
                          All input arguments are optional
                          Outputs are based on inputs, with any missing information filled randomly
                          You will respond to the following method
                          """.trimIndent() + prompt.apiYaml
                                ).trim().toContentList()
                    )
                ) + exampleMessages +
                        listOf(
                            ChatMessage(
                                ApiModel.Role.user,
                                argsToString(prompt.argList).toContentList()
                            )
                        )
            )
        )
        request = request.copy(model = model.modelType.modelName)
        request = request.copy(temperature = temperature)
        val json = toJson(request)
        log.debug("Request JSON: {}", json)
        if (moderated) {
            log.info("Moderating request")
            api.moderate(json)
        }

        val completion = model.chat(request.messages).choices.first().message?.content.orEmpty()
        log.info("Received completion: {}", completion)
        val trimPrefix = trimPrefix(completion)
        val trimSuffix = trimSuffix(trimPrefix)
        log.info("Trimmed completion: {}", trimSuffix)
        return trimSuffix
    }

    companion object {

        private val log = LoggerFactory.getLogger(ChatProxy::class.java)
        private fun trimPrefix(completion: String): String {
            val braceIndex = completion.indexOf('{')
            val bracketIndex = completion.indexOf('[')
            val start = when {
                braceIndex == -1 && bracketIndex == -1 -> -1
                braceIndex == -1 -> bracketIndex
                bracketIndex == -1 -> braceIndex
                else -> minOf(braceIndex, bracketIndex)
            }
            return if (start < 0) {
                completion
            } else {
                completion.substring(start)
            }
        }

        private fun trimSuffix(completion: String): String {
            val braceIndex = completion.lastIndexOf('}')
            val bracketIndex = completion.lastIndexOf(']')
            val end = when {
                braceIndex == -1 && bracketIndex == -1 -> -1
                braceIndex == -1 -> bracketIndex
                bracketIndex == -1 -> braceIndex
                else -> maxOf(braceIndex, bracketIndex)
            }
            return if (end < 0) {
                completion
            } else {
                completion.substring(0, end + 1)
            }
        }

        private fun argsToString(argList: Map<String, String>) =
            "{" + argList.entries.joinToString(",\n", transform = { (argName, argValue) ->
                """"$argName": $argValue"""
            }) + "}"
    }
}