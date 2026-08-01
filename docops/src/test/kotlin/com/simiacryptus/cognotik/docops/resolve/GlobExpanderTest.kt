package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.docops.child
    import com.simiacryptus.cognotik.docops.childDir
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File

    class GlobExpanderTest {

      @TempDir
      lateinit var tempDir: File

      private val root: File get() = tempDir.canonicalFile

      private fun names(files: List<File>) = files.map { it.name }.sorted()

      @Test
      fun `isGlobPattern detects wildcards only`() {
        assertTrue(GlobExpander.isGlobPattern("*.kt"))
        assertTrue(GlobExpander.isGlobPattern("a?.kt"))
        assertTrue(GlobExpander.isGlobPattern("[ab].kt"))
        assertFalse(GlobExpander.isGlobPattern("src/Main.kt"))
      }

      @Test
      fun `literal paths are returned even when missing`() {
        val expanded = GlobExpander.expandPatternOrLiteral(root, "src/NotThereYet.kt")
        assertEquals(1, expanded.size)
        assertFalse(expanded[0].exists())
        assertEquals(File(root, "src/NotThereYet.kt").canonicalFile, expanded[0])
      }

      @Test
      fun `simple glob matches only files in the given directory`() {
        root.child("src/a.kt")
        root.child("src/b.kt")
        root.child("src/c.txt")
        root.child("src/nested/d.kt")
        root.childDir("src/dir.kt")

        assertEquals(listOf("a.kt", "b.kt"), names(GlobExpander.expandSimpleGlob(root, "src/*.kt")))
      }

      @Test
      fun `simple glob uses the base directory when the pattern has no parent`() {
        root.child("x.kt")
        root.child("y.md")
        assertEquals(listOf("x.kt"), names(GlobExpander.expandSimpleGlob(root, "*.kt")))
      }

      @Test
      fun `simple glob on a missing directory is empty`() {
        assertTrue(GlobExpander.expandSimpleGlob(root, "nope/*.kt").isEmpty())
      }

      @Test
      fun `recursive glob matches file names at any depth`() {
        root.child("src/a.kt")
        root.child("src/deep/deeper/b.kt")
        root.child("src/deep/c.txt")

        assertEquals(listOf("a.kt", "b.kt"), names(GlobExpander.expandRecursiveGlob(root, "src/**/*.kt")))
      }

      @Test
      fun `bare double star matches every file under the base`() {
        root.child("src/a.kt")
        root.child("src/deep/b.txt")
        assertEquals(listOf("a.kt", "b.txt"), names(GlobExpander.expandRecursiveGlob(root, "src/**")))
      }

      @Test
      fun `recursive glob on a missing base is empty`() {
        assertTrue(GlobExpander.expandRecursiveGlob(root, "nope/**/*.kt").isEmpty())
      }

      @Test
      fun `expandPatternOrLiteral dispatches on the pattern shape`() {
        root.child("src/a.kt")
        root.child("src/deep/b.kt")
        assertEquals(listOf("a.kt"), names(GlobExpander.expandPatternOrLiteral(root, "src/*.kt")))
        assertEquals(listOf("a.kt", "b.kt"), names(GlobExpander.expandPatternOrLiteral(root, "src/**/*.kt")))
      }

      @Test
      fun `recursive glob uses the injected lister`() {
        val a = root.child("src/a.kt")
        var calls = 0
        val expanded = GlobExpander.expandRecursiveGlob(root, "src/**/*.kt") { dir ->
          calls++
          dir.walkTopDown().filter { it.isFile }.toList()
        }
        assertEquals(1, calls)
        assertEquals(listOf(a.canonicalFile), expanded.map { it.canonicalFile })
      }
    }