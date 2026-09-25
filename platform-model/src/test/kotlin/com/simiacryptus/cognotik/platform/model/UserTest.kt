package com.simiacryptus.cognotik.platform.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64

class UserTest {

  // ---------------------------------------------------------------- helpers

  private fun enc(value: String) =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

  private fun dec(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

  /** Rebuilds the exact wire format of [User.toSignedToken] so we can forge edge cases. */
  private fun forgeToken(
    email: String,
    name: String,
    expiresAt: Long,
    key: String = User.signingKey,
    version: String = User.SIGNATURE_VERSION,
  ): String {
    val payload = listOf(version, enc(email), enc(name), expiresAt.toString()).joinToString("|")
    return enc(payload) + "." + User.sign(payload, key)
  }

  private fun nowSeconds() = System.currentTimeMillis() / 1000

  // ---------------------------------------------------------------- identity

  @Nested
  @DisplayName("identity")
  inner class Identity {

    @Test
    fun `id is a 20 char prefix of the sha256 hex hash of the email`() {
      val user = User(email = "alice@example.com")
      assertEquals("alice@example.com".hexHash().take(20), user.id)
      assertEquals(20, user.id.length)
      assertTrue(user.id.all { it in "0123456789abcdef" }, "id should be lowercase hex")
    }

    @Test
    fun `id is stable across instances and independent of name`() {
      val a = User(email = "bob@example.com", name = "Bob")
      val b = User(email = "bob@example.com", name = "Robert")
      assertEquals(a.id, b.id)
    }

    @Test
    fun `different emails produce different ids`() {
      assertNotEquals(User("a@example.com").id, User("b@example.com").id)
    }

    @Test
    fun `name defaults to email`() {
      val user = User(email = "carol@example.com")
      assertEquals("carol@example.com", user.name)
    }

    @Test
    fun `userId wraps id`() {
      val user = User(email = "dave@example.com")
      assertEquals(UserId(user.id), UserId(user.id))
    }

    @Test
    fun `equality and hashCode are based on id only`() {
      val a = User(email = "eve@example.com", name = "Eve")
      val b = User(email = "eve@example.com", name = "Different Name")
      val c = User(email = "mallory@example.com", name = "Eve")

      assertEquals(a, a)
      assertEquals(a, b)
      assertEquals(a.hashCode(), b.hashCode())
      assertNotEquals(a, c)
      assertFalse(a.equals(null))
      assertFalse(a.equals("eve@example.com"))
    }

    @Test
    fun `toString does not leak the email address`() {
      val user = User(email = "secret@example.com")
      assertEquals(user.id, user.toString())
      assertFalse(user.toString().contains("secret@example.com"))
    }
  }

  // ---------------------------------------------------------------- redaction

  @Nested
  @DisplayName("redaction")
  inner class Redaction {

    @Test
    fun `redacts the local part but keeps the domain`() {
      assertEquals("a***@example.com", User.redact("alice@example.com"))
      assertEquals("a***@example.com", User(email = "alice@example.com").redactedEmail)
    }

    @Test
    fun `fully redacts values without a usable local part`() {
      assertEquals("***", User.redact("@example.com"))
      assertEquals("***", User.redact("no-at-sign"))
      assertEquals("***", User.redact(""))
    }

    @Test
    fun `single character local part is still redacted`() {
      assertEquals("x***@example.com", User.redact("x@example.com"))
    }
  }

  // ---------------------------------------------------------------- signing

  @Nested
  @DisplayName("sign / verify")
  inner class Signing {

    @Test
    fun `sign is deterministic and lowercase hex of 32 bytes`() {
      val a = User.sign("payload", "key")
      val b = User.sign("payload", "key")
      assertEquals(a, b)
      assertEquals(64, a.length)
      assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun `sign matches the known HmacSHA256 test vector`() {
      // RFC-style sanity vector: HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog")
      assertEquals(
        "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
        User.sign("The quick brown fox jumps over the lazy dog", "key")
      )
    }

    @Test
    fun `different keys produce different signatures`() {
      assertNotEquals(User.sign("payload", "k1"), User.sign("payload", "k2"))
    }

    @Test
    fun `sign rejects an empty key`() {
      assertThrows(IllegalArgumentException::class.java) { User.sign("payload", "") }
    }

    @Test
    fun `verify accepts a matching signature`() {
      assertTrue(User.verify("payload", User.sign("payload", "key"), "key"))
    }

    @Test
    fun `verify is tolerant of surrounding whitespace and case`() {
      val sig = User.sign("payload", "key")
      assertTrue(User.verify("payload", "  ${sig.uppercase()}  ", "key"))
    }

    @Test
    fun `verify rejects null blank wrong-key and tampered signatures`() {
      val sig = User.sign("payload", "key")
      assertFalse(User.verify("payload", null, "key"))
      assertFalse(User.verify("payload", "", "key"))
      assertFalse(User.verify("payload", "   ", "key"))
      assertFalse(User.verify("payload", sig, "other-key"))
      assertFalse(User.verify("other-payload", sig, "key"))
      assertFalse(User.verify("payload", sig.dropLast(1) + if (sig.last() == 'a') 'b' else 'a', "key"))
    }
  }

  // ---------------------------------------------------------------- payload

  @Nested
  @DisplayName("signingPayload / signature")
  inner class SigningPayload {

    @Test
    fun `payload is versioned and base64url encoded per field`() {
      val user = User(email = "frank@example.com", name = "Frank|Smith")
      val parts = user.signingPayload().split("|")

      assertEquals(4, parts.size)
      assertEquals(User.SIGNATURE_VERSION, parts[0])
      assertEquals(user.id, dec(parts[1]))
      assertEquals(user.email, dec(parts[2]))
      assertEquals(user.name, dec(parts[3]))
    }

    @Test
    fun `field encoding keeps delimiters from corrupting the layout`() {
      val user = User(email = "a|b@example.com", name = "x|y|z")
      assertEquals(4, user.signingPayload().split("|").size)
      assertTrue(user.isSignatureValid(user.signature))
    }

    @Test
    fun `signature validates against itself`() {
      val user = User(email = "grace@example.com", name = "Grace")
      assertTrue(user.isSignatureValid(user.signature))
    }

    @Test
    fun `signature from another user or key does not validate`() {
      val user = User(email = "grace@example.com")
      val other = User(email = "heidi@example.com")

      assertFalse(user.isSignatureValid(other.signature))
      assertFalse(user.isSignatureValid(user.signature, "a-different-key"))
      assertFalse(user.isSignatureValid(null))
    }

    @Test
    fun `requireValid returns the same instance for a self-consistent user`() {
      val user = User(email = "ivan@example.com")
      assertTrue(user === user.requireValid())
    }
  }

  // ---------------------------------------------------------------- tokens

  @Nested
  @DisplayName("signed tokens")
  inner class SignedTokens {

    @Test
    fun `round trips a user`() {
      val user = User(email = "judy@example.com", name = "Judy")
      val parsed = User.fromSignedToken(user.toSignedToken())

      assertEquals(user, parsed)
      assertEquals("judy@example.com", parsed?.email)
      assertEquals("Judy", parsed?.name)
      assertEquals(user.id, parsed?.id)
    }

    @Test
    fun `round trips names containing delimiters and unicode`() {
      val user = User(email = "k|l@example.com", name = "Ka.te|Ω ✓")
      assertEquals(user.name, User.fromSignedToken(user.toSignedToken())?.name)
      assertEquals(user.email, User.fromSignedToken(user.toSignedToken())?.email)
    }

    @Test
    fun `token has exactly one delimiter separating payload and signature`() {
      val token = User(email = "mike@example.com").toSignedToken()
      val split = token.lastIndexOf('.')
      assertTrue(split > 0)
      assertEquals(64, token.length - split - 1)
    }

    @Test
    fun `non positive ttl produces a non expiring token`() {
      val user = User(email = "nina@example.com")
      val token = user.toSignedToken(ttlSeconds = 0)
      val payload = dec(token.substringBeforeLast('.'))

      assertEquals("0", payload.split("|")[3])
      assertEquals(user, User.fromSignedToken(token))
    }

    @Test
    fun `expired tokens are rejected`() {
      val token = forgeToken("olivia@example.com", "Olivia", expiresAt = nowSeconds() - 1)
      assertNull(User.fromSignedToken(token))
    }

    @Test
    fun `not yet expired tokens are accepted`() {
      val token = forgeToken("olivia@example.com", "Olivia", expiresAt = nowSeconds() + 3600)
      assertEquals(User(email = "olivia@example.com"), User.fromSignedToken(token))
    }

    @Test
    fun `tokens signed with another key are rejected`() {
      val token = User(email = "peggy@example.com").toSignedToken(key = "key-a")
      assertNull(User.fromSignedToken(token, "key-b"))
      assertEquals("peggy@example.com", User.fromSignedToken(token, "key-a")?.email)
    }

    @Test
    fun `tampered payloads are rejected`() {
      val user = User(email = "quinn@example.com")
      val token = user.toSignedToken()
      val signature = token.substringAfterLast('.')
      val forgedPayload = listOf(
        User.SIGNATURE_VERSION, enc("attacker@example.com"), enc("Attacker"), (nowSeconds() + 300).toString()
      ).joinToString("|")

      assertNull(User.fromSignedToken(enc(forgedPayload) + "." + signature))
    }

    @Test
    fun `tampered signatures are rejected`() {
      val token = User(email = "rita@example.com").toSignedToken()
      val flipped = token.dropLast(1) + if (token.last() == 'a') 'b' else 'a'
      assertNull(User.fromSignedToken(flipped))
    }

    @Test
    fun `unknown signature versions are rejected`() {
      val token = forgeToken("sam@example.com", "Sam", nowSeconds() + 300, version = "v0")
      assertNull(User.fromSignedToken(token))
    }

    @Test
    fun `malformed tokens are rejected instead of throwing`() {
      assertNull(User.fromSignedToken(null))
      assertNull(User.fromSignedToken(""))
      assertNull(User.fromSignedToken("   "))
      assertNull(User.fromSignedToken("no-delimiter"))
      assertNull(User.fromSignedToken(".signature-only"))
      assertNull(User.fromSignedToken("payload-only."))
      assertNull(User.fromSignedToken("!!!not-base64!!!.deadbeef"))
    }

    @Test
    fun `tokens with a non numeric expiry are rejected`() {
      val payload = listOf(User.SIGNATURE_VERSION, enc("tom@example.com"), enc("Tom"), "soon").joinToString("|")
      val token = enc(payload) + "." + User.sign(payload)
      assertNull(User.fromSignedToken(token))
    }

    @Test
    fun `tokens with the wrong field count are rejected`() {
      val payload = listOf(User.SIGNATURE_VERSION, enc("tom@example.com"), "0").joinToString("|")
      val token = enc(payload) + "." + User.sign(payload)
      assertNull(User.fromSignedToken(token))
    }
  }

  // ---------------------------------------------------------------- key config

  @Nested
  @DisplayName("signing key configuration")
  inner class SigningKeyConfiguration {

    @Test
    fun `system property overrides the built in default when no env var is set`() {
      org.junit.jupiter.api.Assumptions.assumeTrue(
        System.getenv(User.SIGNING_KEY_ENV).isNullOrBlank(),
        "${User.SIGNING_KEY_ENV} is set in this environment"
      )
      val previous = System.getProperty(User.SIGNING_KEY_PROPERTY)
      try {
        System.setProperty(User.SIGNING_KEY_PROPERTY, "unit-test-signing-key")
        assertEquals("unit-test-signing-key", User.signingKey)
        assertFalse(User.isUsingDefaultSigningKey)

        // blank values fall through to the default
        System.setProperty(User.SIGNING_KEY_PROPERTY, "   ")
        assertTrue(User.isUsingDefaultSigningKey)
      } finally {
        if (previous == null) System.clearProperty(User.SIGNING_KEY_PROPERTY)
        else System.setProperty(User.SIGNING_KEY_PROPERTY, previous)
      }
    }

    @Test
    fun `signature follows the configured key`() {
      val user = User(email = "uma@example.com")
      val withKeyA = User.sign(user.signingPayload(), "key-a")
      val withKeyB = User.sign(user.signingPayload(), "key-b")

      assertNotEquals(withKeyA, withKeyB)
      assertTrue(user.isSignatureValid(withKeyA, "key-a"))
      assertFalse(user.isSignatureValid(withKeyA, "key-b"))
    }
  }

  // ---------------------------------------------------------------- hexHash

  @Nested
  @DisplayName("String.hexHash")
  inner class HexHash {

    @Test
    fun `matches the known sha256 of the empty string`() {
      assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "".hexHash()
      )
    }

    @Test
    fun `matches the known sha256 of abc`() {
      assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "abc".hexHash()
      )
    }

    @Test
    fun `is deterministic and 64 hex characters`() {
      val hash = "alice@example.com".hexHash()
      assertEquals(hash, "alice@example.com".hexHash())
      assertEquals(64, hash.length)
      assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `handles unicode via utf8 bytes`() {
      assertNotEquals("Ω".hexHash(), "O".hexHash())
    }
  }
}