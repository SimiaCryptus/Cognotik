package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.docops.child
    import com.simiacryptus.cognotik.docops.docSpec
    import com.simiacryptus.cognotik.docops.model.TransformSpec
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File
    import java.util.regex.Pattern

    class TransformExpanderTest {

      @TempDir
      lateinit var tempDir: File

      private val root: File get() = tempDir.canonicalFile

      private fun matcherFor(regex: String, input: String) =
        Pattern.compile(regex).matcher(input).also { assertTrue(it.matches(), "'$input' should match /$regex/") }

      @Test
      fun `compile returns null for an invalid pattern`() {
        assertNull(TransformExpander.compile("(unclosed"))
        assertNotNull(TransformExpander.compile("(.*)\\.kt"))
      }

      @Test
      fun `backreferences substitute capture groups`() {
        val m = matcherFor("protos/(.*)\\.proto", "protos/widget.proto")
        assertEquals("generated/widget.kt", TransformExpander.applyBackreferences("generated/\$1.kt", m))
      }

      @Test
      fun `backreference arithmetic increments numeric groups`() {
        val m = matcherFor("v(\\d+)/api\\.md", "v3/api.md")
        assertEquals("v4/api.md", TransformExpander.applyBackreferences("v\$1+1/api.md", m))
        assertEquals("v1/api.md", TransformExpander.applyBackreferences("v\$1-2/api.md", m))
      }

      @Test
      fun `arithmetic on a non numeric group is appended literally`() {
        val m = matcherFor("(\\w+)\\.proto", "foo.proto")
        assertEquals("foo+1.kt", TransformExpander.applyBackreferences("\$1+1.kt", m))
      }

      @Test
      fun `out of range groups are left alone`() {
        val m = matcherFor("(\\w+)\\.proto", "foo.proto")
        assertEquals("foo-\$2.kt", TransformExpander.applyBackreferences("\$1-\$2.kt", m))
      }

      @Test
      fun `multiple backreferences are all replaced`() {
        val m = matcherFor("(\\w+)/(\\w+)\\.proto", "pkg/foo.proto")
        assertEquals("pkg/foo/foo.kt", TransformExpander.applyBackreferences("\$1/\$2/\$2.kt", m))
      }

      @Test
      fun `relativize is relative to the doc directory and uses forward slashes`() {
        val doc = root.child("docs/spec.md")
        val spec = docSpec(doc)
        val candidate = root.child("docs/protos/a.proto")
        assertEquals("protos/a.proto", TransformExpander.relativize(spec, candidate))
      }

      @Test
      fun `expand walks the root and maps sources to destinations`() {
        val doc = root.child("spec.md")
        val a = root.child("protos/a.proto")
        root.child("protos/notes.txt")
         val transform = TransformSpec("protos/(.*)\\.proto", "gen/\$1.kt")
         val spec = docSpec(doc, transforms = listOf(transform))

         val matches = TransformExpander.expand(root, transform, spec)
        assertEquals(1, matches.size)
        assertEquals(a.canonicalFile, matches[0].sourceFile)
        assertEquals(File(root, "gen/a.kt").canonicalFile, matches[0].destinationFile)
        assertSame(spec, matches[0].spec)
      }

      @Test
      fun `expand skips invalid regexes`() {
         val transform = TransformSpec("(unclosed", "x")
         val spec = docSpec(root.child("spec.md"), transforms = listOf(transform))
         assertTrue(TransformExpander.expand(root, transform, spec).isEmpty())
      }

      @Test
      fun `destinationForHypothetical works on files that do not exist`() {
        val spec = docSpec(root.child("spec.md"))
        val transform = TransformSpec("(.*)\\.kt", "\$1.docs.md")
        val hypothetical = File(root, "gen/a.kt")
        assertFalse(hypothetical.exists())
        assertEquals(
          File(root, "gen/a.docs.md").canonicalFile,
          TransformExpander.destinationForHypothetical(spec, transform, hypothetical)
        )
      }

      @Test
      fun `destinationForHypothetical returns null when the source does not match`() {
        val spec = docSpec(root.child("spec.md"))
        val transform = TransformSpec("(.*)\\.proto", "\$1.kt")
        assertNull(TransformExpander.destinationForHypothetical(spec, transform, File(root, "a.kt")))
      }
    }