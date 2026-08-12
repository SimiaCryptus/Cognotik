package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.slf4j.LoggerFactory.getLogger

open class DynamicEnum<T : DynamicEnum<T>>(val name: String) {
  companion object {
    private val log = getLogger(DynamicEnum::class.java)
    private val registries = mutableMapOf<Class<*>, MutableList<Pair<String, DynamicEnum<*>>>>()

    internal fun <T> getRegistry(clazz: Class<T>): MutableList<Pair<String, T>> {

      @Suppress("UNCHECKED_CAST")
      return registries.getOrPut(clazz) { mutableListOf() } as MutableList<Pair<String, T>>
    }

    fun <T> valueOf(clazz: Class<T>, name: String): T {
      val get = getRegistry(clazz).toMap().get(name)
      return if (get != null) get else {
        throw IllegalArgumentException("Unknown enum constant: $name")
      }
    }

    fun <T : DynamicEnum<T>> values(clazz: Class<T>): List<T> {
      return getRegistry(clazz).map { it.second }
    }

    @JvmStatic
    fun <T : DynamicEnum<T>> register(clazz: Class<T>, enumConstant: T) {
      val registry = getRegistry(clazz)
      if (registry.any { it.first == enumConstant.name }) {
        //throw IllegalArgumentException("Enum constant with name '${enumConstant.name}' is already registered for class ${clazz.name}")
        log.info("Enum constant with name '${enumConstant.name}' is already registered for class ${clazz.name}, skipping registration")
      } else {
        registry.add(enumConstant.name to enumConstant)
      }
    }

    /**
     * Unregister a dynamic enum constant by name.
     * Returns true if the constant was found and removed.
     */
    @JvmStatic
    fun <T : DynamicEnum<T>> unregister(clazz: Class<T>, name: String): Boolean {
      return getRegistry(clazz).removeAll { it.first == name }
    }
  }

  override fun toString() = name
  override fun hashCode() = name.hashCode()
  override fun equals(other: Any?): Boolean {
    return this === other || other is DynamicEnum<*> && name == other.name
  }
}

abstract class DynamicEnumSerializer<T : DynamicEnum<T>>(
  private val clazz: Class<T>
) : StdSerializer<T>(clazz) {
  private val log = getLogger(javaClass)
  override fun serialize(value: T, gen: JsonGenerator, provider: SerializerProvider) {
    log.debug("Serializing value: {} for class: {}", value.name, clazz.name)
    DynamicEnum.getRegistry(clazz).find { it.second == value }?.first?.let { gen.writeString(it) }
  }
}

abstract class DynamicEnumDeserializer<T : DynamicEnum<T>>(
  private val clazz: Class<T>
) : JsonDeserializer<T>() {
  private val log = getLogger(javaClass)
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T? {
    val values = DynamicEnum.getRegistry(clazz).toMap()
    return when (val node = p.codec.readTree<JsonNode>(p)) {
      is TextNode -> values[node.asText()]
        ?: run {
          log.error("Unknown enum constant: {}", node.asText())
          null
        }

      is ObjectNode -> values[node.get("name")?.asText()]
        ?: run {
          log.error("Unknown enum constant: {}", node.toPrettyString())
          null
        }

      else -> throw JsonMappingException(p, "Unexpected JSON value type: ${node.nodeType}")
    }
  }
}