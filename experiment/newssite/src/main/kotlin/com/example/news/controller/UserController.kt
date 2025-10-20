package com.example.news.controller

import com.example.news.api.*
import com.example.news.config.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val newsApiService: NewsApiService) {
    
    @GetMapping("/{username}")
    @Cacheable(CacheConfig.USER_CACHE, key = "#username")
    suspend fun getUserDetails(@PathVariable username: String): User {
      val userDetails = newsApiService.getUserDetails(username)
      return userDetails
    }
    
    @GetMapping("/{username}/stories")
    @Cacheable(CacheConfig.USER_STORIES_CACHE, key = "#username + '-' + #page + '-' + #pageSize")
    suspend fun getUserStories(
        @PathVariable username: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "30") pageSize: Int
    ): PaginatedStoryIds {
        val pagination = PaginationParams(page = page, pageSize = pageSize)
        return newsApiService.getUserStories(username, pagination)
    }
    
    @GetMapping("/{username}/comments")
    @Cacheable(CacheConfig.USER_COMMENTS_CACHE, key = "#username + '-' + #page + '-' + #pageSize")
    suspend fun getUserComments(
        @PathVariable username: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "30") pageSize: Int
    ): PaginatedCommentIds {
        val pagination = PaginationParams(page = page, pageSize = pageSize)
        return newsApiService.getUserComments(username, pagination)
    }
}