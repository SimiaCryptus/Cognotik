package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApiData
import com.simiacryptus.cognotik.platform.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val mask = "********"

class UserSettingsServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    response.status = HttpServletResponse.SC_OK
    val user =
      UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
    try {
      val settings =
        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user)
      val visibleSettings = UserSettings(
        apis = settings.apis.map { apiData ->
          ApiData(
            key = when (apiData.key?.decrypt) {
              "" -> ""
              null -> null
              else -> mask
            }?.encrypt,
            baseUrl = apiData.apiBase,
            provider = apiData.provider
          )//.validate()
        }.toMutableList(),
        collectSessionData = settings.collectSessionData,
        passwordHash = settings.passwordHash,
        smartModel = settings.smartModel,
        fastModel = settings.fastModel,
      ).jsonCast<Map<String,Any>>() + mapOf(
        "user" to user
      )
      val json = JsonUtil.toJson(visibleSettings)
      val acceptHeader = request.getHeader("Accept") ?: ""
      if (acceptHeader.contains("application/json")) {
        response.contentType = "application/json"
        response.writer.write(json)
      } else {
        response.contentType = "text/html"
        response.writer.write(
          """
                        <html>
                        <head>
                            <title>Settings</title>
                            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                        </head>
                        <body>
                        <form action="/userSettings/" method="post">
                            <input type="hidden" name="action" value="save"/>
                            <textarea name="settings" style="width: 100%; height: 100px;">""".trimIndent() + json + """</textarea>
                            <input type="submit" value="Save"/>
                        </form>
                        </body>
                        </html>
                      """.trimIndent()
        )
      }
    } catch (e: Exception) {
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR

      response.writer.write(
        """
                        <html>
                        <head>
                            <title>Error</title>
                            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                        </head>
                        <body>
                        <h1>Error</h1>
                        <pre>""".trimIndent() + e.message + """</pre>
                        </body>
                        </html>
                  """.trimIndent()
      )
    }
  }

  public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val user =
      UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
    val settings = JsonUtil.fromJson<UserSettings>(request.getParameter("settings"), UserSettings::class.java)
    val userSettingsManager = ApplicationServices.fileApplicationServices().userSettingsManager
    val prevSettings =
      userSettingsManager.getUserSettings(user)
    val reconstructedApis = settings.apis.mapIndexed { index, apiData ->
      val prevApiData = prevSettings.apis.getOrNull(index)
      ApiData(
        key = when (apiData.key?.decrypt) {
          mask -> prevApiData?.key ?: "".encrypt
          else -> apiData.key
        },
        baseUrl = apiData.apiBase,
        provider = apiData.provider
      ).validate()
    }.toMutableList()
    val reconstructedSettings = UserSettings(
      apis = reconstructedApis,
      collectSessionData = settings.collectSessionData,
      passwordHash = settings.passwordHash,
      smartModel = settings.smartModel,
      fastModel = settings.fastModel
    )
    userSettingsManager.updateUserSettings(
      user,
      reconstructedSettings
    )
    response.sendRedirect("/")
  }

  companion object
}