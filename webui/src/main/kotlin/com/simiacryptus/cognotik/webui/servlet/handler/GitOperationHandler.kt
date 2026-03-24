package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils.jsonEscape
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object GitOperationHandler {
  private val log = LoggerFactory.getLogger(GitOperationHandler::class.java)
  fun isGitRepository(dir: File?): Boolean {
    if (dir == null) return false
    var current: File? = dir
    while (current != null) {
      if (File(current, ".git").exists()) return true
      current = current.parentFile
    }
    return false
  }

  fun handleGitOperation(req: HttpServletRequest, resp: HttpServletResponse, gitRoot: File?) {
    val action = req.getParameter("gitAction")
    if (gitRoot == null || !gitRoot.exists()) {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": "Git root directory not found"}""")
      return
    }
    try {
      when (action) {
        "init" -> {
          val result = executeGitCommand(gitRoot, "git", "init")
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "message": "Git repository initialized", "output": ${jsonEscape(result)}}""")
        }

        "status" -> {
          val result = executeGitCommand(gitRoot, "git", "status", "--porcelain")
          val branchResult = executeGitCommand(gitRoot, "git", "branch", "--show-current")
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write(
            """{"success": true, "branch": ${jsonEscape(branchResult.trim())}, "status": ${
              jsonEscape(
                result
              )
            }}"""
          )
        }

        "add" -> {
          val filePath = req.getParameter("filePath") ?: "."
          val result = executeGitCommand(gitRoot, "git", "add", filePath)
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "message": "Files staged", "output": ${jsonEscape(result)}}""")
        }

        "commit" -> {
          val message = req.getParameter("message") ?: "Commit from web UI"
          executeGitCommand(gitRoot, "git", "add", "-A")
          val result = executeGitCommand(gitRoot, "git", "commit", "-m", message)
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "message": "Changes committed", "output": ${jsonEscape(result)}}""")
        }

        "log" -> {
          val count = req.getParameter("count") ?: "20"
          val result = executeGitCommand(gitRoot, "git", "log", "--oneline", "-n", count)
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "log": ${jsonEscape(result)}}""")
        }

        "diff" -> {
          val result = executeGitCommand(gitRoot, "git", "diff")
          val stagedResult = executeGitCommand(gitRoot, "git", "diff", "--cached")
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "unstaged": ${jsonEscape(result)}, "staged": ${jsonEscape(stagedResult)}}""")
        }

        "reset" -> {
          val filePath = req.getParameter("filePath")
          val result = if (filePath != null) {
            buildString {
              append(executeGitCommand(gitRoot, "git", "checkout", "--", filePath))
              append("\n")
              append(executeGitCommand(gitRoot, "git", "reset", "--hard", "HEAD", filePath))
              append("\n")
              append(executeGitCommand(gitRoot, "git", "clean", "-fdx", "--", filePath))
            }
          } else {
            buildString {
              append(executeGitCommand(gitRoot, "git", "checkout", "--", "."))
              append("\n")
              append(executeGitCommand(gitRoot, "git", "reset", "--hard", "HEAD"))
              append("\n")
              append(executeGitCommand(gitRoot, "git", "clean", "-fdx"))
            }
          }
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write("""{"success": true, "message": "Changes reset", "output": ${jsonEscape(result)}}""")
        }

        "branches" -> {
          val result = executeGitCommand(gitRoot, "git", "branch")
          val currentBranch = executeGitCommand(gitRoot, "git", "branch", "--show-current").trim()
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write(
            """{"success": true, "currentBranch": ${jsonEscape(currentBranch)}, "branches": ${
              jsonEscape(
                result
              )
            }}"""
          )
        }

        "create-branch" -> {
          val branchName = req.getParameter("branchName")
          if (branchName.isNullOrBlank()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
            return
          }
          val checkout = req.getParameter("checkout") ?: "true"
          val result = if (checkout == "true") {
            executeGitCommand(gitRoot, "git", "checkout", "-b", branchName)
          } else {
            executeGitCommand(gitRoot, "git", "branch", branchName)
          }
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write(
            """{"success": true, "message": "Branch '$branchName' created", "output": ${
              jsonEscape(
                result
              )
            }}"""
          )
        }

        "switch-branch" -> {
          val branchName = req.getParameter("branchName")
          if (branchName.isNullOrBlank()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
            return
          }
          val result = executeGitCommand(gitRoot, "git", "checkout", branchName)
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write(
            """{"success": true, "message": "Switched to branch '$branchName'", "output": ${
              jsonEscape(
                result
              )
            }}"""
          )
        }

        "delete-branch" -> {
          val branchName = req.getParameter("branchName")
          if (branchName.isNullOrBlank()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
            return
          }
          val force = req.getParameter("force") == "true"
          val flag = if (force) "-D" else "-d"
          val result = executeGitCommand(gitRoot, "git", "branch", flag, branchName)
          resp.contentType = "application/json"
          resp.status = HttpServletResponse.SC_OK
          resp.writer.write(
            """{"success": true, "message": "Branch '$branchName' deleted", "output": ${
              jsonEscape(
                result
              )
            }}"""
          )
        }

        else -> {
          resp.status = HttpServletResponse.SC_BAD_REQUEST
          resp.contentType = "application/json"
          resp.writer.write("""{"success": false, "message": "Unknown git action: $action"}""")
        }
      }
    } catch (e: Exception) {
      log.error("Error executing git operation: $action", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": ${jsonEscape(e.message ?: "Unknown error")}}""")
    }
  }

  fun executeGitCommand(workDir: File, vararg command: String): String {
    log.debug("Executing git command in ${workDir.absolutePath}: ${command.joinToString(" ")}")
    val processBuilder = ProcessBuilder(*command)
      .directory(workDir)
      .redirectErrorStream(true)
    val process = processBuilder.start()
    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      log.warn("Git command exited with code $exitCode: $output")
    }
    return output
  }
}