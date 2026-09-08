package com.simiacryptus.cognotik.fileserver.handler

import com.simiacryptus.cognotik.fileserver.util.FsPath
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * GET /.fsapi/v1/snapshot — a zip of a subtree used to prime the client's
 * in-memory VFS for "snapshot mode" (nodejs.md §6.4 S4, §10 cold start).
 *
 * Hidden paths are never included. Truncation (maxBytes) is reported in the
 * X-Fs-Snapshot-Truncated header so the client can fall back to live I/O.
 */
object FsSnapshotHandler {
  fun handle(req: HttpServletRequest, resp: HttpServletResponse, root: File, config: FsApiConfig) {
    if (!config.snapshotEnabled) {
      throw FsException(FsErrorCode.ENOSYS, "snapshot", null, "snapshot capability disabled")
    }
    val requested = req.getParameter("path") ?: "/"
    val target = FsPath.resolve(root, requested, "snapshot")
    if (FileAccessControl.isHidden(root, target.file)) {
      throw FsException(FsErrorCode.ENOENT, "snapshot", target.virtual)
    }
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, "snapshot", target.virtual)
    if (!target.file.isDirectory) throw FsException(FsErrorCode.ENOTDIR, "snapshot", target.virtual)
    val maxBytes = (req.getParameter("maxBytes")?.toLongOrNull() ?: config.maxSnapshotBytes)
      .coerceAtMost(config.maxSnapshotBytes)
    val name = if (target.file == root.canonicalFile) "root" else target.file.name
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "application/zip"
    resp.setHeader("Content-Disposition", "attachment; filename=\"$name.zip\"")
    resp.setHeader("X-Fs-Snapshot-Root", target.virtual)
    val budget = Budget(maxBytes)
    ZipOutputStream(resp.outputStream).use { zip ->
      add(root, target.file, "", zip, budget)
      zip.finish()
    }
    if (!resp.isCommitted) resp.setHeader("X-Fs-Snapshot-Truncated", budget.truncated.toString())
  }

  private class Budget(val maxBytes: Long) {
    var used = 0L
    var truncated = false
  }

  private fun add(root: File, dir: File, prefix: String, zip: ZipOutputStream, budget: Budget) {
    val children = dir.listFiles()?.sortedBy { it.name } ?: return
    for (child in children) {
      if (FileAccessControl.isHidden(root, child)) continue
      val entryName = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
      if (child.isDirectory) {
        zip.putNextEntry(ZipEntry("$entryName/"))
        zip.closeEntry()
        add(root, child, entryName, zip, budget)
      } else {
        if (budget.used + child.length() > budget.maxBytes) {
          budget.truncated = true
          continue
        }
        budget.used += child.length()
        val entry = ZipEntry(entryName)
        entry.time = child.lastModified()
        zip.putNextEntry(entry)
        child.inputStream().use { it.copyTo(zip, 64 * 1024) }
        zip.closeEntry()
      }
    }
  }
}