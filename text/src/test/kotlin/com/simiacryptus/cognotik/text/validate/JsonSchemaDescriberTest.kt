package com.simiacryptus.cognotik.text.validate

    import com.simiacryptus.cognotik.text.JsonSchemaDescriber
    import kotlin.test.Test
    import kotlin.test.assertTrue

    class JsonSchemaDescriberTest {

        private val describer = JsonSchemaDescriber(JsonSchemaDescriber.Config(topN = 3, maxDepth = 6))

        @Test
        fun `describes polymorphic data with sparsity`() {
            val json = """
                {"id": "a", "count": 1, "tags": ["x", "y"], "child": {"id": "b", "count": 2, "tags": []}}
                {"id": "c", "count": null, "tags": ["x"]}
            """.trimIndent()
            val text = describer.describeDataJson(json)
            assertTrue(text.contains("root: object"), text)
            assertTrue(text.contains("\"id\""), text)
            assertTrue(text.contains("array length"), text)
            assertTrue(text.contains("polymorphic"), text)
            assertTrue(text.contains("sparsity"), text)
        }

        @Test
        fun `collapses recursive shapes`() {
            val json = """{"name":"a","next":{"name":"b","next":{"name":"c","next":null}}}"""
            val text = describer.describeDataJson(json)
            assertTrue(text.contains("recursion"), text)
        }

        @Test
        fun `describes json schema documents`() {
            val schema = """
                {
                  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                  "title": "Node",
                  "type": "object",
                  "required": ["name"],
                  "properties": {
                    "name": {"type": "string", "minLength": 1},
                    "children": {"type": "array", "items": {"${'$'}ref": "#"}}
                  },
                  "additionalProperties": false
                }
            """.trimIndent()
            val text = describer.describe(schema)
            assertTrue(text.contains("JSON Schema Description"), text)
            assertTrue(text.contains("\"name\" (required)"), text)
            assertTrue(text.contains("additionalProperties: not allowed"), text)
        }

        @Test
        fun `treats top level array as samples when asked`() {
            val json = """[{"a":1},{"a":2},{"b":3}]"""
            val text = describer.describeDataJson(json, splitTopLevelArray = true)
            assertTrue(text.contains("samples analyzed  : 3"), text)
        }
    }