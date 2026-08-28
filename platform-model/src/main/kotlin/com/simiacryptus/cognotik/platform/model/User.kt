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
  //@get:JsonProperty("provider") val provider: String? = null,
) {
  val provider: String = ""
  @get:JsonIgnore
  val id: String by lazy { (email+provider).hexHash().take(20) }

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
  override fun toString() = id

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
      email = "null@localhost"
    )

    fun redact(email: String): String {
      val at = email.indexOf('@')
      if (at <= 0) return "***"
      return "${email.first()}***@${email.substring(at + 1)}"
    }
  }

}


fun String.hexHash(): String {
  val digest = java.security.MessageDigest.getInstance("SHA-256")
  val hashBytes = digest.digest(this.toByteArray(Charsets.UTF_8))
  return hashBytes.joinToString("") { "%02x".format(it) }
}
