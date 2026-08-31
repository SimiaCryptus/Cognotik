package com.simiacryptus.cognotik.fileserver.handler

import com.simiacryptus.cognotik.fileserver.util.FsPath
import com.simiacryptus.cognotik.fileserver.util.FsJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * GET /.fsapi/v1/watch — SSE change stream backed by java.nio WatchService
 * (nodejs.md §5.9). Bounded subscriber count; heartbeats keep proxies honest;
 * hidden paths are never reported.
 */
object FsWatchHandler {
  private val log = LoggerFactory.getLogger(FsWatchHandler::class.java)
  private const val MAX_WATCHERS = 32
  private const val MAX_REGISTERED_DIRS = 512
  private const val POLL_SECONDS = 15L
  private val active = AtomicInteger(0)

  fun handle(req: HttpServletRequest, resp: HttpServletResponse, root: File, config: FsApiConfig) {
    if (config.watchMode != "sse") {
      throw FsException(FsErrorCode.ENOSYS, "watch", null, "watch capability is '${config.watchMode}'")
    }
    val target = FsPath.resolve(root, req.getParameter("path") ?: "/", "watch")
    if (FileAccessControl.isHidden(root, target.file)) {
      throw FsException(FsErrorCode.ENOENT, "watch", target.virtual)
    }
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, "watch", target.virtual)
    val dir = if (target.file.isDirectory) target.file else target.file.parentFile
    val recursive = req.getParameter("recursive")?.let { it.isEmpty() || it.equals("true", true) } ?: false
    if (active.incrementAndGet() > MAX_WATCHERS) {
      active.decrementAndGet()
      throw FsException(FsErrorCode.EMFILE, "watch", target.virtual, "too many active watchers")
    }

    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "text/event-stream"
    resp.characterEncoding = "UTF-8"
    resp.setHeader("Cache-Control", "no-cache, no-transform")
    resp.setHeader("Connection", "keep-alive")
    resp.setHeader("X-Accel-Buffering", "no")
    resp.flushBuffer()

    val async = req.startAsync()
    async.timeout = 0
    val thread = Thread({
      var service: WatchService? = null
      try {
        service = FileSystems.getDefault().newWatchService()
        val keys = HashMap<WatchKey, Path>()
        register(service, dir.toPath(), recursive, keys)
        val writer = resp.writer
        send(resp, "ready", mapOf("path" to target.virtual, "recursive" to recursive))
        while (!Thread.currentThread().isInterrupted) {
          val key = service.poll(POLL_SECONDS, TimeUnit.SECONDS)
          if (key == null) {
            writer.write(": heartbeat\n\n")
            writer.flush()
            if (writer.checkError()) break
            continue
          }
          val base = keys[key] ?: key.watchable() as Path
          for (event in key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
              send(resp, "overflow", mapOf("path" to target.virtual))
              continue
            }
            val relative = event.context() as? Path ?: continue
            val changed = base.resolve(relative).toFile()
            if (FileAccessControl.isHidden(root, changed)) continue
            val type = if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) "change" else "rename"
            send(
              resp, "change", mapOf(
                "type" to type,
                "path" to FsPath.virtualPath(root, changed),
                "name" to changed.name,
                "isDirectory" to changed.isDirectory
              )
            )
            if (recursive && changed.isDirectory && event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
              register(service, changed.toPath(), true, keys)
            }
          }
          if (!key.reset()) {
            keys.remove(key)
            if (keys.isEmpty()) break
          }
          if (resp.writer.checkError()) break
        }
      } catch (e: InterruptedException) {
        // client disconnected / shutdown
      } catch (e: Exception) {
        log.debug("watch stream terminated for ${target.virtual}", e)
      } finally {
        try {
          service?.close()
        } catch (e: Exception) {
          // best effort
        }
        active.decrementAndGet()
        try {
          async.complete()
        } catch (e: Exception) {
          // best effort
        }
      }
    }, "fs-watch-${target.virtual}")
    thread.isDaemon = true
    thread.start()
  }

  private fun register(service: WatchService, path: Path, recursive: Boolean, keys: MutableMap<WatchKey, Path>) {
    if (keys.size >= MAX_REGISTERED_DIRS) return
    val key = path.register(
      service,
      StandardWatchEventKinds.ENTRY_CREATE,
      StandardWatchEventKinds.ENTRY_DELETE,
      StandardWatchEventKinds.ENTRY_MODIFY
    )
    keys[key] = path
    if (!recursive) return
    path.toFile().listFiles()?.filter { it.isDirectory }?.forEach { register(service, it.toPath(), true, keys) }
  }

  private fun send(resp: HttpServletResponse, event: String, payload: Map<String, Any?>) {
    val writer = resp.writer
    writer.write("event: $event\n")
    writer.write("data: ${FsJson.stringify(payload)}\n\n")
    writer.flush()
  }
}