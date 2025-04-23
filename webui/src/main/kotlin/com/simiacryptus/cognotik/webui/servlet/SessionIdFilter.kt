package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.core.platform.ApplicationServices
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLEncoder

class SessionIdFilter(
  val isSecure: (HttpServletRequest) -> Boolean,
  private val loginRedirect: String
) : Filter {

  override fun init(filterConfig: FilterConfig?) {}

  override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    if (request is HttpServletRequest && response is HttpServletResponse) {
      if (isSecure(request)) {
        val sessionIdCookie = request.getCookie()
        if (sessionIdCookie == null || null == ApplicationServices.authenticationManager.getUser(
            sessionIdCookie
          )
        ) {
          val queryString = request.queryString
          val originalUrl =
            if (queryString != null) "${request.requestURL}?${queryString}" else request.requestURL.toString()
          val encodedUrl = URLEncoder.encode(originalUrl, "UTF-8")
          val redirectUrl = "$loginRedirect?redirect=$encodedUrl"
          response.sendRedirect(redirectUrl)
          return
        }
      }
    }
    chain.doFilter(request, response)
  }

  override fun destroy() {}
}