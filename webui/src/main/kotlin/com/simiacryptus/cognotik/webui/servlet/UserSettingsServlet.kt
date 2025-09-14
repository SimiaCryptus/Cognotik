package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val mask = "********"

class UserSettingsServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_OK
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == userinfo) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            try {
                val settings = ApplicationServices.userSettingsManager.getUserSettings(userinfo)
                val visibleSettings = UserSettings(
                    apis = settings.apis.map { apiData ->
                        ApiData(
                            key = when (apiData.key) {
                                "" -> ""
                                else -> mask
                            },
                            baseUrl = apiData.baseUrl,
                            provider = apiData.provider
                        ).validate()
                    }.toMutableList(),
                    tools = settings.tools.toMutableList(),
                    etc = settings.etc.toMutableMap()
                )
                val json = JsonUtil.toJson(visibleSettings)
                val acceptHeader = req.getHeader("Accept") ?: ""
                if (acceptHeader.contains("application/json")) {
                    resp.contentType = "application/json"
                    resp.writer.write(json)
                } else {
                    resp.contentType = "text/html"
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
                }
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
            val reconstructedApis = settings.apis.mapIndexed { index, apiData ->
                val prevApiData = prevSettings.apis.getOrNull(index)
                ApiData(
                    key = when (apiData.key) {
                        mask -> prevApiData?.key ?: ""
                        else -> apiData.key
                    },
                    baseUrl = apiData.baseUrl,
                    provider = apiData.provider
                ).validate()
            }.toMutableList()
            val reconstructedSettings = UserSettings(
                apis = reconstructedApis,
                tools = (prevSettings.tools + settings.tools).distinctBy { it.name }.toMutableList(),
                etc = settings.etc
            )
            ApplicationServices.userSettingsManager.updateUserSettings(userinfo, reconstructedSettings)
            resp.sendRedirect("/")
        }
    }

    companion object
}