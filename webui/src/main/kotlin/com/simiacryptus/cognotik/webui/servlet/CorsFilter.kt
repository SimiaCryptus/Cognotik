package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.*
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.IOException

@WebFilter(asyncSupported = true, urlPatterns = ["/*"])
class CorsFilter : Filter {
  @Throws(ServletException::class)
  override fun init(filterConfig: FilterConfig?) {
    // No initialization needed for this filter
  }

  @Throws(IOException::class, ServletException::class)
  override fun doFilter(request: ServletRequest?, response: ServletResponse, chain: FilterChain) {
    if (!(request as HttpServletRequest).requestURI.endsWith("/ws")) {
      val httpServletResponse = response as HttpServletResponse
      httpServletResponse.setHeader("Access-Control-Allow-Origin", "*")
      httpServletResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT")
      httpServletResponse.setHeader("Access-Control-Max-Age", "3600")
      httpServletResponse.setHeader(
        "Access-Control-Allow-Headers",
        "Content-Type, x-requested-with, authorization"
      )
    }
    try {
      chain.doFilter(request, response)
    } catch (e: Exception) {
      log.warn("Error in filter", e)
      throw e
    }
  }

  override fun destroy() {
    // Cleanup any resources if needed
  }

  companion object {
    private val log = LoggerFactory.getLogger(CorsFilter::class.java)
  }
}