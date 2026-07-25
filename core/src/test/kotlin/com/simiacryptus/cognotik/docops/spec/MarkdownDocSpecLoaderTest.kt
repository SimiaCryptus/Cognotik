package com.simiacryptus.cognotik.docops.spec

    import com.simiacryptus.cognotik.docops.child
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File

    class MarkdownDocSpecLoaderTest {

      @TempDir
      lateinit var tempDir: File

      private val root: File get() = tempDir.canonicalFile
      private val loader = MarkdownDocSpecLoader()

      @Test
      fun `missing file yields null`() {
        assertNull(loader.load(File(root, "nope.md")))
      }

      @Test
      fun `file without frontmatter yields null`() {
        assertNull(loader.load(root.child("plain.md", "# just prose")))
      }

      @Test
      fun `frontmatter without any target key yields null`() {
        assertNull(loader.load(root.child("meta.md", "---\ntask_type: CodeEdit\n---\nbody")))
      }

      @Test
      fun `folder alone is a valid target`() {
        val spec = loader.load(root.child("folder.md", "---\nfolder: sub\n---\nbody"))!!
        assertEquals("sub", spec.targetFolder)
        assertTrue(spec.hasTargets)
        assertTrue(spec.hasOnlyFolderTarget)
      }

      @Test
      fun `parses every supported key`() {
        val doc = root.child(
          "widget.md",
          """
          ---
          specifies:
            - [Widget](../src/Widget.kt)
            - "*.props"
          documents:
            - src/Other.kt
          transforms:
            - protos/(.*)\.proto -> generated/${'$'}1.kt
          related:
            - ./style-guide.md
            - https://example.com/spec.html
          task_type: CodeEdit
          task_config_json: ./widget-task.json
          update_mode: PatchToUpdate
          folder: sub
          prompt: Keep the public API stable.
          ---

          # Widget

          Prose body.
          """.trimIndent()
        )

        val spec = loader.load(doc)!!
        assertEquals(doc, spec.docFile)
        assertEquals(listOf("../src/Widget.kt", "\"*.props\""), spec.specifies)
        assertEquals(listOf("src/Other.kt"), spec.documents)
        assertEquals(1, spec.transforms.size)
        assertEquals("protos/(.*)\\.proto", spec.transforms[0].sourcePattern)
        assertEquals("generated/\$1.kt", spec.transforms[0].destinationPattern)
        assertEquals(listOf("./style-guide.md", "https://example.com/spec.html"), spec.related)
        assertEquals("CodeEdit", spec.taskType)
        assertEquals("./widget-task.json", spec.taskConfigJson)
        assertEquals("PatchToUpdate", spec.updateMode)
        assertEquals("sub", spec.targetFolder)
        assertEquals("Keep the public API stable.", spec.prompt)
        assertTrue(spec.content.startsWith("# Widget"))
        assertTrue(spec.content.contains("Prose body."))
        assertEquals(root, spec.baseDir)
        assertFalse(spec.hasOnlyFolderTarget)
      }

      @Test
      fun `template variables parameterize both frontmatter and body`() {
        val doc = root.child(
          "tpl.md",
          """
          ---
          vars:
            - MODULE: core
            - LANG=kotlin
          specifies:
            - {{ MODULE }}/Main.kt
          ---
          Language is {{ LANG }}; unknown {{ NOPE }} survives.
          """.trimIndent()
        )

        val spec = loader.load(doc)!!
        assertEquals(listOf("core/Main.kt"), spec.specifies)
        assertEquals("Language is kotlin; unknown {{ NOPE }} survives.", spec.content)
        // the template-var key itself is stripped from the effective frontmatter
        assertFalse(spec.frontmatter.containsKey("vars"))
      }

      @Test
      fun `processor wide overrides beat declared defaults`() {
        val doc = root.child(
          "tpl.md",
          "---\nvars:\n  - MODULE: core\nspecifies:\n  - {{ MODULE }}/Main.kt\n---\nin {{ MODULE }}"
        )
        val spec = MarkdownDocSpecLoader(mapOf("MODULE" to "edge")).load(doc)!!
        assertEquals(listOf("edge/Main.kt"), spec.specifies)
        assertEquals("in edge", spec.content)
      }

      @Test
      fun `loadAll skips non doc files`() {
        val good = root.child("a.md", "---\nspecifies:\n  - a.kt\n---\n")
        val bad = root.child("b.md", "# nope")
        val specs = loader.loadAll(listOf(good, bad, File(root, "missing.md")))
        assertEquals(1, specs.size)
        assertEquals(good, specs[0].docFile)
      }
    }