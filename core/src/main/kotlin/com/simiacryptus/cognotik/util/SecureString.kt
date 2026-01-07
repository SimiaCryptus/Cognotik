package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.io.File
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureString {
  private val data: ByteArray

  private constructor(data: ByteArray) {
    this.data = data
  }

  constructor(str: String) : this(encrypt(str))

  override fun toString() = toJson()

  val decrypt: String get() = decrypt(data)

  @JsonValue
  fun toJson(): String = PREFIX + Base64.getEncoder().encodeToString(data)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SecureString) return false
    return data.contentEquals(other.data)
  }

  override fun hashCode() = data.contentHashCode()

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(SecureString::class.java)
    private const val PREFIX = "SECURE::"
    private val keyFile = File(System.getProperty("user.home"), ".cognotik").resolve(".key")
    private val key: SecretKey by lazy {
      if (keyFile.exists()) {
        try {
          val keyBytes = keyFile.readBytes()
          javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        } catch (e: Throwable) {
          log.error("Error reading encryption key, regenerating", e)
          val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            ?: throw RuntimeException("Unable to generate encryption key")
          keyFile.writeBytes(key.encoded)
          key
        }
      } else {
        keyFile.parentFile.mkdirs()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
          ?: throw RuntimeException("Unable to generate encryption key")
        keyFile.writeBytes(key.encoded)
        key
      }
    }

    private fun encrypt(str: String): ByteArray {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val encrypted = cipher.doFinal(str.toByteArray(Charsets.UTF_8))
      val result = ByteArray(iv.size + encrypted.size)
      System.arraycopy(iv, 0, result, 0, iv.size)
      System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
      return result
    }

    private fun decrypt(bytes: ByteArray): String {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      val iv = bytes.copyOfRange(0, 12)
      val encrypted = bytes.copyOfRange(12, bytes.size)
      val spec = GCMParameterSpec(128, iv)
      cipher.init(Cipher.DECRYPT_MODE, key, spec)
      return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    @JsonCreator
    @JvmStatic
    fun fromJson(value: String): SecureString {
      if (value.startsWith(PREFIX)) {
        try {
          val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
          return SecureString(bytes)
        } catch (e: Throwable) {
          // Ignore
        }
      }
      return SecureString(encrypt(value))
    }
  }
}

val String.encrypt: SecureString get() {
  return SecureString(this)
}
