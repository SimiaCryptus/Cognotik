package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.core.platform.ApplicationServices
import com.simiacryptus.cognotik.core.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.core.platform.Session
import com.simiacryptus.cognotik.core.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class DeleteSessionServlet(
  private val server: ApplicationServer,
) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    resp.contentType = "text/html"
    resp.status = HttpServletResponse.SC_OK
    if (req.parameterMap.containsKey("sessionId")) {
      val session = Session(req.getParameter("sessionId"))
      //language=HTML
      resp.writer.write(
        """
        <html>
        <head>
            <title>Delete Session</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
        </head>
        <body>
        <form action="${req.contextPath}/delete" method="post">
            <input type="hidden" name="sessionId" value="$session"/>
            CONFIRM: <input type='text' name="confirm" placeholder="Type 'confirm' to delete" />
            <input type="submit" value="Delete"/>
        </form>
        </body>
        </html>
        """.trimIndent()
      )
    } else {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Session ID is required")
    }
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    require(req.getParameter("confirm").lowercase() == "confirm") { "Confirmation text is required" }
    resp.contentType = "text/html"
    resp.status = HttpServletResponse.SC_OK
    if (!req.parameterMap.containsKey("sessionId")) {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Session ID is required")
    } else {
      val session = Session(req.getParameter("sessionId"))
      val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
      require(authorizationManager.isAuthorized(javaClass, user, OperationType.Delete))
      { "User $user is not authorized to delete sessions" }
      if (session.isGlobal()) {
        require(authorizationManager.isAuthorized(javaClass, user, OperationType.Public))
        { "User $user is not authorized to delete global sessions" }
      }
      server.dataStorage.deleteSession(user, session)
      resp.sendRedirect("/")
    }
  }
}