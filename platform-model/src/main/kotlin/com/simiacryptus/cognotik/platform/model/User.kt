package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.AuthenticationInterface
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


/**
 * Platform user identity.
 *
 * Identity is [id] (which defaults to [email]); this matches what `Claim.userId`
 * and `SessionMetadata.ownerId` actually persist. See REVIEW.md §3.2.
  *
  * Wire format is intentionally minimal: `email`, `name`, `signature`. Any other
  * property (including legacy `provider` / `authCookies` fields emitted by older
  * servers) is ignored on read so that cross-version tokens keep parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
  @get:JsonProperty("email") val email: String,
  @get:JsonProperty("name") val name: String = email,
  //@get:JsonProperty("provider") val provider: String? = null,
) {
   @get:JsonIgnore
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
    * HMAC-SHA256 signature over this user's identity, computed with the configured
    * signing key (see [signingKey]). This is safe to transmit: it contains no secret
    * material and allows a peer sharing the same key to confirm the identity was
    * issued by a trusted server.
    */
   @get:JsonProperty("signature")
   val signature: String
     get() = sign(signingPayload())
   /** Canonical, delimiter-safe representation of the identity that gets signed. */
   @JsonIgnore
   fun signingPayload(): String =
     listOf(SIGNATURE_VERSION, enc(id), enc(email), enc(name)).joinToString(FIELD_DELIMITER)

/** Verify a signature previously produced by [signature] (constant-time). */
@JvmOverloads
fun isSignatureValid(candidate: String?, key: String = signingKey): Boolean =
  verify(signingPayload(), candidate, key)

   /**
    * Produce a self-contained, expiring token carrying this user's identity, for
    * passing a validated user between servers that share the signing key.
    *
    * Format: `base64url(payload) + "." + hexHmac(payload)`
    *
    * @param ttlSeconds lifetime of the token; `<= 0` produces a non-expiring token.
    */
   @JvmOverloads
   fun toSignedToken(ttlSeconds: Long = DEFAULT_TOKEN_TTL_SECONDS, key: String = signingKey): String {
     val expiresAt = if (ttlSeconds <= 0) 0L else nowSeconds() + ttlSeconds
     val payload = tokenPayload(email, name, expiresAt)
     return enc(payload) + TOKEN_DELIMITER + sign(payload, key)
   }


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
     /** Bumped whenever the signed payload layout changes; old signatures then fail validation. */
     const val SIGNATURE_VERSION = "v1"
     /** Environment variable that overrides the (insecure) built-in default key. */
     const val SIGNING_KEY_ENV = "COGNOTIK_USER_SIGNING_KEY"
     /** System property override, checked after [SIGNING_KEY_ENV]. */
     const val SIGNING_KEY_PROPERTY = "cognotik.user.signingKey"
     /** Default token lifetime for [toSignedToken]. */
     const val DEFAULT_TOKEN_TTL_SECONDS = 300L
     private const val HMAC_ALGORITHM = "HmacSHA256"
     private const val FIELD_DELIMITER = "|"
     private const val TOKEN_DELIMITER = '.'
     /**
      * Hardcoded fallback so signing works out of the box in dev/test.
      * NEVER rely on this in production - set [SIGNING_KEY_ENV].
      */
    private var DEFAULT_SIGNING_KEY = "cognotik-insecure-default-user-signing-key"

    fun DEFAULT_SIGNING_KEY(value: String) {
      DEFAULT_SIGNING_KEY = value
    }

     /** Effective signing key: env var, then system property, then the built-in default. */
     @JvmStatic
     val signingKey: String
       get() = System.getenv(SIGNING_KEY_ENV)?.takeIf { it.isNotBlank() }
         ?: System.getProperty(SIGNING_KEY_PROPERTY)?.takeIf { it.isNotBlank() }
         ?: DEFAULT_SIGNING_KEY
     /** True when no override is configured; callers should warn loudly at startup. */
     @JvmStatic
     val isUsingDefaultSigningKey: Boolean
       get() = signingKey == DEFAULT_SIGNING_KEY
     /** Lowercase hex HMAC-SHA256 of [payload]. */
     @JvmStatic
     @JvmOverloads
     fun sign(payload: String, key: String = signingKey): String {
       require(key.isNotEmpty()) { "Signing key must not be empty" }
       val mac = Mac.getInstance(HMAC_ALGORITHM)
       mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
       return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
     }
     /** Constant-time verification of [signature] against [payload]. */
     @JvmStatic
     @JvmOverloads
     fun verify(payload: String, signature: String?, key: String = signingKey): Boolean {
       if (signature.isNullOrBlank()) return false
       val expected = sign(payload, key).toByteArray(Charsets.UTF_8)
       val actual = signature.trim().lowercase().toByteArray(Charsets.UTF_8)
       return MessageDigest.isEqual(expected, actual)
     }
     /**
      * Parse and validate a token produced by [User.toSignedToken].
      *
      * @return the reconstructed user, or null if the token is malformed,
      *         the signature does not match, or the token has expired.
      */
     @JvmStatic
     @JvmOverloads
     fun fromSignedToken(token: String?, key: String = signingKey): User? {
       if (token.isNullOrBlank()) return null
       val split = token.lastIndexOf(TOKEN_DELIMITER)
       if (split <= 0 || split == token.length - 1) return null
       val payload = try {
         dec(token.substring(0, split))
       } catch (e: IllegalArgumentException) {
         return null
       }
       if (!verify(payload, token.substring(split + 1), key)) return null
       val parts = payload.split(FIELD_DELIMITER)
       if (parts.size != 4 || parts[0] != SIGNATURE_VERSION) return null
       val expiresAt = parts[3].toLongOrNull() ?: return null
       if (expiresAt > 0 && expiresAt < nowSeconds()) return null
       return try {
         User(email = dec(parts[1]), name = dec(parts[2]))
       } catch (e: IllegalArgumentException) {
         null
       }
     }
     private fun tokenPayload(email: String, name: String, expiresAt: Long) =
       listOf(SIGNATURE_VERSION, enc(email), enc(name), expiresAt.toString()).joinToString(FIELD_DELIMITER)
     private fun nowSeconds() = System.currentTimeMillis() / 1000
     /** base64url (no padding) so fields never collide with [FIELD_DELIMITER]. */
     private fun enc(value: String) =
       Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
     private fun dec(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

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
  /**
   * Resolve this user's auth cookies from the local authentication manager.
   *
   * MUST remain `@JsonIgnore`: the method name matches the JavaBean getter pattern, so without
   * this annotation Jackson exposes it as a read-only `authCookies` property. That caused two
   * production defects:
   *  - serializing a [User] performed a database round-trip (and leaked the session token into
   *    the serialized payload / encrypted API key blob), and
   *  - deserializing a payload containing `authCookies` used USE_GETTERS_AS_SETTERS and tried to
   *    mutate the immutable map returned here, failing with
   *    "Operation is not supported for read-only collection".
   *
   * Auth cookies are host-local state and are deliberately *not* part of this object's wire form.
   * Returns an empty map when no token is available for this user.
   */
  @JsonIgnore
  fun getAuthCookies(): Map<String, String?> {
    val services = ApplicationServices.services ?: throw IllegalStateException("ApplicationServices not initialized")
    val tokenMetadata = services.fileApplicationServices(ApplicationServicesConfig.dataStorageRoot)
      .authenticationManager.listTokens(this).firstOrNull() ?: return emptyMap()
    return mapOf(
      AuthenticationInterface.AUTH_COOKIE to tokenMetadata.token,
      "USER" to name,
      "EMAIL" to email
    )
  }
   @JsonIgnore

  /**
   * WARNING: [signature] is *recomputed* from this instance, so this check can only ever fail if
   * the signing key is unusable - a transported signature is currently dropped on deserialization
   * (it has no creator parameter). To make this a real check, pass the received signature in:
   * `user.requireValid(receivedSignature)`.
   */
  @JvmOverloads
  fun requireValid(candidateSignature: String? = signature): User {
    if (!isSignatureValid(candidateSignature)) throw IllegalArgumentException("Invalid user signature")
    return this
  }
}


fun String.hexHash(): String {
  val digest = java.security.MessageDigest.getInstance("SHA-256")
  val hashBytes = digest.digest(this.toByteArray(Charsets.UTF_8))
  return hashBytes.joinToString("") { "%02x".format(it) }
}