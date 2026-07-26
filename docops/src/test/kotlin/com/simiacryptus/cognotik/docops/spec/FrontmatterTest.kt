package com.simiacryptus.cognotik.docops.spec

    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test

    class FrontmatterTest {

      @Test
      fun `typed scalar accessors`() {
        val fm = Frontmatter(
          mapOf(
            "task_type" to "CodeEdit",
            "task_config_json" to "./cfg.json",
            "update_mode" to "ForceUpdate",
            "folder" to "sub",
            "prompt" to "Keep the API stable.",
          )
        )
        assertEquals("CodeEdit", fm.taskType)
        assertEquals("./cfg.json", fm.taskConfigJson)
        assertEquals("ForceUpdate", fm.updateMode)
        assertEquals("sub", fm.folder)
        assertEquals("Keep the API stable.", fm.prompt)
      }

      @Test
      fun `missing scalars are null`() {
        val fm = Frontmatter(emptyMap())
        assertNull(fm.taskType)
        assertNull(fm.folder)
        assertTrue(fm.specifies.isEmpty())
        assertTrue(fm.related.isEmpty())
        assertTrue(fm.transforms.isEmpty())
        assertTrue(fm.generates.isEmpty())
      }

      @Test
      fun `path lists accept a scalar, a list and markdown links`() {
        assertEquals(listOf("a.kt"), Frontmatter(mapOf("specifies" to "[a](a.kt)")).specifies)
        assertEquals(
          listOf("a.kt", "b.kt"),
          Frontmatter(mapOf("documents" to listOf("[a](a.kt)", "b.kt"))).documents
        )
      }

      @Test
      fun `transforms are split on the arrow and unwrapped`() {
        val fm = Frontmatter(
          mapOf("transforms" to listOf("[src](src/(.*)\\.proto) -> [gen](gen/\$1.kt)", "bad rule"))
        )
        val transforms = fm.transforms
        assertEquals(1, transforms.size)
        assertEquals("[src](src/(.*)\\.proto)", transforms[0].sourcePattern)
        assertEquals("gen/\$1.kt", transforms[0].destinationPattern)
      }

      @Test
      fun `transforms accept a single string value`() {
        val fm = Frontmatter(mapOf("transforms" to "a -> b"))
        assertEquals(1, fm.transforms.size)
        assertEquals("a", fm.transforms[0].sourcePattern)
        assertEquals("b", fm.transforms[0].destinationPattern)
      }

      @Test
      fun `generates accepts one map`() {
        val fm = Frontmatter(mapOf("generates" to mapOf("output" to "out.md", "inputs" to "a.kt")))
        assertEquals(1, fm.generates.size)
        assertEquals("out.md", fm.generates[0].output)
        assertEquals(listOf("a.kt"), fm.generates[0].inputs)
      }

      @Test
      fun `generates accepts a list of maps and drops invalid entries`() {
        val fm = Frontmatter(
          mapOf(
            "generates" to listOf(
              mapOf("output" to "out.md", "inputs" to listOf("a.kt", "b.kt")),
              mapOf("output" to "no-inputs.md"),
              mapOf("inputs" to listOf("a.kt")),
              "not a map",
            )
          )
        )
        assertEquals(1, fm.generates.size)
        assertEquals(listOf("a.kt", "b.kt"), fm.generates[0].inputs)
      }
    }