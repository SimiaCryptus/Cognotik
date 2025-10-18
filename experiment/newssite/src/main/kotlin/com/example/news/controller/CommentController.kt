package com.example.news.controller

import com.example.news.api.Comment
import com.example.news.api.NewsApiService
import com.example.news.config.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/comments")
class CommentController(private val newsApiService: NewsApiService) {
    
    @GetMapping("/{id}")
    @Cacheable(CacheConfig.COMMENT_CACHE, key = "#id")
    suspend fun getCommentDetails(@PathVariable id: String): Comment {
        return newsApiService.getCommentDetails(id)
    }
}