package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServicesImpl.Companion.authorizationManager
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
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

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    require(request.getParameter("confirm").lowercase() == "confirm") { "Confirmation text is required" }
    response.contentType = "text/html"
    response.status = HttpServletResponse.SC_OK
    if (!request.parameterMap.containsKey("sessionId")) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("Session ID is required")
    } else {
      val session = Session(request.getParameter("sessionId"))
      val user = UserProviderImpl().authenticate(request, response)
      if (user == null) {
        throw RuntimeException("User must be authenticated to delete sessions")
      }
      require(authorizationManager.isAuthorized(ResourceRef.of(javaClass), Principal.of(user), OperationType.Delete))
      { "User $user is not authorized to delete sessions" }
      if (session.isGlobal()) {
        require(authorizationManager.isAuthorized(ResourceRef.of(javaClass), Principal.of(user), OperationType.Public))
        { "User $user is not authorized to delete global sessions" }
      }
      server.dataStorage.deleteSession(user, session)
      response.sendRedirect("/")
    }
  }
}