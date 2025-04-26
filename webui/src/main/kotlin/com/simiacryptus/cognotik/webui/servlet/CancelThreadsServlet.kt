package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.clientManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class CancelThreadsServlet : HttpServlet() {
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


    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        require(req.getParameter("confirm").lowercase() == "confirm") { "Confirmation text is required" }
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        if (!req.parameterMap.containsKey("sessionId")) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Session ID is required")
        } else {
            val session = Session(req.getParameter("sessionId"))
            val user = authenticationManager.getUser(req.getCookie())
            require(
                ApplicationServices.authorizationManager.isAuthorized(
                    javaClass,
                    user,
                    AuthorizationInterface.OperationType.Delete
                )
            )
            { "User $user is not authorized to cancel sessions" }
            if (session.isGlobal()) {
                require(
                    ApplicationServices.authorizationManager.isAuthorized(
                        javaClass,
                        user,
                        AuthorizationInterface.OperationType.Public
                    )
                )
                { "User $user is not authorized to cancel global sessions" }
            }
            val pool = clientManager.getPool(session, user)
            pool.shutdownNow()
            resp.sendRedirect("/")
        }
    }

}