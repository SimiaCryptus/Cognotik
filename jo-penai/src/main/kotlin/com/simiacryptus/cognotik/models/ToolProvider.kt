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
open class ToolProvider(name: String) : DynamicEnum<ToolProvider>(name) {

    open fun getExecutables(): List<String> = emptyList()
    open fun getVersion(path: String): String? = null

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

    open fun resolve(root: String? = null, tool: String? = null): List<String> {
        val foundPaths = mutableListOf<String>()
        val executables = getExecutables()
        if (root != null) {
            var rootFile = File(root)
            if (rootFile.exists()) {
                if (!rootFile.isDirectory) {
                    rootFile = rootFile.parentFile
                }
                for (exe in executables) {
                    if (tool != null && !exe.equals(tool, ignoreCase = true)) continue
                    val candidates = listOf(
                        File(rootFile, exe),
                        File(rootFile, "$exe.exe"),
                        File(File(rootFile, "bin"), exe),
                        File(File(rootFile, "bin"), "$exe.exe")
                    )
                    candidates.firstOrNull { canExecute(it) }?.let { foundPaths += it.absolutePath }
                }
            }
        }
        return foundPaths;
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
        val Docker = object : ToolProvider("Docker") {
            override fun getExecutables() = listOf("docker")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Go = object : ToolProvider("Go") {
            override fun getExecutables() = listOf("go")
            override fun getVersion(path: String) = runCommand(listOf(path, "version"))
        }
        val Gradle = object : ToolProvider("Gradle") {
            override fun getExecutables() = listOf("gradle", "gradle.bat")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Maven = object : ToolProvider("Maven") {
            override fun getExecutables() = listOf("mvn", "mvn.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Ant = object : ToolProvider("Ant") {
            override fun getExecutables() = listOf("ant", "ant.bat", "ant.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "-version"))
        }
        val Bash = object : ToolProvider("Bash") {
            override fun getExecutables() = listOf("bash")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Zsh = object : ToolProvider("Zsh") {
            override fun getExecutables() = listOf("zsh")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Powershell = object : ToolProvider("Powershell") {
            override fun getExecutables() = listOf("pwsh", "powershell")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Ruby = object : ToolProvider("Ruby") {
            override fun getExecutables() = listOf("ruby")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Gcc = object : ToolProvider("Gcc") {
            override fun getExecutables() = listOf("gcc", "g++")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Make = object : ToolProvider("Make") {
            override fun getExecutables() = listOf("make")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Cmake = object : ToolProvider("Cmake") {
            override fun getExecutables() = listOf("cmake")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Terraform = object : ToolProvider("Terraform") {
            override fun getExecutables() = listOf("terraform")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Kubectl = object : ToolProvider("Kubectl") {
            override fun getExecutables() = listOf("kubectl")
            override fun getVersion(path: String) = runCommand(listOf(path, "version", "--client"))
        }
        val Gcloud = object : ToolProvider("Gcloud") {
            override fun getExecutables() = listOf("gcloud", "gcloud.cmd")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val Aws = object : ToolProvider("Aws") {
            override fun getExecutables() = listOf("aws")
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }
        val LanguageServer = object : ToolProvider("LanguageServer") {
            override fun getExecutables() = listOf(
                "pylsp",
                "typescript-language-server",
                "kotlin-language-server",
                "jdtls",
                "clangd",
                "gopls",
                "rust-analyzer",
                "bash-language-server",
                "docker-langserver",
                "texlab",
                "yaml-language-server"
            )
            override fun getVersion(path: String) = runCommand(listOf(path, "--version"))
        }

        init {
            register(ToolProvider::class.java, Git)
            register(ToolProvider::class.java, Latex)
            register(ToolProvider::class.java, Python)
            register(ToolProvider::class.java, SSH)
            register(ToolProvider::class.java, Rust)
            register(ToolProvider::class.java, Node)
            register(ToolProvider::class.java, Jdk)
            register(ToolProvider::class.java, Docker)
            register(ToolProvider::class.java, Go)
            register(ToolProvider::class.java, Gradle)
            register(ToolProvider::class.java, Maven)
            register(ToolProvider::class.java, Ant)
            register(ToolProvider::class.java, Bash)
            register(ToolProvider::class.java, Zsh)
            register(ToolProvider::class.java, Powershell)
            register(ToolProvider::class.java, Ruby)
            register(ToolProvider::class.java, Gcc)
            register(ToolProvider::class.java, Make)
            register(ToolProvider::class.java, Cmake)
            register(ToolProvider::class.java, Terraform)
            register(ToolProvider::class.java, Kubectl)
            register(ToolProvider::class.java, Gcloud)
            register(ToolProvider::class.java, Aws)
            register(ToolProvider::class.java, LanguageServer)
        }

        @JvmStatic
        fun valueOf(name: String): ToolProvider = valueOf(ToolProvider::class.java, name)

        @JvmStatic
        fun values(): Collection<ToolProvider> = values(ToolProvider::class.java)

        @JvmStatic
        private fun discoverFromPath(provider: ToolProvider): Collection<String> {
            val pathEnv = System.getenv("PATH") ?: ""
            val pathSeparator = System.getProperty("path.separator")
            val paths = pathEnv.split(pathSeparator)
            val found = mutableSetOf<String>()
            for (p in paths) {
                val dir = File(p)
                if (!dir.exists()) continue
                if (!dir.isDirectory) continue
                found += provider.resolve(dir.absolutePath)
            }
            return found
        }


        @JvmStatic
        fun discoverAllToolsFromPath(): List<ToolData> {
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
                provider.resolve(root.absolutePath).forEach { path ->
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
 * Get via e.g.
 * ```
 *   val executables : List<String>? = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools.flatMap { it.absoluteExecutablePaths() }.distinct().sorted()
 * ```
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
        provider?.let {
            result += it.resolve(path)
        }
        return result
    }

    fun resolve(tool: String?): String? {
        return provider?.resolve(path, tool)?.firstOrNull();
    }
}

class ToolProviderSerializer : DynamicEnumSerializer<ToolProvider>(ToolProvider::class.java)
class ToolProviderDeserializer : DynamicEnumDeserializer<ToolProvider>(ToolProvider::class.java)