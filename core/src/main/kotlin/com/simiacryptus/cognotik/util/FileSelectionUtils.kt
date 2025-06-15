package com.simiacryptus.cognotik.util

import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name

object FileSelectionUtils {
    val log = LoggerFactory.getLogger(FileSelectionUtils::class.java)

    fun filteredWalkAsciiTree(
        rootFile: File, maxFilesPerDir: Int = 20, fn: (File) -> Boolean = { !isLLMIgnored(it.toPath()) }
    ): String {
        val sb = StringBuilder()
        if (!fn(rootFile)) {
            log.debug("Skipping root file for tree: ${rootFile.absolutePath}")
            return "" // Root itself doesn't match, so empty tree
        }
        sb.append(rootFile.name)
        if (rootFile.isDirectory) {
            sb.append("/")
        }
        sb.appendLine()
        if (rootFile.isDirectory) {
            val children = rootFile.listFiles()?.toList() ?: emptyList()
            val entriesToConsider = children.take(maxFilesPerDir)
            entriesToConsider.forEachIndexed { index, child ->
                buildAsciiSubTree(
                    child, "", // Initial parentContinuationPrefix for children of the root
                    index == entriesToConsider.size - 1, maxFilesPerDir, fn, sb
                )
            }
        }
        return sb.toString()
    }

    private fun buildAsciiSubTree(
        currentFile: File, parentContinuationPrefix: String, // Prefix like "│   " or "    "
        isLastInSiblings: Boolean, maxFilesPerDir: Int, filterFn: (File) -> Boolean, sb: StringBuilder
    ) {
        if (!filterFn(currentFile)) {
            // If the current file is filtered out, do not display it or its children.
            // log.debug("Skipping in tree (sub): ${currentFile.absolutePath}") // Optional: for more verbose logging
            return
        }
        sb.append(parentContinuationPrefix)
        sb.append(if (isLastInSiblings) "└── " else "├── ")
        sb.append(currentFile.name)
        if (currentFile.isDirectory) {
            sb.append("/")
        }
        sb.appendLine()
        if (currentFile.isDirectory) {
            val children = currentFile.listFiles()?.toList() ?: emptyList()
            val entriesToConsider = children.take(maxFilesPerDir)
            // The new continuation prefix for the children of currentFile
            val childContinuationPrefix = parentContinuationPrefix + (if (isLastInSiblings) "    " else "│   ")
            entriesToConsider.forEachIndexed { index, child ->
                buildAsciiSubTree(
                    child, childContinuationPrefix, index == entriesToConsider.size - 1, maxFilesPerDir, filterFn, sb
                )
            }
        }
    }

    fun filteredWalk(
        file: File, maxFilesPerDir: Int = 20, fn: (File) -> Boolean = { !isLLMIgnored(it.toPath()) }
    ): List<File> {
        val result = mutableListOf<File>()
        if (fn(file)) {
            if (file.isDirectory) {
                file.listFiles()?.take(maxFilesPerDir)?.forEach { child ->
                    result.addAll(filteredWalk(child, maxFilesPerDir, fn))
                }
            } else {
                result.add(file)
            }
        } else {
            log.debug("Skipping file: ${file.absolutePath}")
        }
        return result
    }

    fun File.listFilesRecursively(): List<File> {
        val files = mutableListOf<File>()
        this.listFiles()?.filter {
            !isGitignore(it.toPath()) && !it.name.startsWith(".") && !it.name.equals("node_modules")
        }?.forEach {
            files.add(it.absoluteFile)
            if (it.isDirectory) {
                files.addAll(it.listFilesRecursively())
            }
        }
        return files
    }

    fun expandFileList(vararg data: File): Array<File> {
        return data.flatMap {
            if (!it.exists()) {
                log.debug("File does not exist during expansion: ${it.absolutePath}")
                return@flatMap emptyList<File>()
            }
            (when {
                it.name.endsWith(".data") -> arrayOf(it)
                isGitignore(it.toPath()) -> arrayOf()
                isLLMIgnored(it.toPath()) -> arrayOf()
                it.length() > 100_000_000L -> {
                    log.debug("File too large (>100MB): ${it.absolutePath}")
                    arrayOf()
                }

                it.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS -> arrayOf()

                isBinaryFile(it) -> arrayOf()
                it.isDirectory -> expandFileList(*it.listFiles() ?: arrayOf())
                else -> arrayOf(it)
            }).toList()
        }.toTypedArray()
    }

    fun isLLMTextFile(file: File): Boolean {
        return when {
            !file.exists() -> false
            file.isDirectory -> false
            file.name.endsWith(".data") -> true
            file.length() > 100_000_000L -> false // 100MB limit
            isGitignore(file.toPath()) -> false
            isLLMIgnored(file.toPath()) -> false
            file.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS -> false

            isBinaryFile(file) -> false

            else -> true
        }
    }

    fun isBinaryFile(file: File): Boolean {
        if (!file.exists() || file.isDirectory || file.length() == 0L) {
            return false
        }

        if (file.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS) {
            return true
        }

        return try {
            file.inputStream().use { input ->
                isBinaryStream(input)
            }
        } catch (e: Exception) {
            log.debug("Error reading file for binary detection: ${file.absolutePath}", e)

            false
        }
    }

    private fun isBinaryStream(input: InputStream): Boolean {
        val sampleSize = 8192 // Increased sample size for better detection
        val bytes = ByteArray(sampleSize)
        val bytesRead = input.read(bytes, 0, sampleSize)
        if (bytesRead <= 0) return false
        // Quick check for UTF-8 BOM
        if (bytesRead >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return false // UTF-8 with BOM is text
        }


        var binaryCount = 0
        var nullCount = 0
        var validUtf8Sequences = 0
        
        for (i in 0 until bytesRead) {
            val b = bytes[i].toInt() and 0xFF
            // Check for valid UTF-8 sequences
            if (b and 0x80 == 0) {
                // ASCII character
                validUtf8Sequences++
            } else if (b and 0xE0 == 0xC0 && i + 1 < bytesRead) {
                // 2-byte UTF-8 sequence
                val next = bytes[i + 1].toInt() and 0xFF
                if (next and 0xC0 == 0x80) {
                    validUtf8Sequences++
                }
            }
            
            when {
                b == 0 -> {
                    binaryCount++
                    nullCount++
                }


                // Allow common control characters: tab(9), newline(10), carriage return(13)
                b in 1..8 -> binaryCount++
                b in 11..12 -> binaryCount++
                b in 14..31 -> binaryCount++

                b >= 127 -> binaryCount++

            }
        }

        // Enhanced binary detection logic
        val nullRatio = nullCount.toDouble() / bytesRead
        val binaryRatio = binaryCount.toDouble() / bytesRead
        val utf8Ratio = validUtf8Sequences.toDouble() / bytesRead

        return when {
            nullRatio > 0.01 -> true // More than 1% null bytes
            binaryRatio > 0.30 -> true // More than 30% non-printable
            utf8Ratio > 0.95 -> false // High UTF-8 confidence
            else -> binaryRatio > 0.15 // Lower threshold with UTF-8 consideration
        }
    }

    private data class IgnoreCache(
        val patterns: List<Regex>, val lastModified: Long
    )

    private val ignorePatternCache = mutableMapOf<File, IgnoreCache>()
    private fun compileIgnorePatterns(ignoreFile: File): List<Regex> {
        val lastModified = ignoreFile.lastModified()
        val cached = ignorePatternCache[ignoreFile]
        if (cached != null && cached.lastModified == lastModified) {
            return cached.patterns
        }

        val patterns = try {
            ignoreFile.readLines()
        } catch (e: Exception) {
            log.warn("Error reading ignore file: ${ignoreFile.absolutePath}", e)
            emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { pattern ->
            try {
                // Handle negation patterns (starting with !)
                val isNegation = pattern.startsWith("!")
                val cleanPattern = if (isNegation) pattern.substring(1) else pattern

                val regexPattern = buildString {
                    append("^")
                    cleanPattern.forEach { char ->
                        when (char) {
                            '*' -> append(".*")
                            '?' -> append(".")
                            '.' -> append("\\.")
                            '/' -> append("[/\\\\]") // Handle both forward and back slashes
                            else -> append(Regex.escape(char.toString()))
                        }
                    }
                    append("$")
                }
                Regex(regexPattern)
            } catch (e: Exception) {
                log.warn("Invalid ignore pattern: $pattern", e)
                null
            }
        }
        ignorePatternCache[ignoreFile] = IgnoreCache(patterns, lastModified)
        return patterns
    }

    private fun isIgnored(path: Path, ignoreFileName: String, markerFileName: String): Boolean {
        // Check common ignored directories
        when (path.name) {
            "node_modules", "target", "build", ".gradle", "dist", "out" -> return true
            ".git" -> return markerFileName == ".git" // Only ignore .git for gitignore
        }

        var currentDir = path.toFile().parentFile ?: return false
        val checkedDirs = mutableSetOf<File>() // Prevent infinite loops

        // Walk up directory tree until we find the marker file
        while (!currentDir.resolve(markerFileName).exists() && currentDir !in checkedDirs) {
            checkedDirs.add(currentDir)
            val ignoreFile = currentDir.resolve(ignoreFileName)
            if (ignoreFile.exists()) {
                val patterns = compileIgnorePatterns(ignoreFile)
                val relativePath = try {
                    currentDir.toPath().relativize(path).toString()
                } catch (e: Exception) {
                    path.fileName.toString()
                }
                if (patterns.any { it.matches(relativePath) || it.matches(path.fileName.toString()) }) {
                    return true
                }
            }
            currentDir = currentDir.parentFile ?: break
        }

        // Check ignore file in the root directory
        val rootIgnoreFile = currentDir.resolve(ignoreFileName)
        if (rootIgnoreFile.exists()) {
            val patterns = compileIgnorePatterns(rootIgnoreFile)
            val relativePath = try {
                currentDir.toPath().relativize(path).toString()
            } catch (e: Exception) {
                path.fileName.toString()
            }
            if (patterns.any { it.matches(relativePath) || it.matches(path.fileName.toString()) }) {
                return true
            }
        }
        return false
    }

    fun isLLMIgnored(path: Path): Boolean {
        if (path.toFile().name == ".llmignore") return true
        return isIgnored(path, ".llmignore", ".llm")
    }

    fun isGitignore(path: Path): Boolean {
        if (path.fileName.toString() == ".gitignore") return true
        return isIgnored(path, ".gitignore", ".git")
    }

    fun String.relativizeFrom(root: Path) = try {
        root.relativize(File(this).toPath()).toString()
    } catch (e: Throwable) {
        this
    }

    fun fuzzyResolveToRelativePath(root: Path, filename: String): String? {
        log.debug("Resolving filename '{}' relative to root '{}'", filename, root)
        if (!root.toFile().exists() || !root.toFile().isDirectory) {
            log.debug("Root path does not exist or is not a directory: {}", root)
            return null
        }

        val backtickPattern = "`([^`]+)`".toRegex()
        var resolvedFilename = filename.trim()
        if (resolvedFilename.isEmpty()) {
            log.debug("Empty filename provided")
            return null
        }

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename

        resolvedFilename = resolvedFilename.split("\\s+".toRegex()).firstOrNull() ?: ""
        if (resolvedFilename.isEmpty()) return null

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename

        if (backtickPattern.containsMatchIn(resolvedFilename)) {
            resolvedFilename = backtickPattern.find(resolvedFilename)?.groupValues?.get(1) ?: resolvedFilename
            log.trace("Extracted filename from backticks: {}", resolvedFilename)
        }

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename
        // Handle absolute paths
        try {
            val path = File(resolvedFilename).toPath()
            if (path.startsWith(root)) {
                resolvedFilename = path.toString().relativizeFrom(root)
                log.debug("Relativized path to: {}", resolvedFilename)
            }
        } catch (e: Throwable) {
            log.debug("Error resolving filename '{}': {}", resolvedFilename, e.message)
        }

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename
        // Recursive search with better performance
        try {
            val resolvedPath = root.resolve(resolvedFilename)
            if (!resolvedPath.toFile().exists() || !resolvedPath.toFile().isFile) {
                log.debug("File not found directly under root, searching recursively")
                val targetFileName = File(resolvedFilename).name
                val foundFile = root.toFile().listFilesRecursively()
                    .asSequence()
                    .filter { it.isFile }
                    .find {
                        val normalizedPath = it.toString().replace("\\", "/")
                        val normalizedTarget = resolvedFilename.replace("\\", "/")
                        normalizedPath.endsWith(normalizedTarget) ||
                                it.name.equals(targetFileName, ignoreCase = true)
                    }
                if (foundFile != null) {
                    resolvedFilename = foundFile.toString().relativizeFrom(root)
                    log.debug("Found file recursively at: {}", resolvedFilename)
                }
            }
        } catch (e: Throwable) {
            log.debug("Error searching for file '{}' recursively: {}", resolvedFilename, e.message)
        }

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename
        // Fuzzy matching with improved algorithm
        try {
            if (!root.resolve(resolvedFilename).toFile().exists()) {
                log.debug("File not found, attempting fuzzy match")
                val levenshtein = LevenshteinDistance()
                val targetName = File(resolvedFilename).name
                val maxDistance = maxOf(2, targetName.length / 4) // More conservative threshold

                val closest = root.toFile().listFilesRecursively()
                    .asSequence()
                    .filter { it.isFile }
                    .map { file ->
                        val distance = levenshtein.apply(file.name.lowercase(), targetName.lowercase())
                        file to distance
                    }
                    .filter { it.second <= maxDistance }
                    .minByOrNull { it.second }?.first

                if (closest != null) {
                    resolvedFilename = closest.toString().relativizeFrom(root)
                    log.debug("Found closest match: {}", resolvedFilename)
                }
            }
        } catch (e: Throwable) {
            log.debug("Error finding fuzzy match for '{}': {}", resolvedFilename, e.message)
        }

        if (root.resolve(resolvedFilename).toFile().exists()) return resolvedFilename
        return null
    }

    private object FileExtensions {
        val BINARY_EXTENSIONS = setOf(
            // Archives
            "jar", "zip", "tar", "gz", "7z", "rar", "bz2", "xz", "war", "ear",
            // Compiled/Binary
            "class", "exe", "dll", "so", "dylib", "bin", "dat", "o", "obj", "lib", "a",
            // Images
            "png", "jpg", "jpeg", "gif", "ico", "bmp", "tiff", "webp", "avif", "heic",
            // Documents
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
            // Media
            "mp3", "mp4", "avi", "mov", "wav", "flac", "mkv", "webm", "m4a", "aac", "ogg",
            // 3D/CAD
            "stl", "obj", "fbx", "blend", "max", "3ds", "dae",
            // Fonts
            "ttf", "otf", "woff", "woff2", "eot",
            // Database
            "db", "sqlite", "sqlite3", "mdb",
            // Other
            "pyc", "pyo", "pyd", "wasm"
        )
    }
}