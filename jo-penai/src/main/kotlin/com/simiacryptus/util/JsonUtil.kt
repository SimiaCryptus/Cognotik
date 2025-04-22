package com.simiacryptus.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.lang.reflect.Type

object JsonUtil {
  private val log = LoggerFactory.getLogger(JsonUtil::class.java.name)
  
  // Hack to pass the target type to the deserializer
  val _initForReading: ThreadLocal<JavaType?> = ThreadLocal.withInitial { null }
  open fun objectMapper(): ObjectMapper {
    return object : ObjectMapper() {
      override fun _initForReading(p: JsonParser?, targetType: JavaType?): JsonToken {
        //log.info("Initializing for reading with targetType: {}", targetType)
        _initForReading.set(targetType)
        return super._initForReading(p, targetType)
      }
    }
      //.findAndRegisterModules()
      .enable(JsonParser.Feature.ALLOW_COMMENTS)
      .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
      .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
      .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
//            .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
//            .enable(SerializationFeature.WRAP_ROOT_VALUE)
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
      .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
      .enable(JsonReadFeature.ALLOW_MISSING_VALUES.mappedFeature())
      .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS.mappedFeature())
      .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature())
      //.enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS.mappedFeature())
      //.enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS.mappedFeature())
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
  }
  
  open fun toJson(data: Any): String {
//        log.debug("Serializing object to JSON: {}", data)
    return objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data)
  }
  
  open fun <T> fromJson(data: String, type: Type): T {
//        log.debug("Deserializing JSON to object of type: {}", type)
    if (type is Class<*> && type.isAssignableFrom(String::class.java)) return data as T
    val objectMapper = objectMapper()
    val value = objectMapper.readValue(data, objectMapper.typeFactory.constructType(type)) as T
//        log.info("Deserialized JSON to object: {}", value)
    return value
  }
//    companion object : JsonUtil()
}

fun <T : Any> T.copy(fn: T.() -> Unit): T {
  return JsonUtil.toJson(this).let { JsonUtil.fromJson<T>(it, this.javaClass).apply { fn(this) } }
}

fun Any.toJson(): String {
  return JsonUtil.toJson(this)
}
