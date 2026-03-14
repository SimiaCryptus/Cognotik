package com.simiacryptus.cognotik.webui.servlet
import com.simiacryptus.cognotik.util.LoggerFactory

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils.parsePath
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
open class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {
    companion object {
        private val log = LoggerFactory.getLogger(SessionFileServlet::class.java)
    }

    override fun getDir(req: HttpServletRequest): File? {
        val pathInfo = req.pathInfo ?: req.servletPath
        val pathSegments = parsePath(pathInfo ?: "/")
        val session = Session(parsePath(pathInfo ?: "/").first())
        onSession(session)
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionDir = dataStorage.getSessionDir(user, session)
        val dataDir = dataStorage.getDataDir(user, session)
        val dirs = if (sessionDir.absolutePath != dataDir.absolutePath) {
            listOf(sessionDir, dataDir)
        } else {
            listOf(sessionDir)
        }

        // First, try to find the exact file
        val path = pathSegments.drop(1).joinToString("/")
        val exactMatch = dirs.firstOrNull { File(it, path).exists() }
        if (exactMatch != null) return exactMatch

        // If not found, check if this is a request for HTML/PDF with an equivalent .md file
        val requestedFile = File(dirs.first(), path)
        val fileName = requestedFile.name
        if (fileName.endsWith(".html") || fileName.endsWith(".pdf") || fileName.endsWith(".txt")) {
            val mdFileName = fileName.substringBeforeLast(".") + ".md"
            val mdMatch = dirs.firstOrNull {
                val mdFile = File(File(it, path).parentFile, mdFileName)
                mdFile.exists() && mdFile.isFile
            }
            if (mdMatch != null) return mdMatch
        }
        return dirs.firstOrNull()
    }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val pathInfo = req.pathInfo ?: req.servletPath ?: "/"
        // Handle git API endpoints
        if (pathInfo.contains("/.git/api/")) {
            handleGitApiGet(req, resp, pathInfo)
            return
        }
        super.doGet(req, resp)
    }
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val pathInfo = req.pathInfo ?: req.servletPath ?: "/"
        // Handle git API endpoints
        if (pathInfo.contains("/.git/api/")) {
            handleGitApiPost(req, resp, pathInfo)
            return
        }
        super.doPost(req, resp)
    }
    private fun handleGitApiGet(req: HttpServletRequest, resp: HttpServletResponse, pathInfo: String) {
        try {
            val pathSegments = parsePath(pathInfo)
            val session = Session(pathSegments.first())
            onSession(session)
            val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
            val sessionDir = dataStorage.getSessionDir(user, session)
            // Extract the git API action from the path
            val gitApiIndex = pathSegments.indexOf(".git")
            if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.size) {
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.contentType = "application/json"
                resp.writer.write("""{"error": "Invalid git API path"}""")
                return
            }
            val action = pathSegments[gitApiIndex + 2] // .git/api/<action>
            when (action) {
                "status" -> gitStatus(sessionDir, resp)
                "branches" -> gitListBranches(sessionDir, resp)
                "log" -> gitLog(sessionDir, req, resp)
                else -> {
                    resp.status = HttpServletResponse.SC_BAD_REQUEST
                    resp.contentType = "application/json"
                    resp.writer.write("""{"error": "Unknown git GET action: $action"}""")
                }
            }
        } catch (e: Exception) {
            log.error("Error handling git API GET request", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"error": "Git operation failed: ${e.message}"}""")
        }
    }
    private fun handleGitApiPost(req: HttpServletRequest, resp: HttpServletResponse, pathInfo: String) {
        try {
            val pathSegments = parsePath(pathInfo)
            val session = Session(pathSegments.first())
            onSession(session)
            val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
            val sessionDir = dataStorage.getSessionDir(user, session)
            val gitApiIndex = pathSegments.indexOf(".git")
            if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.size) {
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.contentType = "application/json"
                resp.writer.write("""{"error": "Invalid git API path"}""")
                return
            }
            val action = pathSegments[gitApiIndex + 2]
            when (action) {
                "init" -> gitInit(sessionDir, resp)
                "commit" -> {
                    val body = req.reader.readText()
                    val message = parseJsonField(body, "message") ?: "Auto-commit"
                    gitCommit(sessionDir, message, resp)
                }
                "checkout" -> {
                    val body = req.reader.readText()
                    val branch = parseJsonField(body, "branch")
                    val create = parseJsonField(body, "create")?.toBoolean() ?: false
                    if (branch.isNullOrBlank()) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.contentType = "application/json"
                        resp.writer.write("""{"error": "Branch name is required"}""")
                        return
                    }
                    gitCheckout(sessionDir, branch, create, resp)
                }
                else -> {
                    resp.status = HttpServletResponse.SC_BAD_REQUEST
                    resp.contentType = "application/json"
                    resp.writer.write("""{"error": "Unknown git POST action: $action"}""")
                }
            }
        } catch (e: Exception) {
            log.error("Error handling git API POST request", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"error": "Git operation failed: ${e.message}"}""")
        }
    }
    /**
     * Initialize a git repository in the session directory.
     */
    private fun gitInit(sessionDir: File, resp: HttpServletResponse) {
        log.info("Initializing git repository in: ${sessionDir.absolutePath}")
        val gitDir = File(sessionDir, ".git")
        if (gitDir.exists()) {
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "Git repository already initialized", "path": "${escapeJson(sessionDir.absolutePath)}"}""")
            return
        }
        val result = executeGitCommand(sessionDir, "git", "init")
        if (result.exitCode == 0) {
            // Perform an initial commit so the repo has a valid HEAD
            executeGitCommand(sessionDir, "git", "add", "-A")
            executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "Git repository initialized", "output": "${escapeJson(result.output)}", "path": "${escapeJson(sessionDir.absolutePath)}"}""")
        } else {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}", "output": "${escapeJson(result.output)}"}""")
        }
    }
    /**
     * List all branches in the session's git repository.
     */
    private fun gitListBranches(sessionDir: File, resp: HttpServletResponse) {
        log.info("Listing git branches in: ${sessionDir.absolutePath}")
        ensureGitRepo(sessionDir)
        val result = executeGitCommand(sessionDir, "git", "branch", "-a", "--no-color")
        if (result.exitCode == 0) {
            val branches = result.output.lines()
                .filter { it.isNotBlank() }
                .map { line ->
                    val isCurrent = line.trimStart().startsWith("*")
                    val name = line.trimStart().removePrefix("* ").removePrefix("  ").trim()
                    """{"name": "${escapeJson(name)}", "current": $isCurrent}"""
                }
            // Also get the current branch name
            val currentBranchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
            val currentBranch = currentBranchResult.output.trim()
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "currentBranch": "${escapeJson(currentBranch)}", "branches": [${branches.joinToString(", ")}]}""")
        } else {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
        }
    }
    /**
     * Checkout a branch in the session's git repository.
     */
    private fun gitCheckout(sessionDir: File, branch: String, create: Boolean, resp: HttpServletResponse) {
        log.info("Checking out branch '$branch' (create=$create) in: ${sessionDir.absolutePath}")
        ensureGitRepo(sessionDir)
        // Validate branch name
        if (!isValidBranchName(branch)) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "Invalid branch name: ${escapeJson(branch)}"}""")
            return
        }
        val args = mutableListOf("git", "checkout")
        if (create) {
            args.add("-b")
        }
        args.add(branch)
        val result = executeGitCommand(sessionDir, *args.toTypedArray())
        if (result.exitCode == 0) {
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "Checked out branch '${escapeJson(branch)}'", "output": "${escapeJson(result.output)}"}""")
        } else {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}", "output": "${escapeJson(result.output)}"}""")
        }
    }
    /**
     * Commit all changes in the session's git repository.
     */
    private fun gitCommit(sessionDir: File, message: String, resp: HttpServletResponse) {
        log.info("Committing changes in: ${sessionDir.absolutePath} with message: $message")
        ensureGitRepo(sessionDir)
        // Stage all changes
        val addResult = executeGitCommand(sessionDir, "git", "add", "-A")
        if (addResult.exitCode != 0) {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "Failed to stage changes: ${escapeJson(addResult.error)}"}""")
            return
        }
        // Check if there are changes to commit
        val statusResult = executeGitCommand(sessionDir, "git", "status", "--porcelain")
        if (statusResult.exitCode == 0 && statusResult.output.isBlank()) {
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "Nothing to commit, working tree clean"}""")
            return
        }
        val commitResult = executeGitCommand(
            sessionDir, "git", "commit", "-m", message,
            "--author=SessionFileServlet <noreply@localhost>"
        )
        if (commitResult.exitCode == 0) {
            // Get the commit hash
            val hashResult = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD")
            val commitHash = hashResult.output.trim()
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "Changes committed", "commitHash": "${escapeJson(commitHash)}", "output": "${escapeJson(commitResult.output)}"}""")
        } else {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "${escapeJson(commitResult.error)}", "output": "${escapeJson(commitResult.output)}"}""")
        }
    }
    /**
     * Get the git status of the session directory.
     */
    private fun gitStatus(sessionDir: File, resp: HttpServletResponse) {
        log.info("Getting git status for: ${sessionDir.absolutePath}")
        val gitDir = File(sessionDir, ".git")
        if (!gitDir.exists()) {
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "initialized": false, "message": "Not a git repository"}""")
            return
        }
        val result = executeGitCommand(sessionDir, "git", "status", "--porcelain")
        val branchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
        val currentBranch = branchResult.output.trim()
        val changes = result.output.lines().filter { it.isNotBlank() }.map { line ->
            val status = line.substring(0, 2).trim()
            val file = line.substring(3).trim()
            """{"status": "${escapeJson(status)}", "file": "${escapeJson(file)}"}"""
        }
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write("""{"success": true, "initialized": true, "currentBranch": "${escapeJson(currentBranch)}", "clean": ${changes.isEmpty()}, "changes": [${changes.joinToString(", ")}]}""")
    }
    /**
     * Get the git log for the session directory.
     */
    private fun gitLog(sessionDir: File, req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Getting git log for: ${sessionDir.absolutePath}")
        ensureGitRepo(sessionDir)
        val maxCount = req.getParameter("maxCount")?.toIntOrNull() ?: 20
        val result = executeGitCommand(
            sessionDir, "git", "log",
            "--format=%H%n%an%n%ae%n%aI%n%s",
            "-n", maxCount.coerceIn(1, 100).toString()
        )
        if (result.exitCode == 0) {
            val lines = result.output.lines().filter { it.isNotBlank() }
            val commits = mutableListOf<String>()
            var i = 0
            while (i + 4 < lines.size) {
                val hash = lines[i]
                val authorName = lines[i + 1]
                val authorEmail = lines[i + 2]
                val date = lines[i + 3]
                val subject = lines[i + 4]
                commits.add("""{"hash": "${escapeJson(hash)}", "author": "${escapeJson(authorName)}", "email": "${escapeJson(authorEmail)}", "date": "${escapeJson(date)}", "message": "${escapeJson(subject)}"}""")
                i += 5
            }
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "commits": [${commits.joinToString(", ")}]}""")
        } else {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
        }
    }
    /**
     * Ensure the session directory has a git repository initialized.
     * If not, initialize one.
     */
    private fun ensureGitRepo(sessionDir: File) {
        val gitDir = File(sessionDir, ".git")
        if (!gitDir.exists()) {
            log.info("Auto-initializing git repository in: ${sessionDir.absolutePath}")
            executeGitCommand(sessionDir, "git", "init")
            executeGitCommand(sessionDir, "git", "add", "-A")
            executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
        }
    }
    private fun isValidBranchName(name: String): Boolean {
        // Basic validation for git branch names
        return name.isNotBlank() &&
                !name.contains("..") &&
                !name.contains("~") &&
                !name.contains("^") &&
                !name.contains(":") &&
                !name.contains("\\") &&
                !name.contains(" ") &&
                !name.startsWith("-") &&
                !name.endsWith(".lock") &&
                !name.endsWith(".") &&
                !name.contains("@{") &&
                name.all { it.code >= 33 && it.code <= 126 }
    }
    private data class GitResult(val exitCode: Int, val output: String, val error: String)
    private fun executeGitCommand(workingDir: File, vararg command: String): GitResult {
        log.info("Executing git command: ${command.joinToString(" ")} in ${workingDir.absolutePath}")
        return try {
            val processBuilder = ProcessBuilder(*command)
                .directory(workingDir)
                .redirectErrorStream(false)
            // Set minimal git config for commits if not configured
            processBuilder.environment()["GIT_AUTHOR_NAME"] = "SessionFileServlet"
            processBuilder.environment()["GIT_AUTHOR_EMAIL"] = "noreply@localhost"
            processBuilder.environment()["GIT_COMMITTER_NAME"] = "SessionFileServlet"
            processBuilder.environment()["GIT_COMMITTER_EMAIL"] = "noreply@localhost"
            val process = processBuilder.start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                log.warn("Git command exited with code $exitCode: ${command.joinToString(" ")} - stderr: $error")
            }
            GitResult(exitCode, output, error)
        } catch (e: Exception) {
            log.error("Failed to execute git command: ${command.joinToString(" ")}", e)
            GitResult(-1, "", e.message ?: "Unknown error")
        }
    }
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    /**
     * Simple JSON field parser for request bodies.
     * Extracts the value of a given field from a JSON string.
     */
    private fun parseJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\t", "\t")
    }


    override fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
        file?.let { return super.listContents(it, req) }
        val pathInfo = req.pathInfo ?: req.servletPath
        val session = Session(parsePath(pathInfo ?: "/").first())
        onSession(session)
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionPair = listContents(dataStorage.getSessionDir(user, session), req)
        val dataPair = listContents(dataStorage.getDataDir(user, session), req)
        return Pair(sessionPair.first + dataPair.first, sessionPair.second + dataPair.second)
    }

    open fun onSession(session: Session) {

    }
}