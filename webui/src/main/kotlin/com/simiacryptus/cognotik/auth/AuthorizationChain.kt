package com.simiacryptus.cognotik.auth

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Chains multiple [AuthorizationStep]s together. Each step must succeed before the next is attempted.
 * The final [onSuccess] callback is invoked only if all steps pass.
 */
class AuthorizationChain(
    private val steps: List<AuthorizationStep>
) {
    companion object {
        private val log = LoggerFactory.getLogger(AuthorizationChain::class.java)
         /** Active authorization sessions keyed by session ID */
         private val activeSessions = ConcurrentHashMap<String, AuthorizationSession>()

         /** Maximum session age before automatic cleanup (30 minutes) */
         private val SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30)

        /**
         * Builder DSL for constructing an [AuthorizationChain].
         */
        fun build(block: Builder.() -> Unit): AuthorizationChain {
            val builder = Builder()
            builder.block()
             require(builder.steps.isNotEmpty()) { "AuthorizationChain must have at least one step" }
            return AuthorizationChain(builder.steps.toList())
        }

         /**
          * Get an active session by ID. Returns null if session not found or expired.
          */
         fun getSession(sessionId: String): AuthorizationSession? {
             cleanupExpiredSessions()
             return activeSessions[sessionId]
         }

         /**
          * Remove a completed or expired session.
          */
         fun removeSession(sessionId: String) {
             activeSessions.remove(sessionId)
         }

         /**
          * Remove sessions that have exceeded the timeout.
          */
         private fun cleanupExpiredSessions() {
             val now = System.currentTimeMillis()
             val expired = activeSessions.entries.filter { (_, session) ->
                 now - session.createdAt > SESSION_TIMEOUT_MS
             }
             for ((id, _) in expired) {
                 activeSessions.remove(id)
                 log.info("Cleaned up expired authorization session: {}", id)
             }
         }
    }

     /**
      * Tracks the state of a web-based authorization flow.
      * This class is not thread-safe; callers should synchronize access if needed.
      */
     class AuthorizationSession(
         val sessionId: String,
         val chain: AuthorizationChain,
         val createdAt: Long = System.currentTimeMillis()
     ) {
         @Volatile
         var currentStepIndex: Int = 0
         @Volatile
         var status: SessionStatus = SessionStatus.IN_PROGRESS
         @Volatile
         var failureReason: String? = null
         val metadata: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

         val currentStep: AuthorizationStep?
             get() = if (currentStepIndex < chain.steps.size) chain.steps[currentStepIndex] else null
         val isComplete: Boolean
             get() = status != SessionStatus.IN_PROGRESS
         val totalSteps: Int
             get() = chain.steps.size

         override fun toString(): String =
             "AuthorizationSession(sessionId=$sessionId, step=${currentStepIndex + 1}/$totalSteps, status=$status)"
     }

     enum class SessionStatus {
         IN_PROGRESS,
         COMPLETED,
         FAILED
     }


    class Builder {
        val steps = mutableListOf<AuthorizationStep>()

        fun step(step: AuthorizationStep) {
            steps.add(step)
        }
    }

    /**
     * Execute the chain. Each step is run in order; if any step fails, [onFailure] is called
     * and no further steps are attempted.
     */
    fun execute(onSuccess: () -> Unit, onFailure: (reason: String) -> Unit) {
         if (steps.isEmpty()) {
             log.info("Authorization chain has no steps, succeeding immediately")
             onSuccess()
             return
         }
        executeStep(0, onSuccess, onFailure)
    }

     /**
      * Start a web-based authorization flow.
      * Returns a session that can be used to track progress and render HTML.
      *
      * @return The authorization session, or null if there are no steps
      */
     fun startWebFlow(): AuthorizationSession? {
         if (steps.isEmpty()) return null
         val sessionId = UUID.randomUUID().toString()
         val session = AuthorizationSession(
             sessionId = sessionId,
             chain = this
         )
         // Skip any non-interactive steps at the beginning
         advancePastNonInteractiveSteps(session)
         activeSessions[sessionId] = session
         log.info("Started web authorization flow, sessionId={}, totalSteps={}", sessionId, steps.size)
         return session
     }

     /**
      * Advance past non-interactive steps by executing them programmatically.
      * Note: This assumes non-interactive steps invoke callbacks synchronously.
      */
     private fun advancePastNonInteractiveSteps(session: AuthorizationSession) {
         while (session.currentStepIndex < steps.size) {
             val step = steps[session.currentStepIndex]
             if (step.requiresWebInteraction()) {
                 break // Stop at the first interactive step
             }
             // Execute non-interactive step synchronously
             var stepPassed = false
             var stepFailed = false
             var failReason = ""
             log.debug(
                 "Auto-executing non-interactive step {}/{}: {}",
                 session.currentStepIndex + 1, steps.size, step.javaClass.simpleName
             )
             try {
                 step.authorize(
                     onSuccess = { stepPassed = true },
                     onFailure = { reason ->
                         stepFailed = true
                         failReason = reason
                     }
                 )
             } catch (e: Exception) {
                 log.error("Exception executing non-interactive step {}/{}: {}",
                     session.currentStepIndex + 1, steps.size, e.message, e)
                 session.status = SessionStatus.FAILED
                 session.failureReason = "Step execution error: ${e.message}"
                 break
             }
             if (stepFailed) {
                 session.status = SessionStatus.FAILED
                 session.failureReason = failReason
                 break
             }
             if (stepPassed) {
                 session.currentStepIndex++
             } else {
                 // Step didn't call either callback synchronously - treat as needing interaction
                 log.debug("Non-interactive step {}/{} did not invoke callback synchronously, treating as interactive",
                     session.currentStepIndex + 1, steps.size)
                 break
             }
         }
     }


    private fun executeStep(index: Int, onSuccess: () -> Unit, onFailure: (reason: String) -> Unit) {
        if (index >= steps.size) {
            log.info("All {} authorization steps passed", steps.size)
            onSuccess()
            return
        }
        val step = steps[index]
        log.debug("Executing authorization step {}/{}: {}", index + 1, steps.size, step.javaClass.simpleName)
         try {
             step.authorize(
                 onSuccess = { executeStep(index + 1, onSuccess, onFailure) },
                 onFailure = { reason ->
                     log.debug("Authorization step {}/{} failed: {}", index + 1, steps.size, reason)
                     onFailure(reason)
                 }
             )
         } catch (e: Exception) {
             log.error("Exception in authorization step {}/{}: {}", index + 1, steps.size, e.message, e)
             onFailure("Step execution error: ${e.message}")
         }
    }
}