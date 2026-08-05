package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Platform user identity.
 *
 * Identity is [id] (which defaults to [email]); this matches what `Claim.userId`
 * and `SessionMetadata.ownerId` actually persist. See REVIEW.md §3.2.
 */
data class User(
  @get:JsonProperty("email") val email: String,
  @get:JsonProperty("name") val name: String = email,
  @get:JsonProperty("id") val id: String = email,
) {

  /** Typed form of [id]. */
  @get:JsonIgnore
  val userId: UserId
    get() = UserId(id)

  /** Email with the local part redacted, safe for logs. */
  @get:JsonIgnore
  val redactedEmail: String
    get() = redact(email)

  /**
   * Deliberately redacted: this value ends up in log lines.
   * Use [email] explicitly when the real address is required.
   */
  override fun toString() = redactedEmail

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as User
    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    @Deprecated(
      "Sentinel user with overlapping semantics versus null and the configured default " +
          "user; prefer Principal.Anonymous / Principal.System.",
      ReplaceWith("Principal.Anonymous")
    )
    val NULL: User = User(
      id = "0",
      email = "null@localhost"
    )

    fun redact(email: String): String {
      val at = email.indexOf('@')
      if (at <= 0) return "***"
      return "${email.first()}***@${email.substring(at + 1)}"
    }
  }

}

/**
 * Global mutable default user.
 *
 * Retained as a source-compatible proxy onto [ApplicationServicesConfig.defaultUser];
 * new code should inject a [Principal] instead of reading process-wide state.
 */
@Deprecated(
  "Global mutable state; inject a Principal or read ApplicationServicesConfig.defaultUser.",
  ReplaceWith("ApplicationServicesConfig.defaultUser")
)
var defaultUser: User
  get() = ApplicationServicesConfig.defaultUser
  set(value) {
    ApplicationServicesConfig.defaultUser = value
  }

/**
 * Servlet-based user resolution.
 *
 * Moved to the web-facing package so that `platform.model` carries no framework
 * dependencies (REVIEW.md §3.2). This alias remains so existing implementations
 * and call sites keep compiling.
 */
@Deprecated(
  "Moved out of platform.model to keep the model framework-free.",
  ReplaceWith("com.simiacryptus.cognotik.platform.web.UserProvider")
)
interface UserProvider : com.simiacryptus.cognotik.platform.web.UserProvider