package com.simiacryptus.cognotik.fileserver.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.simiacryptus.cognotik.util.JsonUtil

/**
 * JSON reader/writer for the file server, backed by the shared
 * [JsonUtil] Jackson mapper (replaces the old hand-rolled `MiniJson`).
 *
 * Differences from the plain [JsonUtil] mapper:
 *  - `NON_NULL` inclusion is disabled: the FS API wire protocol is explicit
 *    about nulls (`"path": null`, `"signal": null`).
 *  - output is compact by default ([pretty] is available when needed).
 *  - [Raw] allows already-serialized JSON to be spliced into a document.
 */
object FsJson {

  /** Wrapper that splices already-serialized JSON into an output document. */
  class Raw(val json: String)

  private class RawSerializer : JsonSerializer<Raw>() {
    override fun serialize(value: Raw, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeRawValue(value.json)
    }
  }

  val mapper: ObjectMapper = JsonUtil.objectMapper()
    .setSerializationInclusion(JsonInclude.Include.ALWAYS)
    .registerModule(SimpleModule().addSerializer(Raw::class.java, RawSerializer()))

  fun stringify(value: Any?): String = mapper.writeValueAsString(value)

  fun pretty(value: Any?): String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)

  fun parse(text: String): Any? = try {
    mapper.readValue(text, Any::class.java)
  } catch (e: Exception) {
    throw IllegalArgumentException("Malformed JSON: ${e.message}", e)
  }

  @Suppress("UNCHECKED_CAST")
  fun parseObject(text: String?): Map<String, Any?> {
    if (text.isNullOrBlank()) return emptyMap()
    val parsed: Any? = try {
      JsonUtil.fromJson<Any?>(text, Map::class.java)
    } catch (e: Exception) {
      throw IllegalArgumentException("Malformed JSON body: ${e.message}", e)
    }
    return (parsed as? Map<String, Any?>) ?: throw IllegalArgumentException("Expected a JSON object body")
  }

  fun string(map: Map<String, Any?>, key: String): String? = when (val v = map[key]) {
    null -> null
    is String -> v
    else -> v.toString()
  }

  fun boolean(map: Map<String, Any?>, key: String, default: Boolean = false): Boolean = when (val v = map[key]) {
    null -> default
    is Boolean -> v
    is String -> v.equals("true", ignoreCase = true) || v == "1"
    is Number -> v.toDouble() != 0.0
    else -> default
  }

  fun long(map: Map<String, Any?>, key: String): Long? = when (val v = map[key]) {
    null -> null
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull()
    else -> null
  }

  fun int(map: Map<String, Any?>, key: String): Int? = long(map, key)?.toInt()

  fun list(map: Map<String, Any?>, key: String): List<Any?> = when (val v = map[key]) {
    null -> emptyList()
    is List<*> -> v
    is Array<*> -> v.toList()
    else -> listOf(v)
  }

  /** Quoted + escaped JSON string literal. */
  fun escape(value: String): String = mapper.writeValueAsString(value)
}