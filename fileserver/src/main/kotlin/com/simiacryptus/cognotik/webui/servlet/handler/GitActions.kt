package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.action.ActionParam
import com.simiacryptus.cognotik.webui.servlet.action.GitAction
import com.simiacryptus.cognotik.webui.servlet.action.GitActionContext
import java.io.File

/**
 * Registry of the built-in [GitAction]s. Touching this object registers
 * them; downstream code may add or replace entries via
 * `GitAction.register(...)`.
 */
object GitActions {

  private val FILE_PATH = ActionParam(
    "filePath", description = "path relative to the repository root", default = "."
  )
  private val BRANCH_NAME = ActionParam("branchName", required = true, description = "branch name")
  private val BRANCH_PATTERN = Regex("[A-Za-z0-9._/-]+")

  init {
    reg("init", "git init - create a repository in the served directory", mutating = true) { ctx ->
      linkedMapOf("message" to "Git repository initialized", "output" to ctx.git("git", "init"))
    }
    reg("status", "git status --porcelain, plus the current branch name") { ctx ->
      linkedMapOf(
        "branch" to ctx.git("git", "branch", "--show-current").trim(),
        "status" to ctx.git("git", "status", "--porcelain")
      )
    }
    reg("add", "git add <filePath>", listOf(FILE_PATH), mutating = true) { ctx ->
      linkedMapOf(
        "message" to "Files staged",
        "output" to ctx.git("git", "add", ctx.param("filePath", "."))
      )
    }
    reg(
      "commit", "git add -A followed by git commit -m <message>",
      listOf(ActionParam("message", description = "commit message")), mutating = true
    ) { ctx ->
      ctx.git("git", "add", "-A")
      linkedMapOf(
        "message" to "Changes committed",
        "output" to ctx.git("git", "commit", "-m", ctx.param("message", "Commit from web UI"))
      )
    }
    reg("log", "git log --oneline -n <count>", listOf(ActionParam("count", "int", default = 20))) { ctx ->
      linkedMapOf("log" to ctx.git("git", "log", "--oneline", "-n", ctx.param("count", "20")))
    }
    reg("diff", "unstaged and staged diffs") { ctx ->
      linkedMapOf(
        "unstaged" to ctx.git("git", "diff"),
        "staged" to ctx.git("git", "diff", "--cached")
      )
    }
    reg(
      "reset", "discard changes: checkout -- / reset --hard HEAD / clean -fdx",
      listOf(FILE_PATH), mutating = true
    ) { ctx ->
      val filePath = ctx.param("filePath")
      val output = if (filePath != null) listOf(
        ctx.git("git", "checkout", "--", filePath),
        ctx.git("git", "reset", "--hard", "HEAD", filePath),
        ctx.git("git", "clean", "-fdx", "--", filePath)
      ) else listOf(
        ctx.git("git", "checkout", "--", "."),
        ctx.git("git", "reset", "--hard", "HEAD"),
        ctx.git("git", "clean", "-fdx")
      )
      linkedMapOf("message" to "Changes reset", "output" to output.joinToString("\n"))
    }
    reg("branches", "git branch, plus the current branch name") { ctx ->
      linkedMapOf(
        "currentBranch" to ctx.git("git", "branch", "--show-current").trim(),
        "branches" to ctx.git("git", "branch")
      )
    }
    reg(
      "create-branch", "git branch/checkout -b <branchName>",
      listOf(BRANCH_NAME, ActionParam("checkout", "boolean", default = true)), mutating = true
    ) { ctx ->
      val branch = validBranch(ctx.required("branchName"))
      val output = if (ctx.flag("checkout", true)) ctx.git("git", "checkout", "-b", branch)
      else ctx.git("git", "branch", branch)
      linkedMapOf("message" to "Branch '$branch' created", "output" to output)
    }
    reg("switch-branch", "git checkout <branchName>", listOf(BRANCH_NAME), mutating = true) { ctx ->
      val branch = validBranch(ctx.required("branchName"))
      linkedMapOf(
        "message" to "Switched to branch '$branch'",
        "output" to ctx.git("git", "checkout", branch)
      )
    }
    reg(
      "delete-branch", "git branch -d/-D <branchName>",
      listOf(BRANCH_NAME, ActionParam("force", "boolean", default = false)), mutating = true
    ) { ctx ->
      val branch = validBranch(ctx.required("branchName"))
      val flag = if (ctx.flag("force")) "-D" else "-d"
      linkedMapOf(
        "message" to "Branch '$branch' deleted",
        "output" to ctx.git("git", "branch", flag, branch)
      )
    }
    reg("describe", "self-description of every registered git action") {
      linkedMapOf("actions" to describe())
    }
  }

  /** Idempotent no-op whose only purpose is to force registration. */
  fun install() = Unit

  fun describe(): List<Map<String, Any?>> = GitAction.values().map { it.describe() }

  fun names(): List<String> = GitAction.values().map { it.name }.sorted()

  /**
   * Runs [name] against [gitRoot]. Throws [IllegalArgumentException] for an
   * unknown action or a missing/invalid parameter (callers map that to 400).
   */
  fun execute(name: String, params: Map<String, Any?>, gitRoot: File): Map<String, Any?> {
    val action = GitAction.find(name)
      ?: throw IllegalArgumentException("Unknown git action: '$name' (known: ${names().joinToString(", ")})")
    return action.handler(GitActionContext(gitRoot, params))
  }

  private fun reg(
    name: String,
    description: String,
    parameters: List<ActionParam> = emptyList(),
    mutating: Boolean = false,
    handler: (GitActionContext) -> Map<String, Any?>,
  ) = GitAction.register(GitAction(name, description, parameters, mutating, handler))

  /** Blocks option/argument injection through branch names. */
  private fun validBranch(name: String): String {
    require(BRANCH_PATTERN.matches(name) && !name.startsWith("-") && !name.contains("..")) {
      "Invalid branch name: '$name'"
    }
    return name
  }
}