package com.simiacryptus.cognotik.webui.servlet.util

import java.io.File
import java.nio.file.Path

object PathUtils {

    /**
     * Verifies whether the target file or path string is safely contained within the specified root directory.
     * Prevents path traversal via '..' relative sequences and symbolic links resolving outside the root directory.
     */
    fun isSafePath(root: File, targetPath: String): Boolean {
        if (targetPath.contains("..")) {
            return false
        }
        val targetFile = File(root, targetPath)
        return isSafePath(root, targetFile)
    }

    /**
     * Verifies whether the target file is safely contained within the specified root directory using canonical path resolution.
     */
    fun isSafePath(root: File, targetFile: File): Boolean {
        return try {
            val canonicalRoot = root.canonicalFile.toPath().normalize()
            val canonicalTarget = targetFile.canonicalFile.toPath().normalize()
            canonicalTarget.startsWith(canonicalRoot)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves a relative path string against a root directory safely, throwing SecurityException or IllegalArgumentException
     * if path traversal or symbolic link escape is detected.
     */
    fun resolveSafePath(root: File, relativePath: String): File {
        if (relativePath.contains("..")) {
            throw IllegalArgumentException("Path traversal attempt detected in path: $relativePath")
        }
        val resolvedFile = File(root, relativePath)
        if (!isSafePath(root, resolvedFile)) {
            throw SecurityException("Access denied: path points outside root directory: $relativePath")
        }
        return resolvedFile
    }

    /**
     * Validates and normalizes target Path against root Path using canonical path verification.
     */
    fun validateAndNormalize(root: Path, target: Path): Path {
        val rootCanonical = root.toFile().canonicalFile.toPath().normalize()
        val targetCanonical = target.toFile().canonicalFile.toPath().normalize()
        if (!targetCanonical.startsWith(rootCanonical)) {
            throw SecurityException("Path traversal detected: $target outside of root $root")
        }
        return targetCanonical
    }

    fun isValidFileName(fileName: String): Boolean {
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return fileName.isNotEmpty() && invalidChars.none { it in fileName }
    }
}