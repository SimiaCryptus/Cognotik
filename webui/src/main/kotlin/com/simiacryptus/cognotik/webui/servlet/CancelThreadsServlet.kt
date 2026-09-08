package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl.Companion.threadPoolManager
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class CancelThreadsServlet : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    resp.contentType = "text/html"
    resp.status = HttpServletResponse.SC_OK
    if (req.parameterMap.containsKey("sessionId")) {
      val session = Session(req.getParameter("sessionId"))

      resp.writer.write(
        """
        <html>
        <head>
            <title>Cancel Session</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
        </head>
        <body>
        <form action="""".trimIndent() + req.contextPath + """/cancel" method="post">
            <input type="hidden" name="sessionId" value="""".trimIndent() + session + """"/>
            CONFIRM: <input type='text' name="confirm" placeholder="Type 'confirm' to cancel" />
            <input type="submit" value="Cancel"/>
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
        throw RuntimeException("User must be authenticated to cancel sessions")
      }
      require(
        ApplicationServicesImpl.authorizationManager.isAuthorized(
          ResourceRef.of(javaClass),
          Principal.of(user),
          OperationType.Delete
        )
      )
      { "User $user is not authorized to cancel sessions" }
      if (session.isGlobal()) {
        require(
          ApplicationServicesImpl.authorizationManager.isAuthorized(
            ResourceRef.of(javaClass),
            Principal.of(user),
            OperationType.Public
          )
        )
        { "User $user is not authorized to cancel global sessions" }
      }
      val pool = threadPoolManager.getPool(session, user ?: return)
      pool.shutdownNow()
      response.sendRedirect("/")
    }
  }

}