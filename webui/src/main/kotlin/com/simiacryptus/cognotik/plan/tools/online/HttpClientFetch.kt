package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.input.getReader
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.util.HtmlSimplifier
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class HttpClientFetch : FetchMethodFactory {
    override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
        override fun fetch(
            url: String,
            webSearchDir: File,
            index: Int,
            pool: ExecutorService,
            orchestrationConfig: OrchestrationConfig
        ): String {
            FetchMethod.Companion.log.info("HttpClient fetching URL: $url (index: $index)")
            // Create SSL context that accepts all certificates
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), SecureRandom())

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(sslContext)
                .build()
val request = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (compatible; CognotikBot/1.0; +https://github.com/SimiaCryptus/cognotik)"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                //.header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Charset", "utf-8, iso-8859-1;q=0.5")
                .GET()
                .build()
            FetchMethod.Companion.log.debug("Sending HTTP request to: $url")
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                FetchMethod.Companion.log.error("HTTP request failed for URL: $url", e)
                throw RuntimeException("Failed to fetch URL: $url - ${e.message}", e)
            }

            val contentType = response.headers().firstValue("Content-Type").orElse("")
            FetchMethod.Companion.log.debug("Received response from $url with status: ${response.statusCode()}, Content-Type: $contentType")
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()} error for URL: $url")
            }

            val content = when {
                // Handle HTML content
                contentType.startsWith("text/html") || contentType.isEmpty() -> {
                    val body = response.body()
                    if (body.isNullOrBlank()) {
                        FetchMethod.Companion.log.warn("Received empty body from URL: $url")
                        return ""
                    }

                    FetchMethod.Companion.log.debug("Saving raw HTML content for URL: $url")
                    task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
                    FetchMethod.Companion.log.debug("Simplifying HTML content for URL: $url")
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
                        FetchMethod.Companion.log.info("Content too large (${simplified.length} chars) for URL: $url, truncating")
                        simplified = simplified.substring(0, 1_000_000)
                    }

                    FetchMethod.Companion.log.debug("Saving simplified content for URL: $url")
                    task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, simplified)
                    processHtmlContent(body, url, webSearchDir, task)
                }

                // Handle document formats (PDF, DOCX, etc.)
                contentType.startsWith("application/pdf") ||
                        contentType.startsWith("application/msword") ||
                        contentType.startsWith("application/vnd.openxmlformats-officedocument") ||
                        contentType.startsWith("application/vnd.ms-") ||
                        contentType.startsWith("application/vnd.oasis.opendocument") -> {
                    FetchMethod.Companion.log.info("Detected document content type: $contentType for URL: $url")
                    val binaryResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    val bytes = binaryResponse.body()
                    // Check file size limit (10MB)
                    if (bytes.size > 10_000_000) {
                        FetchMethod.Companion.log.warn("Document too large (${bytes.size} bytes) for URL: $url, skipping")
                        return "Document too large to process (${bytes.size} bytes)"
                    }


                    val extension = getExtensionFromContentType(contentType, url)

                    // Save the original document file
                    val urlSafe = url.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)
                    val documentsDir = webSearchDir.resolve("documents")
                    documentsDir.mkdirs()
                    val documentFile = File(documentsDir, "${urlSafe}_${index}.$extension")
                    FileOutputStream(documentFile).use { it.write(bytes) }
                    FetchMethod.Companion.log.debug("Saved original document to: ${documentFile.absolutePath}")

                    // Also create a temporary file for text extraction
                    val tempFile = File.createTempFile("webcrawl_", ".$extension")
                    tempFile.deleteOnExit()

                    FileOutputStream(tempFile).use { it.write(bytes) }
                    FetchMethod.Companion.log.debug("Saved document to temporary file: ${tempFile.absolutePath}")

                    // Use DocumentReader to extract text
                    val extractedText = try {
                        tempFile.getReader().use { reader ->
                            reader.getText()
                        }
                    } catch (e: Exception) {
                        FetchMethod.Companion.log.error("Failed to extract text from document at $url", e)
                        ""
                    } finally {
                        tempFile.delete()
                    }

                    if (extractedText.isNotBlank()) {
                        FetchMethod.Companion.log.debug("Extracted ${extractedText.length} characters from document")
                        task.saveRawContent(webSearchDir.resolve("extracted_text"), url, extractedText)
                    }
                    extractedText
                }

                // Handle plain text
                contentType.startsWith("text/") -> {
                    val body = response.body()
                    FetchMethod.Companion.log.debug("Processing plain text content for URL: $url")
                    task.saveRawContent(webSearchDir.resolve("text_pages"), url, body)
                    body
                }

                // Skip other content types
                else -> {
                    FetchMethod.Companion.log.warn("Skipping unsupported content type: $contentType for URL: $url")
                    ""
                }
            }

            task.urlContentCache[url] = content
            FetchMethod.Companion.log.info("Successfully processed URL: $url, content length: ${content.length}")
            return content
        }

        private fun processHtmlContent(
            body: String,
            url: String,
            webSearchDir: File,
            task: CrawlerAgentTask
        ): String {
            FetchMethod.Companion.log.debug("Saving raw HTML content for URL: $url")
            task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
            FetchMethod.Companion.log.debug("Simplifying HTML content for URL: $url")
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
                FetchMethod.Companion.log.error("HTML simplification failed for URL: $url, using raw content", e)
                // Fallback to basic text extraction if HTML simplification fails
                body.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            FetchMethod.Companion.log.debug("Saving simplified content for URL: $url")
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
}