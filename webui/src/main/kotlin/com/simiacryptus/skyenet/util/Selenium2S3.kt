package com.simiacryptus.skyenet.util

import com.simiacryptus.skyenet.core.platform.ApplicationServices.cloud
import com.simiacryptus.skyenet.core.util.Selenium
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.Method
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

open class Selenium2S3(
  val pool: ThreadPoolExecutor = Executors.newCachedThreadPool() as ThreadPoolExecutor,
  private val cookies: Array<out jakarta.servlet.http.Cookie>? = null,
  val driver: RemoteWebDriver = chromeDriver()
) : Selenium {
  override fun navigate(url: String) {
    (driver as WebDriver).navigate().to(url)
  }

  override fun getPageSource(): String {
    return (driver as WebDriver).pageSource
  }

  override fun getCurrentUrl(): String {
    return (driver as WebDriver).currentUrl
  }

  override fun executeScript(script: String): Any? {
    return (driver as JavascriptExecutor).executeScript(script)
  }

  override fun quit() {
    (driver as WebDriver).quit()
  }

  var loadImages: Boolean = false

  private val httpClient by lazy {
    HttpAsyncClientBuilder.create()
      .useSystemProperties()
      .setDefaultCookieStore(BasicCookieStore().apply {
        cookies?.forEach { cookie -> addCookie(BasicClientCookie(cookie.name, cookie.value)) }
      })
      .setThreadFactory(pool.threadFactory)
      .build()
      .also { it.start() }
  }

  private val linkReplacements = mutableMapOf<String, String>()
  private val htmlPages: MutableMap<String, String> = mutableMapOf()
  private val jsonPages = mutableMapOf<String, String>()
  private val links: MutableList<String> = mutableListOf()

  override fun save(
    url: URL,
    currentFilename: String?,
    saveRoot: String
  ) {
    log.info("Saving URL: $url")
    log.info("Current filename: $currentFilename")
    log.info("Save root: $saveRoot")
    driver.navigate().to(url)
    driver.navigate().refresh()
    Thread.sleep(5000) // Wait for javascript to load

    htmlPages += mutableMapOf((currentFilename ?: url.file.split("/").last()) to editPage(driver.pageSource))
    val baseUrl = url.toString().split("#").first()
    links += toAbsolute(baseUrl, *currentPageLinks(driver).map { link ->
      val relative = toRelative(baseUrl, link) ?: return@map link
      linkReplacements[link] = "${cloud!!.shareBase}/$saveRoot/${toArchivePath(relative)}"
      linkReplacements[relative] = "${cloud!!.shareBase}/$saveRoot/${toArchivePath(relative)}"
      link
    }.toTypedArray()).toMutableList()
    val completionSemaphores = mutableListOf<Semaphore>()

    log.info("Fetching page source")
    log.info("Base URL: $baseUrl")
    val coveredLinks = mutableSetOf<String>()
    log.info("Processing links")
    while (links.isNotEmpty()) {
      val href = links.removeFirst()
      try {
        if (coveredLinks.contains(href)) continue
        coveredLinks += href
        log.debug("Processing $href")
        process(url, href, completionSemaphores, saveRoot)
      } catch (e: Exception) {
        log.warn("Error processing $href", e)
      }
    }

    log.info("Fetching current page links")
    log.debug("Waiting for completion")
    completionSemaphores.forEach { it.acquire(); it.release() }

    log.debug("Saving")
    saveAll(saveRoot)
    log.debug("Done")
  }

  override fun setScriptTimeout(timeout: Long) {
    (driver as WebDriver).manage().timeouts().setScriptTimeout(timeout, TimeUnit.MILLISECONDS)
  }

  override fun getBrowserInfo(): String {
    return driver.capabilities.browserName
  }

  override fun forceQuit() {
    driver.quit()
  }

  override fun isAlive(): Boolean {
    return driver.sessionId != null
  }

  override fun getLogs(): String {
    return driver.manage().logs().get(LogType.BROWSER).all.joinToString("\n")
  }

  protected open fun process(
    url: URL,
    href: String,
    completionSemaphores: MutableList<Semaphore>,
    saveRoot: String
  ): Boolean {
    val base = url.toString().split("/").dropLast(1).joinToString("/")
    val relative = toArchivePath(toRelative(base, href) ?: return true)
    when (val mimeType = mimeType(relative)) {

      "text/html" -> {
        if (htmlPages.containsKey(relative)) return true
        log.info("Fetching $href")
        val semaphore = Semaphore(0)
        completionSemaphores += semaphore
        getHtml(href, htmlPages, relative, links, saveRoot, semaphore)
      }

      "application/json" -> {
        if (jsonPages.containsKey(relative)) return true
        log.info("Fetching $href")
        val semaphore = Semaphore(0)
        completionSemaphores += semaphore
        getJson(href, jsonPages, relative, semaphore)
      }

      else -> {
        val semaphore = Semaphore(0)
        completionSemaphores += semaphore
        getMedia(href, mimeType, saveRoot, relative, semaphore)
      }
    }
    return false
  }

  protected open fun getHtml(
    href: String,
    htmlPages: MutableMap<String, String>,
    relative: String,
    links: MutableList<String>,
    saveRoot: String,
    semaphore: Semaphore
  ) {
    httpClient.execute(get(href), object : FutureCallback<SimpleHttpResponse> {

      override fun completed(p0: SimpleHttpResponse?) {
        log.debug("Fetched $href")
        val html = p0?.body?.bodyText ?: ""
        htmlPages[relative] = html
        links += toAbsolute(href, *currentPageLinks(html).map { link ->
          val relative = toArchivePath(toRelative(href, link) ?: return@map link)
          linkReplacements[link] = "${cloud!!.shareBase}/$saveRoot/$relative"
          link
        }.toTypedArray())
        semaphore.release()
      }

      override fun failed(p0: java.lang.Exception?) {
        log.info("Error fetching $href", p0)
        semaphore.release()
      }

      override fun cancelled() {
        log.info("Cancelled fetching $href")
        semaphore.release()
      }

    })
  }

  protected open fun getJson(
    href: String,
    jsonPages: MutableMap<String, String>,
    relative: String,
    semaphore: Semaphore
  ) {
    httpClient.execute(get(href), object : FutureCallback<SimpleHttpResponse> {

      override fun completed(p0: SimpleHttpResponse?) {
        log.debug("Fetched $href")
        jsonPages[relative] = p0?.body?.bodyText ?: ""
        semaphore.release()
      }

      override fun failed(p0: java.lang.Exception?) {
        log.info("Error fetching $href", p0)
        semaphore.release()
      }

      override fun cancelled() {
        log.info("Cancelled fetching $href")
        semaphore.release()
      }

    })
  }

  protected open fun getMedia(
    href: String,
    mimeType: String,
    saveRoot: String,
    relative: String,
    semaphore: Semaphore
  ) {
    val request = get(href)
    httpClient.execute(request, object : FutureCallback<SimpleHttpResponse> {

      override fun completed(p0: SimpleHttpResponse?) {
        try {
          log.debug("Fetched $request")
          val bytes = p0?.body?.bodyBytes ?: return
          if (validate(mimeType, p0.body.contentType.mimeType, bytes))
            cloud!!.upload(
              path = "/$saveRoot/$relative",
              contentType = mimeType,
              bytes = bytes
            )
        } finally {
          semaphore.release()
        }
      }

      override fun failed(p0: java.lang.Exception?) {
        log.info("Error fetching $href", p0)
        semaphore.release()
      }

      override fun cancelled() {
        log.info("Cancelled fetching $href")
        semaphore.release()
      }

    })
  }

  private fun saveAll(
    saveRoot: String
  ) {
    (htmlPages.map { (filename, html) ->
      pool.submit {
        try {
          saveHTML(html, saveRoot, filename)
        } catch (e: Exception) {
          log.warn("Error processing $filename", e)
        }
      }
    } + jsonPages.map { (filename, js) ->
      pool.submit {
        try {
          saveJS(js, saveRoot, filename)
        } catch (e: Exception) {
          log.warn("Error processing $filename", e)
        }
      }
    }).forEach {
      try {
        it.get()
      } catch (e: Exception) {
        log.warn("Error processing", e)
      }
    }
  }

  protected open fun saveJS(js: String, saveRoot: String, filename: String) {
    val finalJs = linkReplacements.toList().sortedBy { it.first.length }
      .fold(js) { acc, (href, relative) -> //language=RegExp
        acc.replace("""(?<![/\w])$href""".toRegex(), relative)
      }
    cloud!!.upload(
      path = "/$saveRoot/$filename",
      contentType = "application/json",
      request = finalJs
    )
  }

  protected open fun saveHTML(html: String, saveRoot: String, filename: String) {
    val finalHtml = linkReplacements.toList().filter { it.first.isNotEmpty() }.fold(html)
    { acc, (href, relative) -> acc.replace("""(?<![/\w#])$href""".toRegex(), relative) }
    cloud!!.upload(
      path = "/$saveRoot/$filename",
      contentType = "text/html",
      request = finalHtml
    )
  }

  protected open fun get(href: String): SimpleHttpRequest {
    val request = SimpleHttpRequest(Method.GET, URI(href))
    cookies?.forEach { cookie ->
      request.addHeader("Cookie", "${cookie.name}=${cookie.value}")
    }
    return request
  }

  protected open fun currentPageLinks(driver: WebDriver) = listOf(
    driver.findElements(By.xpath("//a[@href]")).map<WebElement?, String?> { it?.getAttribute("href") }.toSet(),
    driver.findElements(By.xpath("//img[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
    driver.findElements(By.xpath("//link[@href]")).map<WebElement?, String?> { it?.getAttribute("href") }.toSet(),
    driver.findElements(By.xpath("//script[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
    driver.findElements(By.xpath("//source[@src]")).map<WebElement?, String?> { it?.getAttribute("src") }.toSet(),
  ).flatten().filterNotNull()

  private fun currentPageLinks(html: String) = listOf(
    Jsoup.parse(html).select("a[href]").map { it.attr("href") }.toSet(),
    Jsoup.parse(html).select("img[src]").map { it.attr("src") }.toSet(),
    Jsoup.parse(html).select("link[href]").map { it.attr("href") }.toSet(),
    Jsoup.parse(html).select("script[src]").map { it.attr("src") }.toSet(),
    Jsoup.parse(html).select("source[src]").map { it.attr("src") }.toSet(),
  ).flatten().filterNotNull()

  protected open fun toAbsolute(base: String, vararg links: String) = links
    .map { it.split("#").first() }.filter { it.isNotBlank() }.distinct()
    .map { link ->
      val newLink = when {
        link.startsWith("http") -> link
        else -> URI.create(base).resolve(link).toString()
      }
      newLink
    }

  protected open fun toRelative(base: String, link: String): String? = when {
    link.startsWith(base) -> toRelative(
      base,
      link.removePrefix(base).replace("/{2,}".toRegex(), "/").removePrefix("/")
    ) // relativize
    link.startsWith("http") -> null // absolute
    else -> link // relative
  }

  protected open fun toArchivePath(link: String): String = when {
    link.startsWith("fileIndex") -> link.split("/").drop(2).joinToString("/") // rm file segment
    else -> link
  }

  protected open fun validate(
    expected: String,
    actual: String,
    bytes: ByteArray
  ): Boolean {
    if (!actual.startsWith(expected)) {
      log.warn("Content type mismatch: $actual != $expected")
      if (actual.startsWith("text/html")) {
        log.warn("Response Error: ${String(bytes)}", Exception())
      }
      return false
    }
    return true
  }

  protected open fun mimeType(relative: String): String {
    val extension = relative.split(".").last().split("?").first()
    val contentType = when (extension) {
      "css" -> "text/css"
      "js" -> "text/javascript"
      "json" -> "application/json"
      "pdf" -> "application/pdf"
      "zip" -> "application/zip"
      "tar" -> "application/x-tar"
      "gz" -> "application/gzip"
      "bz2" -> "application/bzip2"
      "mp3" -> "audio/mpeg"
      //"tsv" -> "text/tab-separated-values"
      "csv" -> "text/csv"
      "txt" -> "text/plain"
      "xml" -> "text/xml"
      "svg" -> "image/svg+xml"
      "png" -> "image/png"
      "jpg" -> "image/jpeg"
      "jpeg" -> "image/jpeg"
      "gif" -> "image/gif"
      "ico" -> "image/x-icon"
      "html" -> "text/html"
      "htm" -> "text/html"
      else -> "text/plain"
    }
    return contentType
  }

  protected open fun editPage(html: String): String {
    val doc = Jsoup.parse(html)
    doc.select("#toolbar").remove()
    doc.select("#namebar").remove()
    doc.select("#main-input").remove()
    doc.select("#footer").remove()
    return doc.toString()
  }

  override fun close() {
    log.debug("Closing", Exception())
    driver.quit()
    httpClient.close()
    //driver.close()
    //Companion.chromeDriverService.close()
  }


  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(Selenium2S3::class.java)

    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        try {
        } catch (e: Exception) {
          log.warn("Error closing com.simiacryptus.skyenet.webui.util.Selenium2S3", e)
        }
      })
    }

    fun chromeDriver(headless: Boolean = true, loadImages: Boolean = !headless): ChromeDriver {
      val osname = System.getProperty("os.name")
      val chromePath = when {
        // Windows
        osname.contains("Windows") -> listOf(
          "C:\\Program Files\\Google\\Chrome\\Application\\chromedriver.exe",
          "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chromedriver.exe"
        )
        // Ubuntu
        osname.contains("Linux") -> listOf("/usr/bin/chromedriver")
        else -> throw RuntimeException("Not implemented for $osname")
      }
      System.setProperty(
        "webdriver.chrome.driver",
        chromePath.find { File(it).exists() } ?: throw RuntimeException("Chrome not found"))
      val options = ChromeOptions()
      val args = mutableListOf<String>()
      if (headless) args += "--headless"
      if (loadImages) args += "--blink-settings=imagesEnabled=false"
      options.addArguments(*args.toTypedArray())
      options.setPageLoadTimeout(Duration.of(90, ChronoUnit.SECONDS))
      return ChromeDriver(chromeDriverService, options)
    }

    private val chromeDriverService by lazy { ChromeDriverService.createDefaultService() }
  }

}