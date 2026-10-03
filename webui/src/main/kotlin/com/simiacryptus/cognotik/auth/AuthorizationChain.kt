package com.simiacryptus.cognotik.auth

import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Chains multiple [AuthorizationStep]s together. Each step must succeed before the next is attempted.
 * The final [onSuccess] callback is invoked only if all steps pass.
 */
class AuthorizationChain(
  val steps: List<AuthorizationStep>
) {
  private val sessionStatusListeners = CopyOnWriteArrayList<(AuthorizationSession) -> Unit>()

  /**
   * Register a listener that is invoked whenever an [AuthorizationSession]'s status changes
   * to [SessionStatus.COMPLETED] or [SessionStatus.FAILED].
   */
  fun onSessionStatusChanged(listener: (AuthorizationSession) -> Unit) {
    sessionStatusListeners.add(listener)
    log.debug("Registered session status listener (total: {})", sessionStatusListeners.size)
  }

  /**
   * Notify all registered listeners of a session status change.
   * Called internally when a session's status is updated via web callbacks.
   */
  fun notifySessionStatusChanged(session: AuthorizationSession) {
    for (listener in sessionStatusListeners) {
      try {
        listener(session)
      } catch (e: Exception) {
        // Prevent one listener from breaking others, but don't hide the problem
        log.warn("Session status listener failed for session {}", session.sessionId, e)
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(AuthorizationChain::class.java)

    /** Active authorization sessions keyed by session ID */
    private val activeSessions = ConcurrentHashMap<String, AuthorizationSession>()

    /** Maximum session age before automatic cleanup (30 minutes) */
    private val SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30)

    /** Readable step name; anonymous classes and lambdas have an empty simpleName. */
    private fun stepName(step: AuthorizationStep): String =
      step.javaClass.simpleName.ifEmpty { step.javaClass.name }

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
      val session = activeSessions[sessionId]
      if (session == null) {
        log.debug("Authorization session not found (unknown or expired)")
      }
      return session
    }

    /**
     * Remove a completed or expired session.
     */
    fun removeSession(sessionId: String) {
      val removed = activeSessions.remove(sessionId)
      if (removed != null) {
        log.debug("Removed authorization session {} (status={})", sessionId, removed.status)
      }
    }

    /**
     * Remove sessions that have exceeded the timeout.
     */
    private fun cleanupExpiredSessions() {
      val now = System.currentTimeMillis()
      val expired = activeSessions.entries.filter { (_, session) ->
        now - session.createdAt > SESSION_TIMEOUT_MS
      }
      for ((id, session) in expired) {
        if (activeSessions.remove(id) != null) {
          log.info(
            "Cleaned up expired authorization session: {} (age={}s, status={})",
            id, (now - session.createdAt) / 1000, session.status
          )
        }
      }
    }
  }

  /**
   * Tracks the state of a web-based authorization flow.
   * This class is not thread-safe; callers should synchronize access if needed.
   */
  inner class AuthorizationSession(
    val sessionId: String,
    val chain: AuthorizationChain,
    val createdAt: Long = System.currentTimeMillis()
  ) {
    @Volatile
    var currentStepIndex: Int = 0

    @Volatile
    var status: SessionStatus = SessionStatus.IN_PROGRESS
      set(value) {
        synchronized(this) {
          if (field == value) return // No change
          log.debug("Authorization session {} status changed: {} -> {}", sessionId, field, value)
          field = value
          // Notify listeners of status change
          notifySessionStatusChanged(this)
        }
      }

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
    log.debug("Executing authorization chain with {} step(s)", steps.size)
    executeStep(0, onSuccess, onFailure)
  }

  /**
   * Start a web-based authorization flow.
   * Returns a session that can be used to track progress and render HTML.
   *
   * @return The authorization session, or null if there are no steps
   */
  fun startWebFlow(): AuthorizationSession? {
    if (steps.isEmpty()) {
      log.debug("Not starting web authorization flow: chain has no steps")
      return null
    }
    val sessionId = UUID.randomUUID().toString()
    val session = AuthorizationSession(
      sessionId = sessionId,
      chain = this
    )
    // Skip any non-interactive steps at the beginning
    advancePastNonInteractiveSteps(session)
    activeSessions[sessionId] = session
    log.info(
      "Started web authorization flow, sessionId={}, totalSteps={}, currentStep={}, status={}",
      sessionId, steps.size, session.currentStepIndex + 1, session.status
    )
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
        session.currentStepIndex + 1, steps.size, stepName(step)
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
        log.error(
          "Exception executing non-interactive step {}/{} ({}) in session {}",
          session.currentStepIndex + 1, steps.size, stepName(step), session.sessionId, e
        )
        // Set the reason first so status listeners can see it
        session.failureReason = "Step execution error: ${e.message}"
        session.status = SessionStatus.FAILED
        break
      }
      if (stepFailed) {
        log.warn(
          "Non-interactive step {}/{} ({}) failed in session {}: {}",
          session.currentStepIndex + 1, steps.size, stepName(step), session.sessionId, failReason
        )
        session.failureReason = failReason
        session.status = SessionStatus.FAILED
        break
      }
      if (stepPassed) {
        log.debug(
          "Non-interactive step {}/{} ({}) passed in session {}",
          session.currentStepIndex + 1, steps.size, stepName(step), session.sessionId
        )
        session.currentStepIndex++
      } else {
        // Step didn't call either callback synchronously - treat as needing interaction
        log.debug(
          "Non-interactive step {}/{} ({}) did not invoke callback synchronously, treating as interactive",
          session.currentStepIndex + 1, steps.size, stepName(step)
        )
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
    log.debug("Executing authorization step {}/{}: {}", index + 1, steps.size, stepName(step))
    try {
      step.authorize(
        onSuccess = {
          log.debug("Authorization step {}/{} ({}) succeeded", index + 1, steps.size, stepName(step))
          executeStep(index + 1, onSuccess, onFailure)
        },
        onFailure = { reason ->
          log.warn("Authorization step {}/{} ({}) failed: {}", index + 1, steps.size, stepName(step), reason)
          onFailure(reason)
        }
      )
    } catch (e: Exception) {
      log.error("Exception in authorization step {}/{} ({})", index + 1, steps.size, stepName(step), e)
      onFailure("Step execution error: ${e.message}")
    }
  }
}