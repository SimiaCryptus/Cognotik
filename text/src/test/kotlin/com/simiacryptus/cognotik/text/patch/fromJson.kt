package com.simiacryptus.cognotik.text.patch

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser.Feature
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.lang.reflect.Type

fun <T> fromJson(data: String, type: Type): T {
  if (type is Class<*> && type.isAssignableFrom(String::class.java)) return data as T
  val objectMapper = ObjectMapper()

    .disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    .enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

    .enable(Feature.ALLOW_COMMENTS)
    .enable(Feature.ALLOW_UNQUOTED_FIELD_NAMES)
    .enable(Feature.ALLOW_SINGLE_QUOTES)
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
        val module = SimpleModule()
        module.addSerializer(
          Class.forName("groovy.lang.GString") as Class<Any>,
          object : JsonSerializer<Any>() {
            override fun serialize(
              value: Any,
              gen: JsonGenerator,
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
  try {
    val value = objectMapper.readValue(data, objectMapper.typeFactory.constructType(type)) as T
    return value
  } catch (e: Exception) {
    throw RuntimeException("Failed to parse JSON: $data", e)
  }
}