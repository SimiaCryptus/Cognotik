package com.simiacryptus.cognotik.fileserver

import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Path

abstract class GitProvider(
  val dataStorage: StorageInterface
) {
  companion object {
    val log = LoggerFactory.getLogger(GitProvider::class.java)
     private val VALID_RESET_MODES = setOf("soft", "mixed", "hard", "merge", "keep")
     private val VALID_SUBMODULE_ACTIONS = setOf("add", "init", "update", "sync", "deinit", "status")
     private val SEQUENCER_ACTIONS = setOf("continue", "abort", "quit", "skip")
     private val STASH_ACTIONS = setOf("push", "save", "pop", "apply", "list", "drop", "clear", "show")
     /** `submodule.<name>.<prop>` keys we surface from `.gitmodules`. */
     private val SUBMODULE_CONFIG_REGEX = """^submodule\.(.+)\.(path|url|branch|update|ignore)$""".toRegex()
     /** ` <sha1> <path> (<describe>)` as emitted by `git submodule status`. */
     private val SUBMODULE_STATUS_REGEX =
       """^([-+U ])?([0-9a-fA-F]{4,40})\s+(\S+)(?:\s+\((.*)\))?$""".toRegex()
     /** https/http/git/ssh/file URLs, scp-style `user@host:path` and relative `../` URLs. */
     private val SUBMODULE_URL_REGEX =
       """^(?:(?:https?|git|ssh|file)://[^\s]+|[A-Za-z0-9._~+-]+@[A-Za-z0-9._-]+:[^\s]+|\.{1,2}/[^\s]+)$""".toRegex()
     private val STASH_REF_REGEX = """^(?:stash@\{\d+\}|\d+)$""".toRegex()
  }


  fun handleGitApiGet(request: HttpServletRequest, response: HttpServletResponse, pathInfo: String) {
    log.info("handleGitApiGet: pathInfo=$pathInfo")
    try {
      val pathSegments = Path.of(pathInfo).normalize()
      if (pathSegments.toList().isEmpty()) {
        log.warn("Empty path segments for git API GET: $pathInfo")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.writer.write("""{"error": "Invalid path"}""")
        return
      }
      val session = Session(pathSegments.toList().first().toString())
      log.debug("Git API GET session: ${session.sessionId}")
      val user = authenticate(request, response) ?: run {
        log.warn("Authentication failed for git API GET on session ${session.sessionId}")
        throw IllegalStateException("Authentication failed")
      }
      log.debug("Git API GET authenticated user: ${user.email}")
      onSession(session, user)
      val sessionDir = dataStorage.getUserDir(user, session)
      // Extract the git API action from the path
      val gitApiIndex = pathSegments.toList().map { it.toString() }.indexOf(".git")
      if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.toList().size) {
        log.warn("Invalid git API path structure: $pathInfo (gitApiIndex=$gitApiIndex, segments=${pathSegments.toList().size})")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.writer.write("""{"error": "Invalid git API path"}""")
        return
      }
      val action = pathSegments.toList().map { it.toString() }[gitApiIndex + 2] // .git/api/<action>
      log.info("Git API GET action: $action for session ${session.sessionId}, user ${user.email}")
      when (action) {
        "status" -> gitStatus(sessionDir, response)
        "branches", "branch" -> gitListBranches(sessionDir, response)
        "log" -> gitLog(sessionDir, request, response)
        "submodules", "submodule" -> gitListSubmodules(sessionDir, response)
        "diff" -> gitDiff(sessionDir, request, response)
        "show" -> gitShow(sessionDir, request, response)
        "remotes", "remote" -> gitRemotes(sessionDir, response)
        "tags", "tag" -> gitListTags(sessionDir, response)
        "stashes", "stash" -> gitListStashes(sessionDir, response)
        else -> {
          log.warn("Unknown git GET action: $action")
          response.status = HttpServletResponse.SC_BAD_REQUEST
          response.contentType = "application/json"
          response.writer.write("""{"error": "Unknown git GET action: $action"}""")
        }
      }
    } catch (e: IllegalStateException) {
      log.warn("Git API GET authentication/state error: ${e.message}")
      if (!response.isCommitted) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error": "${escapeJson(e.message ?: "Authentication failed")}"}""")
      }
    } catch (e: Exception) {
      log.error("Error handling git API GET request", e)
      if (!response.isCommitted) {
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.contentType = "application/json"
        response.writer.write("""{"error": "Git operation failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  abstract fun authenticate(
    request: HttpServletRequest,
    response: HttpServletResponse
  ): User?


  /**
   * Initialize a git repository in the session directory.
   */
  private fun gitInit(sessionDir: File, resp: HttpServletResponse) {
    log.info("Initializing git repository in: ${sessionDir.absolutePath}")
    try {
      if (!sessionDir.exists()) {
        log.warn("Session directory does not exist, creating: ${sessionDir.absolutePath}")
        if (!sessionDir.mkdirs()) {
          log.error("Failed to create session directory: ${sessionDir.absolutePath}")
          resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          resp.contentType = "application/json"
          resp.writer.write("""{"success": false, "error": "Failed to create session directory"}""")
          return
        }
      }
      val gitDir = File(sessionDir, ".git")
      if (gitDir.exists()) {
        log.info("Git repository already exists in: ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "message": "Git repository already initialized", "path": "${
            escapeJson(sessionDir.absolutePath)
          }"}"""
        )
        return
      }
      val result = executeGitCommand(sessionDir, "git", "init")
      if (result.exitCode == 0) {
        log.info("Git init succeeded, creating initial commit")
        // Perform an initial commit so the repo has a valid HEAD
        executeGitCommand(sessionDir, "git", "add", "-A")
        executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "message": "Git repository initialized", "output": "${escapeJson(result.output)}", "path": "${
            escapeJson(sessionDir.absolutePath)
          }"}"""
        )
      } else {
        log.error("Git init failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
            escapeJson(
              result.output
            )
          }"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitInit for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  /**
   * List all branches in the session's git repository.
   */
  private fun gitListBranches(sessionDir: File, resp: HttpServletResponse) {
    log.info("Listing git branches in: ${sessionDir.absolutePath}")
    try {
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
        log.debug("Found ${branches.size} branches in ${sessionDir.absolutePath}")
        // Also get the current branch name
        val currentBranchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
        val currentBranch = currentBranchResult.output.trim()
        log.debug("Current branch: $currentBranch")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "currentBranch": "${escapeJson(currentBranch)}", "branches": [${
            branches.joinToString(", ")
          }]}"""
        )
      } else {
        log.error("git branch failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
      }
    } catch (e: Exception) {
      log.error("Exception during gitListBranches for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  private data class GitResult(val exitCode: Int, val output: String, val error: String)

  private fun executeGitCommand(workingDir: File, vararg command: String): GitResult {
    log.info("Executing git command: ${command.joinToString(" ")} in ${workingDir.absolutePath}")
    val startTime = System.currentTimeMillis()
    return try {
      if (!workingDir.exists()) {
        log.error("Working directory does not exist: ${workingDir.absolutePath}")
        return GitResult(-1, "", "Working directory does not exist: ${workingDir.absolutePath}")
      }
      if (!workingDir.isDirectory) {
        log.error("Working directory is not a directory: ${workingDir.absolutePath}")
        return GitResult(-1, "", "Working directory is not a directory: ${workingDir.absolutePath}")
      }
      val processBuilder = ProcessBuilder(*command)
        .directory(workingDir)
        .redirectErrorStream(false)
      // Set minimal git config for commits if not configured
      processBuilder.environment()["GIT_AUTHOR_NAME"] = "SessionFileServlet"
      processBuilder.environment()["GIT_AUTHOR_EMAIL"] = "noreply@localhost"
      processBuilder.environment()["GIT_COMMITTER_NAME"] = "SessionFileServlet"
      processBuilder.environment()["GIT_COMMITTER_EMAIL"] = "noreply@localhost"
      /* Never block on an interactive editor / pager / credential prompt (submodule fetches!). */
      processBuilder.environment()["GIT_EDITOR"] = "true"
      processBuilder.environment()["GIT_SEQUENCE_EDITOR"] = "true"
      processBuilder.environment()["GIT_PAGER"] = "cat"
      processBuilder.environment()["GIT_TERMINAL_PROMPT"] = "0"
      processBuilder.environment()["GIT_ASKPASS"] = "echo"
      val process = processBuilder.start()
      val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
      val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
      val exitCode = process.waitFor()
      val elapsed = System.currentTimeMillis() - startTime
      if (exitCode != 0) {
        log.warn("Git command exited with code $exitCode in ${elapsed}ms: ${command.joinToString(" ")} - stderr: $error")
      } else {
        log.debug("Git command completed successfully in ${elapsed}ms: ${command.joinToString(" ")}")
      }
      GitResult(exitCode, output, error)
    } catch (e: InterruptedException) {
      log.error("Git command interrupted: ${command.joinToString(" ")}", e)
      Thread.currentThread().interrupt()
      GitResult(-1, "", "Command interrupted: ${e.message}")
    } catch (e: IOException) {
      log.error("IO error executing git command (is git installed?): ${command.joinToString(" ")}", e)
      GitResult(-1, "", "IO error: ${e.message}")
    } catch (e: Exception) {
      log.error("Failed to execute git command: ${command.joinToString(" ")}", e)
      GitResult(-1, "", e.message ?: "Unknown error")
    }
  }


  fun handleGitApiPost(request: HttpServletRequest, response: HttpServletResponse, pathInfo: String) {
    log.info("handleGitApiPost: pathInfo=$pathInfo")
    try {
      val pathSegments = Path.of(pathInfo).normalize()
      if (pathSegments.toList().isEmpty()) {
        log.warn("Empty path segments for git API POST: $pathInfo")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.writer.write("""{"error": "Invalid path"}""")
        return
      }
      val session = Session(pathSegments.toList().first().toString())
      log.debug("Git API POST session: ${session.sessionId}")
      val user = authenticate(request, response) ?: run {
        log.warn("Authentication failed for git API POST on session ${session.sessionId}")
        throw IllegalStateException("Authentication failed")
      }
      log.debug("Git API POST authenticated user: ${user.email}")
      onSession(session, user)
      val sessionDir = dataStorage.getUserDir(user, session)
      val gitApiIndex = pathSegments.toList().map { it.toString() }.indexOf(".git")
      if (gitApiIndex == -1 || gitApiIndex + 2 >= pathSegments.toList().size) {
        log.warn("Invalid git API path structure: $pathInfo (gitApiIndex=$gitApiIndex, segments=${pathSegments.toList().size})")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.writer.write("""{"error": "Invalid git API path"}""")
        return
      }
      val action = pathSegments.toList().map { it.toString() }[gitApiIndex + 2]
      log.info("Git API POST action: $action for session ${session.sessionId}, user ${user.email}")
      when (action) {
        "init" -> gitInit(sessionDir, response)
        "commit" -> {
          val body = try {
            request.reader.readText()
          } catch (e: Exception) {
            log.error("Failed to read request body for commit action", e)
            ""
          }
          log.debug("Commit request body length: ${body.length}")
          val message = parseJsonField(body, "message") ?: "Auto-commit"
          gitCommit(sessionDir, message, response)
        }

        "checkout" -> {
          val body = try {
            request.reader.readText()
          } catch (e: Exception) {
            log.error("Failed to read request body for checkout action", e)
            ""
          }
          log.debug("Checkout request body length: ${body.length}")
          val branch = parseJsonField(body, "branch")
          val startPoint = parseJsonField(body, "startPoint")
          val create = parseJsonBoolean(body, "create", false)
          if (branch.isNullOrBlank()) {
            log.warn("Checkout request missing branch name")
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.contentType = "application/json"
            response.writer.write("""{"error": "Branch name is required"}""")
            return
          }
          gitCheckout(sessionDir, branch, create, startPoint, response)
        }

        "branch" -> {
          val body = try {
            request.reader.readText()
          } catch (e: Exception) {
            log.error("Failed to read request body for branch action", e)
            ""
          }
          log.debug("Branch request body length: ${body.length}")
          val branch = parseJsonField(body, "branch") ?: request.getParameter("branch")
          val startPoint = parseJsonField(body, "startPoint")
            ?: parseJsonField(body, "start_point")
            ?: parseJsonField(body, "from")
            ?: request.getParameter("startPoint")
          val delete = parseJsonBoolean(body, "delete", false)
          /* Creating is the default intent for POST .git/api/branch */
          val create = parseJsonBoolean(body, "create", !delete)
          val checkout = parseJsonBoolean(body, "checkout", false)
          if (branch.isNullOrBlank()) {
            log.warn("Branch request missing branch name")
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.contentType = "application/json"
            response.writer.write("""{"success": false, "error": "Branch name is required"}""")
            return
          }
          gitBranch(sessionDir, branch, startPoint, create, checkout, delete, response)
        }
         "reset" -> {
           val body = try {
             request.reader.readText()
           } catch (e: Exception) {
             log.error("Failed to read request body for reset action", e)
             ""
           }
           log.debug("Reset request body length: ${body.length}")
           val mode = parseJsonField(body, "mode")
             ?: request.getParameter("mode")
             ?: "mixed"
           val ref = parseJsonField(body, "ref")
             ?: parseJsonField(body, "commit")
             ?: parseJsonField(body, "revision")
             ?: request.getParameter("ref")
             ?: "HEAD"
           gitReset(sessionDir, mode, ref, response)
         }
         "clean" -> {
           val body = try {
             request.reader.readText()
           } catch (e: Exception) {
             log.error("Failed to read request body for clean action", e)
             ""
           }
           log.debug("Clean request body length: ${body.length}")
           val directories = parseJsonBoolean(body, "directories", false)
           val ignored = parseJsonBoolean(body, "ignored", false)
           val force = parseJsonBoolean(body, "force", false)
           val dryRun = parseJsonBoolean(body, "dryRun", parseJsonBoolean(body, "dry_run", false))
           gitClean(sessionDir, directories, ignored, force, dryRun, response)
         }
         "revert", "cherry-pick", "cherrypick", "cherry_pick" -> {
           val command = if (action == "revert") "revert" else "cherry-pick"
           val body = readBody(request, command)
           val commits = parseJsonStringArray(body, "commits")
             ?: parseJsonStringArray(body, "refs")
             ?: listOfNotNull(
               parseJsonField(body, "commit")
                 ?: parseJsonField(body, "ref")
                 ?: parseJsonField(body, "revision")
                 ?: request.getParameter("commit")
                 ?: request.getParameter("ref")
             )
           gitSequencer(
             sessionDir = sessionDir,
             command = command,
             commits = commits,
             noCommit = parseJsonBoolean(body, "noCommit", parseJsonBoolean(body, "no_commit", false)),
             mainline = parseJsonInt(body, "mainline"),
             recordOrigin = parseJsonBoolean(body, "recordOrigin", parseJsonBoolean(body, "x", false)),
             sequence = parseJsonField(body, "sequence")
               ?: parseJsonField(body, "operation")
               ?: request.getParameter("sequence"),
             resp = response
           )
         }
         "merge" -> {
           val body = readBody(request, "merge")
           val ref = parseJsonField(body, "ref")
             ?: parseJsonField(body, "branch")
             ?: parseJsonField(body, "commit")
             ?: request.getParameter("ref")
           gitMerge(
             sessionDir = sessionDir,
             ref = ref,
             noFastForward = parseJsonBoolean(body, "noFastForward", parseJsonBoolean(body, "no_ff", false)),
             squash = parseJsonBoolean(body, "squash", false),
             message = parseJsonField(body, "message"),
             sequence = parseJsonField(body, "sequence")
               ?: parseJsonField(body, "operation")
               ?: request.getParameter("sequence"),
             resp = response
           )
         }
         "stash" -> {
           val body = readBody(request, "stash")
           val stashAction = parseJsonField(body, "action")
             ?: parseJsonField(body, "operation")
             ?: request.getParameter("action")
             ?: "push"
           gitStash(
             sessionDir = sessionDir,
             action = stashAction,
             message = parseJsonField(body, "message"),
             ref = parseJsonField(body, "ref") ?: parseJsonField(body, "stash") ?: request.getParameter("ref"),
             includeUntracked = parseJsonBoolean(
               body, "includeUntracked", parseJsonBoolean(body, "include_untracked", false)
             ),
             keepIndex = parseJsonBoolean(body, "keepIndex", parseJsonBoolean(body, "keep_index", false)),
             resp = response
           )
         }
         "tag" -> {
           val body = readBody(request, "tag")
           gitTag(
             sessionDir = sessionDir,
             name = parseJsonField(body, "tag") ?: parseJsonField(body, "name") ?: request.getParameter("tag"),
             ref = parseJsonField(body, "ref") ?: parseJsonField(body, "commit") ?: request.getParameter("ref"),
             message = parseJsonField(body, "message"),
             force = parseJsonBoolean(body, "force", false),
             delete = parseJsonBoolean(body, "delete", false),
             resp = response
           )
         }
         "submodule", "submodules" -> {
           val body = readBody(request, "submodule")
           val subAction = parseJsonField(body, "action")
             ?: parseJsonField(body, "operation")
             ?: request.getParameter("action")
             ?: "update"
           gitSubmodule(
             sessionDir = sessionDir,
             action = subAction,
             path = parseJsonField(body, "path") ?: parseJsonField(body, "name") ?: request.getParameter("path"),
             url = parseJsonField(body, "url")
               ?: parseJsonField(body, "repository")
               ?: request.getParameter("url"),
             branch = parseJsonField(body, "branch"),
             recursive = parseJsonBoolean(body, "recursive", false),
             force = parseJsonBoolean(body, "force", false),
             initSubmodules = parseJsonBoolean(body, "init", true),
             remote = parseJsonBoolean(body, "remote", false),
             resp = response
           )
         }


        else -> {
          log.warn("Unknown git POST action: $action")
          response.status = HttpServletResponse.SC_BAD_REQUEST
          response.contentType = "application/json"
          response.writer.write("""{"error": "Unknown git POST action: $action"}""")
        }
      }
    } catch (e: IllegalStateException) {
      log.warn("Git API POST authentication/state error: ${e.message}")
      if (!response.isCommitted) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error": "${escapeJson(e.message ?: "Authentication failed")}"}""")
      }
    } catch (e: Exception) {
      log.error("Error handling git API POST request", e)
      if (!response.isCommitted) {
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.contentType = "application/json"
        response.writer.write("""{"error": "Git operation failed: ${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }


  /**
   * Checkout a branch in the session's git repository.
   * Optionally creates the branch (`create`) at the given `startPoint`.
   */
  private fun gitCheckout(
    sessionDir: File,
    branch: String,
    create: Boolean,
    startPoint: String? = null,
    resp: HttpServletResponse
  ) {
    log.info("Checking out branch '$branch' (create=$create, startPoint=$startPoint) in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      // Validate branch name
      if (!isValidBranchName(branch)) {
        log.warn("Invalid branch name rejected: '$branch'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid branch name: ${escapeJson(branch)}"}""")
        return
      }
      if (!startPoint.isNullOrBlank() && !isValidRevision(startPoint)) {
        log.warn("Invalid start point rejected: '$startPoint'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid start point: ${escapeJson(startPoint)}"}""")
        return
      }
      val args = mutableListOf("git", "checkout")
      if (create) {
        args.add("-b")
      }
      args.add(branch)
      if (create && !startPoint.isNullOrBlank()) {
        args.add(startPoint)
      }
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        log.info("Successfully checked out branch '$branch' in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "message": "Checked out branch '${escapeJson(branch)}'", "output": "${
            escapeJson(result.output)
          }"}"""
        )
      } else {
        log.error("git checkout failed for branch '$branch' with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
            escapeJson(
              result.output
            )
          }"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitCheckout for branch '$branch' in ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  /**
   * Create / checkout / delete a branch in the session's git repository.
   *
   * Handles `POST .git/api/branch` with a body such as:
   * `{"branch": "b1", "startPoint": "e12c5cc...", "create": true, "checkout": true}`
   */
  private fun gitBranch(
    sessionDir: File,
    branch: String,
    startPoint: String?,
    create: Boolean,
    checkout: Boolean,
    delete: Boolean,
    resp: HttpServletResponse
  ) {
    log.info(
      "Branch operation '$branch' (startPoint=$startPoint, create=$create, checkout=$checkout, delete=$delete) in: ${sessionDir.absolutePath}"
    )
    try {
      ensureGitRepo(sessionDir)
      if (!isValidBranchName(branch)) {
        log.warn("Invalid branch name rejected: '$branch'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid branch name: ${escapeJson(branch)}"}""")
        return
      }
      if (!startPoint.isNullOrBlank() && !isValidRevision(startPoint)) {
        log.warn("Invalid start point rejected: '$startPoint'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid start point: ${escapeJson(startPoint)}"}""")
        return
      }
      if (!create && !checkout && !delete) {
        log.warn("Branch request with no operation requested for '$branch'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "No operation requested (create, checkout or delete)"}""")
        return
      }
      val branchExists = executeGitCommand(
        sessionDir, "git", "rev-parse", "--verify", "--quiet", "refs/heads/$branch"
      ).exitCode == 0
      log.debug("Branch '$branch' exists=$branchExists")
      if (delete) {
        if (!branchExists) {
          resp.status = HttpServletResponse.SC_NOT_FOUND
          resp.contentType = "application/json"
          resp.writer.write("""{"success": false, "error": "Branch does not exist: ${escapeJson(branch)}"}""")
          return
        }
        val deleteResult = executeGitCommand(sessionDir, "git", "branch", "-D", branch)
        if (deleteResult.exitCode != 0) {
          log.error("git branch -D failed for '$branch': ${deleteResult.error}")
          resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          resp.contentType = "application/json"
          resp.writer.write("""{"success": false, "error": "${escapeJson(deleteResult.error)}"}""")
          return
        }
        log.info("Deleted branch '$branch' in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "deleted": true, "branch": "${escapeJson(branch)}", "output": "${
            escapeJson(deleteResult.output)
          }"}"""
        )
        return
      }
      val output = StringBuilder()
      var created = false
      var checkedOut = false
      if (create && branchExists && !checkout) {
        log.warn("Branch '$branch' already exists")
        resp.status = HttpServletResponse.SC_CONFLICT
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "existed": true, "branch": "${escapeJson(branch)}", "error": "Branch already exists: ${
            escapeJson(branch)
          }"}"""
        )
        return
      }
      if (create && !branchExists) {
        val args = mutableListOf("git", "branch", branch)
        if (!startPoint.isNullOrBlank()) args.add(startPoint)
        val createResult = executeGitCommand(sessionDir, *args.toTypedArray())
        if (createResult.exitCode != 0) {
          log.error("git branch failed for '$branch': ${createResult.error}")
          resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          resp.contentType = "application/json"
          resp.writer.write(
            """{"success": false, "error": "${escapeJson(createResult.error)}", "output": "${
              escapeJson(createResult.output)
            }"}"""
          )
          return
        }
        created = true
        output.append(createResult.output).append(createResult.error)
        log.info("Created branch '$branch'${if (startPoint.isNullOrBlank()) "" else " at $startPoint"}")
      }
      if (checkout) {
        val checkoutResult = executeGitCommand(sessionDir, "git", "checkout", branch)
        if (checkoutResult.exitCode != 0) {
          log.error("git checkout failed for '$branch': ${checkoutResult.error}")
          resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          resp.contentType = "application/json"
          resp.writer.write(
            """{"success": false, "created": $created, "error": "${escapeJson(checkoutResult.error)}", "output": "${
              escapeJson(checkoutResult.output)
            }"}"""
          )
          return
        }
        checkedOut = true
        output.append(checkoutResult.output).append(checkoutResult.error)
        log.info("Checked out branch '$branch' in ${sessionDir.absolutePath}")
      }
      val currentBranch = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD").output.trim()
      val head = executeGitCommand(sessionDir, "git", "rev-parse", branch).output.trim()
      resp.status = HttpServletResponse.SC_OK
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": true, "branch": "${escapeJson(branch)}", "existed": $branchExists, "created": $created, "checkedOut": $checkedOut, "startPoint": "${
          escapeJson(startPoint ?: "")
        }", "currentBranch": "${escapeJson(currentBranch)}", "head": "${escapeJson(head)}", "output": "${
          escapeJson(output.toString())
        }"}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitBranch for branch '$branch' in ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
   /**
    * Reset the session's git repository.
    *
    * Handles `POST .git/api/reset` with a body such as:
    * `{"mode": "hard", "ref": "HEAD"}`
    *
    * `mode` must be one of soft / mixed / hard / merge / keep.
    */
   private fun gitReset(sessionDir: File, mode: String, ref: String, resp: HttpServletResponse) {
     log.info("Resetting repository (mode=$mode, ref=$ref) in: ${sessionDir.absolutePath}")
     try {
       ensureGitRepo(sessionDir)
       val normalizedMode = mode.trim().trim('"', '\'').removePrefix("--").lowercase()
       if (normalizedMode !in VALID_RESET_MODES) {
         log.warn("Invalid reset mode rejected: '$mode'")
         resp.status = HttpServletResponse.SC_BAD_REQUEST
         resp.contentType = "application/json"
         resp.writer.write(
           """{"success": false, "error": "Invalid reset mode: ${escapeJson(mode)}. Expected one of ${
             VALID_RESET_MODES.joinToString("/")
           }"}"""
         )
         return
       }
       val normalizedRef = ref.trim().trim('"', '\'').ifBlank { "HEAD" }
       if (!isValidRevision(normalizedRef)) {
         log.warn("Invalid reset ref rejected: '$ref'")
         resp.status = HttpServletResponse.SC_BAD_REQUEST
         resp.contentType = "application/json"
         resp.writer.write("""{"success": false, "error": "Invalid ref: ${escapeJson(ref)}"}""")
         return
       }
       val result = executeGitCommand(sessionDir, "git", "reset", "--$normalizedMode", normalizedRef)
       if (result.exitCode == 0) {
         val head = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD").output.trim()
         val currentBranch = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD").output.trim()
         log.info("Reset --$normalizedMode to $normalizedRef succeeded (HEAD=$head) in ${sessionDir.absolutePath}")
         resp.status = HttpServletResponse.SC_OK
         resp.contentType = "application/json"
         resp.writer.write(
           """{"success": true, "message": "Reset --$normalizedMode to '${escapeJson(normalizedRef)}'", "mode": "${
             escapeJson(normalizedMode)
           }", "ref": "${escapeJson(normalizedRef)}", "head": "${escapeJson(head)}", "currentBranch": "${
             escapeJson(currentBranch)
           }", "output": "${escapeJson(result.output + result.error)}"}"""
         )
       } else {
         log.error("git reset failed with exit code ${result.exitCode}: ${result.error}")
         resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
         resp.contentType = "application/json"
         resp.writer.write(
           """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
             escapeJson(result.output)
           }"}"""
         )
       }
     } catch (e: Exception) {
       log.error("Exception during gitReset for ${sessionDir.absolutePath}", e)
       if (!resp.isCommitted) {
         resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
         resp.contentType = "application/json"
         resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
       }
     }
   }
   /**
    * Remove untracked files from the session's git repository.
    *
    * Handles `POST .git/api/clean` with a body such as:
    * `{"directories": true, "ignored": true, "force": true, "dryRun": false}`
    *
    * Either `force` or `dryRun` must be set (mirrors git's own safety requirement).
    * When `dryRun` is true nothing is deleted and the would-be removals are reported.
    */
   private fun gitClean(
     sessionDir: File,
     directories: Boolean,
     ignored: Boolean,
     force: Boolean,
     dryRun: Boolean,
     resp: HttpServletResponse
   ) {
     log.info(
       "Cleaning repository (directories=$directories, ignored=$ignored, force=$force, dryRun=$dryRun) in: ${sessionDir.absolutePath}"
     )
     try {
       ensureGitRepo(sessionDir)
       if (!force && !dryRun) {
         log.warn("Clean request refused: neither force nor dryRun requested")
         resp.status = HttpServletResponse.SC_BAD_REQUEST
         resp.contentType = "application/json"
         resp.writer.write("""{"success": false, "error": "Refusing to clean without 'force' (or use 'dryRun')"}""")
         return
       }
       /* -n (dry run) takes precedence over -f so nothing is deleted when previewing */
       val flags = StringBuilder("-")
       if (dryRun) flags.append("n") else flags.append("f")
       if (directories) flags.append("d")
       if (ignored) flags.append("x")
       val result = executeGitCommand(sessionDir, "git", "clean", flags.toString())
       if (result.exitCode == 0) {
         val removed = result.output.lines()
           .map { it.trim() }
           .filter { it.isNotBlank() }
           .mapNotNull { line ->
             when {
               line.startsWith("Removing ") -> line.removePrefix("Removing ").trim()
               line.startsWith("Would remove ") -> line.removePrefix("Would remove ").trim()
               else -> null
             }
           }
           .map { """"${escapeJson(it)}"""" }
         log.info("git clean ${flags} affected ${removed.size} path(s) in ${sessionDir.absolutePath}")
         resp.status = HttpServletResponse.SC_OK
         resp.contentType = "application/json"
         resp.writer.write(
           """{"success": true, "dryRun": $dryRun, "directories": $directories, "ignored": $ignored, "count": ${removed.size}, "removed": [${
             removed.joinToString(", ")
           }], "output": "${escapeJson(result.output)}"}"""
         )
       } else {
         log.error("git clean failed with exit code ${result.exitCode}: ${result.error}")
         resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
         resp.contentType = "application/json"
         resp.writer.write(
           """{"success": false, "error": "${escapeJson(result.error)}", "output": "${
             escapeJson(result.output)
           }"}"""
         )
       }
     } catch (e: Exception) {
       log.error("Exception during gitClean for ${sessionDir.absolutePath}", e)
       if (!resp.isCommitted) {
         resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
         resp.contentType = "application/json"
         resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
       }
     }
   }
  /**
   * List the submodules declared in `.gitmodules` and/or known to the index.
   *
   * Handles `GET .git/api/submodules`.
   */
  private fun gitListSubmodules(sessionDir: File, resp: HttpServletResponse) {
    log.info("Listing git submodules in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val configured = linkedMapOf<String, MutableMap<String, String>>()
      if (File(sessionDir, ".gitmodules").exists()) {
        val configResult = executeGitCommand(sessionDir, "git", "config", "-f", ".gitmodules", "--list")
        if (configResult.exitCode != 0) {
          log.warn("Failed to read .gitmodules: ${configResult.error}")
        }
        configResult.output.lines().filter { it.isNotBlank() }.forEach { line ->
          val idx = line.indexOf('=')
          if (idx <= 0) return@forEach
          val key = line.substring(0, idx).trim()
          val value = line.substring(idx + 1).trim()
          val match = SUBMODULE_CONFIG_REGEX.find(key) ?: return@forEach
          val name = match.groupValues[1]
          configured.getOrPut(name) { linkedMapOf("name" to name) }[match.groupValues[2]] = value
        }
      } else {
        log.debug("No .gitmodules in ${sessionDir.absolutePath}")
      }
      val statusResult = executeGitCommand(sessionDir, "git", "submodule", "status")
      /* path -> (state, sha, describe) */
      val statusByPath = linkedMapOf<String, Triple<String, String, String>>()
      if (statusResult.exitCode == 0) {
        statusResult.output.lines().filter { it.isNotBlank() }.forEach { line ->
          val m = SUBMODULE_STATUS_REGEX.find(line.trimEnd()) ?: run {
            log.debug("Unparsed submodule status line: '$line'")
            return@forEach
          }
          val state = when (m.groupValues[1]) {
            "-" -> "uninitialized"
            "+" -> "modified"
            "U" -> "conflict"
            else -> "initialized"
          }
          statusByPath[m.groupValues[3]] = Triple(state, m.groupValues[2], m.groupValues[4])
        }
      } else {
        log.warn("git submodule status failed with exit code ${statusResult.exitCode}: ${statusResult.error}")
      }
      val paths = LinkedHashSet<String>()
      configured.values.forEach { cfg -> cfg["path"]?.let { paths.add(it) } }
      paths.addAll(statusByPath.keys)
      val entries = paths.map { path ->
        val cfg = configured.values.firstOrNull { it["path"] == path }
        val status = statusByPath[path]
        val initialized = File(sessionDir, path).let { dir -> File(dir, ".git").exists() }
        """{"name": "${escapeJson(cfg?.get("name") ?: path)}", "path": "${escapeJson(path)}", "url": "${
          escapeJson(cfg?.get("url") ?: "")
        }", "branch": "${escapeJson(cfg?.get("branch") ?: "")}", "state": "${
          escapeJson(status?.first ?: "unknown")
        }", "sha": "${escapeJson(status?.second ?: "")}", "describe": "${
          escapeJson(status?.third ?: "")
        }", "initialized": $initialized}"""
      }
      log.debug("Found ${entries.size} submodules in ${sessionDir.absolutePath}")
      resp.status = HttpServletResponse.SC_OK
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": true, "count": ${entries.size}, "submodules": [${entries.joinToString(", ")}]}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitListSubmodules for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * Perform a submodule operation.
   *
   * Handles `POST .git/api/submodule` with a body such as:
   * `{"action": "add", "url": "https://host/repo.git", "path": "libs/repo", "branch": "main"}`
   * `{"action": "update", "recursive": true, "init": true}`
   *
   * `action` must be one of add / init / update / sync / deinit / status.
   */
  private fun gitSubmodule(
    sessionDir: File,
    action: String,
    path: String?,
    url: String?,
    branch: String?,
    recursive: Boolean,
    force: Boolean,
    initSubmodules: Boolean,
    remote: Boolean,
    resp: HttpServletResponse
  ) {
    log.info(
      "Submodule operation '$action' (path=$path, url=$url, branch=$branch, recursive=$recursive) in: ${sessionDir.absolutePath}"
    )
    try {
      ensureGitRepo(sessionDir)
      val normalizedAction = action.trim().trim('"', '\'').removePrefix("--").lowercase()
      if (normalizedAction !in VALID_SUBMODULE_ACTIONS) {
        log.warn("Invalid submodule action rejected: '$action'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "error": "Invalid submodule action: ${escapeJson(action)}. Expected one of ${
            VALID_SUBMODULE_ACTIONS.joinToString("/")
          }"}"""
        )
        return
      }
      if (!path.isNullOrBlank() && !isValidRelativePath(path)) {
        log.warn("Invalid submodule path rejected: '$path'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid submodule path: ${escapeJson(path)}"}""")
        return
      }
      if (!branch.isNullOrBlank() && !isValidBranchName(branch)) {
        log.warn("Invalid submodule branch rejected: '$branch'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid branch name: ${escapeJson(branch)}"}""")
        return
      }
      val args = mutableListOf("git", "submodule", normalizedAction)
      when (normalizedAction) {
        "add" -> {
          if (url.isNullOrBlank() || !isValidRepositoryUrl(url)) {
            log.warn("Invalid submodule url rejected: '$url'")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write(
              """{"success": false, "error": "A valid repository url is required: ${escapeJson(url ?: "")}"}"""
            )
            return
          }
          if (path.isNullOrBlank()) {
            log.warn("Submodule add request missing path")
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "error": "Submodule path is required"}""")
            return
          }
          if (!branch.isNullOrBlank()) {
            args.add("-b")
            args.add(branch)
          }
          if (force) args.add("--force")
          args.add("--")
          args.add(url)
          args.add(path)
        }
        "init" -> if (!path.isNullOrBlank()) {
          args.add("--")
          args.add(path)
        }
        "update" -> {
          if (initSubmodules) args.add("--init")
          if (recursive) args.add("--recursive")
          if (remote) args.add("--remote")
          if (force) args.add("--force")
          if (!path.isNullOrBlank()) {
            args.add("--")
            args.add(path)
          }
        }
        "sync" -> {
          if (recursive) args.add("--recursive")
          if (!path.isNullOrBlank()) {
            args.add("--")
            args.add(path)
          }
        }
        "deinit" -> {
          if (force) args.add("--force")
          if (path.isNullOrBlank()) args.add("--all") else {
            args.add("--")
            args.add(path)
          }
        }
        "status" -> {
          if (recursive) args.add("--recursive")
          if (!path.isNullOrBlank()) {
            args.add("--")
            args.add(path)
          }
        }
      }
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        log.info("Submodule '$normalizedAction' succeeded in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "action": "${escapeJson(normalizedAction)}", "path": "${
            escapeJson(path ?: "")
          }", "url": "${escapeJson(url ?: "")}", "branch": "${escapeJson(branch ?: "")}", "output": "${
            escapeJson(result.output + result.error)
          }"}"""
        )
      } else {
        log.error("git submodule $normalizedAction failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "action": "${escapeJson(normalizedAction)}", "error": "${
            escapeJson(result.error)
          }", "output": "${escapeJson(result.output)}"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitSubmodule('$action') for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * Shared implementation for the sequencer commands `revert` and `cherry-pick`.
   *
   * Handles `POST .git/api/revert` / `POST .git/api/cherry-pick` with bodies such as:
   * `{"commit": "e12c5cc"}`, `{"commits": ["a1b2c3", "d4e5f6"], "noCommit": true}`
   * or `{"sequence": "abort"}` to continue/abort/quit/skip an in-progress sequence.
   *
   * Conflicts are reported with HTTP 409 and the list of unmerged paths.
   */
  private fun gitSequencer(
    sessionDir: File,
    command: String,
    commits: List<String>,
    noCommit: Boolean,
    mainline: Int?,
    recordOrigin: Boolean,
    sequence: String?,
    resp: HttpServletResponse
  ) {
    log.info(
      "$command (commits=$commits, noCommit=$noCommit, mainline=$mainline, sequence=$sequence) in: ${sessionDir.absolutePath}"
    )
    try {
      ensureGitRepo(sessionDir)
      if (!sequence.isNullOrBlank()) {
        val op = sequence.trim().trim('"', '\'').removePrefix("--").lowercase()
        if (op !in SEQUENCER_ACTIONS) {
          log.warn("Invalid $command sequence action rejected: '$sequence'")
          resp.status = HttpServletResponse.SC_BAD_REQUEST
          resp.contentType = "application/json"
          resp.writer.write(
            """{"success": false, "error": "Invalid sequence action: ${escapeJson(sequence)}. Expected one of ${
              SEQUENCER_ACTIONS.joinToString("/")
            }"}"""
          )
          return
        }
        val seqResult = executeGitCommand(sessionDir, "git", command, "--$op")
        if (seqResult.exitCode != 0) {
          log.error("git $command --$op failed: ${seqResult.error}")
          resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          resp.contentType = "application/json"
          resp.writer.write(
            """{"success": false, "operation": "$command", "sequence": "${escapeJson(op)}", "error": "${
              escapeJson(seqResult.error)
            }", "output": "${escapeJson(seqResult.output)}"}"""
          )
          return
        }
        val seqHead = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD").output.trim()
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "operation": "$command", "sequence": "${escapeJson(op)}", "head": "${
            escapeJson(seqHead)
          }", "output": "${escapeJson(seqResult.output + seqResult.error)}"}"""
        )
        return
      }
      if (commits.isEmpty()) {
        log.warn("$command request missing commit(s)")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "At least one commit is required"}""")
        return
      }
      val invalid = commits.firstOrNull { !isValidRevision(it) }
      if (invalid != null) {
        log.warn("Invalid $command revision rejected: '$invalid'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid commit: ${escapeJson(invalid)}"}""")
        return
      }
      if (mainline != null && mainline !in 1..16) {
        log.warn("Invalid mainline parent rejected: $mainline")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid mainline parent: $mainline"}""")
        return
      }
      val args = mutableListOf("git", command)
      if (command == "revert") args.add("--no-edit")
      if (noCommit) args.add("--no-commit")
      if (mainline != null) {
        args.add("-m")
        args.add(mainline.toString())
      }
      if (command == "cherry-pick" && recordOrigin) args.add("-x")
      args.addAll(commits)
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        val head = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD").output.trim()
        val currentBranch = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD").output.trim()
        log.info("$command succeeded (HEAD=$head) in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "operation": "$command", "commits": [${
            commits.joinToString(", ") { """"${escapeJson(it)}"""" }
          }], "noCommit": $noCommit, "head": "${escapeJson(head)}", "currentBranch": "${
            escapeJson(currentBranch)
          }", "output": "${escapeJson(result.output + result.error)}"}"""
        )
      } else {
        val conflicts = conflictedFiles(sessionDir)
        log.error("git $command failed with exit code ${result.exitCode} (${conflicts.size} conflicts): ${result.error}")
        resp.status =
          if (conflicts.isEmpty()) HttpServletResponse.SC_INTERNAL_SERVER_ERROR else HttpServletResponse.SC_CONFLICT
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "operation": "$command", "conflicts": [${
            conflicts.joinToString(", ") { """"${escapeJson(it)}"""" }
          }], "error": "${escapeJson(result.error)}", "output": "${escapeJson(result.output)}"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during git $command for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * Merge a ref into the current branch.
   *
   * Handles `POST .git/api/merge` with `{"ref": "feature", "noFastForward": true}`
   * or `{"sequence": "abort"}`.
   */
  private fun gitMerge(
    sessionDir: File,
    ref: String?,
    noFastForward: Boolean,
    squash: Boolean,
    message: String?,
    sequence: String?,
    resp: HttpServletResponse
  ) {
    log.info("Merging '$ref' (noFf=$noFastForward, squash=$squash, sequence=$sequence) in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      if (!sequence.isNullOrBlank()) {
        val op = sequence.trim().trim('"', '\'').removePrefix("--").lowercase()
        if (op !in SEQUENCER_ACTIONS) {
          resp.status = HttpServletResponse.SC_BAD_REQUEST
          resp.contentType = "application/json"
          resp.writer.write("""{"success": false, "error": "Invalid merge action: ${escapeJson(sequence)}"}""")
          return
        }
        val seqResult = executeGitCommand(sessionDir, "git", "merge", "--$op")
        resp.status =
          if (seqResult.exitCode == 0) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": ${seqResult.exitCode == 0}, "operation": "merge", "sequence": "${escapeJson(op)}", "error": "${
            escapeJson(seqResult.error)
          }", "output": "${escapeJson(seqResult.output)}"}"""
        )
        return
      }
      if (ref.isNullOrBlank() || !isValidRevision(ref)) {
        log.warn("Invalid merge ref rejected: '$ref'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "A valid ref is required: ${escapeJson(ref ?: "")}"}""")
        return
      }
      val args = mutableListOf("git", "merge", "--no-edit")
      if (noFastForward) args.add("--no-ff")
      if (squash) args.add("--squash")
      if (!message.isNullOrBlank()) {
        args.add("-m")
        args.add(message)
      }
      args.add(ref)
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        val head = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD").output.trim()
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "operation": "merge", "ref": "${escapeJson(ref)}", "head": "${
            escapeJson(head)
          }", "output": "${escapeJson(result.output + result.error)}"}"""
        )
      } else {
        val conflicts = conflictedFiles(sessionDir)
        log.error("git merge failed with exit code ${result.exitCode} (${conflicts.size} conflicts): ${result.error}")
        resp.status =
          if (conflicts.isEmpty()) HttpServletResponse.SC_INTERNAL_SERVER_ERROR else HttpServletResponse.SC_CONFLICT
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "operation": "merge", "conflicts": [${
            conflicts.joinToString(", ") { """"${escapeJson(it)}"""" }
          }], "error": "${escapeJson(result.error)}", "output": "${escapeJson(result.output)}"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitMerge for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * Stash operations.
   *
   * Handles `POST .git/api/stash` with `{"action": "push", "message": "wip", "includeUntracked": true}`.
   */
  private fun gitStash(
    sessionDir: File,
    action: String,
    message: String?,
    ref: String?,
    includeUntracked: Boolean,
    keepIndex: Boolean,
    resp: HttpServletResponse
  ) {
    log.info("Stash operation '$action' (ref=$ref) in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val normalized = action.trim().trim('"', '\'').removePrefix("--").lowercase()
      if (normalized !in STASH_ACTIONS) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "error": "Invalid stash action: ${escapeJson(action)}. Expected one of ${
            STASH_ACTIONS.joinToString("/")
          }"}"""
        )
        return
      }
      if (!ref.isNullOrBlank() && !isValidStashRef(ref)) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid stash ref: ${escapeJson(ref)}"}""")
        return
      }
      val stashRef = ref?.let { if (it.matches("""^\d+$""".toRegex())) "stash@{$it}" else it }
      val args = mutableListOf("git", "stash")
      when (normalized) {
        "push", "save" -> {
          args.add("push")
          if (includeUntracked) args.add("--include-untracked")
          if (keepIndex) args.add("--keep-index")
          if (!message.isNullOrBlank()) {
            args.add("-m")
            args.add(message)
          }
        }
        "pop", "apply", "drop", "show" -> {
          args.add(normalized)
          if (!stashRef.isNullOrBlank()) args.add(stashRef)
        }
        else -> args.add(normalized)
      }
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        val stashes = executeGitCommand(sessionDir, "git", "stash", "list").output.lines()
          .filter { it.isNotBlank() }
          .map { """"${escapeJson(it.trim())}"""" }
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "action": "${escapeJson(normalized)}", "stashes": [${
            stashes.joinToString(", ")
          }], "output": "${escapeJson(result.output + result.error)}"}"""
        )
      } else {
        val conflicts = conflictedFiles(sessionDir)
        log.error("git stash $normalized failed with exit code ${result.exitCode}: ${result.error}")
        resp.status =
          if (conflicts.isEmpty()) HttpServletResponse.SC_INTERNAL_SERVER_ERROR else HttpServletResponse.SC_CONFLICT
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "action": "${escapeJson(normalized)}", "conflicts": [${
            conflicts.joinToString(", ") { """"${escapeJson(it)}"""" }
          }], "error": "${escapeJson(result.error)}", "output": "${escapeJson(result.output)}"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitStash for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * Create or delete a tag.
   *
   * Handles `POST .git/api/tag` with `{"tag": "v1", "ref": "HEAD", "message": "release"}`
   * or `{"tag": "v1", "delete": true}`.
   */
  private fun gitTag(
    sessionDir: File,
    name: String?,
    ref: String?,
    message: String?,
    force: Boolean,
    delete: Boolean,
    resp: HttpServletResponse
  ) {
    log.info("Tag operation '$name' (ref=$ref, delete=$delete) in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      if (name.isNullOrBlank() || !isValidBranchName(name)) {
        log.warn("Invalid tag name rejected: '$name'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid tag name: ${escapeJson(name ?: "")}"}""")
        return
      }
      if (!ref.isNullOrBlank() && !isValidRevision(ref)) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid ref: ${escapeJson(ref)}"}""")
        return
      }
      val args = mutableListOf("git", "tag")
      if (delete) {
        args.add("-d")
        args.add(name)
      } else {
        if (force) args.add("-f")
        if (!message.isNullOrBlank()) {
          args.add("-a")
          args.add("-m")
          args.add(message)
        }
        args.add(name)
        if (!ref.isNullOrBlank()) args.add(ref)
      }
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      resp.status = if (result.exitCode == 0) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": ${result.exitCode == 0}, "tag": "${escapeJson(name)}", "deleted": $delete, "error": "${
          escapeJson(result.error)
        }", "output": "${escapeJson(result.output)}"}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitTag for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /** `GET .git/api/tags` */
  private fun gitListTags(sessionDir: File, resp: HttpServletResponse) {
    log.info("Listing git tags in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val result = executeGitCommand(sessionDir, "git", "tag", "--list", "--sort=-creatordate")
      val tags = result.output.lines().filter { it.isNotBlank() }.map { """"${escapeJson(it.trim())}"""" }
      resp.status = if (result.exitCode == 0) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": ${result.exitCode == 0}, "count": ${tags.size}, "tags": [${tags.joinToString(", ")}], "error": "${
          escapeJson(result.error)
        }"}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitListTags for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /** `GET .git/api/stashes` */
  private fun gitListStashes(sessionDir: File, resp: HttpServletResponse) {
    log.info("Listing git stashes in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val result = executeGitCommand(sessionDir, "git", "stash", "list")
      val stashes = result.output.lines().filter { it.isNotBlank() }.mapIndexed { index, line ->
        val idx = line.indexOf(':')
        val ref = if (idx > 0) line.substring(0, idx).trim() else "stash@{$index}"
        val description = if (idx > 0) line.substring(idx + 1).trim() else line.trim()
        """{"ref": "${escapeJson(ref)}", "index": $index, "description": "${escapeJson(description)}"}"""
      }
      resp.status = if (result.exitCode == 0) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": ${result.exitCode == 0}, "count": ${stashes.size}, "stashes": [${
          stashes.joinToString(", ")
        }], "error": "${escapeJson(result.error)}"}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitListStashes for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /** `GET .git/api/remotes` */
  private fun gitRemotes(sessionDir: File, resp: HttpServletResponse) {
    log.info("Listing git remotes in: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val result = executeGitCommand(sessionDir, "git", "remote", "-v")
      val fetch = linkedMapOf<String, String>()
      val push = linkedMapOf<String, String>()
      result.output.lines().filter { it.isNotBlank() }.forEach { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return@forEach
        val kind = parts.getOrNull(2)?.trim('(', ')') ?: "fetch"
        if (kind == "push") push[parts[0]] = parts[1] else fetch[parts[0]] = parts[1]
      }
      val remotes = (fetch.keys + push.keys).distinct().map { name ->
        """{"name": "${escapeJson(name)}", "fetch": "${escapeJson(fetch[name] ?: "")}", "push": "${
          escapeJson(push[name] ?: fetch[name] ?: "")
        }"}"""
      }
      resp.status = if (result.exitCode == 0) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": ${result.exitCode == 0}, "count": ${remotes.size}, "remotes": [${
          remotes.joinToString(", ")
        }], "error": "${escapeJson(result.error)}"}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitRemotes for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * `GET .git/api/diff?staged=true&from=<rev>&to=<rev>&path=<path>&nameOnly=true`
   */
  private fun gitDiff(sessionDir: File, req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Getting git diff for: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val staged = req.getParameter("staged").toBoolean() || req.getParameter("cached").toBoolean()
      val nameOnly = req.getParameter("nameOnly").toBoolean()
      val from = req.getParameter("from") ?: req.getParameter("ref")
      val to = req.getParameter("to")
      val path = req.getParameter("path")
      listOfNotNull(from, to).firstOrNull { it.isNotBlank() && !isValidRevision(it) }?.let { bad ->
        log.warn("Invalid diff revision rejected: '$bad'")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid revision: ${escapeJson(bad)}"}""")
        return
      }
      if (!path.isNullOrBlank() && !isValidRelativePath(path)) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid path: ${escapeJson(path)}"}""")
        return
      }
      val args = mutableListOf("git", "diff", "--no-color")
      if (staged) args.add("--cached")
      if (nameOnly) args.add("--name-only")
      if (!from.isNullOrBlank()) args.add(from)
      if (!to.isNullOrBlank()) args.add(to)
      if (!path.isNullOrBlank()) {
        args.add("--")
        args.add(path)
      }
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        val files = if (nameOnly) result.output.lines().filter { it.isNotBlank() }
          .map { """"${escapeJson(it.trim())}"""" } else emptyList()
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "staged": $staged, "nameOnly": $nameOnly, "files": [${
            files.joinToString(", ")
          }], "diff": "${escapeJson(result.output)}"}"""
        )
      } else {
        log.error("git diff failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
      }
    } catch (e: Exception) {
      log.error("Exception during gitDiff for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }
  /**
   * `GET .git/api/show?ref=<rev>&path=<path>&stat=true`
   */
  private fun gitShow(sessionDir: File, req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Getting git show for: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val ref = (req.getParameter("ref") ?: req.getParameter("commit") ?: "HEAD").trim()
      val path = req.getParameter("path")
      if (!isValidRevision(ref)) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid revision: ${escapeJson(ref)}"}""")
        return
      }
      if (!path.isNullOrBlank() && !isValidRelativePath(path)) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Invalid path: ${escapeJson(path)}"}""")
        return
      }
      val args = mutableListOf("git", "show", "--no-color")
      if (req.getParameter("stat").toBoolean()) args.add("--stat")
      args.add(if (path.isNullOrBlank()) ref else "$ref:$path")
      val result = executeGitCommand(sessionDir, *args.toTypedArray())
      if (result.exitCode == 0) {
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "ref": "${escapeJson(ref)}", "path": "${escapeJson(path ?: "")}", "content": "${
            escapeJson(result.output)
          }"}"""
        )
      } else {
        log.error("git show failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
      }
    } catch (e: Exception) {
      log.error("Exception during gitShow for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }



  /**
   * Commit all changes in the session's git repository.
   */
  private fun gitCommit(sessionDir: File, message: String, resp: HttpServletResponse) {
    log.info("Committing changes in: ${sessionDir.absolutePath} with message: $message")
    try {
      ensureGitRepo(sessionDir)
      // Stage all changes
      val addResult = executeGitCommand(sessionDir, "git", "add", "-A")
      if (addResult.exitCode != 0) {
        log.error("git add failed with exit code ${addResult.exitCode}: ${addResult.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "Failed to stage changes: ${escapeJson(addResult.error)}"}""")
        return
      }
      // Check if there are changes to commit
      val statusResult = executeGitCommand(sessionDir, "git", "status", "--porcelain")
      if (statusResult.exitCode == 0 && statusResult.output.isBlank()) {
        log.info("Nothing to commit in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write("""{"success": true, "message": "Nothing to commit, working tree clean"}""")
        return
      }
      log.debug("Staged changes detected, proceeding with commit")
      val commitResult = executeGitCommand(
        sessionDir, "git", "commit", "-m", message,
        "--author=SessionFileServlet <noreply@localhost>"
      )
      if (commitResult.exitCode == 0) {
        // Get the commit hash
        val hashResult = executeGitCommand(sessionDir, "git", "rev-parse", "HEAD")
        val commitHash = hashResult.output.trim()
        log.info("Commit successful: $commitHash in ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": true, "message": "Changes committed", "commitHash": "${escapeJson(commitHash)}", "output": "${
            escapeJson(commitResult.output)
          }"}"""
        )
      } else {
        log.error("git commit failed with exit code ${commitResult.exitCode}: ${commitResult.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write(
          """{"success": false, "error": "${escapeJson(commitResult.error)}", "output": "${
            escapeJson(commitResult.output)
          }"}"""
        )
      }
    } catch (e: Exception) {
      log.error("Exception during gitCommit for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  /**
   * Get the git status of the session directory.
   */
  private fun gitStatus(sessionDir: File, resp: HttpServletResponse) {
    log.info("Getting git status for: ${sessionDir.absolutePath}")
    try {
      val gitDir = File(sessionDir, ".git")
      if (!gitDir.exists()) {
        log.debug("No git repository at ${sessionDir.absolutePath}")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write("""{"success": true, "initialized": false, "message": "Not a git repository"}""")
        return
      }
      val result = executeGitCommand(sessionDir, "git", "status", "--porcelain")
      val branchResult = executeGitCommand(sessionDir, "git", "rev-parse", "--abbrev-ref", "HEAD")
      val currentBranch = branchResult.output.trim()
      val changes = result.output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        try {
          if (line.length < 3) {
            log.warn("Unexpectedly short status line: '$line'")
            return@mapNotNull null
          }
          val status = line.substring(0, 2).trim()
          val file = line.substring(3).trim()
          """{"status": "${escapeJson(status)}", "file": "${escapeJson(file)}"}"""
        } catch (e: Exception) {
          log.warn("Failed to parse status line: '$line'", e)
          null
        }
      }
      /* Report any in-progress sequencer operation so the UI can offer continue/abort. */
      val operation = when {
        File(gitDir, "CHERRY_PICK_HEAD").exists() -> "cherry-pick"
        File(gitDir, "REVERT_HEAD").exists() -> "revert"
        File(gitDir, "MERGE_HEAD").exists() -> "merge"
        File(gitDir, "rebase-merge").exists() || File(gitDir, "rebase-apply").exists() -> "rebase"
        else -> ""
      }
      val conflicts = if (operation.isBlank()) emptyList() else conflictedFiles(sessionDir)
      val hasSubmodules = File(sessionDir, ".gitmodules").exists()
      log.debug("Git status: branch=$currentBranch, ${changes.size} changes, operation='$operation'")
      resp.status = HttpServletResponse.SC_OK
      resp.contentType = "application/json"
      resp.writer.write(
        """{"success": true, "initialized": true, "currentBranch": "${escapeJson(currentBranch)}", "clean": ${changes.isEmpty()}, "changes": [${
          changes.joinToString(", ")
        }], "operation": "${escapeJson(operation)}", "conflicts": [${
          conflicts.joinToString(", ") { """"${escapeJson(it)}"""" }
        }], "hasSubmodules": $hasSubmodules}"""
      )
    } catch (e: Exception) {
      log.error("Exception during gitStatus for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
    }
  }

  /**
   * Get the git log for the session directory.
   */
  private fun gitLog(sessionDir: File, req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Getting git log for: ${sessionDir.absolutePath}")
    try {
      ensureGitRepo(sessionDir)
      val maxCount = req.getParameter("maxCount")?.toIntOrNull() ?: 20
      log.debug("git log maxCount=$maxCount")
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
          try {
            val hash = lines[i]
            val authorName = lines[i + 1]
            val authorEmail = lines[i + 2]
            val date = lines[i + 3]
            val subject = lines[i + 4]
            commits.add(
              """{"hash": "${escapeJson(hash)}", "author": "${escapeJson(authorName)}", "email": "${
                escapeJson(authorEmail)
              }", "date": "${escapeJson(date)}", "message": "${escapeJson(subject)}"}"""
            )
          } catch (e: Exception) {
            log.warn("Failed to parse commit at line $i", e)
          }
          i += 5
        }
        log.debug("Parsed ${commits.size} commits from git log")
        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "application/json"
        resp.writer.write("""{"success": true, "commits": [${commits.joinToString(", ")}]}""")
      } else {
        log.error("git log failed with exit code ${result.exitCode}: ${result.error}")
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(result.error)}"}""")
      }
    } catch (e: Exception) {
      log.error("Exception during gitLog for ${sessionDir.absolutePath}", e)
      if (!resp.isCommitted) {
        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        resp.contentType = "application/json"
        resp.writer.write("""{"success": false, "error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      }
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
      try {
        if (!sessionDir.exists()) {
          log.warn("Session directory does not exist, creating: ${sessionDir.absolutePath}")
          if (!sessionDir.mkdirs()) {
            log.error("Failed to create session directory: ${sessionDir.absolutePath}")
            throw RuntimeException("Failed to create session directory: ${sessionDir.absolutePath}")
          }
        }
        val initResult = executeGitCommand(sessionDir, "git", "init")
        if (initResult.exitCode != 0) {
          log.error("Auto-init git failed: ${initResult.error}")
          throw RuntimeException("git init failed: ${initResult.error}")
        }
        executeGitCommand(sessionDir, "git", "add", "-A")
        executeGitCommand(sessionDir, "git", "commit", "-m", "Initial commit", "--allow-empty")
        log.info("Auto-initialized git repository in ${sessionDir.absolutePath}")
      } catch (e: Exception) {
        log.error("Failed to auto-initialize git repository in ${sessionDir.absolutePath}", e)
        throw e
      }
    } else {
      log.debug("Git repository already exists at ${gitDir.absolutePath}")
    }
  }

  open fun onSession(session: Session, user: User?) {

  }
  /** Read a request body, never throwing (an unreadable body is treated as empty). */
  private fun readBody(request: HttpServletRequest, action: String): String = try {
    request.reader.readText()
  } catch (e: Exception) {
    log.error("Failed to read request body for $action action", e)
    ""
  }
  /** Paths with unmerged (conflicted) entries in the index. */
  private fun conflictedFiles(sessionDir: File): List<String> {
    val result = executeGitCommand(sessionDir, "git", "diff", "--name-only", "--diff-filter=U")
    if (result.exitCode != 0) {
      log.debug("Failed to list conflicted files: ${result.error}")
      return emptyList()
    }
    return result.output.lines().map { it.trim() }.filter { it.isNotBlank() }
  }


  fun escapeJson(value: String): String {
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
   * Tolerates unquoted keys and unquoted (boolean / numeric / bare) values, e.g.
   * `{branch: "b1", create: true}`.
   */
  private fun parseJsonField(json: String, field: String): String? {
    return try {
      if (json.isBlank()) {
        log.debug("parseJsonField: empty JSON body for field '$field'")
        return null
      }
      val key = Regex.escape(field)
      /* Quoted string value: "field": "value" or field: 'value' */
      val quoted = """["']?$key["']?\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
      quoted.find(json)?.let { return unescapeJson(it.groupValues[1]) }
      val singleQuoted = """["']?$key["']?\s*:\s*'((?:[^'\\]|\\.)*)'""".toRegex()
      singleQuoted.find(json)?.let { return unescapeJson(it.groupValues[1]) }
      /* Bare value: field: true / field: 42 / field: null */
      val bare = """["']?$key["']?\s*:\s*([^,}\s"']+)""".toRegex()
      val match = bare.find(json) ?: run {
        log.debug("parseJsonField: field '$field' not found in JSON")
        return null
      }
      val value = match.groupValues[1].trim()
      if (value.equals("null", ignoreCase = true)) null else value
    } catch (e: Exception) {
      log.warn("Failed to parse JSON field '$field' from body", e)
      null
    }
  }

  /**
   * Parse a boolean JSON field, tolerating quoted and unquoted forms.
   */
  private fun parseJsonBoolean(json: String, field: String, default: Boolean): Boolean {
    val raw = parseJsonField(json, field)?.trim()?.trim('"', '\'') ?: return default
    return when (raw.lowercase()) {
      "true", "1", "yes", "on" -> true
      "false", "0", "no", "off" -> false
      else -> {
        log.debug("parseJsonBoolean: unrecognized value '$raw' for field '$field', using default $default")
        default
      }
    }
  }
  /**
   * Parse a JSON array of strings (tolerating bare/unquoted elements), e.g.
   * `{"commits": ["a1b2c3", 'd4e5f6', HEAD~1]}`. Returns null when absent/empty.
   */
  private fun parseJsonStringArray(json: String, field: String): List<String>? {
    return try {
      if (json.isBlank()) return null
      val key = Regex.escape(field)
      val array = """["']?$key["']?\s*:\s*\[([^\]]*)\]""".toRegex().find(json) ?: return null
      val items = """"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)'|([^,\s\[\]]+)""".toRegex()
        .findAll(array.groupValues[1])
        .mapNotNull { m ->
          val value = when {
            m.groups[1] != null -> unescapeJson(m.groupValues[1])
            m.groups[2] != null -> unescapeJson(m.groupValues[2])
            else -> m.groupValues[3].trim()
          }
          value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }
        .toList()
      items.ifEmpty { null }
    } catch (e: Exception) {
      log.warn("Failed to parse JSON array field '$field' from body", e)
      null
    }
  }
  /** Parse an integer JSON field, tolerating quoted forms. */
  private fun parseJsonInt(json: String, field: String): Int? =
    parseJsonField(json, field)?.trim()?.trim('"', '\'')?.toIntOrNull()


  private fun unescapeJson(value: String): String = value
    .replace("\\\"", "\"")
    .replace("\\n", "\n")
    .replace("\\r", "\r")
    .replace("\\t", "\t")
    .replace("\\/", "/")
    .replace("\\\\", "\\")


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

  /**
   * Basic validation for a git revision / start point (commit hash, tag, branch, HEAD~2, ...).
   * Deliberately conservative: rejects anything that could be interpreted as an option or
   * that contains shell/ref-unsafe characters.
   */
  private fun isValidRevision(rev: String): Boolean {
    return rev.isNotBlank() &&
        !rev.startsWith("-") &&
        !rev.contains("..") &&
        !rev.contains(" ") &&
        !rev.contains("\\") &&
        !rev.contains(":") &&
        !rev.contains("?") &&
        !rev.contains("*") &&
        !rev.contains("[") &&
        !rev.contains("@{") &&
        rev.length <= 255 &&
        rev.all { it.code in 33..126 }
  }
  /**
   * Validation for a repository-relative path (submodule path, diff path, ...).
   * Rejects absolute paths, parent traversal, option-looking values and control characters.
   */
  private fun isValidRelativePath(path: String): Boolean {
    return path.isNotBlank() &&
        !path.startsWith("-") &&
        !path.startsWith("/") &&
        !path.startsWith("\\") &&
        !path.contains("..") &&
        !Regex("^[A-Za-z]:").containsMatchIn(path) &&
        path.length <= 1024 &&
        path.none { it.code < 32 || it.code == 127 }
  }
  /**
   * Conservative validation for a submodule repository URL: http(s)/git/ssh/file URLs,
   * scp-style `user@host:path` and relative `../` URLs only.
   */
  private fun isValidRepositoryUrl(url: String): Boolean {
    return url.isNotBlank() &&
        !url.startsWith("-") &&
        url.length <= 2048 &&
        url.none { it.code < 32 || it.code == 127 } &&
        SUBMODULE_URL_REGEX.matches(url)
  }
  /** `stash@{N}` or a bare stash index. */
  private fun isValidStashRef(ref: String): Boolean = STASH_REF_REGEX.matches(ref.trim())
}