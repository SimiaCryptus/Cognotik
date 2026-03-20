package com.simiacryptus.cognotik.agents

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.DescriptorUtil
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.toContentList
import java.lang.reflect.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaType

open class ProxyAgent<T : Any>(
  val clazz: Class<out T>,
  private var model: ChatInterface,
  private var temperature: Double = 0.5,
  val validation: Boolean = true,
  private var maxRetries: Int = 5,
  val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "com.simiacryptus"
  ) {
    override val includeMethods: Boolean get() = false
  }
) {

  init {
    log.info("Created proxy for class: ${clazz.simpleName}")
  }

  open val metrics: Map<String, Any>
    get() = hashMapOf(
      "requests" to requestCounter.get(),
      "attempts" to attemptCounter.get(),
    ) + requestCounters.mapValues { it.value.get() }.mapKeys { "requests.${it.key}" }
  private val requestCounter = AtomicInteger(0)
  private val attemptCounter = AtomicInteger(0)
  private val requestCounters = HashMap<String, AtomicInteger>()

  fun create() = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args ->
    if (method.name == "toString") return@newProxyInstance clazz.simpleName
    log.debug("Invoking method: ${method.name} with arguments: ${args?.joinToString()}")
    requestCounters.computeIfAbsent(method.name) { AtomicInteger(0) }.incrementAndGet()
    val type: Type = if (clazz.isKotlinClass()) {
      val returnType = DescriptorUtil.resolveMethodReturnType(clazz.kotlin, method.name)
      returnType.javaType
    } else {
      method.genericReturnType
    }
    val argList = if (clazz.isKotlinClass()) {
      val declaredMethod = clazz.kotlin.functions.find { it.name == method.name }
      if (null != declaredMethod) {
        (args ?: arrayOf()).zip(declaredMethod.parameters.filter { it.kind == KParameter.Kind.VALUE })
          .filter { (arg: Any?, _) -> arg != null }
          .withIndex()
          .associate { (idx, p) ->
            val (arg, param) = p
            val toJson = JsonUtil.toJson(arg!!)
            (param.name ?: "arg$idx") to toJson
          }
      } else {
        (args ?: arrayOf()).zip(method.parameters)
          .filter { (arg: Any?, _) -> arg != null }
          .associate { (arg, param) -> param.name to JsonUtil.toJson(arg!!) }
      }
    } else {
      (args ?: arrayOf()).zip(method.parameters)
        .filter { (arg: Any?, _) -> arg != null }
        .associate { (arg, param) -> param.name to JsonUtil.toJson(arg!!) }
    }
    val prompt = ProxyRequest(
      method.name,
      describer.describe(method, clazz).trimIndent(),
      argList
    )

    var lastException: Exception? = null
    val originalTemp = temperature
    try {
      requestCounter.incrementAndGet()
      for (retry in 0 until maxRetries) {
        attemptCounter.incrementAndGet()
        log.debug("Attempt $retry for method: ${method.name}")
        if (retry > 0) {

          temperature =
            if (temperature <= 0.0) 0.0 else temperature.coerceAtLeast(0.1).pow(1.0 / (retry + 1))
        }
        val jsonResult0 = complete(prompt, *examples[method.name]?.toTypedArray() ?: arrayOf())
        val jsonResult = fixup(jsonResult0, type)
        try {
          val obj = JsonUtil.fromJson<Any>(jsonResult, type)
          if (validation) {
            if (obj is ValidatedObject) {
              val validate = obj.validate()
              if (null != validate) {
                log.error("Validation failed for method: ${method.name}, reason: $validate")
                lastException = ValidatedObject.ValidationError(validate, obj)
                continue
              }
            }
          }
          log.info("Successfully parsed response for method: ${method.name}")
          return@newProxyInstance obj
        } catch (e: Exception) {
          log.error("Failed to parse response for method: ${method.name}, response: $jsonResult", e)
          lastException = e
          log.debug("Retry $retry of $maxRetries for method: ${method.name}")
        }
      }
      log.error("Exhausted retries for method: ${method.name}, throwing exception")
      throw lastException ?: RuntimeException("Failed to parse response for method: ${method.name}")
    } finally {
      temperature = originalTemp
    }
  } as T

  val examples = HashMap<String, MutableList<RequestResponse>>()

  fun <R : Any> addExample(returnValue: R, functionCall: (T) -> Unit) {
    functionCall(
      Proxy.newProxyInstance(
        clazz.classLoader,
        arrayOf(clazz)
      ) { _: Any, method: Method, args: Array<Any> ->
        if (method.name == "toString") return@newProxyInstance clazz.simpleName
        val argList = args.zip(method.parameters)
          .filter<Pair<Any?, Parameter>> { (arg: Any?, _) -> arg != null }
          .associate { (arg, param) ->
            param.name to JsonUtil.toJson(arg!!)
          }
        val result = JsonUtil.toJson(returnValue)
        examples.getOrPut(method.name) { ArrayList() }.add(RequestResponse(argList, result))
        return@newProxyInstance returnValue
      } as T)
  }

  data class ProxyRequest(
    val methodName: String = "",
    val apiYaml: String = "",
    val argList: Map<String, String> = mapOf(),
  )

  data class RequestResponse(
    val argList: Map<String, String> = mapOf(),
    val response: String,
  )

  fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String {
    log.info("Starting completion with prompt: {}", prompt.toString())
    var request = ModelSchema.ChatRequest()
    val exampleMessages = examples.flatMap {
      listOf(
        ModelSchema.ChatMessage(
          ModelSchema.Role.user,
          argsToString(it.argList).toContentList()
        ),
        ModelSchema.ChatMessage(
          ModelSchema.Role.assistant,
          it.response.toContentList()
        )
      )
    }
    request = request.copy(
      messages = ArrayList(
        listOf(
          ModelSchema.ChatMessage(
            ModelSchema.Role.system, ("""
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
              ModelSchema.ChatMessage(
                ModelSchema.Role.user,
                argsToString(prompt.argList).toContentList()
              )
            )
      )
    )
    request = request.copy(model = model.modelType.modelId)
    request = request.copy(temperature = temperature)
    val json = JsonUtil.toJson(request)
    log.info("Request JSON: {}", json)
    val completion = model.chat(request.messages).choices.first().message?.content.orEmpty()
    log.debug("Received completion: {}", completion)
    val trimPrefix = trimPrefix(completion)
    val trimSuffix = trimSuffix(trimPrefix)
    log.info("Trimmed completion: {}", trimSuffix)
    return trimSuffix
  }

  companion object {
    fun fixup(jsonResult: String, type: Type): String {
      var jsonResult1 = jsonResult
      // Remove JSON-RPC wrapper if present
      jsonResult1 = unwrapJsonRpc(jsonResult1)

      if (type is ParameterizedType && List::class.java.isAssignableFrom(type.rawType as Class<*>) && !jsonResult1.startsWith(
          "["
        )
      ) {
        val obj =
          JsonUtil.fromJson<Map<String, Any>>(jsonResult1, object : TypeToken<Map<String, Any>>() {}.type)
        if (obj.size == 1) {
          val key = obj.keys.firstOrNull()
          if (key is String && obj[key] is List<*>) {
            jsonResult1 = obj[key]?.let { JsonUtil.toJson(it) } ?: "[]"
          }
        }
      }
      return jsonResult1
    }

    private fun unwrapJsonRpc(jsonResult: String): String {
      return try {
        val obj =
          JsonUtil.fromJson<Map<String, Any>>(jsonResult, object : TypeToken<Map<String, Any>>() {}.type)
        // Check if this looks like a JSON-RPC response
        if (obj.containsKey("jsonrpc") && obj.containsKey("result")) {
          log.debug("Detected JSON-RPC wrapper, extracting result field")
          val result = obj["result"]
          JsonUtil.toJson(result)
        } else {
          jsonResult
        }
      } catch (e: Exception) {
        log.debug("Failed to parse as JSON-RPC wrapper, returning original: ${e.message}")
        jsonResult
      }
    }


    @JvmStatic
    fun main(args: Array<String>) {
      println(
        fixup(
          """
                    {
                      "topics": [
                        "Stand-up comedy",
                        "Slapstick humor",
                        "Satire",
                        "Parody",
                        "Impressions",
                        "Observational comedy",
                        "Sketch comedy",
                        "Dark humor",
                        "Physical comedy",
                        "Improvisational comedy"
                      ]
                    }
                """.trimIndent(), object : TypeToken<List<String>>() {}.type
        )
      )

    }

    private val log = LoggerFactory.getLogger(ProxyAgent::class.java)
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