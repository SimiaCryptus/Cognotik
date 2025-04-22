package com.simiacryptus.jopenai.util

import com.fasterxml.jackson.databind.type.TypeFactory
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.ListWrapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class)
class ListWrapperTest {

    data class Person(val name: String? = null, val age: Int? = null)

    data class Team(val members: ListWrapper<Person>? = null)

    private val objectMapper = JsonUtil.objectMapper()

    //    @Test
    fun testSerializationOfComplexObject() {
        val team = Team(ListWrapper(listOf(Person("Alice", 30), Person("Bob", 25))))
        val mapper = objectMapper
        val json = mapper.writeValueAsString(team)
        val deserialized = mapper.readValue(json, Team::class.java)
        assertEquals(team, deserialized)
    }

    @Test
    fun testDeepClone() {
        val original = ListWrapper(listOf("a", "b", "c"))
        val clone = original.deepClone()
        assertNotSame(original, clone)
        assertEquals(original, clone)
    }

    @Test
    fun testEqualsAndHashCode() {
        val list1 = ListWrapper(listOf("x", "y", "z"))
        val list2 = ListWrapper(listOf("x", "y", "z"))
        val list3 = ListWrapper(listOf("a", "b", "c"))

        assertEquals(list1, list2)
        assertNotEquals(list1, list3)
        assertEquals(list1.hashCode(), list2.hashCode())
        assertNotEquals(list1.hashCode(), list3.hashCode())
    }

    @Test
    fun testToString() {
        val listWrapper = ListWrapper(listOf("hello", "world"))
        assertEquals("[hello, world]", listWrapper.toString())
    }

    @Test
    fun testSerialization() {
        val listWrapper = ListWrapper<Int>(listOf(1, 2, 3))
        val mapper = objectMapper
        val json = mapper.writeValueAsString(listWrapper)
        println(
            """
            Serialized JSON:
            $json
        """.trimIndent()
        )
        val kType = typeOf<ListWrapper<Int>>()
        val javaType = kType.javaType
        val constructType = TypeFactory.defaultInstance().constructType(javaType)
        val deserialized = mapper.readValue<ListWrapper<Int>>(json, constructType)
        assertEquals(listWrapper, deserialized)
    }

    @Test
    fun testSerializationWithDifferentTypes() {
        val listWrapper = ListWrapper(listOf("apple", "banana", "cherry"))
        val mapper = objectMapper
        val json = mapper.writeValueAsString(listWrapper)
        println(
            """
            Serialized JSON:
            $json
        """.trimIndent()
        )
        val kType = typeOf<ListWrapper<String>>()
        val javaType = TypeFactory.defaultInstance().constructCollectionType(ListWrapper::class.java, String::class.java)
        val deserialized = mapper.readValue<ListWrapper<String>>(json, javaType)
        assertEquals(listWrapper, deserialized)
    }

    @Test
    fun testEmptyListSerialization() {
        val emptyListWrapper = ListWrapper(emptyList<Int>())
        val mapper = objectMapper
        val json = mapper.writeValueAsString(emptyListWrapper)
        println(
            """
            Serialized JSON:
            $json
        """.trimIndent()
        )
        val deserialized = mapper.readValue<ListWrapper<Int>>(json, TypeFactory.defaultInstance().constructType(typeOf<ListWrapper<Int>>().javaType))
        assertTrue(deserialized.isEmpty())
        assertEquals("[]", deserialized.toString())
    }

    @Test
    fun testHashCodeConsistency() {
        val listWrapper1 = ListWrapper(listOf("x", "y"))
        val listWrapper2 = ListWrapper(listOf("x", "y"))
        assertEquals(listWrapper1.hashCode(), listWrapper2.hashCode())
    }
}