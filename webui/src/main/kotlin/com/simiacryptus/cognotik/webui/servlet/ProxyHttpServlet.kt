package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.util.Timeout
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * A simple reverse proxy that supports the OpenAI API
 */
open class ProxyHttpServlet(
  private val targetUrl: String = "https://api.openai.com/"
) : HttpServlet() {

  open val asyncClient: CloseableHttpAsyncClient by lazy {
    HttpAsyncClientBuilder.create()
      .setRetryStrategy(DefaultHttpRequestRetryStrategy(0, Timeout.ofSeconds(1)))
      .setDefaultRequestConfig(
        RequestConfig.custom()
          .setConnectionRequestTimeout(Timeout.of(5, TimeUnit.MINUTES))
          .setResponseTimeout(Timeout.of(5, TimeUnit.MINUTES))
          .build()
      )
      .setConnectionManager(with(PoolingAsyncClientConnectionManager()) {
        defaultMaxPerRoute = 1000
        maxTotal = 1000
        this
      }).build().apply {
        start()
      }
  }

  override fun service(request: HttpServletRequest, response: HttpServletResponse) {
    val asyncContext = request.startAsync()
    asyncContext.timeout = 0
    val requestKey = request.getHeaders("Authorization").nextElement().removePrefix("Bearer ")
    val proxyKey = ApiKeyServlet.apiKeyRecords.find { it.apiKey.decrypt == requestKey }
    val path = (request.servletPath ?: "").removePrefix("/")
    val proxyRequest = getProxyRequest(request)
    if (null != proxyKey) proxyRequest.addHeader("Authorization", "Bearer " + proxyKey.mappedKey)
    val user = authenticate(request, response)
    val totalUsage =
      ApplicationServices.fileApplicationServices().usageManager.getUserUsageSummary(user!!).values.sumOf {
        it.cost ?: 0.0
      }
    if (totalUsage > (proxyKey?.budget ?: 0.0)) {
      response.status = 402
      response.contentType = "text/plain"
      response.writer.println("Budget exceeded")
      asyncContext.complete()
      return
    }
    asyncClient.execute(proxyRequest, object : FutureCallback<SimpleHttpResponse> {
      override fun completed(proxyResponse: SimpleHttpResponse?) {
        response.status = proxyResponse?.code ?: 500
        proxyResponse?.headers?.forEach { header ->
          response.addHeader(header.name, header.value)
        }
        val proxyResponseBody = proxyResponse?.bodyBytes ?: ByteArray(0)

        response.outputStream.write(
          onResponse(
            request,
            path,
            proxyResponse,
            proxyResponseBody,
            proxyKey,
            proxyRequest.body?.bodyBytes
          )
        )
        asyncContext.complete()
      }

      override fun failed(exception: Exception?) {
        response.status = 500
        response.contentType = "text/plain"
        response.writer.println(exception?.message)
        asyncContext.complete()
      }

      override fun cancelled() {
        response.status = 500
        response.contentType = "text/plain"
        response.writer.println("Cancelled")
        asyncContext.complete()
      }

    })
  }

  private fun getProxyRequest(req: HttpServletRequest): SimpleHttpRequest {
    val path = (req.servletPath ?: "").removePrefix("/")
    val url = URI(targetUrl).resolve(path).toString()
    val proxyRequest = SimpleHttpRequest(req.method, url)
    val headers = req.headerNames.toList().filter {
      when (it) {
        "Authorization" -> false

        "Connection" -> false
        "Host" -> false
        "Keep-Alive" -> false
        "Transfer-Encoding" -> false
        "Upgrade" -> false
        else -> true
      }
    }.associateWith { req.getHeaders(it).asSequence() }.toMutableMap()
    headers.forEach { (key, values) ->
      values.forEach { value -> proxyRequest.addHeader(key, value) }
    }
    val bytes = req.inputStream.readAllBytes()
    proxyRequest.setBody(onRequest(req, bytes), ContentType.create(req.contentType ?: "text/plain"))
    return proxyRequest
  }

  @OptIn(ExperimentalStdlibApi::class)
  open fun onResponse(
    req: HttpServletRequest,
    path: String,
    proxyResponse: SimpleHttpResponse?,
    bodyBytes: ByteArray,
    proxyKey: ApiKeyServlet.ApiKeyRecord?,
    requestBody: ByteArray?
  ): ByteArray {
    val body = JsonUtil.fromJson<Map<String, Any>>(
      String(GZIPInputStream(bodyBytes.inputStream()).readAllBytes()),
      typeOf<Map<String, Any>>().javaType
    )
    val parsedRequest = JsonUtil.fromJson<Map<String, Any>>(
      String(requestBody ?: ByteArray(0)),
      typeOf<Map<String, Any>>().javaType
    )
    when (path) {
      "moderations" -> {
        log.info(
          "Proxy $path\nRequest: ${
            JsonUtil.toJson(parsedRequest).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }\nResponse: ${
            JsonUtil.toJson(body).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }"
        )
      }

      "chat/completions" -> {
        log.info(
          "Proxy $path\nRequest: ${
            JsonUtil.toJson(parsedRequest).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }\nResponse: ${
            JsonUtil.toJson(body).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }"
        )
      }

      else -> {
        log.info(
          "Proxy $path\nRequest: ${
            JsonUtil.toJson(parsedRequest).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }\nResponse: ${
            JsonUtil.toJson(body).lineSequence()
              .map {
                when {
                  it.isBlank() -> {
                    when {
                      it.length < "  ".length -> "  "
                      else -> it
                    }
                  }

                  else -> "  " + it
                }
              }
              .joinToString("\n")
          }"
        )
      }
    }
    return bodyBytes
  }

  open fun onRequest(req: HttpServletRequest, bytes: ByteArray?): ByteArray? {
    return bytes
  }

  companion object {
    val log = LoggerFactory.getLogger(ProxyHttpServlet::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
      test()
    }

    fun test() {

      val server = Server(8080)
      val contextHandlerCollection = ContextHandlerCollection()
      val servletHandler = ServletHandler()
      servletHandler.server = server
      servletHandler.addServletWithMapping(ServletHolder(ProxyHttpServlet("http://localhost:8080")), "/proxy/*")
      servletHandler.addServletWithMapping(ServletHolder(object : HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
          resp.writer.println("Hello, world!")
        }
      }), "/test")
      contextHandlerCollection.addHandler(servletHandler)
      server.handler = contextHandlerCollection
      server.start()

      val connection = URL("http://localhost:8080/proxy/test").openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.doOutput = true
      connection.doInput = true
      connection.connect()
      val inputStream = connection.inputStream
      val outputStream = System.out
      inputStream.copyTo(outputStream)
      connection.disconnect()
      server.stop()
    }
  }

}