package com.example.news.api

import com.simiacryptus.cognotik.describe.Description
import java.time.Instant


@Description("Represents a story, which can be a link to an external article or a self-post.")
data class Story(
  @Description("The unique identifier for the story.") val id: String = "",
  @Description("The title of the story.") val title: String = "",
  @Description("The URL the story links to. Can be null for self-posts.") val url: String? = null,
  @Description("The text content of the story, for self-posts.") val text: String? = null,
  @Description("The username of the person who submitted the story.") val author: String = "",
  @Description("The story's score, typically the number of upvotes.") val score: Int = 0,
  @Description("The timestamp of when the story was created.") val timestamp: Instant = Instant.EPOCH,
  @Description("A list of unique IDs for the top-level comments on this story.") val commentIds: List<String> = emptyList(),
  @Description("The total number of comments made on the story.") val commentCount: Int = 0,
  @Description("A list of tags associated with the story.") val tags: List<String> = emptyList(),
  @Description("A list of topics the story belongs to.") val topics: List<String> = emptyList(),
  @Description("The geographical location relevant to the story, if any.") val location: String? = null
)

@Description("Represents a comment made on a story or another comment.")
data class Comment(
  @Description("The unique identifier for the comment.") val id: String = "",
  @Description("The username of the commenter.") val author: String = "",
  @Description("The HTML content of the comment.") val text: String = "",
  @Description("The timestamp of when the comment was created.") val timestamp: Instant = Instant.EPOCH,
  @Description("The ID of the parent item (either a story or another comment).") val parentId: String = "",
  @Description("A list of unique IDs for direct replies to this comment.") val childCommentIds: List<String> = emptyList(),
  @Description("A list of tags associated with the comment.") val tags: List<String> = emptyList(),
  @Description("A list of topics the comment is about.") val topics: List<String> = emptyList(),
  @Description("The geographical location relevant to the comment, if any.") val location: String? = null
)

@Description("Represents a user of the news platform.")
data class User(
  @Description("The user's unique, case-sensitive username.") val id: String = "",
  @Description("The timestamp of when the user's account was created.") val created: Instant = Instant.EPOCH,
  @Description("The user's karma score.") val karma: Int = 0,
  @Description("The user's profile description (bio).") val about: String? = null,
  @Description("A list of unique IDs for items (stories and comments) submitted by the user.") val submittedIds: List<String> = emptyList(),
  @Description("The user's geographical location.") val location: String? = null,
  @Description("A list of topics the user is interested in.") val topics: List<String> = emptyList(),
  @Description("A list of tags representing the user's interests or activities.") val tags: List<String> = emptyList()
)

@Description("Pagination parameters for API requests.")
data class PaginationParams(
  @Description("The page number (1-indexed).") val page: Int = 1, @Description("The number of items per page.") val pageSize: Int = 30
)

@Description("Sorting options for stories.")
enum class StorySortOrder {
  @Description("Sort by score (highest first)")
  SCORE,

  @Description("Sort by timestamp (newest first)")
  RECENT,

  @Description("Sort by comment count (most commented first)")
  COMMENTS
}

@Description("A paginated list of story IDs.")
data class PaginatedStoryIds(
  @Description("A list of unique IDs for stories, e.g. `seattle-mt-rainier-erupts` or `trump-invokes-insurrection-act`.") val ids: List<String> = emptyList(),
  @Description("The current page number.") val page: Int,
  @Description("The number of items per page.") val pageSize: Int = 30,
  @Description("The total number of stories available.") val totalCount: Int = 0,
  @Description("Whether there are more pages available.") val hasMore: Boolean
)

@Description("Search parameters for stories.")
data class StorySearchParams(
  @Description("Search query string.") val query: String? = null,
  @Description("Filter by tags.") val tags: List<String> = emptyList(),
  @Description("Filter by topics.") val topics: List<String> = emptyList(),
  @Description("Filter by location.") val location: String? = null,
  @Description("Filter by author.") val author: String? = null,
  @Description("Minimum score threshold.") val minScore: Int? = null,
  @Description("Stories created after this timestamp.") val after: Instant? = null,
  @Description("Stories created before this timestamp.") val before: Instant? = null,
  @Description("Sort order for results.") val sortOrder: StorySortOrder = StorySortOrder.RECENT,
  @Description("Pagination parameters.") val pagination: PaginationParams = PaginationParams()
)


@Description("Defines the API endpoints for the news service.")
interface NewsApiService {

  @Description("Fetches a paginated list of IDs for the current top stories.")
  fun getTopStoryIds(pagination: PaginationParams = PaginationParams()): PaginatedStoryIds

  @Description("Fetches a paginated list of IDs for the most recent stories.")
  fun getNewStoryIds(pagination: PaginationParams = PaginationParams()): PaginatedStoryIds

  @Description("Fetches the full details for a given story.")
  fun getStoryDetails(id: String): Story

  @Description("Fetches the full details for a given comment.")
  fun getCommentDetails(id: String): Comment

  @Description("Fetches the profile details for a given user.")
  fun getUserDetails(username: String): User

  @Description("Searches for stories based on various criteria.")
  fun searchStories(params: StorySearchParams): PaginatedStoryIds

  @Description("Fetches comments for a given story with pagination.")
  fun getStoryComments(
    storyId: String, pagination: PaginationParams = PaginationParams()
  ): PaginatedCommentIds

  @Description("Fetches stories submitted by a specific user.")
  fun getUserStories(
    username: String, pagination: PaginationParams = PaginationParams()
  ): PaginatedStoryIds

  @Description("Fetches comments made by a specific user.")
  fun getUserComments(
    username: String, pagination: PaginationParams = PaginationParams()
  ): PaginatedCommentIds
}

@Description("A paginated list of comment IDs.")
data class PaginatedCommentIds(
  @Description("A list of unique IDs for comments.") val ids: List<String> = emptyList(),
  @Description("The current page number.") val page: Int,
  @Description("The number of items per page.") val pageSize: Int = 30,
  @Description("The total number of comments available.") val totalCount: Int = 0,
  @Description("Whether there are more pages available.") val hasMore: Boolean
)