package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.input.getReader
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.plan.tools.online.FetchConfig.isSeleniumEnabled
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.HtmlSimplifier
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Selenium2S3
import com.simiacryptus.cognotik.util.Selenium2S3.Companion.chromeDriver
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService

interface FetchStrategy : EnabledStrategy {
    fun fetch(url: String, webSearchDir: File, index: Int, pool: ExecutorService, planSettings: PlanSettings): String
}

object FetchConfig {
    var isSeleniumEnabled: Boolean = false
}

@Suppress("unused")
enum class FetchMethod {
    HttpClient {
        override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
            override fun fetch(
                url: String,
                webSearchDir: File,
                index: Int,
                pool: ExecutorService,
                planSettings: PlanSettings
            ): String {
                log.info("HttpClient fetching URL: $url (index: $index)")
                // Create SSL context that accepts all certificates
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }), java.security.SecureRandom())
                
                val client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .sslContext(sslContext)
                    .build()
                val request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    //.header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Charset", "utf-8, iso-8859-1;q=0.5")
                    .GET()
                    .build()
                log.debug("Sending HTTP request to: $url")
                val response = try {
                    client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8))
                } catch (e: Exception) {
                    log.error("HTTP request failed for URL: $url", e)
                    throw RuntimeException("Failed to fetch URL: $url - ${e.message}", e)
                }

                val contentType = response.headers().firstValue("Content-Type").orElse("")
                log.debug("Received response from $url with status: ${response.statusCode()}, Content-Type: $contentType")
                if (response.statusCode() !in 200..299) {
                    throw RuntimeException("HTTP ${response.statusCode()} error for URL: $url")
                }

                val content = when {
                    // Handle HTML content
                    contentType.startsWith("text/html") || contentType.isEmpty() -> {
                        val body = response.body()
                        if (body.isNullOrBlank()) {
                            log.warn("Received empty body from URL: $url")
                            return ""
                        }

                        log.debug("Saving raw HTML content for URL: $url")
                        task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
                        log.debug("Simplifying HTML content for URL: $url")
                        var simplified = HtmlSimplifier.scrubHtml(
                            str = body,
                            baseUrl = url,
                            includeCssData = false,
                            simplifyStructure = true,
                            keepObjectIds = false,
                            preserveWhitespace = false,
                            keepScriptElements = false,
                            keepInteractiveElements = false,
                            keepMediaElements = false,
                            keepEventHandlers = false
                        )

                        // Check for reasonable content length
                        if (simplified.length > 5_000_000) { // 5MB limit
                            log.info("Content too large (${simplified.length} chars) for URL: $url, truncating")
                            simplified = simplified.substring(0, 1_000_000)
                        }

                        log.debug("Saving simplified content for URL: $url")
                        task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, simplified)
                        processHtmlContent(body, url, webSearchDir, task)
                    }

                    // Handle document formats (PDF, DOCX, etc.)
                    contentType.startsWith("application/pdf") ||
                            contentType.startsWith("application/msword") ||
                            contentType.startsWith("application/vnd.openxmlformats-officedocument") ||
                            contentType.startsWith("application/vnd.ms-") ||
                            contentType.startsWith("application/vnd.oasis.opendocument") -> {
                        log.info("Detected document content type: $contentType for URL: $url")
                        val binaryResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                        val bytes = binaryResponse.body()
                        // Check file size limit (10MB)
                        if (bytes.size > 10_000_000) {
                            log.warn("Document too large (${bytes.size} bytes) for URL: $url, skipping")
                            return "Document too large to process (${bytes.size} bytes)"
                        }


                        val extension = getExtensionFromContentType(contentType, url)

                        // Save the original document file
                        val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
                        val documentsDir = webSearchDir.resolve("documents")
                        documentsDir.mkdirs()
                        val documentFile = File(documentsDir, "${urlSafe}_${index}.$extension")
                        FileOutputStream(documentFile).use { it.write(bytes) }
                        log.debug("Saved original document to: ${documentFile.absolutePath}")

                        // Also create a temporary file for text extraction
                        val tempFile = File.createTempFile("webcrawl_", ".$extension")
                        tempFile.deleteOnExit()

                        FileOutputStream(tempFile).use { it.write(bytes) }
                        log.debug("Saved document to temporary file: ${tempFile.absolutePath}")

                        // Use DocumentReader to extract text
                        val extractedText = try {
                            tempFile.getReader().use { reader ->
                                reader.getText()
                            }
                        } catch (e: Exception) {
                            log.error("Failed to extract text from document at $url", e)
                            ""
                        } finally {
                            tempFile.delete()
                        }

                        if (extractedText.isNotBlank()) {
                            log.debug("Extracted ${extractedText.length} characters from document")
                            task.saveRawContent(webSearchDir.resolve("extracted_text"), url, extractedText)
                        }
                        extractedText
                    }

                    // Handle plain text
                    contentType.startsWith("text/") -> {
                        val body = response.body()
                        log.debug("Processing plain text content for URL: $url")
                        task.saveRawContent(webSearchDir.resolve("text_pages"), url, body)
                        body
                    }

                    // Skip other content types
                    else -> {
                        log.warn("Skipping unsupported content type: $contentType for URL: $url")
                        ""
                    }
                }

                task.urlContentCache[url] = content
                log.info("Successfully processed URL: $url, content length: ${content.length}")
                return content
            }

            private fun detectCharset(bytes: ByteArray, contentType: String): java.nio.charset.Charset {
                // First try to extract charset from Content-Type header
                val charsetRegex = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
                val charsetMatch = charsetRegex.find(contentType)
                if (charsetMatch != null) {
                    try {
                        return java.nio.charset.Charset.forName(charsetMatch.groupValues[1])
                    } catch (e: Exception) {
                        log.warn("Invalid charset in Content-Type: ${charsetMatch.groupValues[1]}")
                    }
                }
                // Try to detect charset from HTML meta tags
                val htmlStart = String(bytes.take(2048).toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)
                val metaCharsetRegex =
                    Regex("<meta[^>]+charset[\\s]*=[\\s]*[\"']?([^\"'\\s>]+)", RegexOption.IGNORE_CASE)
                val metaMatch = metaCharsetRegex.find(htmlStart)
                if (metaMatch != null) {
                    try {
                        return java.nio.charset.Charset.forName(metaMatch.groupValues[1])
                    } catch (e: Exception) {
                        log.warn("Invalid charset in meta tag: ${metaMatch.groupValues[1]}")
                    }
                }
                // Fallback to UTF-8
                return java.nio.charset.StandardCharsets.UTF_8
            }

            private fun processHtmlContent(
                body: String,
                url: String,
                webSearchDir: File,
                task: CrawlerAgentTask
            ): String {
                log.debug("Saving raw HTML content for URL: $url")
                task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
                log.debug("Simplifying HTML content for URL: $url")
                val simplified = try {
                    HtmlSimplifier.scrubHtml(
                        str = body,
                        baseUrl = url,
                        includeCssData = false,
                        simplifyStructure = true,
                        keepObjectIds = false,
                        preserveWhitespace = false,
                        keepScriptElements = false,
                        keepInteractiveElements = false,
                        keepMediaElements = false,
                        keepEventHandlers = false
                    )
                } catch (e: Exception) {
                    log.error("HTML simplification failed for URL: $url, using raw content", e)
                    // Fallback to basic text extraction if HTML simplification fails
                    body.replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                log.debug("Saving simplified content for URL: $url")
                task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, simplified)
                return simplified
            }

            private fun getExtensionFromContentType(contentType: String, url: String): String {
                return when {
                    contentType.contains("pdf") -> "pdf"
                    contentType.contains("msword") -> "doc"
                    contentType.contains("wordprocessingml") -> "docx"
                    contentType.contains("spreadsheetml") -> "xlsx"
                    contentType.contains("ms-excel") -> "xls"
                    contentType.contains("presentationml") -> "pptx"
                    contentType.contains("ms-powerpoint") -> "ppt"
                    contentType.contains("opendocument.text") -> "odt"
                    contentType.contains("rtf") -> "rtf"
                    else -> {
                        // Try to extract from URL
                        val urlPath = url.substringBefore("?").substringAfterLast("/")
                        if (urlPath.contains(".")) {
                            urlPath.substringAfterLast(".")
                        } else {
                            "tmp"
                        }
                    }
                }
            }
        }
    },
    Selenium {
        override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
            override fun fetch(
                url: String,
                webSearchDir: File,
                index: Int,
                pool: ExecutorService,
                planSettings: PlanSettings
            ): String {
                log.info("Selenium fetching URL: $url (index: $index)")
                return try {
                    if (task.selenium == null) {
                        log.debug("Initializing Selenium driver")
                        task.selenium = Selenium2S3(
                            pool = pool, cookies = null, driver = chromeDriver()
                        )
                    }
                    try {
                        log.debug("Navigating to URL with Selenium: $url")
                        task.selenium?.navigate(url)
                        val pageSource = task.selenium?.getPageSource() ?: ""
                        log.debug("Retrieved page source with Selenium, length: ${pageSource.length}")
                        pageSource
                    } finally {
                        task.selenium?.let {
                            log.debug("Quitting Selenium driver")
                            it.quit()
                            task.selenium = null
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Selenium fetch failed for URL: $url, falling back to HttpClient. Error: ${e.message}", e)
                    isSeleniumEnabled = false
                    HttpClient.createStrategy(task).fetch(url, webSearchDir, index, pool, planSettings)
                }
            }

            override fun isEnabled(): Boolean {
                return isSeleniumEnabled;
            }
        }
    };

    abstract fun createStrategy(task: CrawlerAgentTask): FetchStrategy

    companion object {
        val log = LoggerFactory.getLogger(FetchMethod::class.java)
    }
}