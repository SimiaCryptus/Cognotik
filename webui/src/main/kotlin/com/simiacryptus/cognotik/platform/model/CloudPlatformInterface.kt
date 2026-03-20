package com.simiacryptus.cognotik.platform.model

/**
 * Interface for cloud platform operations providing storage and encryption capabilities.
 *
 * This interface abstracts cloud platform-specific operations such as file uploads
 * and encryption/decryption services. Implementations of this interface can provide
 * integration with various cloud providers (AWS, Azure, GCP, etc.).
 */

interface CloudPlatformInterface {
  /**
   * The base URL for sharing uploaded content.
   *
   * This property defines the root URL that will be prepended to uploaded file paths
   * to create shareable links. For example, if shareBase is "https://share.example.com"
   * and a file is uploaded to path "documents/file.pdf", the shareable link would be
   * "https://share.example.com/documents/file.pdf".
   */
  val shareBase: String

  /**
   * Uploads binary content to the cloud storage platform.
   *
   * @param path The storage path where the content will be uploaded.
   *             Leading slashes will be removed and consecutive slashes normalized.
   * @param contentType The MIME type of the content being uploaded (e.g., "application/pdf", "image/jpeg").
   *                    This helps the platform serve the content with appropriate headers.
   * @param bytes The binary content to upload as a byte array.
   * @return The complete URL where the uploaded content can be accessed,
   *         typically combining [shareBase] with the normalized path.
   * @throws Exception if the upload operation fails due to network issues,
   *                   authentication problems, or storage errors.
   */

  fun upload(
    path: String,
    contentType: String,
    bytes: ByteArray
  ): String

  /**
   * Uploads text content to the cloud storage platform.
   *
   * This method is optimized for uploading string content directly without
   * requiring conversion to byte arrays by the caller.
   *
   * @param path The storage path where the content will be uploaded.
   *             Leading slashes will be removed and consecutive slashes normalized.
   * @param contentType The MIME type of the content being uploaded (e.g., "text/plain", "application/json").
   *                    This helps the platform serve the content with appropriate headers.
   * @param request The text content to upload as a string.
   * @return The complete URL where the uploaded content can be accessed,
   *         typically combining [shareBase] with the normalized path.
   * @throws Exception if the upload operation fails due to network issues,
   *                   authentication problems, or storage errors.
   */

  fun upload(
    path: String,
    contentType: String,
    request: String
  ): String

  /**
   * Encrypts data using the cloud platform's encryption service.
   *
   * This method leverages cloud-native encryption services (like AWS KMS) to
   * encrypt sensitive data before storage or transmission.
   *
   * @param fileBytes The binary data to encrypt.
   * @param keyId The identifier of the encryption key to use. The format and
   *              requirements for this ID depend on the cloud platform
   *              (e.g., KMS key ARN for AWS, key vault URL for Azure).
   * @return The encrypted data as a Base64-encoded string, or null if encryption fails.
   *         The encoded format allows for safe transmission and storage as text.
   * @throws Exception if the encryption operation fails due to invalid key ID,
   *                   insufficient permissions, or service errors.
   */

  fun encrypt(fileBytes: ByteArray, keyId: String): String?

  /**
   * Decrypts data that was previously encrypted using the platform's encryption service.
   *
   * This method reverses the encryption performed by [encrypt], using the
   * cloud platform's decryption capabilities. The encryption key information
   * is typically embedded in the encrypted data itself.
   *
   * @param encryptedData The Base64-encoded encrypted data to decrypt.
   *                      This should be data that was previously encrypted
   *                      using this platform's [encrypt] method.
   * @return The decrypted content as a UTF-8 string.
   * @throws Exception if the decryption operation fails due to corrupted data,
   *                   missing decryption keys, insufficient permissions, or service errors.
   */
  fun decrypt(encryptedData: ByteArray): String
}