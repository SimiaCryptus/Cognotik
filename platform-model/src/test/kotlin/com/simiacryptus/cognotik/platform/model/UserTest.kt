package com.simiacryptus.cognotik.platform.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class UserTest {

  private val key = "test-signing-key"
  private val otherKey = "other-signing-key"

  private fun b64(s: String) =
    Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

  private fun b64dec(s: String) = String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)

  /** Builds a token from raw payload fields, signed with [k]. */
  private fun craft(fields: List<String>, k: String = key): String {
    val payload = fields.joinToString("|")
    return b64(payload) + "." + User.sign(payload, k)
  }

  private fun nowSeconds() = System.currentTimeMillis() / 1000

  // ---- identity ----

  @Test
  fun `name defaults to email`() {
    assertEquals("alice@example.com", User("alice@example.com").name)
  }

  @Test
  fun `provider is empty and id is hash of email`() {
    val user = User("alice@example.com", "Alice")
    assertEquals("", user.provider)
    assertEquals("alice@example.com".hexHash().take(20), user.id)
    assertEquals(20, user.id.length)
  }

  @Test
  fun `toString is redacted id`() {
    val user = User("alice@example.com")
    assertEquals(user.id, user.toString())
    assertFalse(user.toString().contains("alice"))
  }

  @Test
  fun `equality depends only on id`() {
    val a = User("alice@example.com", "Alice")
    val b = User("alice@example.com", "Someone Else")
    val c = User("bob@example.com", "Alice")
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals(a, c)
    assertNotEquals(a, null)
    assertNotEquals(a, "alice@example.com")
    assertEquals(1, setOf(a, b).size)
  }

  @Suppress("DEPRECATION")
  @Test
  fun `NULL user has placeholder email`() {
    assertEquals("null@localhost", User.NULL.email)
    assertEquals("null@localhost", User.NULL.name)
  }

  // ---- redaction ----

  @Test
  fun `redact hides local part`() {
    assertEquals("a***@example.com", User.redact("alice@example.com"))
    assertEquals("a***@example.com", User("alice@example.com").redactedEmail)
    assertEquals("x***@y", User.redact("x@y"))
  }

  @Test
  fun `redact handles malformed addresses`() {
    assertEquals("***", User.redact("no-at-sign"))
    assertEquals("***", User.redact("@example.com"))
    assertEquals("***", User.redact(""))
  }

  // ---- hexHash ----

  @Test
  fun `hexHash is sha256 hex`() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      "abc".hexHash()
    )
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "".hexHash()
    )
  }

  // ---- sign / verify ----

  @Test
  fun `sign is deterministic lowercase hex`() {
    val s1 = User.sign("payload", key)
    val s2 = User.sign("payload", key)
    assertEquals(s1, s2)
    assertEquals(64, s1.length)
    assertTrue(s1.matches(Regex("[0-9a-f]{64}")))
  }

  @Test
  fun `sign matches known HMAC-SHA256 vector`() {
    // RFC 4231-style sanity check using a well known example
    assertEquals(
      "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
      User.sign("The quick brown fox jumps over the lazy dog", "key")
    )
  }

  @Test
  fun `sign differs by key and payload`() {
    assertNotEquals(User.sign("p", key), User.sign("p", otherKey))
    assertNotEquals(User.sign("p1", key), User.sign("p2", key))
  }

  @Test
  fun `sign rejects empty key`() {
    assertThrows<IllegalArgumentException> { User.sign("payload", "") }
  }

  @Test
  fun `verify accepts correct signature`() {
    val sig = User.sign("payload", key)
    assertTrue(User.verify("payload", sig, key))
  }

  @Test
  fun `verify is case and whitespace tolerant`() {
    val sig = User.sign("payload", key)
    assertTrue(User.verify("payload", sig.uppercase(), key))
    assertTrue(User.verify("payload", "  $sig\n", key))
  }

  @Test
  fun `verify rejects wrong, null, or blank signatures`() {
    val sig = User.sign("payload", key)
    assertFalse(User.verify("payload", sig, otherKey))
    assertFalse(User.verify("other", sig, key))
    assertFalse(User.verify("payload", null, key))
    assertFalse(User.verify("payload", "", key))
    assertFalse(User.verify("payload", "   ", key))
    assertFalse(User.verify("payload", sig.dropLast(1), key))
    assertFalse(User.verify("payload", sig + "0", key))
  }

  // ---- user signature ----

  @Test
  fun `signingPayload has versioned four-field layout`() {
    val user = User("alice@example.com", "Alice")
    val parts = user.signingPayload().split("|")
    assertEquals(4, parts.size)
    assertEquals(User.SIGNATURE_VERSION, parts[0])
    assertEquals(user.id, b64dec(parts[1]))
    assertEquals(user.email, b64dec(parts[2]))
    assertEquals(user.name, b64dec(parts[3]))
  }

  @Test
  fun `signingPayload is delimiter safe`() {
    val a = User("a@x.com", "foo|bar")
    assertEquals(4, a.signingPayload().split("|").size)
  }

  @Test
  fun `signature is valid for itself with configured key`() {
    val user = User("alice@example.com", "Alice")
    assertEquals(User.sign(user.signingPayload()), user.signature)
    assertTrue(user.isSignatureValid(user.signature))
  }

  @Test
  fun `isSignatureValid with explicit key`() {
    val user = User("alice@example.com", "Alice")
    val sig = User.sign(user.signingPayload(), key)
    assertTrue(user.isSignatureValid(sig, key))
    assertFalse(user.isSignatureValid(sig, otherKey))
    assertFalse(user.isSignatureValid(null, key))
  }

  @Test
  fun `signature changes with name`() {
    val a = User("alice@example.com", "Alice")
    val b = User("alice@example.com", "Mallory")
    assertNotEquals(a.signature, b.signature)
    assertFalse(b.isSignatureValid(a.signature))
  }

  @Test
  fun `requireValid returns this for own signature`() {
    val user = User("alice@example.com")
    assertSame(user, user.requireValid())
    assertSame(user, user.requireValid(user.signature))
  }

  @Test
  fun `requireValid throws for bad signature`() {
    val user = User("alice@example.com")
    assertThrows<IllegalArgumentException> { user.requireValid("deadbeef") }
    assertThrows<IllegalArgumentException> { user.requireValid(null) }
    assertThrows<IllegalArgumentException> {
      user.requireValid(User("bob@example.com").signature)
    }
  }

  // ---- signed tokens ----

  @Test
  fun `token roundtrip restores user`() {
    val user = User("alice@example.com", "Alice Smith")
    val token = user.toSignedToken(60, key)
    val parsed = User.fromSignedToken(token, key)
    assertNotNull(parsed)
    assertEquals(user.email, parsed!!.email)
    assertEquals(user.name, parsed.name)
    assertEquals(user, parsed)
  }

  @Test
  fun `token roundtrip with default key`() {
    val user = User("alice@example.com", "Alice")
    val parsed = User.fromSignedToken(user.toSignedToken())
    assertEquals(user, parsed)
    assertEquals("Alice", parsed!!.name)
  }

  @Test
  fun `token roundtrip preserves special characters`() {
    val user = User("weird.name+tag@example.co.uk", "Na|me. with ünïcødé / = +")
    val parsed = User.fromSignedToken(user.toSignedToken(60, key), key)
    assertEquals(user.email, parsed!!.email)
    assertEquals(user.name, parsed.name)
  }

  @Test
  fun `token format is payload dot hex signature`() {
    val token = User("alice@example.com").toSignedToken(60, key)
    val idx = token.lastIndexOf('.')
    val payload = b64dec(token.substring(0, idx))
    val sig = token.substring(idx + 1)
    assertTrue(sig.matches(Regex("[0-9a-f]{64}")))
    assertEquals(User.sign(payload, key), sig)
    assertEquals(1, token.count { it == '.' }, "base64url payload must not contain '.'")
  }

  @Test
  fun `token expiry is set from ttl`() {
    val start = nowSeconds()
    val token = User("alice@example.com").toSignedToken(120, key)
    val end = nowSeconds()
    val payload = b64dec(token.substringBeforeLast('.'))
    val expiresAt = payload.split("|")[3].toLong()
    assertTrue(expiresAt in (start + 120)..(end + 120), "expiresAt=$expiresAt")
  }

  @Test
  fun `non-positive ttl produces non-expiring token`() {
    for (ttl in listOf(0L, -5L)) {
      val token = User("alice@example.com").toSignedToken(ttl, key)
      val payload = b64dec(token.substringBeforeLast('.'))
      assertEquals("0", payload.split("|")[3])
      assertNotNull(User.fromSignedToken(token, key))
    }
  }

  @Test
  fun `token signed with other key is rejected`() {
    val token = User("alice@example.com").toSignedToken(60, key)
    assertNull(User.fromSignedToken(token, otherKey))
  }

  @Test
  fun `tampered signature is rejected`() {
    val token = User("alice@example.com").toSignedToken(60, key)
    val last = token.last()
    val tampered = token.dropLast(1) + (if (last == '0') '1' else '0')
    assertNull(User.fromSignedToken(tampered, key))
  }

  @Test
  fun `swapped payload is rejected`() {
    val alice = User("alice@example.com").toSignedToken(60, key)
    val bob = User("bob@example.com").toSignedToken(60, key)
    val forged = bob.substringBeforeLast('.') + "." + alice.substringAfterLast('.')
    assertNull(User.fromSignedToken(forged, key))
  }

  @Test
  fun `expired token is rejected`() {
    val token = craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), b64("A"), "1"))
    assertNull(User.fromSignedToken(token, key))
    val recentPast = craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), b64("A"), (nowSeconds() - 10).toString()))
    assertNull(User.fromSignedToken(recentPast, key))
  }

  @Test
  fun `crafted valid token is accepted`() {
    val token = craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), b64("A"), (nowSeconds() + 100).toString()))
    val user = User.fromSignedToken(token, key)
    assertEquals("a@b.com", user!!.email)
    assertEquals("A", user.name)
  }

  @Test
  fun `wrong version is rejected`() {
    assertNull(User.fromSignedToken(craft(listOf("v0", b64("a@b.com"), b64("A"), "0")), key))
    assertNull(User.fromSignedToken(craft(listOf("v2", b64("a@b.com"), b64("A"), "0")), key))
  }

  @Test
  fun `wrong field count is rejected`() {
    assertNull(User.fromSignedToken(craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), "0")), key))
    assertNull(
      User.fromSignedToken(craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), b64("A"), "0", "extra")), key)
    )
  }

  @Test
  fun `non numeric expiry is rejected`() {
    assertNull(User.fromSignedToken(craft(listOf(User.SIGNATURE_VERSION, b64("a@b.com"), b64("A"), "soon")), key))
  }

  @Test
  fun `undecodable field is rejected`() {
    assertNull(User.fromSignedToken(craft(listOf(User.SIGNATURE_VERSION, "!!!", b64("A"), "0")), key))
  }

  @Test
  fun `malformed tokens are rejected`() {
    assertNull(User.fromSignedToken(null, key))
    assertNull(User.fromSignedToken("", key))
    assertNull(User.fromSignedToken("   ", key))
    assertNull(User.fromSignedToken("nodelimiter", key))
    assertNull(User.fromSignedToken(".abcdef", key))
    assertNull(User.fromSignedToken("abcdef.", key))
    assertNull(User.fromSignedToken("!!!not-base64!!!.abcdef", key))
    assertNull(User.fromSignedToken("YWJj.notahexsig", key))
  }

  // ---- signing key configuration ----

  @Test
  fun `system property overrides default signing key`() {
    assumeTrue(System.getenv(User.SIGNING_KEY_ENV).isNullOrBlank(), "env var set; skipping")
    val previous = System.getProperty(User.SIGNING_KEY_PROPERTY)
    try {
      System.clearProperty(User.SIGNING_KEY_PROPERTY)
      assertTrue(User.isUsingDefaultSigningKey)
      System.setProperty(User.SIGNING_KEY_PROPERTY, "from-property")
      assertEquals("from-property", User.signingKey)
      assertFalse(User.isUsingDefaultSigningKey)
      System.setProperty(User.SIGNING_KEY_PROPERTY, "   ")
      assertTrue(User.isUsingDefaultSigningKey, "blank property should be ignored")
    } finally {
      if (previous == null) System.clearProperty(User.SIGNING_KEY_PROPERTY)
      else System.setProperty(User.SIGNING_KEY_PROPERTY, previous)
    }
  }

  @Test
  fun `signing key is never empty`() {
    assertTrue(User.signingKey.isNotEmpty())
  }
}