package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import com.simiacryptus.cognotik.webui.servlet.util.MiniJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

/**
 * Server-side CommonJS/ESM resolution (nodejs.md §7.2 "M2 fast path").
 *
 * A bare `require('pkg')` costs ~15 stat() calls in the browser; doing the
 * walk here turns that into a single round trip.
 */
object FsResolveHandler {

  private val BUILTINS = setOf(
    "assert", "async_hooks", "buffer", "child_process", "cluster", "console", "constants", "crypto",
    "dgram", "diagnostics_channel", "dns", "domain", "events", "fs", "fs/promises", "http", "http2",
    "https", "inspector", "module", "net", "os", "path", "path/posix", "path/win32", "perf_hooks",
    "process", "punycode", "querystring", "readline", "repl", "stream", "stream/promises",
    "string_decoder", "sys", "timers", "timers/promises", "tls", "trace_events", "tty", "url",
    "util", "util/types", "v8", "vm", "wasi", "worker_threads", "zlib"
  )
  private val FILE_EXTENSIONS = listOf("", ".js", ".json", ".mjs", ".cjs", ".node")
  private val INDEX_FILES = listOf("index.js", "index.json", "index.mjs", "index.cjs", "index.node")

  fun handle(req: HttpServletRequest, resp: HttpServletResponse, root: File, config: FsApiConfig) {
    if (!config.resolveEnabled) throw FsException(FsErrorCode.ENOSYS, "resolve", null, "resolve capability disabled")
    val request = req.getParameter("request")
      ?: throw FsException(FsErrorCode.EINVAL, "resolve", null, "missing 'request' parameter")
    val from = req.getParameter("from") ?: "/"
    val payload = resolve(root, config, from, request)
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(MiniJson.stringify(payload))
  }

  fun resolve(root: File, config: FsApiConfig, from: String, request: String): Map<String, Any?> {
    val specifier = request.removePrefix("node:")
    if (specifier in BUILTINS && !request.startsWith(".") && !request.startsWith("/")) {
      return linkedMapOf("type" to "builtin", "id" to specifier, "request" to request)
    }
    val baseDir = baseDirectoryOf(root, from)
    val hit = when {
      request.startsWith("/") -> loadAsFileOrDirectory(root, FsPath.resolve(root, request, "resolve").file)
      request.startsWith("./") || request.startsWith("../") || request == "." || request == ".." ->
        loadAsFileOrDirectory(root, File(baseDir, request))

      else -> loadFromNodeModules(root, baseDir, request)
    } ?: throw FsException(
      FsErrorCode.ENOENT, "resolve", request,
      "cannot find module '$request' from '${FsPath.virtualPath(root, baseDir)}'"
    )
    val virtual = FsPath.virtualPath(root, hit)
    return linkedMapOf(
      "type" to "file",
      "path" to virtual,
      "request" to request,
      "format" to formatOf(root, hit),
      "size" to hit.length(),
      "mtimeMs" to hit.lastModified()
    )
  }

  private fun baseDirectoryOf(root: File, from: String): File {
    val resolved = FsPath.resolve(root, from, "resolve").file
    return if (resolved.isDirectory) resolved else (resolved.parentFile ?: root)
  }

  private fun visible(root: File, file: File): Boolean =
    file.exists() && !FileAccessControl.isHidden(root, file)

  private fun loadAsFile(root: File, candidate: File): File? {
    for (extension in FILE_EXTENSIONS) {
      val file = if (extension.isEmpty()) candidate else File(candidate.parentFile, candidate.name + extension)
      if (visible(root, file) && file.isFile) return file
    }
    return null
  }

  private fun loadAsDirectory(root: File, dir: File): File? {
    if (!visible(root, dir) || !dir.isDirectory) return null
    val packageJson = File(dir, "package.json")
    if (visible(root, packageJson) && packageJson.isFile) {
      val manifest = try {
        MiniJson.parseObject(packageJson.readText())
      } catch (e: Exception) {
        emptyMap()
      }
      val main = mainEntry(manifest)
      if (main != null) {
        val target = File(dir, main)
        loadAsFile(root, target)?.let { return it }
        loadIndex(root, target)?.let { return it }
      }
    }
    return loadIndex(root, dir)
  }

  @Suppress("UNCHECKED_CAST")
  private fun mainEntry(manifest: Map<String, Any?>): String? {
    val fromExports = when (val exports = manifest["exports"]) {
      is String -> exports
      is Map<*, *> -> {
        when (val dot = (exports as Map<String, Any?>)["."]) {
          is String -> dot
          is Map<*, *> -> (dot["import"] ?: dot["require"] ?: dot["default"])?.toString()
          else -> null
        }
      }

      else -> null
    }
    return fromExports ?: MiniJson.string(manifest, "main")
  }

  private fun loadIndex(root: File, dir: File): File? {
    if (!dir.isDirectory) return null
    for (name in INDEX_FILES) {
      val file = File(dir, name)
      if (visible(root, file) && file.isFile) return file
    }
    return null
  }

  private fun loadAsFileOrDirectory(root: File, candidate: File): File? =
    loadAsFile(root, candidate) ?: loadAsDirectory(root, candidate)

  private fun loadFromNodeModules(root: File, from: File, request: String): File? {
    val rootPath = root.canonicalFile.path
    var current: File? = from.canonicalFile
    while (current != null) {
      if (current.name != "node_modules") {
        val candidate = File(File(current, "node_modules"), request)
        loadAsFileOrDirectory(root, candidate)?.let { return it }
      }
      if (current.path == rootPath) break
      current = current.parentFile
    }
    return null
  }

  private fun formatOf(root: File, file: File): String {
    return when {
      file.name.endsWith(".json") -> "json"
      file.name.endsWith(".mjs") -> "module"
      file.name.endsWith(".cjs") -> "commonjs"
      file.name.endsWith(".node") -> "addon"
      else -> nearestPackageType(root, file.parentFile)
    }
  }

  private fun nearestPackageType(root: File, start: File?): String {
    val rootPath = root.canonicalFile.path
    var current: File? = start?.canonicalFile
    while (current != null) {
      val packageJson = File(current, "package.json")
      if (packageJson.isFile && !FileAccessControl.isHidden(root, packageJson)) {
        val manifest = try {
          MiniJson.parseObject(packageJson.readText())
        } catch (e: Exception) {
          emptyMap()
        }
        if (MiniJson.string(manifest, "type") == "module") return "module"
        return "commonjs"
      }
      if (current.path == rootPath) break
      current = current.parentFile
    }
    return "commonjs"
  }
}