package com.simiacryptus.cognotik.auth

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a pending authorization flow that can be triggered and completed via the web UI.
 */
class PendingAuthorization(
  val id: String = UUID.randomUUID().toString(),
  val pluginName: String,
  val chain: AuthorizationChain,
  val onSuccess: () -> Unit,
  val onFailure: (reason: String) -> Unit,
   @Volatile var status: Status = Status.PENDING
) {
    enum class Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
     override fun toString(): String =
         "PendingAuthorization(id=$id, pluginName=$pluginName, status=$status)"


    companion object {
        private val log = LoggerFactory.getLogger(PendingAuthorization::class.java)
        private val pendingAuthorizations = ConcurrentHashMap<String, PendingAuthorization>()

        /**
         * Register a new pending authorization flow.
         * Returns the ID that can be used to trigger it from the web UI.
         */
        fun register(pending: PendingAuthorization): String {
            pendingAuthorizations[pending.id] = pending
            log.info("Registered pending authorization '{}' for plugin '{}'", pending.id, pending.pluginName)
            return pending.id
        }

        /**
         * Get all pending authorizations.
         */
        fun getAll(): Map<String, PendingAuthorization> = pendingAuthorizations.toMap()


        /**
         * Get a specific pending authorization by ID.
         */
        fun get(id: String): PendingAuthorization? = pendingAuthorizations[id]

        /**
         * Remove a pending authorization (e.g., after completion or cancellation).
         */
        fun remove(id: String): PendingAuthorization? = pendingAuthorizations.remove(id)

        /**
         * Execute a pending authorization flow by ID.
         * This should be called from the web UI when the user is ready to authorize.
         */
        fun execute(id: String) {
            val pending = pendingAuthorizations[id]
            if (pending == null) {
                log.warn("No pending authorization found with id '{}'", id)
                return
            }
            if (pending.status != Status.PENDING) {
                log.warn("Authorization '{}' is not in PENDING state (current: {})", id, pending.status)
                return
            }
            pending.status = Status.IN_PROGRESS
            log.info("Executing pending authorization '{}' for plugin '{}'", id, pending.pluginName)
             try {
                 pending.chain.execute(
                     onSuccess = {
                         pending.status = Status.COMPLETED
                         log.info("Authorization '{}' completed successfully", id)
                         try {
                             pending.onSuccess()
                         } catch (e: Exception) {
                             log.error("Exception in onSuccess callback for authorization '{}': {}", id, e.message, e)
                         }
                     },
                     onFailure = { reason ->
                         pending.status = Status.FAILED
                         log.warn("Authorization '{}' failed: {}", id, reason)
                         try {
                             pending.onFailure(reason)
                         } catch (e: Exception) {
                             log.error("Exception in onFailure callback for authorization '{}': {}", id, e.message, e)
                         }
                     }
                 )
             } catch (e: Exception) {
                 pending.status = Status.FAILED
                 log.error("Exception executing authorization '{}': {}", id, e.message, e)
                 try {
                     pending.onFailure("Execution error: ${e.message}")
                 } catch (callbackError: Exception) {
                     log.error("Exception in onFailure callback for authorization '{}': {}", id, callbackError.message, callbackError)
                 }
             }
        }
    }
}