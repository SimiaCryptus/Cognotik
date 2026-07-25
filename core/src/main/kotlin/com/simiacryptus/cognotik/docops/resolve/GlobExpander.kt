package com.simiacryptus.cognotik.docops.resolve

    import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
    import org.slf4j.LoggerFactory
    import java.io.File
    import java.nio.file.FileSystems
    import java.nio.file.PathMatcher

    /** Literal / simple-glob / recursive-glob path expansion. Pure apart from directory listings. */
    object GlobExpander {

      private val log = LoggerFactory.getLogger(GlobExpander::class.java)

      /** Default recursive lister; injectable so [com.simiacryptus.cognotik.docops.plan.ResolveContext] can memoize. */
      val defaultLister: (File) -> List<File> = { it.listFilesRecursively() }

      fun isGlobPattern(pattern: String): Boolean =
        pattern.contains("*") || pattern.contains("?") || pattern.contains("[")

      fun expandPatternOrLiteral(
        baseDir: File,
        pattern: String,
        lister: (File) -> List<File> = defaultLister,
      ): List<File> = if (isGlobPattern(pattern)) {
        if (pattern.contains("**")) expandRecursiveGlob(baseDir, pattern, lister)
        else expandSimpleGlob(baseDir, pattern)
      } else {
        listOf(
          try {
            baseDir.resolve(pattern).canonicalFile
          } catch (e: Exception) {
            log.warn("Failed to resolve literal path '$pattern'", e)
            baseDir.resolve(pattern)
          }
        )
      }

      fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
        val patternFile = File(pattern)
        val directory = try {
          if (patternFile.parent != null) baseDir.resolve(patternFile.parent).canonicalFile
          else baseDir.canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve directory for pattern '$pattern'", e)
          return emptyList()
        }
        if (!directory.exists() || !directory.isDirectory) {
          log.warn("Directory does not exist: ${directory.absolutePath}")
          return emptyList()
        }
        val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:${patternFile.name}")
        return directory.listFiles()?.filter { it.isFile && matcher.matches(it.toPath().fileName) } ?: emptyList()
      }

      fun expandRecursiveGlob(
        baseDir: File,
        pattern: String,
        lister: (File) -> List<File> = defaultLister,
      ): List<File> {
        val beforeGlob = pattern.substringBefore("**").removeSuffix("/").removeSuffix("\\")
        val resolvedBase = try {
          if (beforeGlob.isNotEmpty()) baseDir.resolve(beforeGlob).canonicalFile else baseDir.canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve base directory for pattern '$pattern'", e)
          return emptyList()
        }
        if (!resolvedBase.exists()) {
          log.warn("Base directory does not exist: ${resolvedBase.absolutePath}")
          return emptyList()
        }
        val remainingPattern = pattern.substringAfter("**").removePrefix("/").removePrefix("\\")
        val matcher: PathMatcher = if (remainingPattern.isNotEmpty()) {
          try {
            FileSystems.getDefault().getPathMatcher("glob:$remainingPattern")
          } catch (e: Exception) {
            log.warn("Invalid glob pattern: $remainingPattern", e)
            PathMatcher { false }
          }
        } else PathMatcher { true }

        return lister(resolvedBase).filter { it.isFile && matcher.matches(it.toPath().fileName) }
      }
    }