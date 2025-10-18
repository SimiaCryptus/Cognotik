package com.example.news.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Cache configuration properties.
 * Allows configuring cache TTL globally via application properties.
 */
@Component
@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    /**
     * Time-to-live for cache entries.
     * If null or zero, cache entries never expire (unbounded).
     * Examples: "PT1H" (1 hour), "PT30M" (30 minutes), "PT0S" (unbounded)
     */
    var ttl: Duration? = null,
    
    /**
     * Maximum number of entries per cache.
     * Default is 10000 entries.
     */
    var maximumSize: Long = 10000
)

/**
 * Cache configuration for the News API application.
 * Configures Caffeine cache with globally-configurable TTL.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig(private val cacheProperties: CacheProperties) {

    companion object {
        // Cache names
        const val STORY_CACHE = "stories"
        const val COMMENT_CACHE = "comments"
        const val USER_CACHE = "users"
        const val TOP_STORIES_CACHE = "topStories"
        const val NEW_STORIES_CACHE = "newStories"
        const val STORY_COMMENTS_CACHE = "storyComments"
        const val USER_STORIES_CACHE = "userStories"
        const val USER_COMMENTS_CACHE = "userComments"
        const val SEARCH_CACHE = "searchResults"
    }

    /**
     * Configure the cache manager with Caffeine.
     * Applies global TTL settings to all caches.
     */
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(
            STORY_CACHE,
            COMMENT_CACHE,
            USER_CACHE,
            TOP_STORIES_CACHE,
            NEW_STORIES_CACHE,
            STORY_COMMENTS_CACHE,
            USER_STORIES_CACHE,
            USER_COMMENTS_CACHE,
            SEARCH_CACHE
        )
        
        cacheManager.setCaffeine(caffeineConfig())
        return cacheManager
    }

    /**
     * Build Caffeine cache configuration based on properties.
     */
    private fun caffeineConfig(): Caffeine<Any, Any> {
        val builder = Caffeine.newBuilder()
            .maximumSize(cacheProperties.maximumSize)
            .recordStats()
        
        // Apply TTL if configured (non-null and non-zero)
        val ttl = cacheProperties.ttl
        if (ttl != null && !ttl.isZero && !ttl.isNegative) {
            builder.expireAfterWrite(ttl)
        }
        // If TTL is null or zero, cache is unbounded (no expiration)
        
        return builder
    }
}