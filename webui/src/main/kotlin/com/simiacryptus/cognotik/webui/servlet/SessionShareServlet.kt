package com.simiacryptus.cognotik.webui.servlet

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.cloud
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Selenium
import com.simiacryptus.cognotik.util.Selenium2S3
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClients
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

class SessionShareServlet(
    private val server: ApplicationServer,
) : HttpServlet() {

    private fun generateQRCodeDataURL(url: String): String {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 200, 200)
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)
        return "data:image/png;base64,$base64Image"
    }

    val defaultFactory: (pool: java.util.concurrent.ExecutorService, cookies: Array<out jakarta.servlet.http.Cookie>?) -> Selenium =
        { pool, cookies ->
            val chromeOptions = ChromeOptions().apply {
                addArguments("--headless")
                addArguments("--disable-gpu")
                addArguments("--no-sandbox")
                addArguments("--disable-dev-shm-usage")
            }
            try {
                Selenium2S3(
                    pool = pool,
                    cookies = cookies,
                    driver = ChromeDriver(chromeOptions)
                )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to initialize Selenium", e)
            }
        }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

        val user = authenticationManager.getUser(req.getCookie())
        val cookies = req.cookies

        if (!req.parameterMap.containsKey("url")) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Url is required")
            return
        }

        val url = req.getParameter("url")
        val host = URI(url).host
        val appName = url.split("/").dropLast(1).last()
        val sessionID = url.split("#").lastOrNull() ?: throw IllegalArgumentException("No session id in url: $url")

        require(acceptHost(user, host)) { "Invalid url: $url" }

        val storageInterface = ApplicationServices.dataStorageFactory(dataStorageRoot)
        val session = Session.parseSessionID(sessionID)
        val pool = ApplicationServices.clientManager.getPool(session, user)
        val infoFile = storageInterface.getDataDir(user, session).resolve("info.json").apply { parentFile.mkdirs() }
        val json = if (infoFile.exists()) JsonUtil.fromJson<Map<String, Any>>(
            infoFile.readText(),
            typeOf<Map<String, Any>>().javaType
        ) else mapOf()
        val sessionSettings = (json as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        val previousShare = sessionSettings["shareId"]
        var shareURL = url(appName, previousShare ?: Session.long64())
        var qrCodeDataURL = generateQRCodeDataURL(shareURL)
        when {
            null != previousShare && validateUrl(shareURL) -> {
                log.info("Reusing shareId: $previousShare")

                resp.contentType = "text/html"
                resp.status = HttpServletResponse.SC_OK
                //language=HTML
                resp.writer.write(
                    """
            <html>
            <head>
                <title>Save Session</title>
                <style>
                </style>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            </head>
            <body>
                <h1>Sharing URL</h1>
                <p><a href="shareURL" target='_blank'>shareURL</a></p>
                <body>
                <h1>Sharing URL</h1>
                <p><a href="$shareURL" target='_blank'>$shareURL</a></p>
                <img src="$qrCodeDataURL" alt="QR Code for $shareURL">
            </body>
          """.trimIndent()
                )
            }

            !authorizationManager.isAuthorized(server.javaClass, user, OperationType.Share) -> {
                resp.status = HttpServletResponse.SC_FORBIDDEN
                resp.writer.write("Forbidden")
                return
            }

            else -> {
                val shareId = Session.long64()
                currentlyProcessing.add(shareId)
                pool.submit {
                    try {
                        log.info("Generating shareId: $shareId")
                        sessionSettings["shareId"] = shareId
                        infoFile.writeText(JsonUtil.toJson(sessionSettings))
                        val selenium2S3: Selenium = defaultFactory(pool, cookies)
                        if (selenium2S3 is Selenium2S3) {
                            selenium2S3.loadImages = req.getParameter("loadImages")?.toBoolean() ?: false
                        }
                        selenium2S3.save(
                            url = URI(url).toURL(),
                            saveRoot = "$appName/$shareId",
                            currentFilename = "index.html",
                        )
                        log.info("Saved session $sessionID to $appName/$shareId")
                    } catch (e: Throwable) {
                        log.error("Error saving session $sessionID to $appName/$shareId", e)
                    } finally {
                        currentlyProcessing.remove(shareId)
                    }
                }
                resp.contentType = "text/html"
                resp.status = HttpServletResponse.SC_OK
                //language=HTML
                shareURL = url(appName, shareId)
                qrCodeDataURL = generateQRCodeDataURL(shareURL)
                resp.writer.write(
                    """
          <html>
          <head>
              <title>Saving Session</title>
              <style>
              </style>
              <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
          </head>
          <body>
              <h1>Saving Session... This page will soon be ready!</h1>
              <p><a href="$shareURL" target='_blank'>$shareURL</a></p>
              <img src="$qrCodeDataURL" alt="QR Code for $shareURL">
              <p>To monitor progress, you can use the session threads page</p>
          </body>
          </html>
          """.trimIndent()
                )
            }
        }


    }

    private fun url(appName: String, shareId: String) =
        """${cloud!!.shareBase}/$appName/$shareId/index.html"""

    private fun acceptHost(user: User?, host: String?): Boolean {
        return when (host) {
            "localhost" -> true
            domain -> true
            else -> authorizationManager.isAuthorized(server.javaClass, user, OperationType.Admin)
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(SessionShareServlet::class.java)
        private val currentlyProcessing = mutableSetOf<String>()
        fun validateUrl(previousShare: String): Boolean = when {
            currentlyProcessing.contains(previousShare) -> true
            else -> HttpClients.createSystem().use { httpClient: HttpClient ->
                val responseEntity = httpClient.execute(org.apache.http.client.methods.HttpGet(previousShare))
                return responseEntity.statusLine.statusCode == 200
            }
        }

        var domain = System.getProperty("domain", "apps.simiacrypt.us")
    }
}