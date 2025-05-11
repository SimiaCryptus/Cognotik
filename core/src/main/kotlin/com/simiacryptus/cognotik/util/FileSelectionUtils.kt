package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.text.StringBuilder
import kotlin.io.path.name

class FileSelectionUtils {
    companion object {
        val log = LoggerFactory.getLogger(FileSelectionUtils::class.java)
        /**
         * Creates an ASCII-art formatted compact tree listing of files and directories
         * starting from the given root file, subject to filtering and depth constraints.
         *
         * @param rootFile The starting file or directory.
         * @param maxFilesPerDir The maximum number of entries to process in any single directory.
         * @param fn A filter function that determines whether a file or directory should be included in the tree.
         * @return A string representing the ASCII tree of matching files.
         */
        fun filteredWalkAsciiTree(
            rootFile: File,
            maxFilesPerDir: Int = 20,
            fn: (File) -> Boolean = { !isLLMIgnored(it.toPath()) }
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
                        child,
                        "", // Initial parentContinuationPrefix for children of the root
                        index == entriesToConsider.size - 1,
                        maxFilesPerDir,
                        fn,
                        sb
                    )
                }
            }
            return sb.toString()
        }
        private fun buildAsciiSubTree(
            currentFile: File,
            parentContinuationPrefix: String, // Prefix like "│   " or "    "
            isLastInSiblings: Boolean,
            maxFilesPerDir: Int,
            filterFn: (File) -> Boolean,
            sb: StringBuilder
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
                        child,
                        childContinuationPrefix,
                        index == entriesToConsider.size - 1,
                        maxFilesPerDir,
                        filterFn,
                        sb
                    )
                }
            }
        }


        fun filteredWalk(
            file: File,
            maxFilesPerDir: Int = 20,
            fn: (File) -> Boolean = { !isLLMIgnored(it.toPath()) }
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

        fun isLLMIncludableFile(file: File): Boolean {
            return when {
                !file.exists() -> false
                file.isDirectory -> false
                file.name.endsWith(".data") -> true
                file.length() > 1e8 -> false
                isGitignore(file.toPath()) -> false
                isLLMIgnored(file.toPath()) -> false
                file.extension.lowercase(Locale.getDefault()) in setOf(
                    "jar",
                    "zip",
                    "class",
                    "png",
                    "jpg",
                    "jpeg",
                    "gif",
                    "ico",
                    "stl"
                ) -> false

                isBinaryFile(file) -> false

                else -> true
            }
        }

        /**
         * Checks if a file is likely to be binary by examining its content
         * @param file The file to check
         * @return true if the file appears to be binary, false otherwise
         */
        fun isBinaryFile(file: File): Boolean {
            if (!file.exists() || file.isDirectory || file.length() == 0L) {
                return false
            }

            val binaryExtensions = setOf(
                "jar", "zip", "class", "png", "jpg", "jpeg", "gif", "ico", "stl",
                "exe", "dll", "so", "dylib", "bin", "dat", "pdf", "doc", "docx", "xls", "xlsx"
            )
            if (file.extension.lowercase(Locale.getDefault()) in binaryExtensions) {
                return true
            }

            return try {
                file.inputStream().use { input ->
                    isBinaryStream(input)
                }
            } catch (e: Exception) {

                false
            }
        }

        /**
         * Checks if an input stream contains binary data by sampling the first bytes
         * @param input The input stream to check
         * @return true if the stream appears to contain binary data, false otherwise
         */
        private fun isBinaryStream(input: InputStream): Boolean {
            val sampleSize = 1000
            val bytes = ByteArray(sampleSize)
            val bytesRead = input.read(bytes, 0, sampleSize)
            if (bytesRead <= 0) return false


            var binaryCount = 0
            for (i in 0 until bytesRead) {
                val b = bytes[i].toInt() and 0xFF
                when {
                    b == 0 -> binaryCount++

                    b == 7 -> {}

                    b < 9 -> binaryCount++

                    b > 13 && b < 32 -> binaryCount++

                    b >= 127 -> binaryCount++

                }
            }

            return binaryCount.toDouble() / bytesRead > 0.1
        }

        fun expandFileList(vararg data: File): Array<File> {
            return data.flatMap {
                (when {
                    it.name.endsWith(".data") -> arrayOf(it)
                    isGitignore(it.toPath()) -> arrayOf()
                    isLLMIgnored(it.toPath()) -> arrayOf()
                    it.length() > 1e8 -> arrayOf()
                    it.extension.lowercase(Locale.getDefault()) in
                            setOf("jar", "zip", "class", "png", "jpg", "jpeg", "gif", "ico") -> arrayOf()

                    isBinaryFile(it) -> arrayOf()
                    it.isDirectory -> expandFileList(*it.listFiles() ?: arrayOf())
                    else -> arrayOf(it)
                }).toList()
            }.toTypedArray()
        }

        fun isLLMIgnored(path: Path): Boolean {

            when {
                path.name == "node_modules" -> return true
                path.name == "target" -> return true
                path.name == "build" -> return true
                path.name == ".git" -> return true
            }
            var currentDir = path.toFile().parentFile
            currentDir ?: return false
            while (!currentDir.resolve(".llm").exists()) {

                currentDir.resolve(".llmignore").let {
                    if (it.exists()) {
                        val llmignore = it.readText()
                        if (llmignore.split("\n").any { line ->
                                try {
                                    val trimmedLine = line.trim()
                                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false

                                    val regexPattern = "^" + Regex.escape(trimmedLine)
                                        .replace("\\*", ".*")
                                        .replace("\\?", ".") + "$"
                                    return@any path.fileName.toString().matches(Regex(regexPattern))
                                } catch (e: Throwable) {
                                    return@any false
                                }
                            }) return true
                    }
                }
                currentDir = currentDir.parentFile ?: break
            }

            currentDir.resolve(".llmignore").let {
                if (it.exists()) {
                    val llmignore = it.readText()
                    if (llmignore.split("\n").any { line ->
                            val trimmedLine = line.trim()
                            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
                            val regexPattern = "^" + Regex.escape(trimmedLine)
                                .replace("\\*", ".*")
                                .replace("\\?", ".") + "$"
                            path.fileName.toString().matches(Regex(regexPattern))
                        }) {
                        return true
                    }
                }
            }
            return false
        }

        fun isGitignore(path: Path): Boolean {
            when {
                path.name == "node_modules" -> return true
                path.name == "target" -> return true
                path.name == "build" -> return true
            }
            var currentDir = path.toFile().parentFile
            currentDir ?: return false
            while (!currentDir.resolve(".git").exists()) {
                currentDir.resolve(".gitignore").let {
                    if (it.exists()) {
                        val gitignore = it.readText()
                        if (gitignore.split("\n").any { line ->
                                try {
                                    val trimmedLine = line.trim()
                                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false

                                    val regexPattern = "^" + Regex.escape(trimmedLine)
                                        .replace("\\*", ".*")
                                        .replace("\\?", ".") + "$"
                                    return@any path.fileName.toString().matches(Regex(regexPattern))
                                } catch (e: Throwable) {
                                    return@any false
                                }
                            }) return true
                    }
                }
                currentDir = currentDir.parentFile ?: break
            }

            currentDir.resolve(".gitignore").let {
                if (it.exists()) {
                    val gitignore = it.readText()
                    if (gitignore.split("\n").any { line ->
                            val trimmedLine = line.trim()
                            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
                            val regexPattern = "^" + Regex.escape(trimmedLine)
                                .replace("\\*", ".*")
                                .replace("\\?", ".") + "$"
                            path.fileName.toString().matches(Regex(regexPattern))
                        }) {
                        return true
                    }
                }
            }
            return false
        }
    }
}