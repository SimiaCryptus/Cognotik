package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.APIProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GitHubSearchTask(
    planSettings: PlanSettings,
    planTask: GitHubSearchTaskConfigData?
) : AbstractTask<GitHubSearchTask.GitHubSearchTaskConfigData>(planSettings, planTask) {
    class GitHubSearchTaskConfigData(
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
    ) : TaskConfigBase(
        task_type = TaskType.GitHubSearchTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
GitHubSearchTask - Search GitHub for code, commits, issues, repositories, topics, or users
    * Specify the search query
    * Specify the type of search (code, commits, issues, repositories, topics, users)
    * Specify the number of results to return (max 100)
    * Optionally specify sort order (e.g. stars, forks, updated)
    * Optionally specify sort direction (asc or desc)
    """.trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val searchResults = performGitHubSearch(agent.user?.let {
            ApplicationServices.userSettingsManager.getUserSettings(it)
        }?.apiKeys?.get(APIProvider.Github) ?: throw RuntimeException("GitHub API token is required"))
        // formattedResults is the "actor answer text" that will be passed to the task chooser (PlanCoordinator)
        val actorAnswerText = formatSearchResults(searchResults)
        // Output the actor answer text to the task execution's SessionTask (UI tab)
        val displayText = MarkdownUtil.renderMarkdown(actorAnswerText, ui = agent.ui)
        task.add(displayText)
        // Pass the actor answer text to the result function for the PlanCoordinator
        resultFn(actorAnswerText)
    }

    private fun performGitHubSearch(githubToken: String): String {
        val queryParams = mutableListOf<String>()

        var searchQuery = taskConfig?.search_query
        //if (searchQuery.isNullOrBlank()) throw IllegalArgumentException("GitHub search query is required and cannot be empty.")
        if (searchQuery.isNullOrBlank()) {
            searchQuery = ""
        }
        queryParams.add("q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")

        queryParams.add("per_page=${taskConfig?.per_page}") // perPage is now guaranteed non-null
        taskConfig?.sort?.let { queryParams.add("sort=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        taskConfig?.order?.let { queryParams.add("order=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        URI("https://api.github.com")
                          .resolve("/search/${taskConfig?.search_type}")
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
        val effectiveSearchType = this.taskConfig?.search_type ?: GitHubSearchTaskConfigData().search_type
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
    }
}