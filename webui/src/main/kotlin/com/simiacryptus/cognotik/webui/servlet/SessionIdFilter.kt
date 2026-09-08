package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID

class SessionIdFilter : Filter {

  override fun init(filterConfig: FilterConfig?) {}

  override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    if (request is HttpServletRequest && response is HttpServletResponse) {
      var sessionId: String? = null
      val cookies = request.cookies
      if (cookies != null) {
        for (cookie in cookies) {
          if (SESSION_COOKIE_NAME == cookie.name) {
            sessionId = cookie.value
            break
          }
        }
      }

      if (sessionId.isNullOrEmpty()) {
        sessionId = UUID.randomUUID().toString()
        setSessionCookie(request, response, sessionId)
      }

      request.setAttribute(SESSION_ID_ATTRIBUTE, sessionId)
    }
    chain.doFilter(request, response)
  }

  override fun destroy() {}

  companion object {
    const val SESSION_COOKIE_NAME = "JSESSIONID"
    const val SESSION_ID_ATTRIBUTE = "com.simiacryptus.cognotik.sessionId"

    fun setSessionCookie(
      request: HttpServletRequest,
      response: HttpServletResponse,
      sessionId: String,
      maxAge: Int = -1,
      sameSite: String = "Lax"
    ) {
      val isSecure = request.isSecure || "https".equals(request.getHeader("X-Forwarded-Proto"), ignoreCase = true)

      // Add Set-Cookie response header explicitly enforcing HttpOnly, Secure, and SameSite flags
      val cookieHeader = StringBuilder()
      cookieHeader.append("$SESSION_COOKIE_NAME=$sessionId; Path=/; HttpOnly")
      if (isSecure) {
        cookieHeader.append("; Secure")
      }
      if (maxAge >= 0) {
        cookieHeader.append("; Max-Age=$maxAge")
      }
      if (sameSite.isNotEmpty()) {
        cookieHeader.append("; SameSite=$sameSite")
      }

      response.addHeader("Set-Cookie", cookieHeader.toString())

      // Also set standard Jakarta Cookie object with attributes
      response.addCookie(Cookie(SESSION_COOKIE_NAME, sessionId).apply {
        this.path = "/"
        this.isHttpOnly = true
        this.secure = isSecure
        if (maxAge >= 0) {
          this.maxAge = maxAge
        }
      })
    }
  }
}