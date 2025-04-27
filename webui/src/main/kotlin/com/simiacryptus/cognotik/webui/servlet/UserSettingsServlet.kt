package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface.UserSettings
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val mask = "********"

class UserSettingsServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == userinfo) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            try {
                val settings = ApplicationServices.userSettingsManager.getUserSettings(userinfo)
                val visibleSettings = settings.copy(
                    apiKeys = APIProvider.values().map {
                        it to when (settings.apiKeys[it]) {
                            null -> ""
                            "" -> ""
                            else -> mask
                        }
                    }.toMap(),
                    apiBase = APIProvider.values().map {
                        it to when (settings.apiBase[it]) {
                            null -> it.base
                            "" -> it.base
                            else -> settings.apiBase[it]
                        }!!
                    }.toMap(),
                )
                val json = JsonUtil.toJson(visibleSettings)

                resp.writer.write(
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
            } catch (e: Exception) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR

                resp.writer.write(
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
                resp
            }
        }
    }

    public override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == userinfo) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            val settings = JsonUtil.fromJson<UserSettings>(req.getParameter("settings"), UserSettings::class.java)
            val prevSettings = ApplicationServices.userSettingsManager.getUserSettings(userinfo)
            val reconstructedSettings = prevSettings.copy(
                apiKeys = settings.apiKeys.mapValues {
                    when (it.value) {
                        "" -> ""
                        mask -> prevSettings.apiKeys[it.key]!!
                        else -> settings.apiKeys[it.key]!!
                    }
                },
                apiBase = settings.apiBase.mapValues {
                    when (it.value) {
                        null -> "https://api.openai.com/v1"
                        "" -> "https://api.openai.com/v1"
                        else -> settings.apiBase[it.key]!!
                    }
                },
            )
            ApplicationServices.userSettingsManager.updateUserSettings(userinfo, reconstructedSettings)
            resp.sendRedirect("/")
        }
    }

    companion object
}