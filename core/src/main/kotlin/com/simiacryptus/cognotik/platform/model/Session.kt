package com.simiacryptus.cognotik.platform.model

import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.Base64
import kotlin.random.Random

open class Session(
  val sessionId: String
) {


  init {
    validateSessionId()
  }

  override fun toString() = sessionId
  fun isGlobal(): Boolean = sessionId.startsWith("G-")

  companion object {
    val NULL = object : Session("") {
      override fun validateSessionId() {
        // No validation for NULL session
      }
    }
    fun long64(): String {
      val src = ByteBuffer.allocate(8).putLong(Random.Default.nextLong()).array()
      return Base64.getEncoder().encodeToString(src)
        .toString().replace("=", "").replace("/", ".").replace("+", "-")
    }

    fun validateSessionId(session: Session) {
      session.validateSessionId()
    }

    fun newGlobalID(): Session {
      val yyyyMMdd = LocalDate.now().toString().replace("-", "")
      return Session("G-$yyyyMMdd-${id2()}")
    }

    fun newUserID(): Session {
      val yyyyMMdd = LocalDate.now().toString().replace("-", "")
      return Session("U-$yyyyMMdd-${id2()}")
    }

    private fun id2() = long64().filter {
      when (it) {
        in 'a'..'z' -> true
        in 'A'..'Z' -> true
        in '0'..'9' -> true
        else -> false
      }
    }.take(8)

    fun parseSessionID(sessionID: String): Session {
      val session = Session(sessionID)
      session.validateSessionId()
      return session
    }
  }

  internal open fun validateSessionId() {
    if (!sessionId.matches("""([GU]-)?\d{8}-[\w+.\-]{4,12}""".toRegex())) {
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