package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.CloudPlatformInterface
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.EncryptRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.charset.StandardCharsets
import java.util.*


open class AwsPlatform(
  private val bucket: String = System.getProperty("share_bucket", "share.simiacrypt.us"),
  override val shareBase: String = System.getProperty("share_base", "https://" + bucket),
  private val region: Region? = Region.US_EAST_1,
  profileName: String? = System.getProperty("aws.profile", "default").let { if (it.isBlank()) null else it },
) : CloudPlatformInterface {

  open val credentialsProvider = AwsCredentialsProviderChain.builder()
    .credentialsProviders(
      // Try EC2 instance profile credentials first
      InstanceProfileCredentialsProvider.create(),
      // Then try profile credentials if profile name is provided
      profileName?.let {
        ProfileCredentialsProvider.create(it)
      } ?: ProfileCredentialsProvider.create()
    )
    .build()

  private val log = LoggerFactory.getLogger(AwsPlatform::class.java)

  protected open val kmsClient: KmsClient by lazy {
    log.debug("Initializing KMS client for region: {}", Region.US_EAST_1)
    var clientBuilder = KmsClient.builder().region(Region.US_EAST_1)
    if (null != credentialsProvider) clientBuilder = clientBuilder.credentialsProvider(credentialsProvider)
    clientBuilder.build()
  }

  protected open val s3Client: S3Client by lazy {
    log.debug("Initializing S3 client for region: {}", region)
    var clientBuilder = S3Client.builder()
    if (null != credentialsProvider) clientBuilder = clientBuilder.credentialsProvider(credentialsProvider)
    clientBuilder = clientBuilder.region(region)
    clientBuilder.build()
  }

  override fun upload(
    path: String,
    contentType: String,
    bytes: ByteArray
  ): String {
    log.info("Uploading {} bytes to S3 path: {}", bytes.size, path)
    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(bucket).key(path.replace("/{2,}".toRegex(), "/").removePrefix("/"))
        .contentType(contentType)
        .build(),
      RequestBody.fromBytes(bytes)
    )
    log.debug("Upload completed successfully")
    return "$shareBase/$path"
  }

  override fun upload(
    path: String,
    contentType: String,
    request: String
  ): String {
    log.info("Uploading string content to S3 path: {}", path)
    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(bucket).key(path.replace("/{2,}".toRegex(), "/").removePrefix("/"))
        .contentType(contentType)
        .build(),
      RequestBody.fromString(request)
    )
    log.debug("Upload completed successfully")
    return "$shareBase/$path"
  }


  override fun encrypt(fileBytes: ByteArray, keyId: String): String? {
    log.info("Encrypting {} bytes using KMS key: {}", fileBytes.size, keyId)
    val encryptedData = Base64.getEncoder().encodeToString(
      kmsClient.encrypt(
        EncryptRequest.builder()
          .keyId(keyId)
          .plaintext(SdkBytes.fromByteArray(fileBytes))
          .build()
      ).ciphertextBlob().asByteArray()
    )
    log.debug("Encryption completed successfully")
    return encryptedData
  }

  override fun decrypt(encryptedData: ByteArray): String {
    log.info("Decrypting {} bytes of data", encryptedData.size)
    val decryptedData = String(
      kmsClient.decrypt(
        DecryptRequest.builder()
          .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(encryptedData)))
          .build()
      ).plaintext().asByteArray(), StandardCharsets.UTF_8
    )
    log.debug("Decryption completed successfully")
    return decryptedData
  }

  companion object {
    val log = LoggerFactory.getLogger(AwsPlatform::class.java)
    fun get() = try {
      AwsPlatform()
    } catch (e: Throwable) {
      log.warn("Error initializing AWS platform", e)
      null
    }
  }
}