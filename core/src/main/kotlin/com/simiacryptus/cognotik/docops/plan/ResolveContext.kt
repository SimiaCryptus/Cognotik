package com.simiacryptus.cognotik.docops.plan

    import com.simiacryptus.cognotik.docops.resolve.GlobExpander
    import com.simiacryptus.cognotik.docops.resolve.ResourceResolver
    import java.io.File
    import java.util.concurrent.ConcurrentHashMap

    /**
     * Per-plan resolution scope. Recursive directory listings are memoized so a whole-tree walk
     * happens at most once per `plan()` invocation, no matter how many transforms exist.
     */
    class ResolveContext(
      val root: File,
      val resources: ResourceResolver,
      private val rawLister: (File) -> List<File> = GlobExpander.defaultLister,
    ) {

      private val listings = ConcurrentHashMap<String, List<File>>()

      val lister: (File) -> List<File> = { dir -> listFilesRecursively(dir) }

      fun listFilesRecursively(dir: File): List<File> {
        val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        return listings.getOrPut(key) { rawLister(dir) }
      }

      /** Literal-or-glob expansion (literals are returned even when they do not exist yet). */
      fun expand(baseDir: File, pattern: String): List<File> =
        GlobExpander.expandPatternOrLiteral(baseDir, pattern, lister)

      /** Glob-only expansion (used by `documents:` / `generates.inputs`, which never invent files). */
      fun expandGlob(baseDir: File, pattern: String): List<File> =
        if (pattern.contains("**")) GlobExpander.expandRecursiveGlob(baseDir, pattern, lister)
        else GlobExpander.expandSimpleGlob(baseDir, pattern)

      fun resolveRelated(baseDir: File, path: String): List<File> = resources.resolve(baseDir, path)
    }