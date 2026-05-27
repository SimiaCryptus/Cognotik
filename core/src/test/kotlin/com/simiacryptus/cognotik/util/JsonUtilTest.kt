package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.LocalDate
import java.time.LocalDateTime

class JsonUtilTest {

    // Sample data classes for testing
    data class SimplePerson(
        val name: String,
        val age: Int
    )

    data class PersonWithOptional(
        val name: String,
        val age: Int? = null,
        val email: String? = null
    )

    data class NestedObject(
        val id: String,
        val person: SimplePerson,
        val tags: List<String> = emptyList()
    )

    data class DateContainer(
        val name: String,
        val createdAt: LocalDateTime,
        val date: LocalDate
    )

    data class ValidatedPerson(
        val name: String,
        val age: Int
    ) : ValidatedObject {
        override fun validate(): String? {
            return when {
                name.isBlank() -> "Name cannot be blank"
                age < 0 -> "Age cannot be negative"
                else -> null
            }
        }
    }

    @Nested
    @DisplayName("toJson Tests")
    inner class ToJsonTests {

        @Test
        fun `should serialize null to null string`() {
            val result = JsonUtil.toJson(null)
            assertEquals("null", result)
        }

        @Test
        fun `should return string as-is`() {
            val input = "hello world"
            val result = JsonUtil.toJson(input)
            assertEquals(input, result)
        }

        @Test
        fun `should serialize simple object to JSON`() {
            val person = SimplePerson("Alice", 30)
            val result = JsonUtil.toJson(person)
            assertTrue(result.contains("\"name\""))
            assertTrue(result.contains("\"Alice\""))
            assertTrue(result.contains("\"age\""))
            assertTrue(result.contains("30"))
        }

        @Test
        fun `should serialize with pretty printing`() {
            val person = SimplePerson("Bob", 25)
            val result = JsonUtil.toJson(person)
            // Pretty printer adds newlines
            assertTrue(result.contains("\n"))
        }

        @Test
        fun `should exclude null fields from serialization`() {
            val person = PersonWithOptional("Charlie", null, null)
            val result = JsonUtil.toJson(person)
            assertTrue(result.contains("\"name\""))
            assertFalse(result.contains("\"age\""))
            assertFalse(result.contains("\"email\""))
        }

        @Test
        fun `should serialize nested objects`() {
            val nested = NestedObject(
                id = "123",
                person = SimplePerson("Dave", 40),
                tags = listOf("admin", "user")
            )
            val result = JsonUtil.toJson(nested)
            assertTrue(result.contains("\"id\""))
            assertTrue(result.contains("\"person\""))
            assertTrue(result.contains("\"Dave\""))
            assertTrue(result.contains("\"tags\""))
        }

        @Test
        fun `should serialize collections`() {
            val list = listOf(
                SimplePerson("A", 1),
                SimplePerson("B", 2)
            )
            val result = JsonUtil.toJson(list)
            assertTrue(result.startsWith("["))
            assertTrue(result.endsWith("]") || result.trim().endsWith("]"))
        }

        @Test
        fun `should serialize maps`() {
            val map = mapOf("key1" to "value1", "key2" to 42)
            val result = JsonUtil.toJson(map)
            assertTrue(result.contains("\"key1\""))
            assertTrue(result.contains("\"value1\""))
            assertTrue(result.contains("\"key2\""))
            assertTrue(result.contains("42"))
        }

        @Test
        fun `should serialize date types`() {
            val dateObj = DateContainer(
                name = "test",
                createdAt = LocalDateTime.of(2023, 1, 15, 10, 30),
                date = LocalDate.of(2023, 1, 15)
            )
            val result = JsonUtil.toJson(dateObj)
            assertTrue(result.contains("\"name\""))
            assertTrue(result.contains("2023"))
        }
    }

    @Nested
    @DisplayName("fromJson Tests")
    inner class FromJsonTests {

        @Test
        fun `should return string as-is when target type is String`() {
            val input = "raw string data"
            val result: String = JsonUtil.fromJson(input, String::class.java)
            assertEquals(input, result)
        }

        @Test
        fun `should deserialize simple JSON to object`() {
            val json = """{"name":"Alice","age":30}"""
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Alice", result.name)
            assertEquals(30, result.age)
        }

        @Test
        fun `should deserialize JSON with comments`() {
            val json = """
                {
                    // This is a comment
                    "name": "Bob",
                    "age": 25
                }
            """.trimIndent()
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Bob", result.name)
            assertEquals(25, result.age)
        }

        @Test
        fun `should deserialize JSON with single quotes`() {
            val json = "{'name':'Charlie','age':35}"
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Charlie", result.name)
            assertEquals(35, result.age)
        }

        @Test
        fun `should deserialize JSON with unquoted field names`() {
            val json = "{name:\"Dave\",age:40}"
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Dave", result.name)
            assertEquals(40, result.age)
        }

        @Test
        fun `should deserialize JSON with trailing comma`() {
            val json = """{"name":"Eve","age":28,}"""
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Eve", result.name)
            assertEquals(28, result.age)
        }

        @Test
        fun `should ignore unknown properties`() {
            val json = """{"name":"Frank","age":50,"unknownField":"value"}"""
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Frank", result.name)
            assertEquals(50, result.age)
        }

        @Test
        fun `should deserialize float as int`() {
            val json = """{"name":"Grace","age":30.0}"""
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals(30, result.age)
        }

        @Test
        fun `should deserialize nested objects`() {
            val json = """
                {
                    "id": "abc123",
                    "person": {"name":"Henry","age":45},
                    "tags": ["admin","developer"]
                }
            """.trimIndent()
            val result: NestedObject = JsonUtil.fromJson(json, NestedObject::class.java)
            assertEquals("abc123", result.id)
            assertEquals("Henry", result.person.name)
            assertEquals(45, result.person.age)
            assertEquals(listOf("admin", "developer"), result.tags)
        }

        @Test
        fun `should deserialize single value as array`() {
            val json = """
                {
                    "id": "x",
                    "person": {"name":"Ivy","age":20},
                    "tags": "single-tag"
                }
            """.trimIndent()
            val result: NestedObject = JsonUtil.fromJson(json, NestedObject::class.java)
            assertEquals(listOf("single-tag"), result.tags)
        }

        @Test
        fun `should throw RuntimeException for invalid JSON`() {
            val invalidJson = "{not valid json at all}}"
            val exception = assertThrows(RuntimeException::class.java) {
                JsonUtil.fromJson<SimplePerson>(invalidJson, SimplePerson::class.java)
            }
            assertTrue(exception.message?.contains("Failed to parse JSON") == true)
        }

        @Test
        fun `should validate ValidatedObject after deserialization`() {
            val validJson = """{"name":"Jack","age":25}"""
            val result: ValidatedPerson = JsonUtil.fromJson(validJson, ValidatedPerson::class.java)
            assertEquals("Jack", result.name)
            assertEquals(25, result.age)
            // Validation passes silently for valid objects
        }

        @Test
        fun `should deserialize invalid ValidatedObject without throwing`() {
            // Validation failures only log warnings, don't throw
            val invalidJson = """{"name":"","age":-5}"""
            val result: ValidatedPerson = JsonUtil.fromJson(invalidJson, ValidatedPerson::class.java)
            assertEquals("", result.name)
            assertEquals(-5, result.age)
        }

        @Test
        fun `should deserialize using TypeReference for generic types`() {
            val json = """[{"name":"A","age":1},{"name":"B","age":2}]"""
            val type = object : TypeReference<List<SimplePerson>>() {}.type
            val result: List<SimplePerson> = JsonUtil.fromJson(json, type)
            assertEquals(2, result.size)
            assertEquals("A", result[0].name)
            assertEquals("B", result[1].name)
        }
    }

    @Nested
    @DisplayName("Extension Function Tests")
    inner class ExtensionFunctionTests {

        @Test
        fun `toJson extension should serialize object`() {
            val person = SimplePerson("Kate", 33)
            val result = person.toJson()
            assertTrue(result.contains("Kate"))
            assertTrue(result.contains("33"))
        }

        @Test
        fun `jsonCopy should create a deep copy of object`() {
            val original = NestedObject(
                id = "original",
                person = SimplePerson("Liam", 22),
                tags = listOf("tag1", "tag2")
            )
            val copy = original.jsonCopy()
            assertEquals(original, copy)
            assertNotSame(original, copy)
            assertNotSame(original.person, copy.person)
        }

        @Test
        fun `copy with lambda should modify a copy`() {
            val original = SimplePerson("Mia", 28)
            // Note: copy with lambda modifies a deserialized copy, but data classes have immutable vals
            // This works only if the lambda can actually modify the object
            val copy = original.copy { /* no modification */ }
            assertEquals(original, copy)
            assertNotSame(original, copy)
        }

        @Test
        fun `jsonCast inline should cast between types`() {
            val map = mapOf("name" to "Noah", "age" to 27)
            val person: SimplePerson = map.jsonCast()
            assertEquals("Noah", person.name)
            assertEquals(27, person.age)
        }

        @Test
        fun `jsonCast with Type should cast between types`() {
            val map = mapOf("name" to "Olivia", "age" to 31)
            val person: SimplePerson = map.jsonCast(SimplePerson::class.java)
            assertEquals("Olivia", person.name)
            assertEquals(31, person.age)
        }

        @Test
        fun `jsonCast with Type should return as-is for String target`() {
            val input = "some string"
            val result: String = input.jsonCast(String::class.java)
            assertEquals(input, result)
        }
    }

    @Nested
    @DisplayName("Merge Tests")
    inner class MergeTests {

        @Test
        fun `merge should combine two objects`() {
            val base = PersonWithOptional("Paul", 30, "paul@example.com")
            val update = PersonWithOptional("Paul Updated", 31, "newemail@example.com")
            val result = with(JsonUtil) { merge(base, update) }
            // Based on the merge logic, non-null fields get overwritten
            assertNotNull(result)
        }

        @Test
        fun `merge should handle null values in vararg`() {
            val base = SimplePerson("Quinn", 40)
            val result = with(JsonUtil) { merge(base, null) }
            assertEquals(base.name, result.name)
            assertEquals(base.age, result.age)
        }

        @Test
        fun `merge should handle empty vararg`() {
            val base = SimplePerson("Rachel", 35)
            val result = with(JsonUtil) { merge(base) }
            assertEquals(base.name, result.name)
            assertEquals(base.age, result.age)
        }
    }

    @Nested
    @DisplayName("Round-trip Tests")
    inner class RoundTripTests {

        @Test
        fun `should round-trip simple object`() {
            val original = SimplePerson("Sam", 29)
            val json = JsonUtil.toJson(original)
            val restored: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals(original, restored)
        }

        @Test
        fun `should round-trip nested object`() {
            val original = NestedObject(
                id = "nested-id",
                person = SimplePerson("Tom", 38),
                tags = listOf("a", "b", "c")
            )
            val json = JsonUtil.toJson(original)
            val restored: NestedObject = JsonUtil.fromJson(json, NestedObject::class.java)
            assertEquals(original, restored)
        }

        @Test
        fun `should round-trip with date types`() {
            val original = DateContainer(
                name = "event",
                createdAt = LocalDateTime.of(2024, 6, 15, 12, 30, 45),
                date = LocalDate.of(2024, 6, 15)
            )
            val json = JsonUtil.toJson(original)
            val restored: DateContainer = JsonUtil.fromJson(json, DateContainer::class.java)
            assertEquals(original.name, restored.name)
            assertEquals(original.createdAt, restored.createdAt)
            assertEquals(original.date, restored.date)
        }

        @Test
        fun `should round-trip collections`() {
            val original = listOf(
                SimplePerson("X", 1),
                SimplePerson("Y", 2),
                SimplePerson("Z", 3)
            )
            val json = JsonUtil.toJson(original)
            val type = object : TypeReference<List<SimplePerson>>() {}.type
            val restored: List<SimplePerson> = JsonUtil.fromJson(json, type)
            assertEquals(original, restored)
        }

        @Test
        fun `should round-trip maps`() {
            val original = mapOf(
                "person1" to SimplePerson("Uma", 24),
                "person2" to SimplePerson("Victor", 45)
            )
            val json = JsonUtil.toJson(original)
            val type = object : TypeReference<Map<String, SimplePerson>>() {}.type
            val restored: Map<String, SimplePerson> = JsonUtil.fromJson(json, type)
            assertEquals(original, restored)
        }
    }

    @Nested
    @DisplayName("ObjectMapper Configuration Tests")
    inner class ObjectMapperTests {

        @Test
        fun `objectMapper should not be null`() {
            val mapper = JsonUtil.objectMapper()
            assertNotNull(mapper)
        }

        @Test
        fun `objectMapper should create new instance each call`() {
            val mapper1 = JsonUtil.objectMapper()
            val mapper2 = JsonUtil.objectMapper()
            assertNotSame(mapper1, mapper2)
        }

        @Test
        fun `should handle non-numeric numbers`() {
            val json = """{"value": NaN}"""
            val mapper = JsonUtil.objectMapper()
            val node = mapper.readTree(json)
            assertNotNull(node)
        }

        @Test
        fun `should handle YAML-style comments`() {
            val json = """
                {
                    # YAML comment
                    "name": "Walter",
                    "age": 55
                }
            """.trimIndent()
            val result: SimplePerson = JsonUtil.fromJson(json, SimplePerson::class.java)
            assertEquals("Walter", result.name)
            assertEquals(55, result.age)
        }
    }
}