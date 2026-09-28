package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest

/**
 * Resolves the authenticated [User] for an inbound HTTP request.
 *
 * Lives in the web package (not `platform.model`) so the domain model has no
 * `jakarta.servlet` dependency.
 */
interface UserProvider {
  fun authenticate(
    request: HttpServletRequest
  ): User?

}