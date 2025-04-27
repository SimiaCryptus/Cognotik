package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val cookie = req.getCookie()
        val user = ApplicationServices.authenticationManager.getUser(cookie)
        if (null == user) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            ApplicationServices.authenticationManager.logout(cookie ?: "", user)
            resp.sendRedirect("/")
        }
    }

    companion object
}