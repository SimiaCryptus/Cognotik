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
                val client = java.net.http.HttpClient.newBuilder().build()
                val request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    ).GET()
                    .build()
                log.debug("Sending HTTP request to: $url")
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().firstValue("Content-Type").orElse("")
                log.debug("Received response from $url with status: ${response.statusCode()}, Content-Type: $contentType")

                val content = when {
                    // Handle HTML content
                    contentType.startsWith("text/html") || contentType.isEmpty() -> {
                        val body = response.body()
                        if (body.isBlank()) {
                            log.warn("Received empty body from URL: $url")
                            return ""
                        }
                        log.debug("Saving raw HTML content for URL: $url")
                        task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
                        log.debug("Simplifying HTML content for URL: $url")
                        val simplified = HtmlSimplifier.scrubHtml(
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
                        log.debug("Saving simplified content for URL: $url")
                        task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, simplified)
                        simplified
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
                        
                        // Save the document to a temporary file
                        val extension = getExtensionFromContentType(contentType, url)
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
                            task.saveRawContent(webSearchDir.resolve("documents"), url, extractedText)
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