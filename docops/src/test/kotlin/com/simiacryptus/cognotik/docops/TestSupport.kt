package com.simiacryptus.cognotik.docops
     import com.simiacryptus.cognotik.docops.model.DocSpec
     import com.simiacryptus.cognotik.docops.model.TransformSpec

    import com.simiacryptus.cognotik.docops.resolve.HttpFetchRequest
    import com.simiacryptus.cognotik.docops.resolve.HttpFetchResponse
    import com.simiacryptus.cognotik.docops.resolve.HttpFetcher
    import java.io.File
    import java.time.Clock
    import java.time.Duration
    import java.time.Instant
    import java.time.ZoneId

    /**
     * Creates (and returns) a file at [relativePath] under this directory, making any
     * intermediate directories as needed. Used by the docops resolve tests to lay out fixtures.
     */
    fun File.child(relativePath: String, content: String = ""): File {
      val target = File(this, relativePath)
      target.parentFile?.mkdirs()
      target.writeText(content)
      return target
    }

    /** Creates (and returns) a directory at [relativePath] under this directory. */
    fun File.childDir(relativePath: String): File {
      val target = File(this, relativePath)
      target.mkdirs()
      return target
    }
     /**
      * Builds a minimal [DocSpec] anchored at [docFile] for tests. The doc file is canonicalized so
      * that path comparisons in the resolve tests (which also canonicalize) line up on all platforms.
      *
      * NOTE: if [DocSpec] gains/renames constructor parameters, this single helper is the only place
      * the tests need to be updated.
      */
     fun docSpec(
       docFile: File,
       transforms: List<TransformSpec> = emptyList(),
     ): DocSpec = DocSpec(
       docFile = docFile.canonicalFile,
       transforms = transforms,
     )


    /**
     * A [Clock] whose "now" can be moved forward by the test, so TTL expiry can be exercised
     * without sleeping.
     */
    class MutableClock(
      private var now: Instant = Instant.parse("2024-01-01T00:00:00Z"),
      private val zone: ZoneId = ZoneId.of("UTC"),
    ) : Clock() {

      override fun getZone(): ZoneId = zone

      override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

      override fun instant(): Instant = now

      override fun millis(): Long = now.toEpochMilli()

      fun advance(duration: Duration): MutableClock {
        now = now.plus(duration)
        return this
      }

      fun setTo(instant: Instant): MutableClock {
        now = instant
        return this
      }
    }

    /**
     * An [HttpFetcher] that never touches the network. Every request is recorded so tests can
     * assert on call counts and on conditional headers (etag / last-modified).
     */
    class FakeHttpFetcher(
      private val handler: (HttpFetchRequest) -> HttpFetchResponse,
    ) : HttpFetcher {

      private val recorded = mutableListOf<HttpFetchRequest>()

      val requests: List<HttpFetchRequest> get() = recorded.toList()
      val calls: Int get() = recorded.size

      override fun fetch(request: HttpFetchRequest): HttpFetchResponse {
        recorded.add(request)
        return handler(request)
      }
    }