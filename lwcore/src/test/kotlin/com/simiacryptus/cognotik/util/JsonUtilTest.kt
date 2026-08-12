package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonUtilTest {

  data class Config(
    val name: String? = null,
    val count: Int? = null,
    val tags: List<String>? = null
  )

  class MutableBean {
    var name: String = ""
    var count: Int = 0
  }

  @Test
  fun `toJson renders null literal`() {
    assertEquals("null", JsonUtil.toJson(null))
  }

  @Test
  fun `toJson passes strings through unchanged`() {
    assertEquals("not really json", JsonUtil.toJson("not really json"))
  }

  @Test
  fun `toJson pretty prints objects`() {
    val json = JsonUtil.toJson(Config("a", 1))
    assertTrue(json.contains("\"name\""), json)
    assertTrue(json.contains("\n"), json)
  }

  @Test
  fun `toJson omits null fields`() {
    val json = JsonUtil.toJson(Config(name = "a"))
    assertFalse(json.contains("count"), json)
  }

  @Test
  fun `round trip preserves values`() {
    val original = Config("a", 3, listOf("x", "y"))
    val restored: Config = JsonUtil.fromJson(JsonUtil.toJson(original), Config::class.java)
    assertEquals(original, restored)
  }

  @Test
  fun `fromJson returns the raw string for string targets`() {
    val value: String = JsonUtil.fromJson("just text", String::class.java)
    assertEquals("just text", value)
  }

  @Test
  fun `fromJson tolerates comments unquoted names and trailing commas`() {
    val lenient = "{ name: 'a', // trailing comment\n count: 2, }"
    val parsed: Config = JsonUtil.fromJson(lenient, Config::class.java)
    assertEquals(Config("a", 2), parsed)
  }

  @Test
  fun `fromJson ignores unknown properties`() {
    val parsed: Config = JsonUtil.fromJson("""{"name":"a","unknown":true}""", Config::class.java)
    assertEquals("a", parsed.name)
  }

  @Test
  fun `fromJson wraps parse failures`() {
    val e = assertThrows(RuntimeException::class.java) {
      JsonUtil.fromJson<Config>("{ this is not json ", Config::class.java)
    }
    assertTrue(e.message!!.contains("Failed to parse JSON"), e.message)
  }

  @Test
  fun `merge overlays later values`() {
    val merged = JsonUtil.merge(Config("a", 1), Config(count = 2))
    assertEquals(Config("a", 2), merged)
  }

  @Test
  fun `merge ignores null overlays`() {
    val merged = JsonUtil.merge(Config("a", 1), null, Config(name = "b"))
    assertEquals(Config("b", 1), merged)
  }

  @Test
  fun `merge of a single value returns an equal copy`() {
    val base = Config("a", 1)
    assertEquals(base, JsonUtil.merge(base))
  }

  @Test
  fun `merge requires a non-null value`() {
    assertThrows(IllegalArgumentException::class.java) {
      JsonUtil.merge<Config>(null, null)
    }
  }

  @Test
  fun `toJson extension delegates`() {
    assertEquals(JsonUtil.toJson(Config("a")), Config("a").toJson())
  }

  @Test
  fun `jsonCopy produces an equal but distinct instance`() {
    val original = Config("a", 1, listOf("t"))
    val copy = original.jsonCopy()
    assertEquals(original, copy)
    assertNotSame(original, copy)
  }

  @Test
  fun `copy applies the mutation block`() {
    val original = MutableBean().apply { name = "a"; count = 1 }
    val copy = original.copy { count = 42 }
    assertEquals("a", copy.name)
    assertEquals(42, copy.count)
    assertEquals(1, original.count)
  }

  @Test
  fun `jsonCast reified converts between shapes`() {
    val map = mapOf("name" to "a", "count" to 7)
    val config = map.jsonCast<Config>()
    assertEquals(Config("a", 7), config)
  }

  @Test
  fun `jsonCast with type token passes strings through`() {
    val value: String = "hello".jsonCast(String::class.java)
    assertEquals("hello", value)
  }

  @Test
  fun `objectMapper instances are independent`() {
    assertNotSame(JsonUtil.objectMapper(), JsonUtil.objectMapper())
  }
}