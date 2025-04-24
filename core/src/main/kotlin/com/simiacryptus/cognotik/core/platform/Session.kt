package com.simiacryptus.cognotik.core.platform


import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.*
import kotlin.random.Random

data class Session(
  val sessionId: String
) {
  init {
    validateSessionId()
  }

  override fun toString() = sessionId
  fun isGlobal(): Boolean = sessionId.startsWith("G-")

  companion object {
    fun long64() = Base64.getEncoder().encodeToString(ByteBuffer.allocate(8).putLong(Random.Default.nextLong()).array())
      .toString().replace("=", "").replace("/", ".").replace("+", "-")

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
    }.take(4)

    fun parseSessionID(sessionID: String): Session {
      val session = Session(sessionID)
      session.validateSessionId()
      return session
    }
  }

  private fun validateSessionId() {
    if (!sessionId.matches("""([GU]-)?\d{8}-[\w+.\-]{4}""".toRegex())) {
      throw IllegalArgumentException("Invalid session ID: $this")
    }
  }
}