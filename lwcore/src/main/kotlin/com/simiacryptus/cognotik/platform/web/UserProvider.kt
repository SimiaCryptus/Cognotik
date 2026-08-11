package com.simiacryptus.cognotik.platform.web

import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Resolves the authenticated [User] for an inbound HTTP request.
 *
 * Lives in the web package (not `platform.model`) so the domain model has no
 * `jakarta.servlet` dependency.
 */
interface UserProvider {
  fun authenticate(
    request: HttpServletRequest,
    response: AbstractHttpServletResponse?
  ): User?

  fun authenticate(
    request: HttpServletRequest,
    response: HttpServletResponse?
  ): User? = authenticate(request, response?.let {
    object : AbstractHttpServletResponse {
      override fun setHeader(key: String, value: String) {
        it.setHeader(key, value)
      }

      override var status: Int
        get() = it.status
        set(value) {
          it.status = value
        }
    }
  })
}

interface AbstractHttpServletResponse {
  fun setHeader(key: String, value: String)

  var status: Int
}