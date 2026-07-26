package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.docops.FakeHttpFetcher
    import com.simiacryptus.cognotik.docops.MutableClock
    import com.simiacryptus.cognotik.docops.child
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File
    import java.time.Duration

    class ResourceResolverTest {

      @TempDir
      lateinit var tempDir: File

      private val root: File get() = tempDir.canonicalFile

      @Test
      fun `isUrl only accepts http schemes`() {
        assertTrue(Urls.isUrl("http://a"))
        assertTrue(Urls.isUrl("https://a"))
        assertFalse(Urls.isUrl("ftp://a"))
        assertFalse(Urls.isUrl("./a.md"))
      }

      @Test
      fun `file resolver returns missing plain paths anyway`() {
        val resolver = FileResourceResolver()
        assertTrue(resolver.handles("./a.md"))
        assertFalse(resolver.handles("https://a"))

        val resolved = resolver.resolve(root, "not-created-yet.md")
        assertEquals(1, resolved.size)
        assertFalse(resolved[0].exists())
      }

      @Test
      fun `file resolver expands globs`() {
        root.child("a.kt")
        root.child("b.kt")
        root.child("c.md")
        val resolved = FileResourceResolver().resolve(root, "*.kt")
        assertEquals(listOf("a.kt", "b.kt"), resolved.map { it.name }.sorted())
      }

      @Test
      fun `file resolver returns nothing for an unmatched glob`() {
        assertTrue(FileResourceResolver().resolve(root, "*.kt").isEmpty())
      }

      @Test
      fun `url resolver delegates to the cache`() {
        val fetcher = FakeHttpFetcher { HttpFetchResponse(200, "body", "text/plain") }
        val cache = UrlCache(File(root, "cache"), Duration.ofHours(1), fetcher, MutableClock())
        val resolved = UrlResourceResolver(cache).resolve(root, "https://example.com/x.txt")
        assertEquals(1, resolved.size)
        assertTrue(resolved[0].isAbsolute)
        assertEquals("body", resolved[0].readText())
      }

      @Test
      fun `url resolver yields nothing on failure`() {
        val fetcher = FakeHttpFetcher { HttpFetchResponse(500, "", "text/plain") }
        val cache = UrlCache(File(root, "cache"), Duration.ofHours(1), fetcher, MutableClock())
        assertTrue(UrlResourceResolver(cache).resolve(root, "https://example.com/x.txt").isEmpty())
      }

      @Test
      fun `composite dispatches to the first handler`() {
        val fetcher = FakeHttpFetcher { HttpFetchResponse(200, "remote", "text/plain") }
        val cache = UrlCache(File(root, "cache"), Duration.ofHours(1), fetcher, MutableClock())
        val composite = CompositeResourceResolver(listOf(UrlResourceResolver(cache), FileResourceResolver()))

        val local = root.child("local.md", "local")
        assertEquals(listOf(local.canonicalFile), composite.resolve(root, "local.md").map { it.canonicalFile })
        assertEquals("remote", composite.resolve(root, "https://example.com/x.txt").single().readText())
        assertTrue(composite.handles("local.md"))
      }

      @Test
      fun `composite returns nothing when no delegate handles the path`() {
        val composite = CompositeResourceResolver(listOf(UrlResourceResolver(
          UrlCache(File(root, "cache"), Duration.ofHours(1), FakeHttpFetcher { HttpFetchResponse(200, "", "") })
        )))
        assertFalse(composite.handles("local.md"))
        assertTrue(composite.resolve(root, "local.md").isEmpty())
      }
    }