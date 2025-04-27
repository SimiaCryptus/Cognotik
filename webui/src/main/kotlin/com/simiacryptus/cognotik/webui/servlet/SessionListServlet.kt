package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.text.SimpleDateFormat

class SessionListServlet(
    private val dataStorage: StorageInterface,
    private val prefix: String,
    private val applicationServer: ApplicationServer
) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        val user = authenticationManager.getUser(req.getCookie())
        val sessions = dataStorage.listSessions(user, req.contextPath)
        val sessionRows = sessions.joinToString("") { session ->
            val sessionName = dataStorage.getSessionName(user, session)
            val sessionTime = dataStorage.getSessionTime(user, session) ?: return@joinToString ""
            val sessionTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(sessionTime)
            """
            <tr class="session-row" onclick="window.location.href='$prefix#$session'">

                    <td><a href="$prefix#$session" class="session-link">$sessionName</a></td>
                    <td><a href="$prefix#$session" class="session-link">$sessionTimeStr</a></td>
            </tr>
            """.trimIndent()
        }
        val title = """Sessions"""

        resp.writer.write(
            """
            <html>
            <head>
            <title>$title</title>
            <style>
                body { font-family: Arial, sans-serif; }
                table { width: 100%; border-collapse: collapse; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                tr:hover { background-color: #ddd; }
                a { text-decoration: none; color: #333; }
            </style>
            </head>
            <body>
            <div id='app-description'>
            ${applicationServer.description}
            </div>
            <table class='applist' id='session-list'>
                <tr>
                    <th>Session Name</th>
                    <th>Created</th>
                </tr>
                ${sessionRows.indent("    ")}
            </table>
            </body>
            </html>
            """.trimIndent()
        )
    }

}