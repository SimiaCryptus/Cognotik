package com.simiacryptus.cognotik.webui.servlet.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Uploads of ZIP archives that are expanded in place -- the inverse of the ZIP
 * download offered by `StaticZipServlet` / the "Download Current Directory as ZIP" link.
 *
 * Accepted shapes:
 *  - `POST` multipart/form-data where at least one file part is a ZIP
 *    (expansion is on by default, disable with the `expand=false` form field or query param).
 *  - `POST` with a raw body and `Content-Type: application/zip`.
 *  - `PUT ...?expand=true` with a raw ZIP body (opt-in, so ordinary PUTs still write a file).
 *
 * Safety rules applied to every entry:
 *  - zip-slip: the canonical destination must stay inside the target directory;
 *  - hidden and read-only paths (see [FileAccessControl]) are skipped, never overwritten;
 *  - entry count and total uncompressed size are bounded ([MAX_ENTRIES] / [MAX_TOTAL_BYTES]).
 */
object ZipUploadHandler {
  private val log = LoggerFactory.getLogger(ZipUploadHandler::class.java)

  /** Zip-bomb guards. */
  const val MAX_ENTRIES = 10_000
  const val MAX_TOTAL_BYTES = 1L * 1024 * 1024 * 1024 // 1GB uncompressed

  private val ZIP_CONTENT_TYPES = setOf(
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/zip-compressed",
    "multipart/x-zip",
  )
  private val ZIP_EXTENSIONS = setOf("zip")
  private val FALSEY = setOf("false", "0", "no", "off")

  /** Aggregated outcome of one request (may cover several archives). */
  class Summary {
    var archives = 0
    var files = 0
    var directories = 0
    var uploaded = 0
    var bytes = 0L
    var truncated = false
    val skipped = mutableListOf<String>()

    fun message(): String {
      val sb = StringBuilder()
      if (archives > 0) {
        sb.append("Expanded $archives archive(s): $files file(s), $directories folder(s), ${bytes / 1024} KB")
      }
      if (uploaded > 0) {
        if (sb.isNotEmpty()) sb.append("; ")
        sb.append("stored $uploaded file(s)")
      }
      if (sb.isEmpty()) sb.append("Nothing to do")
      if (skipped.isNotEmpty()) sb.append("; skipped ${skipped.size} entry(ies)")
      if (truncated) sb.append(" (archive truncated: entry limit reached)")
      return sb.toString()
    }
  }

  // --- Entry points -------------------------------------------------------

  /**
   * Handles a POST that carries one or more ZIP archives. Returns true when the request
   * was fully handled (a response has been written); false to let the ordinary upload
   * handler deal with it.
   */
  fun tryHandlePost(
    request: HttpServletRequest,
    response: HttpServletResponse,
    targetDir: File?,
    baseDir: File?
  ): Boolean {
    if (targetDir == null) return false
    val contentType = (request.contentType ?: "").lowercase()
    return when {
      ZIP_CONTENT_TYPES.any { contentType.startsWith(it) } -> {
        if (!isExpandEnabled(request)) return false
        val dir = prepareDir(targetDir) ?: run { fail(response, "Unable to create ${targetDir.name}"); return true }
        if (isForbidden(baseDir, dir)) { notFound(response); return true }
        val summary = Summary()
        request.inputStream.use { expand(it, dir, baseDir, overwrite(request), summary) }
        respond(response, summary)
        true
      }

      contentType.startsWith("multipart/form-data") -> {
        val parts = try {
          request.parts
        } catch (e: Exception) {
          log.debug("unable to read multipart parts for zip detection", e)
          return false
        }
        val fileParts = parts.filter { !it.submittedFileName.isNullOrBlank() }
        if (fileParts.none { isZipPart(it) }) return false
        if (!isExpandEnabled(request)) return false
        val dir = prepareDir(targetDir) ?: run { fail(response, "Unable to create ${targetDir.name}"); return true }
        if (isForbidden(baseDir, dir)) { notFound(response); return true }
        val overwrite = overwrite(request)
        val summary = Summary()
        for (part in fileParts) {
          if (isZipPart(part)) {
            part.inputStream.use { expand(it, dir, baseDir, overwrite, summary) }
          } else {
            storePart(part, dir, baseDir, overwrite, summary)
          }
        }
        respond(response, summary)
        true
      }

      else -> false
    }
  }

  /**
   * Handles `PUT /path/?expand=true` with a raw ZIP body. Expansion is opt-in here so a
   * plain `PUT foo.zip` still stores the archive verbatim.
   *
   * The archive is expanded into the addressed path, or into its parent directory when
   * the path itself names a `.zip` file (so `PUT /a/b.zip?expand=true` fills `/a`).
   */
  fun tryHandlePut(
    request: HttpServletRequest,
    response: HttpServletResponse,
    baseDir: File,
    pathSegments: Path
  ): Boolean {
    val expand = request.getParameter("expand") ?: request.getParameter("expandZip") ?: return false
    if (expand.lowercase() in FALSEY) return false
    val relative = pathSegments.drop(1).joinToString("/")
    val addressed = File(baseDir, relative)
    val requested = if (isZipName(addressed.name)) (addressed.parentFile ?: baseDir) else addressed
    if (!requested.canonicalFile.toPath().startsWith(baseDir.canonicalFile.toPath())) {
      notFound(response)
      return true
    }
    val dir = prepareDir(requested) ?: run { fail(response, "Unable to create ${requested.name}"); return true }
    if (isForbidden(baseDir, dir)) { notFound(response); return true }
    val summary = Summary()
    request.inputStream.use { expand(it, dir, baseDir, overwrite(request), summary) }
    respond(response, summary)
    return true
  }

  // --- Expansion ----------------------------------------------------------

  fun expand(input: InputStream, targetDir: File, baseDir: File?, overwrite: Boolean, summary: Summary) {
    val rootPath = targetDir.canonicalFile.toPath()
    val zip = ZipInputStream(BufferedInputStream(input))
    var entries = 0
    var entry = zip.nextEntry
    while (entry != null) {
      if (++entries > MAX_ENTRIES) {
        summary.truncated = true
        log.warn("Refusing to expand more than $MAX_ENTRIES entries into ${targetDir.absolutePath}")
        break
      }
      val name = entry.name.replace('\\', '/')
      val dest = resolveSafely(targetDir, rootPath, name)
      when {
        dest == null -> {
          log.warn("Skipping unsafe zip entry: $name")
          summary.skipped += name
        }

        baseDir != null && (FileAccessControl.isHidden(baseDir, dest) || FileAccessControl.isReadOnly(baseDir, dest)) -> {
          summary.skipped += name
        }

        entry.isDirectory || name.endsWith("/") -> {
          if (dest.isDirectory || dest.mkdirs()) summary.directories++ else summary.skipped += name
        }

        dest.exists() && !overwrite -> summary.skipped += name

        else -> {
          dest.parentFile?.takeIf { !it.exists() }?.mkdirs()
          var written = 0L
          dest.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
              val read = zip.read(buffer)
              if (read < 0) break
              if (summary.bytes + written + read > MAX_TOTAL_BYTES) {
                throw IOException("Archive exceeds the ${MAX_TOTAL_BYTES / (1024 * 1024)}MB uncompressed limit")
              }
              out.write(buffer, 0, read)
              written += read
            }
          }
          summary.bytes += written
          summary.files++
        }
      }
      zip.closeEntry()
      entry = zip.nextEntry
    }
    summary.archives++
    log.info("Expanded archive into ${targetDir.absolutePath}: ${summary.message()}")
  }

  // --- Helpers ------------------------------------------------------------

  fun isZipName(name: String?): Boolean =
    name != null && name.substringAfterLast('.', "").lowercase() in ZIP_EXTENSIONS

  private fun isZipPart(part: Part): Boolean =
    isZipName(part.submittedFileName) || (part.contentType ?: "").lowercase().substringBefore(';') in ZIP_CONTENT_TYPES

  private fun isExpandEnabled(request: HttpServletRequest): Boolean {
    val value = formValue(request, "expand") ?: formValue(request, "expandZip") ?: return true
    return value.lowercase() !in FALSEY
  }

  private fun overwrite(request: HttpServletRequest): Boolean {
    val value = formValue(request, "overwrite") ?: return true
    return value.lowercase() !in FALSEY
  }

  /** Query parameters first, then simple multipart form fields. */
  private fun formValue(request: HttpServletRequest, name: String): String? {
    request.getParameter(name)?.let { return it }
    return try {
      request.parts.firstOrNull { it.name == name && it.submittedFileName.isNullOrBlank() }
        ?.inputStream?.use { String(it.readBytes(), Charsets.UTF_8) }?.trim()?.ifBlank { null }
    } catch (e: Exception) {
      null
    }
  }

  private fun prepareDir(dir: File): File? {
    val target = if (dir.isFile) (dir.parentFile ?: return null) else dir
    if (!target.exists() && !target.mkdirs()) return null
    return if (target.isDirectory) target else null
  }

  private fun isForbidden(baseDir: File?, dir: File): Boolean =
    baseDir != null && (FileAccessControl.isHidden(baseDir, dir) || FileAccessControl.isReadOnly(baseDir, dir))

  private fun resolveSafely(targetDir: File, rootPath: Path, entryName: String): File? {
    val cleaned = entryName.trim().trimStart('/')
    if (cleaned.isBlank()) return null
    if (cleaned.split('/').any { it == ".." || it == "." }) return null
    val canonical = File(targetDir, cleaned).canonicalFile
    return if (canonical.toPath().startsWith(rootPath)) canonical else null
  }

  private fun storePart(part: Part, dir: File, baseDir: File?, overwrite: Boolean, summary: Summary) {
    val submitted = part.submittedFileName ?: return
    val name = submitted.replace('\\', '/').substringAfterLast('/')
    if (name.isBlank()) return
    val dest = File(dir, name).canonicalFile
    if (!dest.toPath().startsWith(dir.canonicalFile.toPath())) {
      summary.skipped += name
      return
    }
    if (baseDir != null && (FileAccessControl.isHidden(baseDir, dest) || FileAccessControl.isReadOnly(baseDir, dest))) {
      summary.skipped += name
      return
    }
    if (dest.exists() && !overwrite) {
      summary.skipped += name
      return
    }
    part.inputStream.use { input -> dest.outputStream().use { input.copyTo(it, 64 * 1024) } }
    summary.uploaded++
  }

  private fun respond(response: HttpServletResponse, summary: Summary) {
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_OK
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    val skipped = summary.skipped.take(50).joinToString(",") { "\"${escapeJson(it)}\"" }
    response.writer.write(
      """{"success": true, "message": "${escapeJson(summary.message())}",""" +
          """"archives": ${summary.archives}, "files": ${summary.files},""" +
          """"directories": ${summary.directories}, "uploaded": ${summary.uploaded},""" +
          """"bytes": ${summary.bytes}, "truncated": ${summary.truncated},""" +
          """"skipped": [$skipped]}"""
    )
  }

  private fun fail(response: HttpServletResponse, message: String) {
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    response.writer.write("""{"success": false, "message": "${escapeJson(message)}"}""")
  }

  private fun notFound(response: HttpServletResponse) {
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_NOT_FOUND
    response.contentType = "text/plain"
    response.characterEncoding = "UTF-8"
    response.writer.write("File not found")
  }

  private fun escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}