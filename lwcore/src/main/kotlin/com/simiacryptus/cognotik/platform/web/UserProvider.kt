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
    response: HttpServletResponse?
  ): User?
}