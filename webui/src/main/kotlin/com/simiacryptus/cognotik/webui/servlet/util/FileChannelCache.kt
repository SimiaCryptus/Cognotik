package com.simiacryptus.cognotik.webui.servlet.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object FileChannelCache {
  private val log = LoggerFactory.getLogger(FileChannelCache::class.java)
  val cache: LoadingCache<File, FileChannel> = CacheBuilder
    .newBuilder().maximumSize(100)
    .expireAfterAccess(10, TimeUnit.SECONDS)
    .removalListener(RemovalListener<File, FileChannel> { notification ->
      log.info("Closing FileChannel for file: ${notification.key}")
      try {
        val channel = notification.value
        if (channel == null) {
          log.error("FileChannel is null for file: ${notification.key}")
        } else {
          channel.close()
          log.info("Successfully closed FileChannel for file: ${notification.key}")
        }
      } catch (e: Throwable) {
        log.error("Error closing FileChannel for file: ${notification.key}", e)
      }
    }).build(object : CacheLoader<File, FileChannel>() {
      override fun load(key: File): FileChannel {
        log.info("Opening FileChannel for file: ${key.absolutePath}")
        return FileChannel.open(key.toPath(), StandardOpenOption.READ)
      }
    })

  fun get(file: File): FileChannel = cache.get(file)
  fun invalidate(file: File) = cache.invalidate(file)
  fun refresh(file: File) = cache.refresh(file)
  fun put(file: File, channel: FileChannel) = cache.put(file, channel)
}