package com.simiacryptus.cognotik.core.platform.model

interface CloudPlatformInterface {
  val shareBase: String

  fun upload(
    path: String,
    contentType: String,
    bytes: ByteArray
  ): String

  fun upload(
    path: String,
    contentType: String,
    request: String
  ): String

  fun encrypt(fileBytes: ByteArray, keyId: String): String?
  fun decrypt(encryptedData: ByteArray): String
}