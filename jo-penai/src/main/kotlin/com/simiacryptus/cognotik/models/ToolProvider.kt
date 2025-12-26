package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

private val log: Logger = LoggerFactory.getLogger(ToolProvider::class.java)

@JsonDeserialize(using = ToolProviderDeserializer::class)
@JsonSerialize(using = ToolProviderSerializer::class)
abstract class ToolProvider(name: String) : DynamicEnum<ToolProvider>(name) {

    abstract fun getExecutables(): List<String>
    abstract fun getVersion(path: String): String?

    open fun validate(path: String): Boolean {
        return try {
            val version = getVersion(path)
            log.info("Tool $name found at $path version: $version")
            version != null
        } catch (e: Exception) {
            log.warn("Tool $name validation failed at $path", e)
            false
        }
    }

    open fun resolve(root: String? = null): String? {
        val executables = getExecutables()
        if (root != null) {
            val rootFile = File(root)
            if (rootFile.exists()) {
                for (exe in executables) {
                    val candidates = listOf(
                        File(rootFile, exe),
                        File(rootFile, "$exe.exe"),
                        File(File(rootFile, "bin"), exe),
                        File(File(rootFile, "bin"), "$exe.exe")
                    )
                    candidates.firstOrNull { canExecute(it) }?.let { return it.absolutePath }
                }
            }
        }

        return null;
    }

    private fun canExecute(file: File) = file.exists() && file.canExecute()

    companion object {
        private fun runCommand(command: List<String>): String? {
            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroy()
                    return null
                }
                process.inputStream.bufferedReader().readText().trim()
            } catch (e: Exception) {
                log.debug("Failed to run command: $command", e)
                null
            }
        }
        val Git = object : ToolProvider("Git") {
            override fun getExecutables() = listOf("git")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Latex = object : ToolProvider("Latex") {
            override fun getExecutables() = listOf("pdflatex", "latex")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Python = object : ToolProvider("Python") {
            override fun getExecutables() = listOf("python", "python3")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val SSH = object : ToolProvider("SSH") {
            override fun getExecutables() = listOf("ssh")
            override fun getVersion(path: String) = runCommand(listOf(path, "-V"))
        }
        val Rust = object : ToolProvider("Rust") {
            override fun getExecutables() = listOf("cargo", "rustc")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Node = object : ToolProvider("Node") {
            override fun getExecutables() = listOf("node", "npm")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Jdk = object : ToolProvider("Jdk") {
            override fun getExecutables() = listOf("java", "javac")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }

        init {
            register(ToolProvider::class.java, Git)
            register(ToolProvider::class.java, Latex)
            register(ToolProvider::class.java, Python)
            register(ToolProvider::class.java, SSH)
            register(ToolProvider::class.java, Rust)
            register(ToolProvider::class.java, Node)
            register(ToolProvider::class.java, Jdk)
        }

        @JvmStatic
        fun valueOf(name: String): ToolProvider = valueOf(ToolProvider::class.java, name)

        @JvmStatic
        fun values(): Collection<ToolProvider> = values(ToolProvider::class.java)

        @JvmStatic
        fun discoverFromPath(provider: ToolProvider): Collection<String> {
            val pathEnv = System.getenv("PATH") ?: ""
            val pathSeparator = System.getProperty("path.separator")
            val paths = pathEnv.split(pathSeparator)
            val found = mutableSetOf<String>()
            for (p in paths) {
                val dir = File(p)
                if (!dir.exists()) continue
                if (!dir.isDirectory) continue
                val resolve = provider.resolve(dir.absolutePath)
                if (resolve != null) found.add(resolve)
            }
            return found
        }


        @JvmStatic
        fun discoverAllToolsFromPath() : List<ToolData> {
            val result = mutableListOf<ToolData>()
            for (provider in values()) {
                val paths = discoverFromPath(provider)
                for (path in paths) {
                    result.add(ToolData(provider, path))
                }
            }
            return result
        }
        @JvmStatic
        fun scanRecursive(root: File, depth: Int = 3): List<ToolData> {
            val results = mutableListOf<ToolData>()
            if (!root.exists() || !root.isDirectory) return results
            for (provider in values()) {
                val path = provider.resolve(root.absolutePath)
                if (path != null) {
                    results.add(ToolData(provider, path))
                }
            }
            if (depth > 0) {
                val files = root.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            results.addAll(scanRecursive(file, depth - 1))
                        }
                    }
                }
            }
            return results
        }
        @JvmStatic
        fun discoverCommon(): List<ToolData> {
            val roots = mutableListOf<String>()
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                roots.add("C:\\Program Files")
                roots.add("C:\\Program Files (x86)")
                roots.add(System.getProperty("user.home") + "\\AppData\\Local\\Programs")
            } else {
                roots.add("/usr/bin")
                roots.add("/usr/local/bin")
                roots.add("/opt")
                roots.add("/opt/homebrew/bin")
            }
            return roots.map { File(it) }.flatMap { scanRecursive(it, 3) }.distinct()
        }
    }
}

/**
 * Represents configuration data for a tool/command that can be executed.
 *
 * @property name The display name of the tool
 * @property description A human-readable description of what the tool does
 * @property command The actual command or script to execute when the tool is invoked
 */
data class ToolData(
    val provider: ToolProvider? = null,
    val path: String? = null,
) {
    fun absoluteExecutablePaths(): List<String> {
        val result = mutableListOf<String>()
        if (path != null) {
            result.add(path)
        }
        provider?.let {
            val resolvedPath = it.resolve()
            if (resolvedPath != null) {
                result.add(resolvedPath)
            }
        }
        return result
    }
}

class ToolProviderSerializer : DynamicEnumSerializer<ToolProvider>(ToolProvider::class.java)
class ToolProviderDeserializer : DynamicEnumDeserializer<ToolProvider>(ToolProvider::class.java)