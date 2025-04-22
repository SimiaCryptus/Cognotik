package com.simiacryptus.jopenai.proxy

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.google.gson.reflect.TypeToken
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.DescriptorUtil.resolveMethodReturnType
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.util.JsonUtil.fromJson
import com.simiacryptus.util.JsonUtil.toJson
import org.slf4j.Logger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.reflect.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaType


abstract class GPTProxyBase<T : Any>(
    val clazz: Class<out T>,
    var temperature: Double = 0.1,
    private var validation: Boolean = true,
    private var maxRetries: Int = 5,
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


    abstract fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String

    fun create() = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args ->
        if (method.name == "toString") return@newProxyInstance clazz.simpleName
        log.debug("Invoking method: ${method.name} with arguments: ${args?.joinToString()}")
        requestCounters.computeIfAbsent(method.name) { AtomicInteger(0) }.incrementAndGet()
        val type: Type = if (clazz.isKotlinClass()) {
            val returnType = resolveMethodReturnType(clazz.kotlin, method.name)
            returnType.javaType
        } else {
            method.genericReturnType
        }
        val argList = if (clazz.isKotlinClass()) {
            val declaredMethod = clazz.kotlin.functions.find { it.name == method.name }
            if (null != declaredMethod) {
                (args ?: arrayOf()).zip(declaredMethod.parameters.filter { it.kind == KParameter.Kind.VALUE })
                    .filter<Pair<Any?, KParameter>> { (arg: Any?, _) -> arg != null }
                    .withIndex()
                    .associate { (idx, p) ->
                        val (arg, param) = p
                        (param.name ?: "arg$idx") to toJson(arg!!)
                    }
            } else {
                (args ?: arrayOf()).zip(method.parameters)
                    .filter<Pair<Any?, Parameter>> { (arg: Any?, _) -> arg != null }
                    .associate { (arg, param) -> param.name to toJson(arg!!) }
            }
        } else {
            (args ?: arrayOf()).zip(method.parameters)
                .filter<Pair<Any?, Parameter>> { (arg: Any?, _) -> arg != null }
                .associate { (arg, param) -> param.name to toJson(arg!!) }
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
                    // Increase temperature on retry; this encourages the model to return a different result
                    temperature =
                        if (temperature <= 0.0) 0.0 else temperature.coerceAtLeast(0.1).pow(1.0 / (retry + 1))
                }
                val jsonResult0 = complete(prompt, *examples[method.name]?.toTypedArray() ?: arrayOf())
                val jsonResult = fixup(jsonResult0, type)
                try {
                    val obj = fromJson<Any>(jsonResult, type)
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

    open val describer: TypeDescriber = object : AbbrevWhitelistYamlDescriber(
        "com.simiacryptus", "com.simiacryptus"
    ) {
        override val includeMethods: Boolean get() = false
    }

    val examples = HashMap<String, MutableList<RequestResponse>>()
    private fun loadExamples(file: File = File("api.examples.json")): List<ProxyRecord> {
        if (!file.exists()) return listOf()
        val json = file.readText()
        return fromJson(json, object : ArrayList<ProxyRecord>() {}.javaClass)
    }

    fun addExamples(file: File) {
        examples.putAll(loadExamples(file).groupBy { it.methodName }
            .mapValues { it.value.map { RequestResponse(it.argList, it.response) }.toMutableList() })
    }

    @Suppress("unused")
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
                        param.name to toJson(arg!!)
                    }
                val result = toJson(returnValue)
                examples.getOrPut(method.name) { ArrayList() }.add(RequestResponse(argList, result))
                return@newProxyInstance returnValue
            } as T)
    }


    private fun openApiLog(file: String): BufferedWriter {
        val writer = BufferedWriter(FileWriter(File(file)))
        writer.write("[")
        writer.newLine()
        writer.flush()
        return writer
    }

    data class ProxyRequest(
        val methodName: String = "",
        val apiYaml: String = "",
        val argList: Map<String, String> = mapOf(),
    )

    data class ProxyRecord(
        val methodName: String = "",
        val argList: Map<String, String> = mapOf(),
        val response: String = "",
    )

    data class RequestResponse(
        val argList: Map<String, String> = mapOf(),
        val response: String,
    )

    companion object {
        private val log: Logger = org.slf4j.LoggerFactory.getLogger(GPTProxyBase::class.java)


        fun fixup(jsonResult: String, type: Type): String {
            var jsonResult1 = jsonResult
            if (type is ParameterizedType && List::class.java.isAssignableFrom(type.rawType as Class<*>) && !jsonResult1.startsWith(
                    "["
                )
            ) {
                val obj = fromJson<Map<String, Any>>(jsonResult1, object : TypeToken<Map<String, Any>>() {}.type)
                if (obj.size == 1) {
                    val key = obj.keys.firstOrNull()
                    if (key is String && obj[key] is List<*>) {
                        jsonResult1 = obj[key]?.let { toJson(it) } ?: "[]"
                    }
                }
            }
            return jsonResult1
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
    }

}