package com.simiacryptus.cognotik.text.patch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive unit tests for [DataMergeProcessor].
 *
 * Assertions are made against *parsed* trees wherever possible so the tests are
 * resilient to formatting/pretty-printer differences between Jackson versions.
 */
class DataMergeProcessorTest {

  private val processor = DataMergeProcessor()

  private val jsonMapper = ObjectMapper()
  private val yamlMapper = ObjectMapper(YAMLFactory())
  private val xmlMapper = XmlMapper()
  private val tomlMapper = TomlMapper()
  private val propsMapper = JavaPropsMapper()

  private fun json(text: String): JsonNode = jsonMapper.readTree(text)
  private fun yaml(text: String): JsonNode = yamlMapper.readTree(text)
  private fun xml(text: String): JsonNode = xmlMapper.readTree(text)
  private fun toml(text: String): JsonNode = tomlMapper.readTree(text)
  private fun props(text: String): JsonNode = propsMapper.readTree(text)

  private fun assertJsonEquals(expected: String, actual: String, message: String? = null) {
    assertEquals(json(expected), json(actual), message ?: "JSON trees should be equal")
  }

  // ---------------------------------------------------------------------
  // Metadata / contract
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Processor metadata")
  inner class Metadata {

    @Test
    fun `label is DataMerge`() {
      assertEquals("DataMerge", processor.label)
    }

    @Test
    fun `is a PatchProcessor`() {
      val asInterface: PatchProcessor = processor
      assertEquals("DataMerge", asInterface.label)
      assertNotNull(asInterface.patchFormatPrompt)
    }

    @Test
    fun `prompt is non-blank and documents all supported formats`() {
      val prompt = processor.patchFormatPrompt
      assertTrue(prompt.isNotBlank(), "Prompt must not be blank")
      listOf("json", "yaml", "xml", "toml", "properties").forEach {
        assertTrue(prompt.contains(it), "Prompt should mention format '$it'")
      }
    }

    @Test
    fun `prompt documents merge and removal semantics`() {
      val prompt = processor.patchFormatPrompt.lowercase()
      assertTrue(prompt.contains("merge"), "Prompt should describe merge behavior")
      assertTrue(prompt.contains("null"), "Prompt should describe null-based removal")
      assertTrue(prompt.contains("preserved"), "Prompt should describe preservation of untouched fields")
    }
  }

  // ---------------------------------------------------------------------
  // applyPatch - JSON / deep merge semantics
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("applyPatch - JSON deep merge")
  inner class JsonApply {

    @Test
    fun `adds a new top-level field`() {
      val source = """{"a": 1}"""
      val patch = """{"b": 2}"""
      assertJsonEquals("""{"a":1,"b":2}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `overrides an existing scalar field`() {
      val source = """{"a": 1, "b": 2}"""
      val patch = """{"a": 99}"""
      assertJsonEquals("""{"a":99,"b":2}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `recursively merges nested objects preserving siblings`() {
      val source = """{"database":{"port":5432,"host":"localhost","user":"admin"},"other":true}"""
      val patch = """{"database":{"port":5433}}"""
      assertJsonEquals(
        """{"database":{"port":5433,"host":"localhost","user":"admin"},"other":true}""",
        processor.applyPatch(source, patch)
      )
    }

    @Test
    fun `creates missing intermediate objects`() {
      val source = """{"a": 1}"""
      val patch = """{"b":{"c":{"d":2}}}"""
      assertJsonEquals("""{"a":1,"b":{"c":{"d":2}}}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `null value removes an existing key`() {
      val source = """{"a": 1, "b": 2}"""
      val patch = """{"b": null}"""
      assertJsonEquals("""{"a":1}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `null value removes a nested key only`() {
      val source = """{"db":{"port":1,"host":"h"}}"""
      val patch = """{"db":{"host":null}}"""
      assertJsonEquals("""{"db":{"port":1}}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `null for a missing key is a no-op`() {
      val source = """{"a": 1}"""
      val patch = """{"missing": null}"""
      assertJsonEquals("""{"a":1}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `null removes an entire object subtree`() {
      val source = """{"a":{"b":{"c":1}},"keep":2}"""
      val patch = """{"a": null}"""
      assertJsonEquals("""{"keep":2}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `arrays are replaced entirely - not merged element-wise`() {
      val source = """{"list":[1,2,3]}"""
      val patch = """{"list":[4]}"""
      assertJsonEquals("""{"list":[4]}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `arrays of objects are replaced entirely`() {
      val source = """{"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}]}"""
      val patch = """{"items":[{"id":3}]}"""
      assertJsonEquals("""{"items":[{"id":3}]}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `empty array in patch clears the array`() {
      val source = """{"list":[1,2,3]}"""
      val patch = """{"list":[]}"""
      assertJsonEquals("""{"list":[]}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `object replaces scalar`() {
      val source = """{"a": 5}"""
      val patch = """{"a":{"b":1}}"""
      assertJsonEquals("""{"a":{"b":1}}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `scalar replaces object`() {
      val source = """{"a":{"b":1}}"""
      val patch = """{"a": 5}"""
      assertJsonEquals("""{"a":5}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `empty patch object leaves source unchanged`() {
      val source = """{"a":1,"b":{"c":2}}"""
      assertJsonEquals(source, processor.applyPatch(source, "{}"))
    }

    @Test
    fun `patch applied to empty source object yields patch content`() {
      assertJsonEquals("""{"a":1}""", processor.applyPatch("{}", """{"a":1}"""))
    }

    @Test
    fun `non-object patch replaces the whole document`() {
      val result = processor.applyPatch("""{"a":1}""", """[1,2,3]""")
      assertJsonEquals("""[1,2,3]""", result)
    }

    @Test
    fun `array source merged with array patch is replaced`() {
      val result = processor.applyPatch("""[1,2]""", """[3]""")
      assertJsonEquals("""[3]""", result)
    }

    @Test
    fun `boolean and numeric types are preserved`() {
      val source = """{"flag": false, "count": 1, "ratio": 1.5}"""
      val patch = """{"flag": true, "count": 2, "ratio": 2.5}"""
      val result = json(processor.applyPatch(source, patch))
      assertTrue(result["flag"].isBoolean)
      assertTrue(result["flag"].asBoolean())
      assertEquals(2, result["count"].asInt())
      assertEquals(2.5, result["ratio"].asDouble())
    }

    @Test
    fun `unicode and escaped characters survive the merge`() {
      val source = """{"msg":"hello"}"""
      val patch = """{"msg":"héllo \" wörld \u00e9 \n line"}"""
      val result = json(processor.applyPatch(source, patch))
      assertEquals("héllo \" wörld é \n line", result["msg"].asText())
    }

    @Test
    fun `whitespace around source and patch is tolerated`() {
      val source = "\n\n   {\"a\": 1}   \n"
      val patch = "   \n {\"b\": 2}\n  "
      assertJsonEquals("""{"a":1,"b":2}""", processor.applyPatch(source, patch))
    }

    @Test
    fun `applying the same patch twice is idempotent`() {
      val source = """{"a":1,"b":{"c":2,"d":3}}"""
      val patch = """{"b":{"c":9},"d":null}"""
      val once = processor.applyPatch(source, patch)
      val twice = processor.applyPatch(once, patch)
      assertJsonEquals(once, twice)
    }

    @Test
    fun `source is not mutated by the merge`() {
      val source = """{"a":{"b":1}}"""
      val sourceCopy = source
      processor.applyPatch(source, """{"a":{"b":2}}""")
      assertEquals(sourceCopy, source)
    }

    @Test
    fun `deeply nested structures merge correctly`() {
      val depth = 25
      val open = (1..depth).joinToString("") { """{"l$it":""" }
      val close = "}".repeat(depth)
      val source = open + """{"value":1}""" + close
      val patch = open + """{"value":2}""" + close
      val result = json(processor.applyPatch(source, patch))
      var node: JsonNode = result
      (1..depth).forEach { node = node["l$it"] }
      assertEquals(2, node["value"].asInt())
    }
  }

  // ---------------------------------------------------------------------
  // generatePatch - minimal diff
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("generatePatch - minimal diff")
  inner class JsonGenerate {

    @Test
    fun `identical documents return the new content verbatim`() {
      val old = """{"a": 1}"""
      val new = """{ "a" : 1 }"""
      assertEquals(new, processor.generatePatch(old, new))
    }

    @Test
    fun `only changed fields are emitted`() {
      val old = """{"a":1,"b":2,"c":3}"""
      val new = """{"a":1,"b":99,"c":3}"""
      val patch = json(processor.generatePatch(old, new))
      assertEquals(1, patch.size(), "Diff should contain exactly one field")
      assertEquals(99, patch["b"].asInt())
    }

    @Test
    fun `added fields are emitted`() {
      val old = """{"a":1}"""
      val new = """{"a":1,"b":2}"""
      val patch = json(processor.generatePatch(old, new))
      assertJsonEquals("""{"b":2}""", patch.toString())
    }

    @Test
    fun `removed fields are emitted as nulls`() {
      val old = """{"a":1,"b":2}"""
      val new = """{"a":1}"""
      val patch = json(processor.generatePatch(old, new))
      assertTrue(patch.has("b"), "Removed field must be present in diff")
      assertTrue(patch["b"].isNull, "Removed field must be represented by null")
      assertFalse(patch.has("a"), "Unchanged field must not appear in diff")
    }

    @Test
    fun `nested changes produce nested minimal diffs`() {
      val old = """{"db":{"port":1,"host":"h"},"untouched":{"x":1}}"""
      val new = """{"db":{"port":2,"host":"h"},"untouched":{"x":1}}"""
      val patch = json(processor.generatePatch(old, new))
      assertJsonEquals("""{"db":{"port":2}}""", patch.toString())
    }

    @Test
    fun `nested removals produce nested nulls`() {
      val old = """{"a":{"b":1,"c":2}}"""
      val new = """{"a":{"b":1}}"""
      val patch = json(processor.generatePatch(old, new))
      assertTrue(patch["a"].has("c"))
      assertTrue(patch["a"]["c"].isNull)
    }

    @Test
    fun `changed arrays are emitted in full`() {
      val old = """{"list":[1,2]}"""
      val new = """{"list":[1,2,3]}"""
      val patch = json(processor.generatePatch(old, new))
      assertJsonEquals("""{"list":[1,2,3]}""", patch.toString())
    }

    @Test
    fun `identical arrays are omitted`() {
      val old = """{"list":[1,2],"x":1}"""
      val new = """{"list":[1,2],"x":2}"""
      val patch = json(processor.generatePatch(old, new))
      assertFalse(patch.has("list"))
      assertEquals(2, patch["x"].asInt())
    }

    @Test
    fun `type changes are emitted as the new value`() {
      val old = """{"a":{"b":1}}"""
      val new = """{"a":"scalar"}"""
      val patch = json(processor.generatePatch(old, new))
      assertEquals("scalar", patch["a"].asText())
    }

    @Test
    fun `everything added when old document is empty`() {
      val patch = json(processor.generatePatch("{}", """{"a":1,"b":{"c":2}}"""))
      assertJsonEquals("""{"a":1,"b":{"c":2}}""", patch.toString())
    }

    @Test
    fun `everything nulled when new document is empty`() {
      val patch = json(processor.generatePatch("""{"a":1,"b":2}""", "{}"))
      assertTrue(patch["a"].isNull)
      assertTrue(patch["b"].isNull)
    }
  }

  // ---------------------------------------------------------------------
  // Round-trip invariants
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Round-trip: applyPatch(old, generatePatch(old, new)) == new")
  inner class RoundTrip {

    private fun roundTrip(old: String, new: String) {
      val patch = processor.generatePatch(old, new)
      val merged = processor.applyPatch(old, patch)
      assertEquals(json(new), json(merged), "Round-trip failed for patch: $patch")
    }

    @Test
    fun `simple field change`() = roundTrip("""{"a":1}""", """{"a":2}""")

    @Test
    fun `field addition`() = roundTrip("""{"a":1}""", """{"a":1,"b":2}""")

    @Test
    fun `field removal`() = roundTrip("""{"a":1,"b":2}""", """{"a":1}""")

    @Test
    fun `nested mixed changes`() = roundTrip(
      """{"a":{"b":1,"c":2},"d":[1,2],"e":"x"}""",
      """{"a":{"b":9},"d":[3],"f":true}"""
    )

    @Test
    fun `no changes at all`() = roundTrip("""{"a":1,"b":{"c":2}}""", """{"a":1,"b":{"c":2}}""")

    @Test
    fun `yaml round trip`() {
      val old = "database:\n  port: 5432\n  host: localhost\n"
      val new = "database:\n  port: 5433\n  host: localhost\nfeature: true\n"
      val patch = processor.generatePatchForFormat(old, new, DataMergeProcessor.DataFormat.YAML)
      val merged = processor.applyPatchForFormat(old, patch, DataMergeProcessor.DataFormat.YAML)
      assertEquals(yaml(new), yaml(merged))
    }
  }

  // ---------------------------------------------------------------------
  // YAML
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("YAML support")
  inner class Yaml {

    private val source = "database:\n  port: 5432\n  host: localhost\nlogging:\n  level: INFO\n"

    @Test
    fun `yaml source is auto-detected and merged`() {
      val patch = "database:\n  port: 5433\n"
      val result = yaml(processor.applyPatch(source, patch))
      assertEquals(5433, result["database"]["port"].asInt())
      assertEquals("localhost", result["database"]["host"].asText())
      assertEquals("INFO", result["logging"]["level"].asText())
    }

    @Test
    fun `yaml output omits the document start marker`() {
      val result = processor.applyPatch(source, "database:\n  port: 5433\n")
      assertFalse(result.trimStart().startsWith("---"), "Should not emit '---' doc start marker: $result")
    }

    @Test
    fun `json patch can be applied to a yaml source via fallback parsing`() {
      val result = yaml(processor.applyPatch(source, """{"database":{"port":5433}}"""))
      assertEquals(5433, result["database"]["port"].asInt())
      assertEquals("localhost", result["database"]["host"].asText())
    }

    @Test
    fun `yaml null removes a key`() {
      val result = yaml(processor.applyPatch(source, "logging: null\n"))
      assertFalse(result.has("logging"))
      assertTrue(result.has("database"))
    }

    @Test
    fun `yaml lists are replaced entirely`() {
      val yamlSource = "items:\n  - a\n  - b\n"
      val result = yaml(processor.applyPatch(yamlSource, "items:\n  - c\n"))
      assertEquals(1, result["items"].size())
      assertEquals("c", result["items"][0].asText())
    }

    @Test
    fun `explicit yaml format converts json source to yaml output`() {
      val result = processor.applyPatchForFormat(
        """{"a":1}""", """{"b":2}""", DataMergeProcessor.DataFormat.YAML
      )
      val parsed = yaml(result)
      assertEquals(1, parsed["a"].asInt())
      assertEquals(2, parsed["b"].asInt())
      assertFalse(result.trimStart().startsWith("{"), "Output should be YAML, not JSON: $result")
    }

    @Test
    fun `yaml diff is minimal`() {
      val new = "database:\n  port: 9999\n  host: localhost\nlogging:\n  level: INFO\n"
      val patch = yaml(processor.generatePatchForFormat(source, new, DataMergeProcessor.DataFormat.YAML))
      assertEquals(1, patch.size())
      assertEquals(9999, patch["database"]["port"].asInt())
    }
  }

  // ---------------------------------------------------------------------
  // XML
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("XML support")
  inner class Xml {

    private val source = "<config><database><port>5432</port><host>localhost</host></database></config>"

    @Test
    fun `xml source is auto-detected and merged`() {
      val patch = "<config><database><port>5433</port></database></config>"
      val result = xml(processor.applyPatch(source, patch))
      assertEquals("5433", result["database"]["port"].asText())
      assertEquals("localhost", result["database"]["host"].asText())
    }

    @Test
    fun `xml declaration prefix is detected`() {
      val declared = """<?xml version="1.0"?><config><a>1</a></config>"""
      val result = xml(processor.applyPatch(declared, "<config><b>2</b></config>"))
      assertEquals("1", result["a"].asText())
      assertEquals("2", result["b"].asText())
    }

    @Test
    fun `xml output is well formed and re-parsable`() {
      val result = processor.applyPatch(source, "<config><database><port>5433</port></database></config>")
      assertNotNull(xml(result), "Merged XML must be re-parsable")
    }

    @Test
    fun `explicit xml format diff returns changed nodes`() {
      val new = "<config><database><port>9999</port><host>localhost</host></database></config>"
      val patch = processor.generatePatchForFormat(source, new, DataMergeProcessor.DataFormat.XML)
      assertTrue(patch.contains("9999"), "Diff should contain the new value: $patch")
    }
  }

  // ---------------------------------------------------------------------
  // TOML
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("TOML support")
  inner class Toml {

    private val source = "[database]\nport = 5432\nhost = \"localhost\"\n"

    @Test
    fun `explicit toml format merges nested tables`() {
      val patch = "[database]\nport = 5433\n"
      val result = toml(
        processor.applyPatchForFormat(source, patch, DataMergeProcessor.DataFormat.TOML)
      )
      assertEquals(5433, result["database"]["port"].asInt())
      assertEquals("localhost", result["database"]["host"].asText())
    }

    @Test
    fun `explicit toml format produces a minimal diff`() {
      val new = "[database]\nport = 9999\nhost = \"localhost\"\n"
      val patch = processor.generatePatchForFormat(source, new, DataMergeProcessor.DataFormat.TOML)
      assertTrue(patch.contains("9999"), "Diff should mention the new value: $patch")
      assertFalse(patch.contains("localhost"), "Diff should omit the unchanged value: $patch")
    }

    @Test
    fun `json patch is accepted for a toml source via fallback parsing`() {
      val result = processor.applyPatchForFormat(
        source, """{"database":{"port":5433}}""", DataMergeProcessor.DataFormat.TOML
      )
      assertTrue(result.contains("5433"), "Fallback-parsed patch should be applied: $result")
    }

    @Test
    fun `auto-detection of toml-like content never throws`() {
      // NOTE: `[section]` + `key = value` content without ':' is currently detected as PROPERTIES.
      // The contract we assert here is only that the call is total (never throws) and is lossless
      // enough to retain the new value.
      val result = processor.applyPatch(source, "[database]\nport = 5433\n")
      assertNotNull(result)
      assertTrue(result.contains("5433"), "New value should survive: $result")
    }
  }

  // ---------------------------------------------------------------------
  // Properties
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Properties support")
  inner class Properties {

    private val source = "database.port=5432\ndatabase.host=localhost\n"

    @Test
    fun `properties source is auto-detected and merged`() {
      val result = props(processor.applyPatch(source, "database.port=5433\n"))
      assertEquals("5433", result["database"]["port"].asText())
      assertEquals("localhost", result["database"]["host"].asText())
    }

    @Test
    fun `new keys are added`() {
      val result = props(processor.applyPatch(source, "logging.level=DEBUG\n"))
      assertEquals("DEBUG", result["logging"]["level"].asText())
      assertEquals("5432", result["database"]["port"].asText())
    }

    @Test
    fun `explicit properties format produces a minimal diff`() {
      val new = "database.port=9999\ndatabase.host=localhost\n"
      val patch = processor.generatePatchForFormat(source, new, DataMergeProcessor.DataFormat.PROPERTIES)
      assertTrue(patch.contains("9999"), "Diff should mention the new value: $patch")
      assertFalse(patch.contains("localhost"), "Diff should omit unchanged values: $patch")
    }
  }

  // ---------------------------------------------------------------------
  // Format detection
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Format detection (via public behavior)")
  inner class FormatDetection {

    @Test
    fun `object braces select JSON`() {
      val result = processor.applyPatch("""{"a":1}""", """{"b":2}""")
      assertTrue(result.trim().startsWith("{"), "Expected JSON output, got: $result")
    }

    @Test
    fun `array brackets select JSON`() {
      val result = processor.applyPatch("""[1,2]""", """[3]""")
      assertTrue(result.trim().startsWith("["), "Expected JSON output, got: $result")
    }

    @Test
    fun `angle bracket prefix selects XML`() {
      val result = processor.applyPatch("<r><a>1</a></r>", "<r><b>2</b></r>")
      assertTrue(result.trim().startsWith("<"), "Expected XML output, got: $result")
    }

    @Test
    fun `equals-only content selects PROPERTIES`() {
      val result = processor.applyPatch("a=1\n", "b=2\n")
      assertTrue(result.contains("a=1"), "Expected properties output, got: $result")
      assertTrue(result.contains("b=2"), "Expected properties output, got: $result")
    }

    @Test
    fun `colon-based mapping selects YAML`() {
      val result = processor.applyPatch("a: 1\n", "b: 2\n")
      val parsed = yaml(result)
      assertEquals(1, parsed["a"].asInt())
      assertEquals(2, parsed["b"].asInt())
    }

    @Test
    fun `detection is driven by the source, not the patch`() {
      // YAML source + JSON patch -> YAML output
      val result = processor.applyPatch("a: 1\n", """{"b": 2}""")
      assertFalse(result.trim().startsWith("{"), "Output format should follow the source: $result")
      val parsed = yaml(result)
      assertEquals(1, parsed["a"].asInt())
      assertEquals(2, parsed["b"].asInt())
    }
  }

  // ---------------------------------------------------------------------
  // Error handling
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Error handling")
  inner class ErrorHandling {

    @Test
    fun `unparsable source falls back to returning the trimmed patch`() {
      val patch = """  {"a":1}  """
      val result = processor.applyPatchForFormat(
        "{ this is not valid json", patch, DataMergeProcessor.DataFormat.JSON
      )
      assertEquals(patch.trim(), result)
    }

    @Test
    fun `unparsable old content in generatePatch returns the new content`() {
      val new = """{"a":1}"""
      val result = processor.generatePatchForFormat(
        "{ broken", new, DataMergeProcessor.DataFormat.JSON
      )
      assertEquals(new, result)
    }

    @Test
    fun `unparsable new content in generatePatch returns the new content`() {
      val new = "{ broken"
      val result = processor.generatePatchForFormat(
        """{"a":1}""", new, DataMergeProcessor.DataFormat.JSON
      )
      assertEquals(new, result)
    }

    @Test
    fun `applyPatch never throws for arbitrary input`() {
      val inputs = listOf(
        "", " ", "\n", "not structured at all", "{", "}", "<", "[]", "null", "0", "\"str\""
      )
      inputs.forEach { source ->
        inputs.forEach { patch ->
          val result = processor.applyPatch(source, patch)
          assertNotNull(result, "applyPatch('$source', '$patch') should not return null")
        }
      }
    }

    @Test
    fun `generatePatch never throws for arbitrary input`() {
      val inputs = listOf("", " ", "not structured at all", "{", "[]", "null")
      inputs.forEach { old ->
        inputs.forEach { new ->
          assertNotNull(processor.generatePatch(old, new), "generatePatch('$old', '$new') should not return null")
        }
      }
    }
  }

  // ---------------------------------------------------------------------
  // DataFormat enum
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("DataFormat enum")
  inner class DataFormatEnum {

    @Test
    fun `all five formats are declared`() {
      assertEquals(5, DataMergeProcessor.DataFormat.entries.size)
      assertEquals(
        listOf("JSON", "YAML", "XML", "TOML", "PROPERTIES"),
        DataMergeProcessor.DataFormat.entries.map { it.name }
      )
    }

    @Test
    fun `extensions are declared as expected`() {
      assertEquals(listOf("json"), DataMergeProcessor.DataFormat.JSON.extensions)
      assertEquals(listOf("yaml", "yml"), DataMergeProcessor.DataFormat.YAML.extensions)
      assertEquals(listOf("xml"), DataMergeProcessor.DataFormat.XML.extensions)
      assertEquals(listOf("toml"), DataMergeProcessor.DataFormat.TOML.extensions)
      assertEquals(listOf("properties"), DataMergeProcessor.DataFormat.PROPERTIES.extensions)
    }

    @Test
    fun `extensions are unique across formats`() {
      val all = DataMergeProcessor.DataFormat.entries.flatMap { it.extensions }
      assertEquals(all.size, all.toSet().size, "Extensions must not overlap: $all")
    }

    @Test
    fun `mappers are non-null for every format`() {
      DataMergeProcessor.DataFormat.entries.forEach {
        assertNotNull(it.readMapper, "${it.name} readMapper")
        assertNotNull(it.writeMapper, "${it.name} writeMapper")
      }
    }

    @Test
    fun `every format can round-trip a simple document`() {
      DataMergeProcessor.DataFormat.entries.forEach { format ->
        val merged = processor.applyPatchForFormat(
          """{"a":1}""", """{"b":2}""", format
        )
        assertNotNull(merged, "${format.name} merge returned null")
        assertTrue(merged.contains("2"), "${format.name} merge lost the patched value: $merged")
      }
    }
  }
}