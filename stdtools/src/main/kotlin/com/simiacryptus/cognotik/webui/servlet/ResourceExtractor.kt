package com.simiacryptus.cognotik.webui.servlet

    import org.slf4j.LoggerFactory
    import java.io.File
    import java.io.InputStream
    import java.net.JarURLConnection
    import java.net.URI
    import java.net.URL
    import java.net.URLClassLoader
    import java.net.URLDecoder
    import java.util.jar.JarFile

    /**
     * The one implementation of "copy a classpath resource tree into a directory".
     *
     * It used to live inside [DocOpsApp]; it is shared now because the FS API exposes the
     * same operation (see `ExtractUtilsFsAction`), and two copies of the jar/filesystem
     * walk would inevitably drift apart.
     *
     * Resolution order (unchanged):
     *  1. `classLoader.getResource(prefix)` - the normal case, jar or exploded classes;
     *  2. a scan of every URL in the classloader hierarchy, because `URLClassLoader`
     *     returns null for *directory* entries of some jars.
     *
     * @return the files actually written; empty when the resource does not exist.
     */
    object ResourceExtractor {
      private val log = LoggerFactory.getLogger(ResourceExtractor::class.java)

      /** Resource root of the shared browser tooling (`extract-utils` default). */
      const val UTIL_RESOURCE_PATH = "web/util"

      fun extract(
        resourcePath: String,
        targetDir: File,
        classLoader: ClassLoader = ResourceExtractor::class.java.classLoader,
        overwrite: Boolean = true,
        skipDemoFolders: Boolean = false,
      ): List<File> {
        val prefix = resourcePath.trim('/')
        if (prefix.isEmpty()) return emptyList()
        targetDir.mkdirs()
        val written = mutableListOf<File>()
        classLoader.getResource(prefix)?.let { url ->
          written += runCatching { extractFromUrl(url, prefix, targetDir, overwrite, skipDemoFolders) }
            .onFailure { log.debug("Failed to extract {} from {}: {}", prefix, url, it.message) }
            .getOrDefault(emptyList())
        }
        if (written.isEmpty()) {
          written += extractFromClassLoaderUrls(prefix, targetDir, classLoader, overwrite)
        }
        return written
      }

      private fun extractFromUrl(
        resourceUrl: URL,
        prefix: String,
        targetDir: File,
        overwrite: Boolean,
        skipDemoFolders: Boolean,
      ): List<File> {
        val decodedUrl = URLDecoder.decode(resourceUrl.toString(), "UTF-8")
        val written = mutableListOf<File>()
        if (decodedUrl.startsWith("jar:")) {
          val jarFile = (resourceUrl.openConnection() as JarURLConnection).jarFile
          val entries = jarFile.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            /* Exact prefix match only: 'web/util' must not pull in 'web/utils2'. */
            if (!entry.name.startsWith("$prefix/")) continue
            val relativePath = entry.name.substring(prefix.length).trimStart('/')
            if (relativePath.isEmpty()) continue
            if (skipDemoFolders && containsDemoFolder(relativePath)) continue
            val targetFile = File(targetDir, relativePath)
            if (!overwrite && targetFile.exists()) continue
            targetFile.parentFile?.mkdirs()
            jarFile.getInputStream(entry).use { input -> copyWithLineEndingNormalization(input, targetFile) }
            written += targetFile
          }
          return written
        }
        /* Non-jar resources (e.g. during development). */
        val resourceDir = File(resourceUrl.toURI())
        resourceDir.walkTopDown().forEach { file ->
          if (!file.isFile) return@forEach
          val relativePath = file.relativeTo(resourceDir).path
          if (skipDemoFolders && containsDemoFolder(relativePath)) return@forEach
          val targetFile = File(targetDir, relativePath)
          if (!overwrite && targetFile.exists()) return@forEach
          targetFile.parentFile?.mkdirs()
          copyFileWithLineEndingNormalization(file, targetFile)
          written += targetFile
        }
        return written
      }

      /**
       * Fallback: scan all URLs in the classloader hierarchy for JARs containing the
       * resource path. This handles URLClassLoader instances where getResource() returns
       * null for directory entries. 'demo' folders are never extracted here.
       */
      private fun extractFromClassLoaderUrls(
        prefix: String,
        targetDir: File,
        classLoader: ClassLoader,
        overwrite: Boolean,
      ): List<File> {
        val written = mutableListOf<File>()
        for (url in collectClassLoaderUrls(classLoader)) {
          val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8")
          if (decodedUrl.endsWith(".jar") || decodedUrl.endsWith(".jar!/")) {
            try {
              val jarPath = if (decodedUrl.startsWith("file:")) {
                File(URI(url.toString().removeSuffix("!/")))
              } else {
                File(decodedUrl.removePrefix("file:").removeSuffix("!/"))
              }
              JarFile(jarPath).use { jarFile ->
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                  val entry = entries.nextElement()
                  if (entry.isDirectory || !entry.name.startsWith("$prefix/")) continue
                  val relativePath = entry.name.substring(prefix.length).trimStart('/')
                  if (relativePath.isEmpty() || containsDemoFolder(relativePath)) continue
                  val targetFile = File(targetDir, relativePath)
                  if (!overwrite && targetFile.exists()) continue
                  targetFile.parentFile?.mkdirs()
                  jarFile.getInputStream(entry).use { input ->
                    copyWithLineEndingNormalization(input, targetFile)
                  }
                  written += targetFile
                }
              }
              if (written.isNotEmpty()) break
            } catch (e: Exception) {
              log.debug("Failed to scan JAR {}: {}", decodedUrl, e.message)
            }
          } else {
            try {
              val resourceDir = File(File(URI(url.toString())), prefix)
              if (!resourceDir.isDirectory) continue
              resourceDir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val relativePath = file.relativeTo(resourceDir).path
                if (containsDemoFolder(relativePath)) return@forEach
                val targetFile = File(targetDir, relativePath)
                if (!overwrite && targetFile.exists()) return@forEach
                targetFile.parentFile?.mkdirs()
                copyFileWithLineEndingNormalization(file, targetFile)
                written += targetFile
              }
              if (written.isNotEmpty()) break
            } catch (e: Exception) {
              log.debug("Failed to scan directory {}: {}", decodedUrl, e.message)
            }
          }
        }
        return written
      }

      private fun collectClassLoaderUrls(cl: ClassLoader?): List<URL> {
        val urls = mutableListOf<URL>()
        var current = cl
        while (current != null) {
          if (current is URLClassLoader) urls.addAll(current.urLs)
          current = current.parent
        }
        return urls
      }

      /** Returns true if the given path contains a folder segment named 'demo'. */
      private fun containsDemoFolder(path: String): Boolean =
        path.replace('\\', '/').split('/').any { it == "demo" }

      private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "html", "htm", "css", "js", "json", "xml", "yaml", "yml",
        "csv", "tsv", "svg", "sh", "bat", "cmd", "ps1", "py", "rb", "pl",
        "java", "kt", "kts", "groovy", "scala", "c", "cpp", "h", "hpp",
        "ts", "tsx", "jsx", "vue", "scss", "sass", "less", "sql", "graphql",
        "properties", "cfg", "conf", "ini", "toml", "env", "gitignore",
        "dockerfile", "makefile", "gradle", "sbt", "rs", "go", "swift",
        "r", "lua", "php", "asp", "jsp", "erb", "ejs", "hbs", "mustache",
        "log", "tex", "rst", "adoc", "asciidoc", "mjs", "cjs"
      )

      fun copyFileWithLineEndingNormalization(source: File, targetFile: File) {
        if (isTextFile(targetFile.name)) {
          targetFile.writeText(source.readText(Charsets.UTF_8).replace("\r\n", "\n"), Charsets.UTF_8)
        } else {
          source.copyTo(targetFile, overwrite = true)
        }
        setExecutableIfShellScript(targetFile)
      }

      fun copyWithLineEndingNormalization(input: InputStream, targetFile: File) {
        if (isTextFile(targetFile.name)) {
          targetFile.writeText(input.readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n"), Charsets.UTF_8)
        } else {
          targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        setExecutableIfShellScript(targetFile)
      }

      private fun isTextFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val baseName = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return ext in TEXT_EXTENSIONS || baseName in setOf(
          "dockerfile", "makefile", "gemfile", "rakefile", "vagrantfile",
          "license", "readme", "changelog", "authors", "contributors"
        )
      }

      /** Sets the executable bit on shell scripts (*.sh) so they can be run directly. */
      private fun setExecutableIfShellScript(file: File) {
        if (!file.name.endsWith(".sh", ignoreCase = true)) return
        try {
          if (!file.setExecutable(true, false)) {
            log.warn("Failed to set executable bit on shell script: ${file.absolutePath}")
          }
        } catch (e: Exception) {
          log.warn("Exception setting executable bit on ${file.absolutePath}: ${e.message}", e)
        }
      }
    }