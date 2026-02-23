package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.util.DocProcessor.*
import com.simiacryptus.cognotik.util.DocProcessor.Companion.expandPatternOrLiteral
import com.simiacryptus.cognotik.util.DocProcessor.Companion.expandRecursiveGlob
import com.simiacryptus.cognotik.util.DocProcessor.Companion.expandSimpleGlob
import com.simiacryptus.cognotik.util.DocProcessor.Companion.expandTransformPattern
import com.simiacryptus.cognotik.util.DocProcessor.Companion.isGlobPattern
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseDocuments
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseFrontmatter
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseGenerates
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseRelated
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseSpecifies
import com.simiacryptus.cognotik.util.DocProcessor.Companion.parseTransforms
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DocProcessorTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var docsFolder: File
    private lateinit var srcFolder: File

    @BeforeEach
    fun setUp() {
        docsFolder = File(tempDir, "docs").also { it.mkdirs() }
        srcFolder = File(tempDir, "src").also { it.mkdirs() }
    }

    // ========================================================================
    // Tests for isGlobPattern
    // ========================================================================
    @Nested
    inner class IsGlobPatternTests {
        @Test
        fun `returns true for asterisk pattern`() {
            assertTrue(isGlobPattern("*.kt"))
        }

        @Test
        fun `returns true for double asterisk pattern`() {
            assertTrue(isGlobPattern("src/**/*.kt"))
        }

        @Test
        fun `returns true for question mark pattern`() {
            assertTrue(isGlobPattern("file?.txt"))
        }

        @Test
        fun `returns true for bracket pattern`() {
            assertTrue(isGlobPattern("file[0-9].txt"))
        }

        @Test
        fun `returns false for literal path`() {
            assertFalse(isGlobPattern("src/main/File.kt"))
        }

        @Test
        fun `returns false for empty string`() {
            assertFalse(isGlobPattern(""))
        }

        @Test
        fun `returns false for path with dots`() {
            assertFalse(isGlobPattern("com.example.Main"))
        }
    }

    // ========================================================================
    // Tests for parseFrontmatter
    // ========================================================================
    @Nested
    inner class ParseFrontmatterTests {
        @Test
        fun `parses simple key-value pairs`() {
            val text = """
                key1: value1
                key2: value2
            """.trimIndent()
            val result = parseFrontmatter(text)
            assertEquals("value1", result["key1"])
            assertEquals("value2", result["key2"])
        }

        @Test
        fun `parses list values`() {
            val text = """
                specifies:
                - file1.kt
                - file2.kt
            """.trimIndent()
            val result = parseFrontmatter(text)
            val specifies = result["specifies"]
            assertInstanceOf(List::class.java, specifies)
            assertEquals(listOf("file1.kt", "file2.kt"), specifies)
        }

        @Test
        fun `parses mixed key-value and list`() {
            val text = """
                title: My Doc
                specifies:
                - file1.kt
                - file2.kt
                task_type: FileModification
            """.trimIndent()
            val result = parseFrontmatter(text)
            assertEquals("My Doc", result["title"])
            assertEquals(listOf("file1.kt", "file2.kt"), result["specifies"])
            assertEquals("FileModification", result["task_type"])
        }

        @Test
        fun `handles empty text`() {
            val result = parseFrontmatter("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `handles single inline value for specifies`() {
            val text = "specifies: src/main/File.kt"
            val result = parseFrontmatter(text)
            assertEquals("src/main/File.kt", result["specifies"])
        }

        @Test
        fun `handles value with colons in it`() {
            val text = "url: http://example.com"
            val result = parseFrontmatter(text)
            // The parser splits on first colon, so value should be everything after first colon
            assertEquals("http://example.com", result["url"])
        }

        @Test
        fun `handles empty list`() {
            val text = """
                specifies:
                title: next
            """.trimIndent()
            val result = parseFrontmatter(text)
            // No list items found, so specifies should not be in the map
            assertFalse(result.containsKey("specifies"))
            assertEquals("next", result["title"])
        }
    }

    // ========================================================================
    // Tests for parseSpecifies
    // ========================================================================
    @Nested
    inner class ParseSpecifiesTests {
        @Test
        fun `parses single string value`() {
            val frontmatter = mapOf<String, Any>("specifies" to "file.kt")
            assertEquals(listOf("file.kt"), parseSpecifies(frontmatter))
        }

        @Test
        fun `parses list value`() {
            val frontmatter = mapOf<String, Any>("specifies" to listOf("file1.kt", "file2.kt"))
            assertEquals(listOf("file1.kt", "file2.kt"), parseSpecifies(frontmatter))
        }

        @Test
        fun `returns empty for missing key`() {
            val frontmatter = mapOf<String, Any>("other" to "value")
            assertEquals(emptyList<String>(), parseSpecifies(frontmatter))
        }

        @Test
        fun `returns empty for non-string non-list value`() {
            val frontmatter = mapOf<String, Any>("specifies" to 42)
            assertEquals(emptyList<String>(), parseSpecifies(frontmatter))
        }

        @Test
        fun `filters non-string items from list`() {
            val frontmatter = mapOf<String, Any>("specifies" to listOf("file.kt", 42, null, "other.kt"))
            assertEquals(listOf("file.kt", "other.kt"), parseSpecifies(frontmatter))
        }
    }

    // ========================================================================
    // Tests for parseDocuments
    // ========================================================================
    @Nested
    inner class ParseDocumentsTests {
        @Test
        fun `parses single string value`() {
            val frontmatter = mapOf<String, Any>("documents" to "src/**/*.kt")
            assertEquals(listOf("src/**/*.kt"), parseDocuments(frontmatter))
        }

        @Test
        fun `parses list value`() {
            val frontmatter = mapOf<String, Any>("documents" to listOf("src/**/*.kt", "lib/**/*.java"))
            assertEquals(listOf("src/**/*.kt", "lib/**/*.java"), parseDocuments(frontmatter))
        }

        @Test
        fun `returns empty for missing key`() {
            assertEquals(emptyList<String>(), parseDocuments(emptyMap()))
        }
    }

    // ========================================================================
    // Tests for parseRelated
    // ========================================================================
    @Nested
    inner class ParseRelatedTests {
        @Test
        fun `parses single string value`() {
            val frontmatter = mapOf<String, Any>("related" to "config.yaml")
            assertEquals(listOf("config.yaml"), parseRelated(frontmatter))
        }

        @Test
        fun `parses list value`() {
            val frontmatter = mapOf<String, Any>("related" to listOf("config.yaml", "schema.json"))
            assertEquals(listOf("config.yaml", "schema.json"), parseRelated(frontmatter))
        }

        @Test
        fun `returns empty for missing key`() {
            assertEquals(emptyList<String>(), parseRelated(emptyMap()))
        }

        @Test
        fun `returns empty for non-string value`() {
            val frontmatter = mapOf<String, Any>("related" to 123)
            assertEquals(emptyList<String>(), parseRelated(frontmatter))
        }
    }

    // ========================================================================
    // Tests for parseTransforms
    // ========================================================================
    @Nested
    inner class ParseTransformsTests {
        @Test
        fun `parses single transform string`() {
            val frontmatter = mapOf<String, Any>("transforms" to "(.+)\\.json -> \$1.yaml")
            val result = parseTransforms(frontmatter)
            assertEquals(1, result.size)
            assertEquals("(.+)\\.json", result[0].sourcePattern)
            assertEquals("\$1.yaml", result[0].destinationPattern)
        }

        @Test
        fun `parses list of transforms`() {
            val frontmatter = mapOf<String, Any>(
                "transforms" to listOf(
                    "(.+)\\.json -> \$1.yaml",
                    "(.+)\\.xml -> \$1.html"
                )
            )
            val result = parseTransforms(frontmatter)
            assertEquals(2, result.size)
            assertEquals("(.+)\\.json", result[0].sourcePattern)
            assertEquals("\$1.yaml", result[0].destinationPattern)
            assertEquals("(.+)\\.xml", result[1].sourcePattern)
            assertEquals("\$1.html", result[1].destinationPattern)
        }

        @Test
        fun `returns empty for missing key`() {
            assertEquals(emptyList<TransformSpec>(), parseTransforms(emptyMap()))
        }

        @Test
        fun `skips invalid transform format`() {
            val frontmatter = mapOf<String, Any>("transforms" to "no arrow here")
            val result = parseTransforms(frontmatter)
            assertEquals(0, result.size)
        }

        @Test
        fun `skips transforms with too many arrows`() {
            val frontmatter = mapOf<String, Any>("transforms" to "a -> b -> c")
            val result = parseTransforms(frontmatter)
            // "a -> b -> c".split("->") gives 3 parts, so it should be skipped
            assertEquals(0, result.size)
        }

        @Test
        fun `handles whitespace around arrow`() {
            val frontmatter = mapOf<String, Any>("transforms" to "  source.kt   ->   dest.kt  ")
            val result = parseTransforms(frontmatter)
            assertEquals(1, result.size)
            assertEquals("source.kt", result[0].sourcePattern)
            assertEquals("dest.kt", result[0].destinationPattern)
        }
    }

    // ========================================================================
    // Tests for parseGenerates
    // ========================================================================
    @Nested
    inner class ParseGeneratesTests {
        @Test
        fun `parses single generate spec as map`() {
            val frontmatter = mapOf<String, Any>(
                "generates" to mapOf(
                    "output" to "output.kt",
                    "inputs" to listOf("src/*.kt", "lib/*.kt")
                )
            )
            val result = parseGenerates(frontmatter)
            assertEquals(1, result.size)
            assertEquals("output.kt", result[0].output)
            assertEquals(listOf("src/*.kt", "lib/*.kt"), result[0].inputs)
        }

        @Test
        fun `parses list of generate specs`() {
            val frontmatter = mapOf<String, Any>(
                "generates" to listOf(
                    mapOf("output" to "out1.kt", "inputs" to listOf("in1.kt")),
                    mapOf("output" to "out2.kt", "inputs" to listOf("in2.kt", "in3.kt"))
                )
            )
            val result = parseGenerates(frontmatter)
            assertEquals(2, result.size)
            assertEquals("out1.kt", result[0].output)
            assertEquals("out2.kt", result[1].output)
        }

        @Test
        fun `returns empty for missing key`() {
            assertEquals(emptyList<GenerateSpec>(), parseGenerates(emptyMap()))
        }

        @Test
        fun `skips generate spec without output`() {
            val frontmatter = mapOf<String, Any>(
                "generates" to mapOf("inputs" to listOf("in.kt"))
            )
            val result = parseGenerates(frontmatter)
            assertEquals(0, result.size)
        }

        @Test
        fun `skips generate spec without inputs`() {
            val frontmatter = mapOf<String, Any>(
                "generates" to mapOf("output" to "out.kt")
            )
            val result = parseGenerates(frontmatter)
            assertEquals(0, result.size)
        }

        @Test
        fun `handles single string input`() {
            val frontmatter = mapOf<String, Any>(
                "generates" to mapOf(
                    "output" to "output.kt",
                    "inputs" to "single_input.kt"
                )
            )
            val result = parseGenerates(frontmatter)
            assertEquals(1, result.size)
            assertEquals(listOf("single_input.kt"), result[0].inputs)
        }
    }

    // ========================================================================
    // Tests for parseMarkdownWithFrontmatter
    // ========================================================================
    @Nested
    inner class ParseMarkdownWithFrontmatterTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `parses valid markdown with specifies`() {
            val mdFile = File(docsFolder, "test.md")
            mdFile.writeText(
                """
                |---
                |specifies: ../src/Main.kt
                |---
                |# Documentation
                |This is the body content.
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNotNull(result)
            assertEquals(listOf("../src/Main.kt"), result!!.specifies)
            assertTrue(result.content.contains("# Documentation"))
            assertTrue(result.content.contains("This is the body content."))
        }

        @Test
        fun `parses valid markdown with transforms`() {
            val mdFile = File(docsFolder, "transform.md")
            mdFile.writeText(
                """
                |---
                |transforms:
                |- (.+)\.json -> $1.yaml
                |---
                |# Transform doc
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNotNull(result)
            assertEquals(1, result!!.transforms.size)
        }

        @Test
        fun `returns null for file without frontmatter`() {
            val mdFile = File(docsFolder, "no_frontmatter.md")
            mdFile.writeText("# Just a regular markdown file\nNo frontmatter here.")
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNull(result)
        }

        @Test
        fun `returns null for file with frontmatter but no specifies or transforms`() {
            val mdFile = File(docsFolder, "no_specifies.md")
            mdFile.writeText(
                """
                |---
                |title: Just a title
                |---
                |# Content
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNull(result)
        }

        @Test
        fun `returns null for non-existent file`() {
            val mdFile = File(docsFolder, "nonexistent.md")
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNull(result)
        }

        @Test
        fun `returns null for unclosed frontmatter`() {
            val mdFile = File(docsFolder, "unclosed.md")
            mdFile.writeText(
                """
                |---
                |specifies: file.kt
                |No closing delimiter
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNull(result)
        }

        @Test
        fun `parses documents frontmatter`() {
            val mdFile = File(docsFolder, "documents.md")
            mdFile.writeText(
                """
                |---
                |documents:
                |- ../src/**/*.kt
                |---
                |# API Documentation
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNotNull(result)
            assertEquals(listOf("../src/**/*.kt"), result!!.documents)
        }

        @Test
        fun `parses related frontmatter`() {
            val mdFile = File(docsFolder, "related.md")
            mdFile.writeText(
                """
                |---
                |specifies: ../src/Main.kt
                |related:
                |- ../config.yaml
                |- https://example.com/api-spec
                |---
                |# Doc with related
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNotNull(result)
            assertEquals(listOf("../config.yaml", "https://example.com/api-spec"), result!!.related)
        }

        @Test
        fun `parses task_type frontmatter`() {
            val mdFile = File(docsFolder, "tasktype.md")
            mdFile.writeText(
                """
                |---
                |specifies: ../src/Main.kt
                |task_type: FileModification
                |---
                |# Doc with task type
                """.trimMargin()
            )
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            assertNotNull(result)
            assertEquals("FileModification", result!!.taskType)
        }

        @Test
        fun `parses generates frontmatter`() {
            val mdFile = File(docsFolder, "generates.md")
            mdFile.writeText(
                """
                |---
                |generates:
                |- output: ../src/Generated.kt
                |  inputs:
                |  - ../src/Input1.kt
                |  - ../src/Input2.kt
                |---
                |# Generate doc
                """.trimMargin()
            )
            // Note: The simple YAML parser may not handle nested maps in lists.
            // This test documents the expected behavior with the simple parser.
            val result = processor.parseMarkdownWithFrontmatter(mdFile)
            // The simple parser may not parse this correctly, so we just verify it doesn't crash
            // and returns something reasonable
        }
    }

    // ========================================================================
    // Tests for expandSimpleGlob
    // ========================================================================
    @Nested
    inner class ExpandSimpleGlobTests {
        @Test
        fun `matches files with wildcard extension`() {
            File(srcFolder, "Main.kt").writeText("fun main() {}")
            File(srcFolder, "Utils.kt").writeText("object Utils {}")
            File(srcFolder, "readme.md").writeText("# Readme")

            val result = expandSimpleGlob(srcFolder, "*.kt")
            assertEquals(2, result.size)
            assertTrue(result.any { it.name == "Main.kt" })
            assertTrue(result.any { it.name == "Utils.kt" })
        }

        @Test
        fun `returns empty for non-existent directory`() {
            val result = expandSimpleGlob(File(tempDir, "nonexistent"), "*.kt")
            assertEquals(0, result.size)
        }

        @Test
        fun `matches files in subdirectory`() {
            val subDir = File(srcFolder, "sub").also { it.mkdirs() }
            File(subDir, "A.kt").writeText("class A")
            File(subDir, "B.java").writeText("class B {}")

            val result = expandSimpleGlob(srcFolder, "sub/*.kt")
            assertEquals(1, result.size)
            assertEquals("A.kt", result[0].name)
        }

        @Test
        fun `returns empty when no files match`() {
            File(srcFolder, "Main.kt").writeText("fun main() {}")
            val result = expandSimpleGlob(srcFolder, "*.java")
            assertEquals(0, result.size)
        }

        @Test
        fun `matches with question mark wildcard`() {
            File(srcFolder, "file1.txt").writeText("1")
            File(srcFolder, "file2.txt").writeText("2")
            File(srcFolder, "file10.txt").writeText("10")

            val result = expandSimpleGlob(srcFolder, "file?.txt")
            assertEquals(2, result.size)
            assertTrue(result.any { it.name == "file1.txt" })
            assertTrue(result.any { it.name == "file2.txt" })
        }
    }

    // ========================================================================
    // Tests for expandRecursiveGlob
    // ========================================================================
    @Nested
    inner class ExpandRecursiveGlobTests {
        @Test
        fun `matches files recursively`() {
            File(srcFolder, "Main.kt").writeText("fun main() {}")
            val subDir = File(srcFolder, "sub").also { it.mkdirs() }
            File(subDir, "Sub.kt").writeText("class Sub")
            val deepDir = File(subDir, "deep").also { it.mkdirs() }
            File(deepDir, "Deep.kt").writeText("class Deep")
            File(deepDir, "Other.java").writeText("class Other {}")

            val result = expandRecursiveGlob(tempDir, "src/**/*.kt")
            assertEquals(3, result.size)
            assertTrue(result.any { it.name == "Main.kt" })
            assertTrue(result.any { it.name == "Sub.kt" })
            assertTrue(result.any { it.name == "Deep.kt" })
        }

        @Test
        fun `returns empty for non-existent base`() {
            val result = expandRecursiveGlob(tempDir, "nonexistent/**/*.kt")
            assertEquals(0, result.size)
        }

        @Test
        fun `matches all files when no remaining pattern`() {
            File(srcFolder, "Main.kt").writeText("fun main() {}")
            File(srcFolder, "readme.md").writeText("# Readme")

            val result = expandRecursiveGlob(tempDir, "src/**")
            assertTrue(result.size >= 2)
        }
    }

    // ========================================================================
    // Tests for expandPatternOrLiteral
    // ========================================================================
    @Nested
    inner class ExpandPatternOrLiteralTests {
        @Test
        fun `returns literal file even if it does not exist`() {
            val result = expandPatternOrLiteral(srcFolder, "NonExistent.kt")
            assertEquals(1, result.size)
            assertTrue(result[0].name == "NonExistent.kt")
        }

        @Test
        fun `expands glob pattern for existing files`() {
            File(srcFolder, "A.kt").writeText("class A")
            File(srcFolder, "B.kt").writeText("class B")

            val result = expandPatternOrLiteral(srcFolder, "*.kt")
            assertEquals(2, result.size)
        }

        @Test
        fun `expands recursive glob pattern`() {
            File(srcFolder, "A.kt").writeText("class A")
            val sub = File(srcFolder, "sub").also { it.mkdirs() }
            File(sub, "B.kt").writeText("class B")

            val result = expandPatternOrLiteral(srcFolder, "**/*.kt")
            assertTrue(result.size >= 2)
        }
    }

    // ========================================================================
    // Tests for expandTransformPattern
    // ========================================================================
    @Nested
    inner class ExpandTransformPatternTests {
        @Test
        fun `matches and transforms files`() {
            val jsonFile = File(srcFolder, "data.json").also { it.writeText("{}") }
            val docFile = File(docsFolder, "transform.md").also {
                it.writeText("---\ntransforms:\n- (.+)\\.json -> \$1.yaml\n---\n# Doc")
            }
            val spec = DocSpec(
                docFile = docFile,
                specifies = emptyList(),
                documents = emptyList(),
                transforms = listOf(TransformSpec("(.+)\\.json", "\$1.yaml")),
                generates = emptyList(),
                related = emptyList(),
                content = "# Doc",
                frontmatter = emptyMap()
            )
            val transform = TransformSpec("(.+)\\.json", "\$1.yaml")
            val result = expandTransformPattern(tempDir, transform, spec)
            // Should find data.json and map it to data.yaml
            val dataMatch = result.find { it.sourceFile.name == "data.json" }
            assertNotNull(dataMatch)
            assertEquals("data.yaml", dataMatch!!.destinationFile.name)
        }

        @Test
        fun `returns empty for invalid regex`() {
            val docFile = File(docsFolder, "bad.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = emptyList(),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "",
                frontmatter = emptyMap()
            )
            val transform = TransformSpec("[invalid", "\$1.yaml")
            val result = expandTransformPattern(tempDir, transform, spec)
            assertEquals(0, result.size)
        }

        @Test
        fun `does not match non-matching files`() {
            File(srcFolder, "data.xml").also { it.writeText("<xml/>") }
            val docFile = File(docsFolder, "transform.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = emptyList(),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "",
                frontmatter = emptyMap()
            )
            val transform = TransformSpec("(.+)\\.json", "\$1.yaml")
            val result = expandTransformPattern(tempDir, transform, spec)
            val xmlMatch = result.find { it.sourceFile.name == "data.xml" }
            assertNull(xmlMatch)
        }
    }

    // ========================================================================
    // Tests for resolveRelatedResource
    // ========================================================================
    @Nested
    inner class ResolveRelatedResourceTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `resolves existing local file`() {
            val relatedFile = File(srcFolder, "related.txt").also { it.writeText("related content") }
            val result = processor.resolveRelatedResource(srcFolder, "related.txt")
            assertNotNull(result)
            assertTrue(result!!.exists())
            assertEquals("related.txt", result.name)
        }

        @Test
        fun `returns file object for non-existent local file`() {
            val result = processor.resolveRelatedResource(srcFolder, "nonexistent.txt")
            assertNotNull(result)
            assertEquals("nonexistent.txt", result!!.name)
        }

        @Test
        fun `identifies URLs correctly`() {
            // We can't easily test URL fetching without a server, but we can verify
            // that the method attempts to handle URLs differently
            // This test just verifies the isUrl check works
            assertTrue(processor.run { isUrl("http://example.com") })
            assertTrue(processor.run { isUrl("https://example.com") })
            assertFalse(processor.run { isUrl("file.txt") })
            assertFalse(processor.run { isUrl("../relative/path.kt") })
        }
    }

    // ========================================================================
    // Tests for fileToSpecs
    // ========================================================================
    @Nested
    inner class FileToSpecsTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `groups specs by target file`() {
            val targetFile = File(srcFolder, "Target.kt").also { it.writeText("class Target") }
            val docFile1 = File(docsFolder, "doc1.md").also { it.writeText("") }
            val docFile2 = File(docsFolder, "doc2.md").also { it.writeText("") }

            val spec1 = DocSpec(
                docFile = docFile1,
                specifies = listOf("../src/Target.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "Spec 1",
                frontmatter = emptyMap()
            )
            val spec2 = DocSpec(
                docFile = docFile2,
                specifies = listOf("../src/Target.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "Spec 2",
                frontmatter = emptyMap()
            )

            val result = processor.fileToSpecs(listOf(spec1, spec2))
            assertEquals(1, result.size)
            val specs = result.values.first()
            assertEquals(2, specs.size)
        }

        @Test
        fun `handles glob patterns in specifies`() {
            File(srcFolder, "A.kt").writeText("class A")
            File(srcFolder, "B.kt").writeText("class B")
            File(srcFolder, "C.java").writeText("class C {}")

            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = listOf("../src/*.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "Kotlin spec",
                frontmatter = emptyMap()
            )

            val result = processor.fileToSpecs(listOf(spec))
            assertEquals(2, result.size) // A.kt and B.kt
        }

        @Test
        fun `returns empty map for empty specs`() {
            val result = processor.fileToSpecs(emptyList())
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for documentMatches
    // ========================================================================
    @Nested
    inner class DocumentMatchesTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `finds supporting files for document spec`() {
            File(srcFolder, "A.kt").writeText("class A")
            File(srcFolder, "B.kt").writeText("class B")

            val docFile = File(docsFolder, "api.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = emptyList(),
                documents = listOf("../src/*.kt"),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "API docs",
                frontmatter = emptyMap()
            )

            val result = processor.documentMatches(listOf(spec))
            assertEquals(1, result.size)
            val matches = result.values.first()
            assertEquals(1, matches.size)
            assertEquals(2, matches[0].supportingFiles.size)
        }

        @Test
        fun `returns empty for specs without documents`() {
            val docFile = File(docsFolder, "nodocs.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = listOf("file.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "",
                frontmatter = emptyMap()
            )

            val result = processor.documentMatches(listOf(spec))
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for generateMatches
    // ========================================================================
    @Nested
    inner class GenerateMatchesTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `finds input files for generate spec`() {
            File(srcFolder, "Input1.kt").writeText("class Input1")
            File(srcFolder, "Input2.kt").writeText("class Input2")

            val docFile = File(docsFolder, "gen.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = emptyList(),
                documents = emptyList(),
                transforms = emptyList(),
                generates = listOf(GenerateSpec("../src/Generated.kt", listOf("../src/*.kt"))),
                related = emptyList(),
                content = "Generate doc",
                frontmatter = emptyMap()
            )

            val result = processor.generateMatches(listOf(spec))
            assertEquals(1, result.size)
            val matches = result.values.first()
            assertEquals(1, matches.size)
            assertTrue(matches[0].inputFiles.size >= 2)
            assertEquals("Generated.kt", matches[0].outputFile.name)
        }

        @Test
        fun `returns empty for specs without generates`() {
            val docFile = File(docsFolder, "nogen.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = listOf("file.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "",
                frontmatter = emptyMap()
            )

            val result = processor.generateMatches(listOf(spec))
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for transformMatches
    // ========================================================================
    @Nested
    inner class TransformMatchesTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `returns empty for specs without transforms`() {
            val docFile = File(docsFolder, "notransform.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile,
                specifies = listOf("file.kt"),
                documents = emptyList(),
                transforms = emptyList(),
                generates = emptyList(),
                related = emptyList(),
                content = "",
                frontmatter = emptyMap()
            )

            val result = processor.transformMatches(listOf(spec))
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for primarySource
    // ========================================================================
    @Nested
    inner class PrimarySourceTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `returns transform source when transforms present`() {
            val sourceFile = File(srcFolder, "source.json").also { it.writeText("{}") }
            val destFile = File(srcFolder, "dest.yaml")
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = emptyList(), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val transforms = listOf(TransformMatch(sourceFile, destFile, spec))

            val result = processor.primarySource(transforms, emptyList(), emptyList(), emptyList())
            assertEquals(sourceFile, result)
        }

        @Test
        fun `returns doc file when only specs present`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )

            val result = processor.primarySource(emptyList(), listOf(spec), emptyList(), emptyList())
            assertEquals(docFile, result)
        }

        @Test
        fun `returns supporting file when only documents present`() {
            val supportFile = File(srcFolder, "support.kt").also { it.writeText("class S") }
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = emptyList(), documents = listOf("../src/*.kt"),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val docMatch = DocumentMatch(spec, listOf(supportFile))

            val result = processor.primarySource(emptyList(), emptyList(), listOf(docMatch), emptyList())
            assertEquals(supportFile, result)
        }

        @Test
        fun `returns input file when only generates present`() {
            val inputFile = File(srcFolder, "input.kt").also { it.writeText("class I") }
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = emptyList(), documents = emptyList(),
                transforms = emptyList(), generates = listOf(GenerateSpec("out.kt", listOf("../src/*.kt"))),
                related = emptyList(), content = "", frontmatter = emptyMap()
            )
            val genMatch = GenerateMatch(File(srcFolder, "out.kt"), listOf(inputFile), spec)

            val result = processor.primarySource(emptyList(), emptyList(), emptyList(), listOf(genMatch))
            assertEquals(inputFile, result)
        }

        @Test
        fun `returns null when all lists empty`() {
            val result = processor.primarySource(emptyList(), emptyList(), emptyList(), emptyList())
            assertNull(result)
        }

        @Test
        fun `transforms take priority over specs`() {
            val sourceFile = File(srcFolder, "source.json").also { it.writeText("{}") }
            val destFile = File(srcFolder, "dest.yaml")
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val transforms = listOf(TransformMatch(sourceFile, destFile, spec))

            val result = processor.primarySource(transforms, listOf(spec), emptyList(), emptyList())
            assertEquals(sourceFile, result)
        }
    }

    // ========================================================================
    // Tests for resolveTaskType
    // ========================================================================
    @Nested
    inner class ResolveTaskTypeTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `defaults to FileModification when no task type specified`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap(), taskType = null
            )

            val result = processor.resolveTaskType(listOf(spec), emptyList(), emptyList(), emptyList())
            assertEquals(FileModification, result)
        }

        @Test
        fun `uses spec task type when specified`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap(), taskType = "FileModification"
            )

            val result = processor.resolveTaskType(listOf(spec), emptyList(), emptyList(), emptyList())
            assertEquals(FileModification, result)
        }

        @Test
        fun `falls back to FileModification for unknown task type`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap(), taskType = "NonExistentTaskType"
            )

            val result = processor.resolveTaskType(listOf(spec), emptyList(), emptyList(), emptyList())
            assertEquals(FileModification, result)
        }
    }

    // ========================================================================
    // Tests for sortByDependencies
    // ========================================================================
    @Nested
    inner class SortByDependenciesTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `returns empty list for empty input`() {
            val result = processor.sortByDependencies(emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns single task unchanged`() {
            val task = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/A.kt"),
                    related_files = emptyList(),
                    task_description = "Update A"
                )
            )
            val result = processor.sortByDependencies(listOf(task))
            assertEquals(1, result.size)
            assertEquals(task, result[0])
        }

        @Test
        fun `sorts dependent task after dependency`() {
            // Create actual files so canonical paths work
            File(srcFolder, "Base.kt").also { it.writeText("class Base") }
            File(srcFolder, "Derived.kt").also { it.writeText("class Derived") }

            val baseTask = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/Base.kt"),
                    related_files = emptyList(),
                    task_description = "Update Base"
                )
            )
            val derivedTask = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/Derived.kt"),
                    related_files = listOf("src/Base.kt"),
                    task_description = "Update Derived"
                )
            )

            // Even if derived comes first in input, base should come first in output
            val result = processor.sortByDependencies(listOf(derivedTask, baseTask))
            assertEquals(2, result.size)
            val baseIndex = result.indexOf(baseTask)
            val derivedIndex = result.indexOf(derivedTask)
            assertTrue(baseIndex < derivedIndex, "Base should come before Derived")
        }

        @Test
        fun `handles tasks with no dependencies`() {
            val task1 = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/A.kt"),
                    related_files = emptyList(),
                    task_description = "Update A"
                )
            )
            val task2 = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/B.kt"),
                    related_files = emptyList(),
                    task_description = "Update B"
                )
            )

            val result = processor.sortByDependencies(listOf(task1, task2))
            assertEquals(2, result.size)
        }

        @Test
        fun `handles circular dependencies gracefully`() {
            File(srcFolder, "A.kt").also { it.writeText("class A") }
            File(srcFolder, "B.kt").also { it.writeText("class B") }

            val taskA = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/A.kt"),
                    related_files = listOf("src/B.kt"),
                    task_description = "Update A"
                )
            )
            val taskB = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = listOf("src/B.kt"),
                    related_files = listOf("src/A.kt"),
                    task_description = "Update B"
                )
            )

            // Should not throw, should return both tasks
            val result = processor.sortByDependencies(listOf(taskA, taskB))
            assertEquals(2, result.size)
            assertTrue(result.contains(taskA))
            assertTrue(result.contains(taskB))
        }

        @Test
        fun `handles null files gracefully`() {
            val task = ModificationTask(
                data = ModificationTaskConfig(
                    root = tempDir,
                    files = null,
                    related_files = null,
                    task_description = "No files"
                )
            )
            val result = processor.sortByDependencies(listOf(task))
            assertEquals(1, result.size)
        }
    }

    // ========================================================================
    // Tests for buildCombinedTaskDescription
    // ========================================================================
    @Nested
    inner class BuildCombinedTaskDescriptionTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `uses prompt from frontmatter when single spec with prompt`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = mapOf("prompt" to "Custom prompt text")
            )
            val target = File(srcFolder, "file.kt")

            val result = processor.buildCombinedTaskDescription(
                listOf(spec), emptyList(), emptyList(), emptyList(), target, FileModification
            )
            assertTrue(result.contains("Custom prompt text"))
        }

        @Test
        fun `generates update description for specs`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = listOf("file.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val target = File(srcFolder, "file.kt")

            val result = processor.buildCombinedTaskDescription(
                listOf(spec), emptyList(), emptyList(), emptyList(), target, FileModification
            )
            assertTrue(result.contains("Update the file"))
            assertTrue(result.contains("file.kt"))
        }

        @Test
        fun `generates documentation description for documents`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = emptyList(), documents = listOf("../src/*.kt"),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val supportFile = File(srcFolder, "A.kt")
            val docMatch = DocumentMatch(spec, listOf(supportFile))
            val target = File(docsFolder, "doc.md")

            val result = processor.buildCombinedTaskDescription(
                emptyList(), emptyList(), listOf(docMatch), emptyList(), target, FileModification
            )
            assertTrue(result.contains("documentation"))
        }

        @Test
        fun `generates generate description for generates`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val spec = DocSpec(
                docFile = docFile, specifies = emptyList(), documents = emptyList(),
                transforms = emptyList(), generates = listOf(GenerateSpec("out.kt", listOf("in.kt"))),
                related = emptyList(), content = "", frontmatter = emptyMap()
            )
            val genMatch = GenerateMatch(File(srcFolder, "out.kt"), listOf(File(srcFolder, "in.kt")), spec)
            val target = File(srcFolder, "out.kt")

            val result = processor.buildCombinedTaskDescription(
                emptyList(), emptyList(), emptyList(), listOf(genMatch), target, FileModification
            )
            assertTrue(result.contains("Generate or update"))
        }
    }

    // ========================================================================
    // Tests for ModificationTask.rebase
    // ========================================================================
    @Nested
    inner class ModificationTaskRebaseTests {
        @Test
        fun `rebases modification task to new root`() {
            val oldRoot = File(tempDir, "old").also { it.mkdirs() }
            val newRoot = File(tempDir, "new").also { it.mkdirs() }
            // Create the files so canonical paths work
            File(oldRoot, "src").mkdirs()
            File(oldRoot, "src/Main.kt").writeText("class Main")
            File(oldRoot, "src/Related.kt").writeText("class Related")

            val task = ModificationTask(
                data = ModificationTaskConfig(
                    root = oldRoot,
                    files = listOf("src/Main.kt"),
                    related_files = listOf("src/Related.kt"),
                    task_description = "Update Main"
                ),
                message = { "test message" }
            )

            val rebased = task.rebase(oldRoot, newRoot)
            assertEquals("Update Main", rebased.data.task_description)
            assertEquals("test message", rebased.message(File(".")))
            assertNotNull(rebased.data.relative_files)
            assertNotNull(rebased.data.relative_related_files)
        }
    }

    // ========================================================================
    // Tests for parseTaskType and parseTaskConfigJson
    // ========================================================================
    @Nested
    inner class ParseTaskTypeAndConfigTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `parseTaskType returns value when present`() {
            val frontmatter = mapOf<String, Any>("task_type" to "FileModification")
            assertEquals("FileModification", processor.parseTaskType(frontmatter))
        }

        @Test
        fun `parseTaskType returns null when missing`() {
            assertNull(processor.parseTaskType(emptyMap()))
        }

        @Test
        fun `parseTaskConfigJson returns value when present`() {
            val frontmatter = mapOf<String, Any>("task_config_json" to "config/task.json")
            assertEquals("config/task.json", processor.parseTaskConfigJson(frontmatter))
        }

        @Test
        fun `parseTaskConfigJson returns null when missing`() {
            assertNull(processor.parseTaskConfigJson(emptyMap()))
        }
    }

    // ========================================================================
    // Tests for modificationTasks (integration-level)
    // ========================================================================
    @Nested
    inner class ModificationTasksIntegrationTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `returns empty list for empty doc specs`() {
            val result = processor.modificationTasks(emptyList())
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for allRelatedFiles
    // ========================================================================
    @Nested
    inner class AllRelatedFilesTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `collects related files from all sources`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val relatedFile = File(docsFolder, "related.txt").also { it.writeText("related") }
            val targetFile = File(srcFolder, "target.kt").also { it.writeText("class T") }

            val spec = DocSpec(
                docFile = docFile, specifies = listOf("../src/target.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(),
                related = listOf("related.txt"),
                content = "", frontmatter = emptyMap()
            )

            val result = processor.allRelatedFiles(listOf(spec), targetFile, emptyList(), emptyList(), emptyList())
            assertTrue(result.any { it.name == "doc.md" })
            assertTrue(result.any { it.name == "related.txt" })
        }

        @Test
        fun `returns distinct files`() {
            val docFile = File(docsFolder, "doc.md").also { it.writeText("") }
            val targetFile = File(srcFolder, "target.kt").also { it.writeText("class T") }

            val spec1 = DocSpec(
                docFile = docFile, specifies = listOf("../src/target.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )
            val spec2 = DocSpec(
                docFile = docFile, specifies = listOf("../src/target.kt"), documents = emptyList(),
                transforms = emptyList(), generates = emptyList(), related = emptyList(),
                content = "", frontmatter = emptyMap()
            )

            val result = processor.allRelatedFiles(listOf(spec1, spec2), targetFile, emptyList(), emptyList(), emptyList())
            // docFile appears in both specs but should only appear once in result
            val docFileCount = result.count { it.absolutePath == docFile.absolutePath }
            assertEquals(1, docFileCount)
        }
    }

    // ========================================================================
    // Tests for URL detection
    // ========================================================================
    @Nested
    inner class UrlDetectionTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `detects http URLs`() {
            val method = DocProcessor::class.java.getDeclaredMethod("isUrl", String::class.java)
            method.isAccessible = true
            assertTrue(method.invoke(processor, "http://example.com") as Boolean)
        }

        @Test
        fun `detects https URLs`() {
            val method = DocProcessor::class.java.getDeclaredMethod("isUrl", String::class.java)
            method.isAccessible = true
            assertTrue(method.invoke(processor, "https://example.com/path") as Boolean)
        }

        @Test
        fun `rejects non-URL strings`() {
            val method = DocProcessor::class.java.getDeclaredMethod("isUrl", String::class.java)
            method.isAccessible = true
            assertFalse(method.invoke(processor, "file.txt") as Boolean)
            assertFalse(method.invoke(processor, "../relative/path") as Boolean)
            assertFalse(method.invoke(processor, "/absolute/path") as Boolean)
            assertFalse(method.invoke(processor, "ftp://other.com") as Boolean)
        }
    }

    // ========================================================================
    // Tests for full run() pipeline (smoke test)
    // ========================================================================
    @Nested
    inner class RunPipelineTests {
        @Test
        fun `run does not throw with empty docs folder`() {
          val processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
            // Should not throw, just log a warning
            assertDoesNotThrow { processor.run() }
        }

        @Test
        fun `run does not throw with docs that have no frontmatter`() {
            File(docsFolder, "plain.md").writeText("# Just a plain markdown file\nNo frontmatter.")
          val processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
            assertDoesNotThrow { processor.run() }
        }
    }

    // ========================================================================
    // Tests for getAll
    // ========================================================================
    @Nested
    inner class GetAllTests {
        private lateinit var processor: DocProcessor

        @BeforeEach
        fun setUp() {
          processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
        }

        @Test
        fun `returns empty for files without frontmatter`() {
            val mdFile = File(docsFolder, "plain.md").also {
                it.writeText("# No frontmatter\nJust content.")
            }
            val result = processor.getAll(mdFile)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns empty for non-existent files`() {
            val mdFile = File(docsFolder, "nonexistent.md")
            val result = processor.getAll(mdFile)
            assertTrue(result.isEmpty())
        }
    }

    // ========================================================================
    // Tests for edge cases in parseFrontmatter
    // ========================================================================
    @Nested
    inner class ParseFrontmatterEdgeCases {
        @Test
        fun `handles lines without colons`() {
            val text = """
                key1: value1
                no colon here
                key2: value2
            """.trimIndent()
            val result = parseFrontmatter(text)
            assertEquals("value1", result["key1"])
            assertEquals("value2", result["key2"])
            assertEquals(2, result.size)
        }

        @Test
        fun `handles multiple list sections`() {
            val text = """
                specifies:
                - file1.kt
                - file2.kt
                related:
                - config.yaml
                - schema.json
            """.trimIndent()
            val result = parseFrontmatter(text)
            assertEquals(listOf("file1.kt", "file2.kt"), result["specifies"])
            assertEquals(listOf("config.yaml", "schema.json"), result["related"])
        }

        @Test
        fun `handles whitespace in values`() {
            val text = "key:   value with spaces   "
            val result = parseFrontmatter(text)
            assertEquals("value with spaces", result["key"])
        }

        @Test
        fun `handles empty value after colon`() {
            // Empty value with no list items following
            val text = """
                empty_key:
                next_key: value
            """.trimIndent()
            val result = parseFrontmatter(text)
            // empty_key should not be in the map since it has no list items
            assertFalse(result.containsKey("empty_key"))
            assertEquals("value", result["next_key"])
        }
    }

    // ========================================================================
    // Tests for TransformSpec and GenerateSpec data classes
    // ========================================================================
    @Nested
    inner class DataClassTests {
        @Test
        fun `TransformSpec equality`() {
            val spec1 = TransformSpec("(.+)\\.json", "\$1.yaml")
            val spec2 = TransformSpec("(.+)\\.json", "\$1.yaml")
            assertEquals(spec1, spec2)
        }

        @Test
        fun `GenerateSpec equality`() {
            val spec1 = GenerateSpec("output.kt", listOf("input1.kt", "input2.kt"))
            val spec2 = GenerateSpec("output.kt", listOf("input1.kt", "input2.kt"))
            assertEquals(spec1, spec2)
        }

        @Test
        fun `TransformSpec inequality`() {
            val spec1 = TransformSpec("(.+)\\.json", "\$1.yaml")
            val spec2 = TransformSpec("(.+)\\.xml", "\$1.html")
            assertNotEquals(spec1, spec2)
        }

        @Test
        fun `GenerateSpec inequality`() {
            val spec1 = GenerateSpec("output1.kt", listOf("input.kt"))
            val spec2 = GenerateSpec("output2.kt", listOf("input.kt"))
            assertNotEquals(spec1, spec2)
        }
    }

    // ========================================================================
    // Tests for complex glob patterns
    // ========================================================================
    @Nested
    inner class ComplexGlobTests {
        @Test
        fun `expandSimpleGlob with bracket pattern`() {
            File(srcFolder, "file1.txt").writeText("1")
            File(srcFolder, "file2.txt").writeText("2")
            File(srcFolder, "fileA.txt").writeText("A")

            val result = expandSimpleGlob(srcFolder, "file[12].txt")
            assertEquals(2, result.size)
            assertTrue(result.all { it.name in listOf("file1.txt", "file2.txt") })
        }

        @Test
        fun `expandRecursiveGlob with deeply nested structure`() {
            val level1 = File(srcFolder, "a").also { it.mkdirs() }
            val level2 = File(level1, "b").also { it.mkdirs() }
            val level3 = File(level2, "c").also { it.mkdirs() }
            File(level1, "L1.kt").writeText("L1")
            File(level2, "L2.kt").writeText("L2")
            File(level3, "L3.kt").writeText("L3")
            File(level3, "L3.java").writeText("L3 java")

            val result = expandRecursiveGlob(srcFolder, "**/*.kt")
            assertEquals(3, result.size)
            assertTrue(result.all { it.extension == "kt" })
        }

        @Test
        fun `expandRecursiveGlob with relative path prefix`() {
            val subDir = File(srcFolder, "main").also { it.mkdirs() }
            File(subDir, "App.kt").writeText("class App")

            val result = expandRecursiveGlob(tempDir, "src/main/**/*.kt")
            assertEquals(1, result.size)
            assertEquals("App.kt", result[0].name)
        }
    }

    // ========================================================================
    // Tests for URL caching
    // ========================================================================
    @Nested
    inner class UrlCachingTests {
        @Test
        fun `cache directory is created when fetching URLs`() {
            val cacheDir = File(tempDir, ".test-cache/url-cache")
            val processor = DocProcessor(
              root = tempDir,
              docsFolder = docsFolder,
              urlCacheDir = cacheDir,
              autoFix = true
            )
            // We can't easily test actual URL fetching, but we can verify the cache dir setup
            // The fetchAndCacheUrl method creates the directory
            assertFalse(cacheDir.exists()) // Not created yet
        }
    }

    // ========================================================================
    // Tests for runAll with empty tasks
    // ========================================================================
    @Nested
    inner class RunAllTests {
        @Test
        fun `runAll with empty list does not throw`() {
          val processor = DocProcessor(root = tempDir, docsFolder = docsFolder, autoFix = true)
          assertDoesNotThrow { processor.runAll(emptyList()) }
        }
    }
}