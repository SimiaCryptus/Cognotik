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
  val data: ByteArray

  private constructor(data: ByteArray) {
    this.data = data
  }

  constructor(str: String) : this(encrypt(str))

  override fun toString() = toJson()

  val decrypt: String? get() = decrypt(data)

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
    val possibleKeyFiles = listOf(
      File("/var/cognotik"),
      File(System.getProperty("user.home"), ".cognotik")
    ).toMutableList()

    /**
     * Resolves the key file lazily and defensively:
     * 1. Prefer an existing, readable `.key` in any candidate directory.
     * 2. Otherwise pick the first candidate directory we can actually create/write to.
     * 3. Otherwise fall back to a temp directory.
     */
    private fun resolveKeyFile(): File {

      possibleKeyFiles.map { it.resolve(".key") }
        .firstOrNull { it.isFile && it.canRead() }
        ?.let { return it }

      for (dir in possibleKeyFiles) {
        try {
          if (!dir.exists()) dir.mkdirs()
          if (dir.isDirectory && dir.canWrite()) return dir.resolve(".key")
        } catch (e: Throwable) {
          log.debug("Key directory unusable: ${dir.absolutePath}", e)
        }
      }

      val fallback = File(System.getProperty("java.io.tmpdir"), "cognotik")
      try {
        fallback.mkdirs()
      } catch (e: Throwable) {
        log.debug("Unable to create fallback key directory: ${fallback.absolutePath}", e)
      }
      log.warn("No writable key directory found in $possibleKeyFiles; falling back to ${fallback.absolutePath}")
      return fallback.resolve(".key")
    }

    var key: SecretKey? = null
      get() {
        if (field == null) {
          field = _key
        }
        return field
      }

    private val _key: SecretKey by lazy { loadOrCreateKey() }

    private fun loadOrCreateKey(): SecretKey {
      val keyFile = resolveKeyFile()
      if (keyFile.isFile) {
        try {
          val keyBytes = keyFile.readBytes()
          if (keyBytes.isNotEmpty()) return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
          log.error("Encryption key file is empty, regenerating: ${keyFile.absolutePath}")
        } catch (e: Throwable) {
          log.error("Error reading encryption key, regenerating: ${keyFile.absolutePath}", e)
        }
      }
      val key = randomKey()
      try {
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(key.encoded)



        try {
          keyFile.setReadable(false, false)
          keyFile.setReadable(true, true)
          keyFile.setWritable(false, false)
          keyFile.setWritable(true, true)
        } catch (e: Throwable) {
          log.debug("Unable to restrict permissions on ${keyFile.absolutePath}", e)
        }
      } catch (e: Throwable) {

        log.warn(
          "Unable to persist encryption key to ${keyFile.absolutePath}; using an ephemeral in-memory key. " +
              "Previously encrypted values will not be recoverable.", e
        )
      }
      /* Or, as a shell script:
      *    mkdir -p /var/cognotik
      *    openssl rand -out /var/cognotik/.key 32
      *
      * */
      return key
    }

    fun randomKey(): SecretKey = (KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
      ?: throw RuntimeException("Unable to generate encryption key"))

    private fun encrypt(str: String): ByteArray {
      if (str.isEmpty()) return ByteArray(0)
      if (str.startsWith(PREFIX)) try {
        return Base64.getDecoder().decode(str.removePrefix(PREFIX)).apply {
          // Test decryption to ensure validity
          decrypt(this)
        }
      } catch (e: Throwable) {
        throw RuntimeException("Failed to decrypt pre-encrypted string", e)
      }
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val encrypted = cipher.doFinal(str.toByteArray(Charsets.UTF_8))
      val result = ByteArray(iv.size + encrypted.size)
      System.arraycopy(iv, 0, result, 0, iv.size)
      System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
      return result
    }

    private fun decrypt(bytes: ByteArray): String? {
      try {
        if (bytes.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
      } catch (e: Throwable) {
        log.error("Error decrypting data", e)
        return null
      }
    }

    @JsonCreator
    @JvmStatic
    fun fromJson(value: String): SecureString {
      if (value.startsWith(PREFIX)) {
        try {
          val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
          return SecureString(bytes)
        } catch (e: Throwable) {
          log.warn("Malformed secure string payload; treating as plaintext", e)
        }
      }
      return try {
        SecureString(encrypt(value))
      } catch (e: Throwable) {
        log.error("Unable to encrypt value; storing empty SecureString", e)
        SecureString(ByteArray(0))
      }
    }
  }
}

val String.encrypt: SecureString
  get() {
    return SecureString(this)
  }