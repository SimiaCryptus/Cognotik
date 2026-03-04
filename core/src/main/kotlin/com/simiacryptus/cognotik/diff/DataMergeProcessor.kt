package com.simiacryptus.cognotik.diff

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.simiacryptus.cognotik.util.LoggerFactory
/**
* A processor that handles JSON merge operations.
* The LLM provides structured data content (JSON, YAML, XML, TOML, Properties)
* which is deep-merged with the existing data.
* This is useful for configuration files where only specific fields need to change.
*/
class DataMergeProcessor : PatchProcessor {
override val label = "DataMerge"
override val patchFormatPrompt = """
        Response should provide structured data content within fenced code blocks.
        Supported formats: json, yaml, xml, toml, properties.
        The data will be deep-merged with the existing file content.
Only include the fields you want to add or modify - existing fields not mentioned will be preserved.
To remove a field, set its value to null.
Each code block should be preceded by a header that identifies the file being modified.
        
        Example with JSON:
Here is the updated configuration:
### config/settings.json
```json
{
"database": {
"port": 5433,
"maxConnections": 20
},
"newFeature": {
"enabled": true
}
}
```
        Example with YAML:

        ### config/settings.yaml
        ```yaml
        database:
          port: 5433
          maxConnections: 20
        newFeature:
          enabled: true
        ```

        Example with XML:

        ### config/settings.xml
        ```xml
        <config>
          <database>
            <port>5433</port>
            <maxConnections>20</maxConnections>
          </database>
        </config>
        ```

        This will merge the provided data with the existing file, updating "database.port" and
"database.maxConnections", adding the "newFeature" section, and preserving all other existing fields.
""".trimIndent()
override fun getInitiatorPattern(): Regex {
        return """(?s)```(?:json|yaml|yml|xml|toml|properties)\n""".toRegex()
}
override fun extractCodeBlocks(response: String): List<Pair<String, String>> {
        val codeblockPattern = """(?s)(?<![^\n])```(json|yaml|yml|xml|toml|properties)\n(.*?)\n```""".toRegex()
return codeblockPattern.findAll(response).map { match ->
            val format = match.groupValues[1]
            val code = match.groupValues[2]
            format to code
}.toList()
}
override fun generatePatch(oldCode: String, newCode: String): String {
        return generatePatchForFormat(oldCode, newCode, detectFormat(oldCode))
    }

    fun generatePatchForFormat(oldCode: String, newCode: String, format: DataFormat): String {
        log.debug("Generating {} merge patch", format.name)
try {
            val mapper = format.readMapper
            val oldJson = mapper.readTree(oldCode)
            val newJson = mapper.readTree(newCode)
// Compute a minimal diff: only fields that changed
val diff = computeDiff(oldJson, newJson)
return if (diff != null) {
                format.writeMapper.writerWithDefaultPrettyPrinter().writeValueAsString(diff)
} else {
newCode
}
} catch (e: Exception) {
            log.warn("Failed to compute {} diff, returning full new content", format.name, e)
return newCode
}
}
override fun applyPatch(source: String, patch: String): String {
        return applyPatchForFormat(source, patch, detectFormat(source))
    }

    fun applyPatchForFormat(source: String, patch: String, format: DataFormat): String {
        log.debug("Applying {} merge patch", format.name)
try {
            val readMapper = format.readMapper
            val sourceJson = readMapper.readTree(source.trim())
            // Patch might be in a different format (e.g., JSON patch for YAML source)
            val patchJson = tryParseWithFallback(patch.trim(), format)
val merged = deepMerge(sourceJson, patchJson)
            return format.writeMapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged)
} catch (e: Exception) {
            log.warn("Failed to merge {}, returning patch content as-is", format.name, e)
return patch.trim()
}
}
/**
     * Attempts to parse content with the given format's mapper, falling back to other formats.
     */
    private fun tryParseWithFallback(content: String, preferredFormat: DataFormat): JsonNode {
        // Try preferred format first
        try {
            return preferredFormat.readMapper.readTree(content)
        } catch (_: Exception) {
        }
        // Try all other formats
        for (format in DataFormat.entries) {
            if (format == preferredFormat) continue
            try {
                return format.readMapper.readTree(content)
            } catch (_: Exception) {
            }
        }
        throw IllegalArgumentException("Unable to parse content as any supported format")
    }
    /**
* Deep merges the patch JSON into the source JSON.
* - Object nodes are recursively merged
* - Null values in the patch remove the corresponding key from the source
* - Array nodes from the patch replace the source arrays entirely
* - All other values from the patch override the source
*/
private fun deepMerge(source: JsonNode, patch: JsonNode): JsonNode {
if (source is ObjectNode && patch is ObjectNode) {
val result = source.deepCopy()
patch.fields().forEach { (key, patchValue) ->
if (patchValue.isNull) {
result.remove(key)
} else if (result.has(key) && result[key] is ObjectNode && patchValue is ObjectNode) {
result.set<JsonNode>(key, deepMerge(result[key], patchValue))
} else {
result.set<JsonNode>(key, patchValue.deepCopy())
}
}
return result
}
// For non-object nodes, patch wins
return patch.deepCopy()
}
/**
* Computes a minimal diff between two JSON trees, returning only changed/added fields.
* Returns null if the trees are identical.
*/
private fun computeDiff(oldNode: JsonNode, newNode: JsonNode): JsonNode? {
if (oldNode == newNode) return null
if (oldNode is ObjectNode && newNode is ObjectNode) {
val diff = objectMapper.createObjectNode()
// Fields changed or added in newNode
newNode.fields().forEach { (key, newValue) ->
if (!oldNode.has(key)) {
diff.set<JsonNode>(key, newValue.deepCopy())
} else {
val childDiff = computeDiff(oldNode[key], newValue)
if (childDiff != null) {
diff.set<JsonNode>(key, childDiff)
}
}
}
// Fields removed in newNode
oldNode.fieldNames().forEach { key ->
if (!newNode.has(key)) {
diff.putNull(key)
}
}
return if (diff.isEmpty) null else diff
}
// For non-object changes, return the new value
return newNode.deepCopy()
}
    /**
     * Detects the data format from content by attempting to parse with each mapper.
     */
    private fun detectFormat(content: String): DataFormat {
        val trimmed = content.trim()
        // Quick heuristic checks before parsing
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return DataFormat.JSON
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) return DataFormat.XML
        if (trimmed.contains("=") && !trimmed.contains(":") && !trimmed.contains("{")) return DataFormat.PROPERTIES
        // TOML often has [section] headers and key = value
        if (trimmed.contains("[") && trimmed.contains("]") && trimmed.contains("=")) {
            try {
                DataFormat.TOML.readMapper.readTree(trimmed)
                return DataFormat.TOML
            } catch (_: Exception) {
            }
        }
        // Try YAML (which is a superset of JSON, so check after JSON)
        try {
            DataFormat.YAML.readMapper.readTree(trimmed)
            return DataFormat.YAML
        } catch (_: Exception) {
        }
        // Default to JSON
        return DataFormat.JSON
    }
    /**
     * Supported data formats for merge operations.
     */
    enum class DataFormat(
        val readMapper: ObjectMapper,
        val writeMapper: ObjectMapper,
        val extensions: List<String>
    ) {
        JSON(
            readMapper = ObjectMapper(),
            writeMapper = ObjectMapper(),
            extensions = listOf("json")
        ),
        YAML(
            readMapper = ObjectMapper(
                YAMLFactory()
            ),
            writeMapper = ObjectMapper(
                YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            ),
            extensions = listOf("yaml", "yml")
        ),
        XML(
            readMapper = XmlMapper(),
            writeMapper = XmlMapper(),
            extensions = listOf("xml")
        ),
        TOML(
            readMapper = TomlMapper(),
            writeMapper = TomlMapper(),
            extensions = listOf("toml")
        ),
        PROPERTIES(
            readMapper = JavaPropsMapper(),
            writeMapper = JavaPropsMapper(),
            extensions = listOf("properties")
        );
        companion object {
            fun fromExtension(ext: String): DataFormat {
                val lower = ext.lowercase().removePrefix(".")
                return entries.find { it.extensions.contains(lower) } ?: JSON
            }
            fun fromFormatName(name: String): DataFormat {
                val lower = name.lowercase()
                return when (lower) {
                    "json" -> JSON
                    "yaml", "yml" -> YAML
                    "xml" -> XML
                    "toml" -> TOML
                    "properties" -> PROPERTIES
                    else -> JSON
                }
            }
        }
    }

companion object {
private val log = LoggerFactory.getLogger(DataMergeProcessor::class.java)
private val objectMapper = ObjectMapper()
}
}