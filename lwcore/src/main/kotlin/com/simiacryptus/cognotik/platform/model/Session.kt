package com.simiacryptus.cognotik.platform.model

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64

open class Session(
  val sessionId: String
) {

  /*
   * NOTE: this init block calls an `open` member, which is a known hazard
   * (REVIEW.md §3.1). It is retained only because `Session.NULL` relies on
   * overriding validation; new code should not subclass Session.
   */
  init {
    @Suppress("LeakingThis")
    validateSessionId()
  }

  override fun toString() = sessionId

  fun isGlobal(): Boolean = sessionId.startsWith("G-")

  /**
   * True for the [NULL] sentinel (and any other empty-id session).
   * Storage implementations should reject such sessions rather than deriving
   * a directory from them.
   */
  fun isNull(): Boolean = sessionId.isEmpty()

  fun toGlobal(): Session = when {
    isGlobal() -> this
    else -> Session("G-${sessionId.removePrefix("U-")}")
  }

  companion object {

    private val secureRandom = SecureRandom()

    /** Alphabet used to generate session id suffixes; every character is valid in [SESSION_ID_REGEX]. */
    const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** Canonical session id syntax. */
    val SESSION_ID_REGEX = """([GU]-)?\d{8}-[\w+.\-]{4,12}""".toRegex()

    @Deprecated(
      "Sentinel session with an invalid, empty id; prefer a nullable Session. " +
          "Storage boundaries must reject it explicitly.",
      ReplaceWith("null")
    )
    val NULL = object : Session("") {
      override fun validateSessionId() {
        // No validation for NULL session
      }
    }

    /**
     * Generates a cryptographically random id of [length] characters drawn from [ID_ALPHABET].
     *
     * Unlike the previous base64-filtering approach, the result is always exactly
     * [length] characters and always passes [isValid] when used as a suffix.
     */
    fun randomId(length: Int = 8): String = buildString(length) {
      repeat(length) { append(ID_ALPHABET[secureRandom.nextInt(ID_ALPHABET.length)]) }
    }

    fun validateSessionId(session: Session) {
      session.validateSessionId()
    }

    /** @return true if [sessionID] is syntactically valid. */
    fun isValid(sessionID: String): Boolean = SESSION_ID_REGEX.matches(sessionID)

    /**
     * Non-throwing counterpart of [parseSessionID], for untrusted input.
     *
     * @return the parsed session, or null when [sessionID] is invalid
     */
    fun tryParse(sessionID: String): Session? = if (isValid(sessionID)) Session(sessionID) else null

    fun newGlobalID(): Session {
      val yyyyMMdd = LocalDate.now().toString().replace("-", "")
      return Session("G-$yyyyMMdd-${id2()}")
    }

    fun newUserID(): Session {
      val yyyyMMdd = LocalDate.now().toString().replace("-", "")
      return Session("U-$yyyyMMdd-${id2()}")
    }

    private fun id2() = randomId(8)

    fun parseSessionID(sessionID: String): Session {
      val session = Session(sessionID)
      session.validateSessionId()
      return session
    }
  }

  internal open fun validateSessionId() {
    if (!SESSION_ID_REGEX.matches(sessionId)) {
      throw IllegalArgumentException("Invalid session ID: $this")
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Session) return false

    if (sessionId != other.sessionId) return false

    return true
  }

  override fun hashCode(): Int {
    return sessionId.hashCode()
  }
}