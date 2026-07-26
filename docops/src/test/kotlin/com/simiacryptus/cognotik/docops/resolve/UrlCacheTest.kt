package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.docops.FakeHttpFetcher
    import com.simiacryptus.cognotik.docops.MutableClock
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.io.TempDir
    import java.io.File
    import java.time.Duration

    class UrlCacheTest {

      @TempDir
      lateinit var tempDir: File

      private val url = "https://example.com/docs/spec.txt"
      private val dir: File get() = File(tempDir.canonicalFile, "url-cache")

      private fun ok(body: String, etag: String? = null) =
        HttpFetchResponse(200, body, "text/plain", etag = etag)

      @Test
      fun `first call fetches and stores content plus metadata`() {
        val fetcher = FakeHttpFetcher { ok("hello", etag = "e1") }
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, MutableClock())

        val file = cache.get(url)!!
        assertEquals("hello", file.readText())
        assertEquals(1, fetcher.calls)

        val meta = File(dir, file.name + ".meta").readText()
        assertTrue(meta.contains("url=$url"))
        assertTrue(meta.contains("content-type=text/plain"))
        assertTrue(meta.contains("etag=e1"))
      }

      @Test
      fun `second call within the ttl does not hit the network`() {
        val fetcher = FakeHttpFetcher { ok("hello") }
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, MutableClock())

        cache.get(url)
        val again = cache.get(url)!!
        assertEquals("hello", again.readText())
        assertEquals(1, fetcher.calls)
      }

      @Test
      fun `expired entries are refetched with conditional headers`() {
        val fetcher = FakeHttpFetcher { ok("hello", etag = "e1") }
        val clock = MutableClock()
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, clock)

        cache.get(url)
        clock.advance(Duration.ofHours(2))
        cache.get(url)

        assertEquals(2, fetcher.calls)
        assertEquals("e1", fetcher.requests.last().etag)
      }

      @Test
      fun `304 keeps the cached body and refreshes the timestamp`() {
        var call = 0
        val fetcher = FakeHttpFetcher {
          if (call++ == 0) ok("v1", etag = "e1") else HttpFetchResponse(304, "", "", etag = "e1")
        }
        val clock = MutableClock()
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, clock)

        cache.get(url)
        clock.advance(Duration.ofHours(2))
        assertEquals("v1", cache.get(url)!!.readText())
        assertEquals(2, fetcher.calls)

        // timestamp refreshed -> no third fetch
        cache.get(url)
        assertEquals(2, fetcher.calls)
      }

      @Test
      fun `a fresh 200 replaces the cached body`() {
        var call = 0
        val fetcher = FakeHttpFetcher { if (call++ == 0) ok("v1") else ok("v2") }
        val clock = MutableClock()
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, clock)

        assertEquals("v1", cache.get(url)!!.readText())
        clock.advance(Duration.ofHours(2))
        assertEquals("v2", cache.get(url)!!.readText())
      }

      @Test
      fun `non 2xx with no cache returns null`() {
        val fetcher = FakeHttpFetcher { HttpFetchResponse(404, "", "text/plain") }
        assertNull(UrlCache(dir, Duration.ofHours(1), fetcher, MutableClock()).get(url))
      }

      @Test
      fun `non 2xx with a stale cache serves the stale entry`() {
        var call = 0
        val fetcher = FakeHttpFetcher {
          if (call++ == 0) ok("v1") else HttpFetchResponse(500, "", "text/plain")
        }
        val clock = MutableClock()
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, clock)

        cache.get(url)
        clock.advance(Duration.ofHours(2))
        assertEquals("v1", cache.get(url)!!.readText())
      }

      @Test
      fun `transport failure with a stale cache serves the stale entry, otherwise null`() {
        var call = 0
        val fetcher = FakeHttpFetcher {
          if (call++ == 0) ok("v1") else throw RuntimeException("boom")
        }
        val clock = MutableClock()
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, clock)

        cache.get(url)
        clock.advance(Duration.ofHours(2))
        assertEquals("v1", cache.get(url)!!.readText())

        val broken = UrlCache(File(dir, "other"), Duration.ofHours(1),
          FakeHttpFetcher { throw RuntimeException("boom") }, clock)
        assertNull(broken.get(url))
      }

      @Test
      fun `distinct urls get distinct cache entries`() {
        val fetcher = FakeHttpFetcher { ok(it.url) }
        val cache = UrlCache(dir, Duration.ofHours(1), fetcher, MutableClock())
        val a = cache.get("https://example.com/a.txt")!!
        val b = cache.get("https://example.com/b.txt")!!
        assertNotEquals(a.name, b.name)
        assertEquals("https://example.com/a.txt", a.readText())
        assertEquals("https://example.com/b.txt", b.readText())
      }
    }