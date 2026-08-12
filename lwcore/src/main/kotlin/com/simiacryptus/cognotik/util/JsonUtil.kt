package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.lang.reflect.Type

object JsonUtil {

  val _initForReading: ThreadLocal<JavaType?> = ThreadLocal.withInitial { null }
  fun objectMapper(): ObjectMapper {
    return object : ObjectMapper() {
      override fun _initForReading(p: JsonParser?, targetType: JavaType?): JsonToken {

        _initForReading.set(targetType)
        return super._initForReading(p, targetType)
      }
    }

      .disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

      .enable(JsonParser.Feature.ALLOW_COMMENTS)
      .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
      .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
      .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
      .enable(JsonReadFeature.ALLOW_MISSING_VALUES.mappedFeature())
      .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature())


      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .registerModule(
        KotlinModule.Builder()
          .withReflectionCacheSize(512)
          .configure(KotlinFeature.NullToEmptyCollection, false)
          .configure(KotlinFeature.NullToEmptyMap, false)
          .configure(KotlinFeature.NullIsSameAsDefault, false)
          .configure(KotlinFeature.SingletonSupport, false)
          .configure(KotlinFeature.StrictNullChecks, false)
          .build()
      ).registerModule(JavaTimeModule())
      .apply {
        try {
          val module = com.fasterxml.jackson.databind.module.SimpleModule()
          module.addSerializer(
            Class.forName("groovy.lang.GString") as Class<Any>,
            object : JsonSerializer<Any>() {
              override fun serialize(
                value: Any,
                gen: com.fasterxml.jackson.core.JsonGenerator,
                serializers: SerializerProvider
              ) {
                gen.writeString(value.toString())
              }
            }
          )
          registerModule(module)
        } catch (e: Throwable) {
          // Ignore
        }
      }
  }

  @JvmStatic
  fun toJson(data: Any?): String = when (data) {
    null -> "null"
    is String -> data
    else -> objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data)
  }

  @JvmStatic
  fun <T> fromJson(data: String, type: Type): T {
    if (type is Class<*> && type.isAssignableFrom(String::class.java)) return data as T
    val objectMapper = objectMapper()
    try {
      val value = objectMapper.readValue(data, objectMapper.typeFactory.constructType(type)) as T
      if (value is ValidatedObject) {
        val validate = value.validate()
        if (null != validate) {
          log.warn("Validation failed for object of type ${type.typeName} with message: $validate")
        }
      }
      return value
    } catch (e: Exception) {
      throw RuntimeException("Failed to parse JSON: $data", e)
    } finally {
      _initForReading.remove()
    }
  }

  @JvmStatic
  fun <T : Any> merge(vararg values: T?): T {
    val objectMapper = objectMapper()
    val nonNullValues = values.filterNotNull()
    require(nonNullValues.isNotEmpty()) { "At least one non-null value is required for merge" }
    val base = nonNullValues.first()
    val jsonNode = objectMapper.valueToTree<JsonNode>(base)
    nonNullValues.drop(1).forEach { value ->
      val updateNode = objectMapper.valueToTree<JsonNode>(value)
      updateNode.fields().forEach { (fieldName, fieldValue) ->
        if (!fieldValue.isNull) {
          (jsonNode as com.fasterxml.jackson.databind.node.ObjectNode).set<JsonNode>(fieldName, fieldValue)
        }
      }
    }
    @Suppress("UNCHECKED_CAST")
    return objectMapper.treeToValue(jsonNode, base.javaClass) as T
  }

  private val log = LoggerFactory.getLogger(JsonUtil::class.java)
}


fun <T : Any> T.copy(fn: T.() -> Unit): T {
  return JsonUtil.toJson(this).let { JsonUtil.fromJson<T>(it, this.javaClass).apply { fn(this) } }
}

fun Any.toJson(): String {
  return JsonUtil.toJson(this)
}

fun <T : Any> T.jsonCopy(): T {
  return JsonUtil.toJson(this).let { JsonUtil.fromJson<T>(it, this.javaClass) }
}

inline fun <reified T> Any.jsonCast(): T = JsonUtil.fromJson(JsonUtil.toJson(this), T::class.java)

fun <T> Any.jsonCast(type: Type): T = when (type) {
  is Class<*> if type.isAssignableFrom(String::class.java) -> this as T
  else -> JsonUtil.fromJson(JsonUtil.toJson(this), type)
}