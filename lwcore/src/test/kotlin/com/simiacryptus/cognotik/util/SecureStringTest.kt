package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class SecureStringTest {

  companion object {
    /** Install an in-memory key so tests never touch (or create) the on-disk key file. */
    @JvmStatic
    @BeforeAll
    fun installEphemeralKey() {
      SecureString.key = SecureString.randomKey()
    }
  }

  @Test
  fun `round trips a plaintext value`() {
    assertEquals("hunter2", SecureString("hunter2").decrypt)
  }

  @Test
  fun `round trips unicode`() {
    val value = "pässwörd-\u00e9\u4e2d\u6587"
    assertEquals(value, SecureString(value).decrypt)
  }

  @Test
  fun `empty values are supported`() {
    val secure = SecureString("")
    assertEquals(0, secure.data.size)
    assertEquals("", secure.decrypt)
    assertEquals("SECURE::", secure.toJson())
  }

  @Test
  fun `toString and toJson use the secure prefix`() {
    val secure = SecureString("abc")
    assertTrue(secure.toJson().startsWith("SECURE::"), secure.toJson())
    assertEquals(secure.toJson(), secure.toString())
  }

  @Test
  fun `ciphertext differs between instances of the same value`() {
    val a = SecureString("same")
    val b = SecureString("same")
    assertFalse(a.data.contentEquals(b.data), "Random IV should produce distinct ciphertexts")
    assertEquals(a.decrypt, b.decrypt)
  }

  @Test
  fun `fromJson reuses an already encrypted payload`() {
    val original = SecureString("secret")
    val restored = SecureString.fromJson(original.toJson())
    assertEquals(original, restored)
    assertEquals("secret", restored.decrypt)
  }

  @Test
  fun `fromJson encrypts plaintext input`() {
    val restored = SecureString.fromJson("plaintext")
    assertEquals("plaintext", restored.decrypt)
    assertTrue(restored.toJson().startsWith("SECURE::"))
  }

  @Test
  fun `constructing from an encrypted payload preserves the value`() {
    val original = SecureString("nested")
    assertEquals("nested", SecureString(original.toJson()).decrypt)
  }

  @Test
  fun `equality is based on the ciphertext bytes`() {
    val a = SecureString("x")
    assertEquals(a, SecureString.fromJson(a.toJson()))
    assertEquals(a.hashCode(), SecureString.fromJson(a.toJson()).hashCode())
    assertNotEquals<Any?>(a, "not a secure string")
    assertNotEquals<Any?>(a, null)
    assertEquals(a, a)
  }

  @Test
  fun `string extension encrypts`() {
    assertEquals("from-extension", "from-extension".encrypt.decrypt)
  }

  @Test
  fun `jackson serialization uses the json value`() {
    val mapper = JsonUtil.objectMapper()
    val json = mapper.writeValueAsString(SecureString("json-value"))
    assertTrue(json.contains("SECURE::"), json)
    val restored: SecureString = mapper.readValue(json)
    assertEquals("json-value", restored.decrypt)
  }

  @Test
  fun `randomKey produces distinct AES keys`() {
    val a = SecureString.randomKey()
    val b = SecureString.randomKey()
    assertEquals("AES", a.algorithm)
    assertFalse(a.encoded.contentEquals(b.encoded))
  }

  @Test
  fun `decrypt with a different key returns null`() {
    val secure = SecureString("rotated")
    val previous = SecureString.key
    try {
      SecureString.key = SecureString.randomKey()
      assertNull(secure.decrypt)
    } finally {
      SecureString.key = previous
    }
    assertEquals("rotated", secure.decrypt)
  }
}