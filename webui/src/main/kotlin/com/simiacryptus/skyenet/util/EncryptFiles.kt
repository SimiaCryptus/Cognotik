package com.simiacryptus.skyenet.util

import com.simiacryptus.skyenet.core.platform.ApplicationServices
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object EncryptFiles {

  @JvmStatic
  fun main(args: Array<String>) {
    File("""C:\Users\andre\code\Cognotik\webui\src\test\resources\client_secret_google_oauth.json""")
      .readText().encrypt("arn:aws:kms:us-east-1:470240306861:key/a1340b89-64e6-480c-a44c-e7bc0c70dcb1")
      .write("""C:\Users\andre\code\Cognotik\webui\src\test\resources\client_secret_google_oauth.json.kms""")
  }

}

fun String.write(outpath: String) {
  Files.write(Paths.get(outpath), toByteArray())
}

fun String.encrypt(keyId: String) = ApplicationServices.cloud?.encrypt(encodeToByteArray(), keyId)
  ?: throw RuntimeException("Unable to encrypt data")