package com.example.news.controller

import com.example.news.api.*
import com.example.news.config.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/stories")
class StoryController(private val newsApiService: NewsApiService) {

  @GetMapping("/top")
  @Cacheable(CacheConfig.TOP_STORIES_CACHE, key = "#page + '-' + #pageSize")
  suspend fun getTopStories(
    @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "5") pageSize: Int
  ): PaginatedStoryIds {
    val pagination = PaginationParams(page = page, pageSize = pageSize)
    val topStoryIds = newsApiService.getTopStoryIds(pagination)
    return topStoryIds.copy(
      ids = topStoryIds.ids.take(pageSize),
      totalCount = topStoryIds.totalCount.coerceAtMost(50),
      page = page,
      pageSize = pageSize,
      hasMore = (page * pageSize) < topStoryIds.totalCount.coerceAtMost(50)
    )
  }

  @GetMapping("/new")
  @Cacheable(CacheConfig.NEW_STORIES_CACHE, key = "#page + '-' + #pageSize")
  suspend fun getNewStories(
    @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "5") pageSize: Int
  ): PaginatedStoryIds {
    val pagination = PaginationParams(page = page, pageSize = pageSize)
    val storyIds = newsApiService.getNewStoryIds(pagination)
    return storyIds.copy(
      ids = storyIds.ids.take(pageSize),
      totalCount = storyIds.totalCount.coerceAtMost(50),
      page = page,
      pageSize = pageSize,
      hasMore = (page * pageSize) < storyIds.totalCount.coerceAtMost(50)
    )
  }

  @GetMapping("/{id}")
  @Cacheable(CacheConfig.STORY_CACHE, key = "#id")
  suspend fun getStoryDetails(@PathVariable id: String): Story {
    return newsApiService.getStoryDetails(id)
  }

  @PostMapping("/search")
  @Cacheable(CacheConfig.SEARCH_CACHE, key = "#searchParams.hashCode()")
  suspend fun searchStories(@RequestBody searchParams: StorySearchRequest): PaginatedStoryIds {
    val params = StorySearchParams(
      query = searchParams.query,
      tags = searchParams.tags,
      topics = searchParams.topics,
      location = searchParams.location,
      author = searchParams.author,
      minScore = searchParams.minScore,
      after = searchParams.after,
      before = searchParams.before,
      sortOrder = searchParams.sortOrder ?: StorySortOrder.RECENT,
      pagination = PaginationParams(
        page = searchParams.page ?: 1, pageSize = searchParams.pageSize ?: 5
      )
    )
    return newsApiService.searchStories(params)
  }

  @GetMapping("/{id}/comments")
  @Cacheable(CacheConfig.STORY_COMMENTS_CACHE, key = "#id + '-' + #page + '-' + #pageSize")
  suspend fun getStoryComments(
    @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "5") pageSize: Int
  ): PaginatedCommentIds {
    val pagination = PaginationParams(page = page, pageSize = pageSize)
    val storyComments = newsApiService.getStoryComments(id, pagination)
    return storyComments.copy(
      ids = storyComments.ids.take(pageSize),
      totalCount = storyComments.totalCount.coerceAtMost(50),
      page = page,
      pageSize = pageSize,
      hasMore = (page * pageSize) < storyComments.totalCount.coerceAtMost(50)
    )
  }
}

/**
 * Request body for story search endpoint
 */
data class StorySearchRequest(
  val query: String? = null,
  val tags: List<String> = emptyList(),
  val topics: List<String> = emptyList(),
  val location: String? = null,
  val author: String? = null,
  val minScore: Int? = null,
  val after: Instant? = null,
  val before: Instant? = null,
  val sortOrder: StorySortOrder? = null,
  val page: Int? = null,
  val pageSize: Int? = null
)