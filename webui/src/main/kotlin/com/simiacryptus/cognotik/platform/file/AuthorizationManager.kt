package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory.getLogger
import java.util.*

/**
 * Classpath-file backed implementation of [AuthorizationInterface].
 *
 * Permissions are declared in `/permissions/<op>.txt` (global) and
 * `/permissions/<package/path>/<op>.txt` (application scoped).
 *
 * Two contract changes versus the previous implementation:
 *  - authorization is keyed on ([ResourceRef], [Principal], [OperationType])
 *    rather than `Class<*>`/`User?` (REVIEW.md §3.5, §4.1);
 *  - the implication matrix is taken from [OperationType.implies] instead of
 *    being re-derived here (REVIEW.md §3.10), so e.g. an `admin.txt` grant
 *    now satisfies a `Read` check.
 *
 * Fail-secure: any error results in a denial, never an exception.
 */
open class AuthorizationManager : AuthorizationInterface {

  override fun isAuthorized(
    resource: ResourceRef?,
    principal: Principal,
    operationType: OperationType,
  ): Boolean = try {
    log.debug("Checking authorization for {} / {} on {}", principal, operationType, resource)
    when (principal) {
      is Principal.System -> true
      else -> {
        val user = principal.user
        val granted = OperationType.values().any { grant ->
          grant.implies(operationType) && isGranted(resource, user, grant)
        }
        if (granted) {
          log.info("{} authorized for {} on {}", principal, operationType, resource)
        } else {
          log.warn("{} not authorized for {} on {}", principal, operationType, resource)
        }
        granted
      }
    }
  } catch (e: Exception) {
    log.error("Error checking authorization; denying request", e)
    false
  }

  override fun authorizedOperations(
    resource: ResourceRef?,
    principal: Principal,
  ): Set<OperationType> = try {
    if (principal is Principal.System) {
      OperationType.values().toSet()
    } else {
      val user = principal.user
      OperationType.values()
        .filter { isGranted(resource, user, it) }
        .flatMap { setOf(it) + it.impliedOperations }
        .toSet()
    }
  } catch (e: Exception) {
    log.error("Error enumerating authorized operations; denying all", e)
    emptySet()
  }

  @Deprecated(
    "Class<*> keys cannot express instance scope; use ResourceRef/Principal.",
    ReplaceWith("isAuthorized(ResourceRef.of(applicationClass), Principal.of(user), operationType)")
  )
  open fun isAuthorized(
    applicationClass: Class<*>?,
    user: User?,
    operationType: OperationType,
  ): Boolean = isAuthorized(ResourceRef.of(applicationClass), Principal.of(user), operationType)

  /** Is [operationType] granted *directly* (no implication expansion)? */
  private fun isGranted(resource: ResourceRef?, user: User?, operationType: OperationType): Boolean {
    val opName = operationType.name.lowercase(Locale.getDefault())
    if (isUserAuthorized("/permissions/$opName.txt", user)) return true
    val packagePath = resource?.applicationClass?.`package`?.name?.replace('.', '/') ?: return false
    log.debug("Checking application-scoped permissions at /permissions/{}/{}.txt", packagePath, opName)
    return isUserAuthorized("/permissions/$packagePath/$opName.txt", user)
  }

  private fun isUserAuthorized(permissionPath: String, user: User?): Boolean {
    log.debug("Checking user authorization at path: {}", permissionPath)
    return javaClass.getResourceAsStream(permissionPath)?.use { stream ->
      stream.bufferedReader().readLines().any { line -> matches(user, line) }
    } ?: run {
      log.debug("Permission file not found: {}", permissionPath)
      false
    }
  }

  open fun matches(user: User?, line: String): Boolean {
    val trimmed = line.trim()
    return !(trimmed.isEmpty() || trimmed.startsWith("#")) && when {
      trimmed.equals(user?.email, ignoreCase = true) -> {
        log.debug("Exact match found for user: {}", user)
        true
      }

      trimmed.startsWith("@") && user?.email?.endsWith(trimmed.substring(1), ignoreCase = true) == true -> {
        log.debug("Domain match found for user: {}", user)
        true
      }

      trimmed == "." && user != null -> {
        log.debug("Any authenticated user match for: {}", user)
        true
      }

      trimmed == "*" -> {
        log.debug("Any user (including anonymous) match")
        true
      }

      else -> false
    }
  }

  companion object {
    private val log = getLogger(AuthorizationManager::class.java)
  }
}