package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import java.util.*

open class AuthorizationManager : AuthorizationInterface {

    override fun isAuthorized(
        applicationClass: Class<*>?,
        user: User?,
        operationType: AuthorizationInterface.OperationType,
    ) = try {
        log.debug(
            "Checking authorization for user: {}, operation: {}, application: {}",
            user,
            operationType,
            applicationClass
        )
        if (isUserAuthorized("/permissions/${operationType.name.lowercase(Locale.getDefault())}.txt", user)) {
            log.info("User {} authorized for {} globally", user, operationType)
            true
        } else if (null != applicationClass) {
            val packagePath = applicationClass.`package`.name.replace('.', '/')
            val opName = operationType.name.lowercase(Locale.getDefault())
            log.debug(
                "Checking application-specific authorization at path: /permissions/{}/{}.txt",
                packagePath,
                opName
            )
            if (isUserAuthorized("/permissions/$packagePath/$opName.txt", user)) {
                log.info("User {} authorized for {} on {}", user, operationType, applicationClass)
                true
            } else {
                log.warn("User {} not authorized for {} on {}", user, operationType, applicationClass)
                false
            }
        } else {
            log.warn("User {} not authorized for {} globally", user, operationType)
            false
        }
    } catch (e: Exception) {
        log.error("Error checking authorization", e)
        false
    }

    private fun isUserAuthorized(permissionPath: String, user: User?): Boolean {
        log.debug("Checking user authorization at path: {}", permissionPath)
        return javaClass.getResourceAsStream(permissionPath)?.use { stream ->
            val lines = stream.bufferedReader().readLines()
            log.trace("Permission file contents: {}", lines)
            lines.any { line ->
                matches(user, line)
            }
        } ?: run {
            log.warn("Permission file not found: {}", permissionPath)
            false
        }
    }

    open fun matches(user: User?, line: String): Boolean {
        log.trace("Matching user {} against line: {}", user, line)
        return when {
            line.equals(user?.email, ignoreCase = true) -> {
                log.debug("Exact match found for user: {}", user)
                true
            }

            line.startsWith("@") && user?.email?.endsWith(line.substring(1)) == true -> {
                log.debug("Domain match found for user: {}", user)
                true
            }

            line == "." && user != null -> {
                log.debug("Any authenticated user match for: {}", user)
                true
            }

            line == "*" -> {
                log.debug("Any user (including anonymous) match")
                true
            }

            else -> {
                log.trace("No match found for user: {} and line: {}", user, line)
                false
            }
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(AuthorizationManager::class.java)
    }
}