package com.simiacryptus.cognotik.docops.resolve

    import java.net.URI
    import java.net.http.HttpClient
    import java.net.http.HttpRequest
    import java.net.http.HttpResponse
    import java.nio.charset.StandardCharsets
    import java.time.Duration

    data class HttpFetchRequest(
      val url: String,
      val etag: String? = null,
      val lastModified: String? = null,
    )

    data class HttpFetchResponse(
      val statusCode: Int,
      val body: String,
      val contentType: String,
      val etag: String? = null,
      val lastModified: String? = null,
    ) {
      val isSuccess: Boolean get() = statusCode in 200..299
      val isNotModified: Boolean get() = statusCode == 304
    }

    /** The only place `docops` touches the network. Tests substitute a fake. */
    fun interface HttpFetcher {
      fun fetch(request: HttpFetchRequest): HttpFetchResponse
    }

    class JdkHttpFetcher(
      private val connectTimeout: Duration = Duration.ofSeconds(30),
      private val requestTimeout: Duration = Duration.ofSeconds(60),
      private val userAgent: String = "Mozilla/5.0 (compatible; CognotikBot/1.0)",
    ) : HttpFetcher {

      private val client: HttpClient by lazy {
        HttpClient.newBuilder()
          .connectTimeout(connectTimeout)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build()
      }

      override fun fetch(request: HttpFetchRequest): HttpFetchResponse {
        val builder = HttpRequest.newBuilder()
          .uri(URI.create(request.url))
          .timeout(requestTimeout)
          .header("User-Agent", userAgent)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
          .header("Accept-Language", "en-US,en;q=0.5")
          .header("Accept-Charset", "utf-8, iso-8859-1;q=0.5")
        request.etag?.let { builder.header("If-None-Match", it) }
        request.lastModified?.let { builder.header("If-Modified-Since", it) }

        val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return HttpFetchResponse(
          statusCode = response.statusCode(),
          body = response.body() ?: "",
          contentType = response.headers().firstValue("Content-Type").orElse("") ?: "",
          etag = response.headers().firstValue("ETag").orElse(null),
          lastModified = response.headers().firstValue("Last-Modified").orElse(null),
        )
      }
    }