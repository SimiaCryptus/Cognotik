@file:Suppress("unused")

package com.simiacryptus.cognotik.core.util

import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.util.JsonUtil
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class FunctionWrapper(val inner: FunctionInterceptor) : FunctionInterceptor {
  inline fun <reified T : Any> wrap(crossinline fn: () -> T) = inner.intercept(T::class.java) { fn() }
  inline fun <P : Any, reified T : Any> wrap(p: P, crossinline fn: (P) -> T) =
    inner.intercept(p, T::class.java) { fn(it) }

  inline fun <P1 : Any, P2 : Any, reified T : Any> wrap(p1: P1, p2: P2, crossinline fn: (P1, P2) -> T) =
    inner.intercept(p1, p2, T::class.java) { p1, p2 -> fn(p1, p2) }

  inline fun <P1 : Any, P2 : Any, P3 : Any, reified T : Any> wrap(
    p1: P1,
    p2: P2,
    p3: P3,
    crossinline fn: (P1, P2, P3) -> T
  ) =
    inner.intercept(p1, p2, p3, T::class.java) { p1, p2, p3 -> fn(p1, p2, p3) }

  inline fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, reified T : Any> wrap(
    p1: P1,
    p2: P2,
    p3: P3,
    p4: P4,
    crossinline fn: (P1, P2, P3, P4) -> T
  ) =
    inner.intercept(p1, p2, p3, p4, T::class.java) { p1, p2, p3, p4 -> fn(p1, p2, p3, p4) }

  override fun <T : Any> intercept(returnClazz: Class<T>, fn: () -> T) = inner.intercept(returnClazz, fn)

  override fun <P : Any, T : Any> intercept(params: P, returnClazz: Class<T>, fn: (P) -> T) =
    inner.intercept(params, returnClazz, fn)

  override fun <P1 : Any, P2 : Any, T : Any> intercept(
    p1: P1,
    p2: P2,
    returnClazz: Class<T>,
    fn: (P1, P2) -> T
  ) = inner.intercept(p1, p2, returnClazz, fn)
}

interface FunctionInterceptor {
  fun <T : Any> intercept(returnClazz: Class<T>, fn: () -> T) = fn()
  fun <P : Any, T : Any> intercept(params: P, returnClazz: Class<T>, fn: (P) -> T) = fn(params)
  fun <P1 : Any, P2 : Any, T : Any> intercept(p1: P1, p2: P2, returnClazz: Class<T>, fn: (P1, P2) -> T) =
    intercept(listOf(p1, p2), returnClazz) {
      @Suppress("UNCHECKED_CAST")
      fn(it[0] as P1, it[1] as P2)
    }

  fun <P1 : Any, P2 : Any, P3 : Any, T : Any> intercept(
    p1: P1,
    p2: P2,
    p3: P3,
    returnClazz: Class<T>,
    fn: (P1, P2, P3) -> T
  ) =
    intercept(listOf(p1, p2, p3), returnClazz) {
      @Suppress("UNCHECKED_CAST")
      fn(it[0] as P1, it[1] as P2, it[2] as P3)
    }

  fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, T : Any> intercept(
    p1: P1,
    p2: P2,
    p3: P3,
    p4: P4,
    returnClazz: Class<T>,
    fn: (P1, P2, P3, P4) -> T
  ) =
    intercept(listOf(p1, p2, p3, p4), returnClazz) {
      @Suppress("UNCHECKED_CAST")
      fn(it[0] as P1, it[1] as P2, it[2] as P3, it[3] as P4)
    }
}

class NoopFunctionInterceptor : FunctionInterceptor {
  override fun <T : Any> intercept(returnClazz: Class<T>, fn: () -> T) = fn()
  override fun <P : Any, T : Any> intercept(params: P, returnClazz: Class<T>, fn: (P) -> T) = fn(params)
}

class JsonFunctionRecorder(baseDir: File) : FunctionInterceptor, Closeable {
  private val baseDirectory = baseDir.apply {
    if (exists()) {
      throw IllegalStateException("File already exists: $this")
    }
    mkdirs()
  }
  private val sequenceId = AtomicInteger(0)

  override fun close() {
    // No resources to close in this implementation
  }

  override fun <T : Any> intercept(returnClazz: Class<T>, fn: () -> T): T {
    val dir = operationDir()
    try {
      val result = fn()
      if (result is BufferedImage) {
        ImageIO.write(result, "png", File(dir, "output.png"))
      } else {
        File(dir, "output.json").writeText(JsonUtil.toJson(result))
      }
      return result
    } catch (e: Throwable) {
      try {
        File(dir, "error.json").writeText(JsonUtil.toJson(e))
      } catch (e: Throwable) {
        log.warn("Error writing error file", e)
      }
      throw e
    }
  }

  override fun <P : Any, T : Any> intercept(params: P, returnClazz: Class<T>, fn: (P) -> T): T {
    val dir = operationDir()
    File(dir, "input.json").writeText(JsonUtil.toJson(params))
    try {
      val result = fn(params)
      if (result is BufferedImage) {
        ImageIO.write(result, "png", File(dir, "output.png"))
      } else {
        File(dir, "output.json").writeText(JsonUtil.toJson(result))
      }
      return result
    } catch (e: Throwable) {
      try {
        File(dir, "error.json").writeText(JsonUtil.toJson(e))
      } catch (e: Throwable) {
        log.warn("Error writing error file", e)
      }
      throw e
    }
  }

  private fun operationDir(): File {
    val id = sequenceId.incrementAndGet().toString().padStart(3, '0')
    val yyyyMMddHHmmss =
      java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now())
    val internalClassList = listOf(
      java.lang.Thread::class.java,
      JsonFunctionRecorder::class.java,
      FunctionWrapper::class.java,
      FunctionInterceptor::class.java,
      NoopFunctionInterceptor::class.java,
    )
    // Get the caller method name from the stack trace (first caller not in internalClassList)
    val caller = Thread.currentThread().stackTrace
      .filter { !internalClassList.contains(Class.forName(it.className)) }
      .filter { it.methodName != "intercept" }
      .firstOrNull()
    val methodName = caller?.methodName ?: "unknown"
    val file = File(baseDirectory, "$id-$yyyyMMddHHmmss-$methodName")
    if (file.exists()) {
      throw IllegalStateException("File already exists: $file")
    }
    file.mkdirs()
    return file
  }

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(JsonFunctionRecorder::class.java)
  }
}


fun getModel(modelName: String?): OpenAIModel? = ChatModel.values().values.find { it.modelName == modelName }
  ?: EmbeddingModels.values().values.find { it.modelName == modelName }
  ?: ImageModels.values().find { it.modelName == modelName }


