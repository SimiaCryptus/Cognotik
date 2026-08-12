package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class TestColor(name: String) : DynamicEnum<TestColor>(name)

class TestColorSerializer : DynamicEnumSerializer<TestColor>(TestColor::class.java)
class TestColorDeserializer : DynamicEnumDeserializer<TestColor>(TestColor::class.java)

class DynamicEnumTest {

  private lateinit var red: TestColor
  private lateinit var green: TestColor

  @BeforeEach
  fun setUp() {
    red = TestColor("RED")
    green = TestColor("GREEN")
    DynamicEnum.register(TestColor::class.java, red)
    DynamicEnum.register(TestColor::class.java, green)
  }

  @AfterEach
  fun tearDown() {
    DynamicEnum.unregister(TestColor::class.java, "RED")
    DynamicEnum.unregister(TestColor::class.java, "GREEN")
    DynamicEnum.unregister(TestColor::class.java, "BLUE")
  }

  @Test
  fun `valueOf resolves registered constants`() {
    assertSame(red, DynamicEnum.valueOf(TestColor::class.java, "RED"))
    assertSame(green, DynamicEnum.valueOf(TestColor::class.java, "GREEN"))
  }

  @Test
  fun `valueOf throws for unknown constants`() {
    val e = assertThrows(IllegalArgumentException::class.java) {
      DynamicEnum.valueOf(TestColor::class.java, "PURPLE")
    }
    assertTrue(e.message!!.contains("PURPLE"), e.message)
  }

  @Test
  fun `values returns registration order`() {
    val values = DynamicEnum.values(TestColor::class.java)
    assertEquals(listOf("RED", "GREEN"), values.map { it.name })
  }

  @Test
  fun `duplicate registration is ignored`() {
    DynamicEnum.register(TestColor::class.java, TestColor("RED"))
    assertEquals(2, DynamicEnum.values(TestColor::class.java).size)
    assertSame(red, DynamicEnum.valueOf(TestColor::class.java, "RED"))
  }

  @Test
  fun `unregister removes a constant`() {
    assertTrue(DynamicEnum.unregister(TestColor::class.java, "RED"))
    assertFalse(DynamicEnum.unregister(TestColor::class.java, "RED"))
    assertThrows(IllegalArgumentException::class.java) {
      DynamicEnum.valueOf(TestColor::class.java, "RED")
    }
  }

  @Test
  fun `equality is name based`() {
    assertEquals(red, TestColor("RED"))
    assertEquals(red.hashCode(), TestColor("RED").hashCode())
    assertNotEquals(red, green)
    assertNotEquals<Any?>(red, null)
  }

  @Test
  fun `toString returns the name`() {
    assertEquals("RED", red.toString())
  }

  private fun mapper(): ObjectMapper = ObjectMapper().registerModule(
    SimpleModule()
      .addSerializer(TestColor::class.java, TestColorSerializer())
      .addDeserializer(TestColor::class.java, TestColorDeserializer())
  )

  @Test
  fun `serializes to its registered name`() {
    assertEquals("\"RED\"", mapper().writeValueAsString(red))
  }

  @Test
  fun `deserializes from a text node`() {
    assertSame(red, mapper().readValue("\"RED\"", TestColor::class.java))
  }

  @Test
  fun `deserializes from an object node`() {
    assertSame(green, mapper().readValue("""{"name":"GREEN"}""", TestColor::class.java))
  }

  @Test
  fun `deserializing an unknown name yields null`() {
    assertNull(mapper().readValue("\"PURPLE\"", TestColor::class.java))
  }
}