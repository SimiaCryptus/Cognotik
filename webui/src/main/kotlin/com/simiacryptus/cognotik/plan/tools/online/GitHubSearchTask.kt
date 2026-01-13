package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.util.*

class GitHubSearchTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GitHubSearchTaskExecutionConfigData?
) : AbstractTask<GitHubSearchTask.GitHubSearchTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    class GitHubSearchTaskExecutionConfigData(
        @Description("The search query to use for GitHub search")
        val search_query: String = "",
        @Description("The type of GitHub search to perform (code, commits, issues, repositories, topics, users)")
        val search_type: String = "repositories",
        @Description("The number of results to return (max 100)")
        val per_page: Int = 30,
        @Description("Sort order for results")
        val sort: String? = null,
        @Description("Sort direction (asc or desc)")
        val order: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = GitHubSearch.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (search_query.isBlank()) {
                return "GitHub search query cannot be blank"
            }

            val validSearchTypes = setOf("code", "commits", "issues", "repositories", "topics", "users")
            if (search_type !in validSearchTypes) {
                return "Invalid search_type: $search_type. Must be one of: ${validSearchTypes.joinToString(", ")}"
            }

            if (per_page < 1 || per_page > 100) {
                return "per_page must be between 1 and 100, got: $per_page"
            }

            order?.let { if (it !in setOf("asc", "desc")) return "Invalid order: $it. Must be 'asc' or 'desc'" }

            return null
        }
    }

    override fun promptSegment() = """
 GitHubSearch - Search GitHub for code, commits, issues, repositories, topics, or users
    * Specify the search query
    * Specify the type of search (code, commits, issues, repositories, topics, users)
    * Specify the number of results to return (max 100)
    * Optionally specify sort order (e.g. stars, forks, updated)
    * Optionally specify sort direction (asc or desc)
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        executionConfig?.validate()?.let { error ->
            task.error(ValidatedObject.ValidationError(error, executionConfig!!))
            return
        }

        task.header("GitHub Search: ${executionConfig?.search_query}")

        val transcriptFile = "full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val transcriptStream = file?.outputStream()

        val configDesc = buildString {
            appendLine("- **Query**: ${executionConfig?.search_query}")
            appendLine("- **Search Type**: ${executionConfig?.search_type}")
            appendLine("- **Results Per Page**: ${executionConfig?.per_page}")
            executionConfig?.sort?.let { appendLine("- **Sort**: $it") }
            executionConfig?.order?.let { appendLine("- **Order**: $it") }
        }

        task.expandable("Search Configuration", MarkdownUtil.renderMarkdown(configDesc, ui = task.ui))
        transcriptStream?.write("# GitHub Search Task\n\n## Configuration\n\n$configDesc\n\n## Search Results\n\n".toByteArray())

        try {
            val searchResults = performGitHubSearch(
                agent.user
                  .let { ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(it) }
                  .apis.firstOrNull { it.provider == APIProvider.Github }?.key?.decrypt?.trim()
                    ?: throw RuntimeException("GitHub API token is required")
            )
            val actorAnswerText = formatSearchResults(searchResults)
            transcriptStream?.write(actorAnswerText.toByteArray())

            task.add(MarkdownUtil.renderMarkdown(actorAnswerText, ui = task.ui))

            val transcriptLinks =
                "Transcript: <a href='$link' target='_blank'>Markdown</a> | <a href='${link.removeSuffix(".md")}.html' target='_blank'>HTML</a> | <a href='${
                    link.removeSuffix(".md")
                }.pdf' target='_blank'>PDF</a>"
            task.add(transcriptLinks)

            resultFn(actorAnswerText)
            task.complete()
        } catch (e: Exception) {
            task.error(e)
            transcriptStream?.write("\n\nError: ${e.message}\n".toByteArray())
        } finally {
            transcriptStream?.close()
        }

    }


    private fun performGitHubSearch(githubToken: String): String {
        val queryParams = mutableListOf<String>()

        var searchQuery = executionConfig?.search_query
        if (searchQuery.isNullOrBlank()) {
            throw IllegalArgumentException("GitHub search query is required and cannot be empty.")
        }
        queryParams.add("q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")

        queryParams.add("per_page=${executionConfig?.per_page ?: 30}")
        executionConfig?.sort?.let { queryParams.add("sort=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        executionConfig?.order?.let { queryParams.add("order=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        URI("https://api.github.com")
                            .resolve("/search/${executionConfig?.search_type}")
                            .toURL().toString() + "?" + queryParams.joinToString("&")
                    )
                )
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer ${githubToken}")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString()
        ).apply {
            if (statusCode() != 200) {
                throw RuntimeException("GitHub API request failed with status ${statusCode()}: ${body()}")
            }
        }.body()
    }

    private fun formatSearchResults(results: String): String {
        val mapper = ObjectMapper()
        val searchResults: Map<String, Any> = mapper.readValue(results)
        val effectiveSearchType = this.executionConfig?.search_type ?: GitHubSearchTaskExecutionConfigData().search_type
        return buildString {
            appendLine("# GitHub Search Results")
            appendLine()
            appendLine("Total results: ${searchResults["total_count"]}")
            appendLine()
            appendLine("## Top Results:")
            appendLine()
            val items = searchResults["items"] as List<Map<String, Any>>
            items.take(minOf(10, items.size)).forEach { item -> // Ensure we don't go over items.size
                when (effectiveSearchType) { // Use the resolved effectiveSearchType
                    "repositories" -> formatRepositoryResult(item)
                    "code" -> formatCodeResult(item)
                    "commits" -> formatCommitResult(item)
                    "issues" -> formatIssueResult(item)
                    "users" -> formatUserResult(item)
                    "topics" -> formatTopicResult(item)
                    else -> appendLine("- ${item["name"] ?: item["title"] ?: item["login"]}")
                }
                appendLine()
            }
        }
    }

    private fun StringBuilder.formatTopicResult(topic: Map<String, Any>) {
        appendLine("### [${topic["name"]}](${topic["url"]})")
        appendLine("${topic["short_description"]}")
        appendLine("Featured: ${topic["featured"]} | Curated: ${topic["curated"]}")
    }

    private fun StringBuilder.formatRepositoryResult(repo: Map<String, Any>) {
        appendLine("### ${repo["full_name"]}")
        appendLine("${repo["description"]}")
        appendLine("Stars: ${repo["stargazers_count"]} | Forks: ${repo["forks_count"]}")
        appendLine("[View on GitHub](${repo["html_url"]})")
    }

    private fun StringBuilder.formatCodeResult(code: Map<String, Any>) {
        val repo = code["repository"] as Map<String, Any>
        appendLine("### [${repo["full_name"]}](${code["html_url"]})")
        appendLine("File: ${code["path"]}")
        appendLine("```")
        appendLine(code["text_matches"]?.toString()?.take(200) ?: "")
        appendLine("```")
    }

    private fun StringBuilder.formatCommitResult(commit: Map<String, Any>) {
        val repo = commit["repository"] as Map<String, Any>
        appendLine("### [${repo["full_name"]}](${commit["html_url"]})")
        appendLine("${(commit["commit"] as Map<String, Any>)["message"]}")
        appendLine("Author: ${(commit["author"] as Map<String, Any>)["login"]} | Date: ${((commit["commit"] as Map<String, Any>)["author"] as Map<String, Any>)["date"]}")
    }

    private fun StringBuilder.formatIssueResult(issue: Map<String, Any>) {
        appendLine("### [${issue["title"]}](${issue["html_url"]})")
        appendLine("State: ${issue["state"]} | Comments: ${issue["comments"]}")
        appendLine("Created by ${(issue["user"] as Map<String, Any>)["login"]} on ${issue["created_at"]}")
    }

    private fun StringBuilder.formatUserResult(user: Map<String, Any>) {
        appendLine("### [${user["login"]}](${user["html_url"]})")
        appendLine("Type: ${user["type"]} | Repos: ${user["public_repos"]}")
        appendLine("![Avatar](${user["avatar_url"]})")
    }

    companion object {
        val GitHubSearch = TaskType(
            "GitHubSearch",
            "Online & Search",
            GitHubSearchTask::class.java,
            GitHubSearchTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Search GitHub repositories, code, issues and users",
            """
          Performs comprehensive searches across GitHub's content.
          <ul>
            <li>Searches repositories, code, and issues</li>
            <li>Supports advanced search queries</li>
            <li>Filters results by various criteria</li>
            <li>Formats results with relevant details</li>
            <li>Handles API rate limiting</li>
          </ul>
        """,
        )

    }
}