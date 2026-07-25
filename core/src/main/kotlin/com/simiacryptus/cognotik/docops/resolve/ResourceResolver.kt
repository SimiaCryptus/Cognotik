package com.simiacryptus.cognotik.docops.resolve

    import org.slf4j.LoggerFactory
    import java.io.File

    /** Resolves a single `related:` entry (path, glob, or URL) into concrete files. */
    interface ResourceResolver {
      fun handles(path: String): Boolean
      fun resolve(baseDir: File, path: String): List<File>
    }

    object Urls {
      fun isUrl(path: String): Boolean = path.startsWith("http://") || path.startsWith("https://")
    }

    class UrlResourceResolver(private val cache: UrlCache) : ResourceResolver {
      override fun handles(path: String) = Urls.isUrl(path)
      override fun resolve(baseDir: File, path: String): List<File> =
        listOfNotNull(cache.get(path)?.absoluteFile)
    }

    /**
     * Globs are expanded; plain paths are returned even when missing (a sibling task may be about
     * to create them).
     */
    class FileResourceResolver(
      private val lister: (File) -> List<File> = GlobExpander.defaultLister,
    ) : ResourceResolver {

      override fun handles(path: String) = !Urls.isUrl(path)

      override fun resolve(baseDir: File, path: String): List<File> {
        if (GlobExpander.isGlobPattern(path)) {
          val expanded = GlobExpander.expandPatternOrLiteral(baseDir, path, lister)
          if (expanded.isEmpty()) log.debug("Related glob matched no files: $path (base: ${baseDir.absolutePath})")
          return expanded
        }
        val resolved = baseDir.resolve(path)
        if (!resolved.exists()) log.debug("Related file does not exist: ${resolved.absolutePath}")
        return listOf(resolved)
      }

      companion object {
        private val log = LoggerFactory.getLogger(FileResourceResolver::class.java)
      }
    }

    class CompositeResourceResolver(private val delegates: List<ResourceResolver>) : ResourceResolver {
      override fun handles(path: String) = delegates.any { it.handles(path) }
      override fun resolve(baseDir: File, path: String): List<File> =
        delegates.firstOrNull { it.handles(path) }?.resolve(baseDir, path) ?: emptyList()
    }