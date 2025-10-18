package com.example.news.controller

import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for cache management operations.
 * Provides endpoints to view cache statistics and clear caches.
 */
@RestController
@RequestMapping("/api/cache")
class CacheController(private val cacheManager: CacheManager) {

    /**
     * Get statistics for all caches.
     */
    @GetMapping("/stats")
    fun getCacheStats(): Map<String, CacheStats> {
        val stats = mutableMapOf<String, CacheStats>()
        
        cacheManager.cacheNames.forEach { cacheName ->
            val cache = cacheManager.getCache(cacheName)
            if (cache is CaffeineCache) {
                val nativeCache = cache.nativeCache
                val cacheStats = nativeCache.stats()
                
                stats[cacheName] = CacheStats(
                    hitCount = cacheStats.hitCount(),
                    missCount = cacheStats.missCount(),
                    loadSuccessCount = cacheStats.loadSuccessCount(),
                    loadFailureCount = cacheStats.loadFailureCount(),
                    evictionCount = cacheStats.evictionCount(),
                    estimatedSize = nativeCache.estimatedSize(),
                    hitRate = cacheStats.hitRate(),
                    missRate = cacheStats.missRate()
                )
            }
        }
        
        return stats
    }

    /**
     * Clear all caches.
     */
    @DeleteMapping
    fun clearAllCaches(): ResponseEntity<Map<String, String>> {
        cacheManager.cacheNames.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
        return ResponseEntity.ok(mapOf("message" to "All caches cleared"))
    }

    /**
     * Clear a specific cache by name.
     */
    @DeleteMapping("/{cacheName}")
    fun clearCache(@PathVariable cacheName: String): ResponseEntity<Map<String, String>> {
        val cache = cacheManager.getCache(cacheName)
        return if (cache != null) {
            cache.clear()
            ResponseEntity.ok(mapOf("message" to "Cache '$cacheName' cleared"))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Get list of all cache names.
     */
    @GetMapping("/names")
    fun getCacheNames(): List<String> {
        return cacheManager.cacheNames.toList()
    }
}

/**
 * Cache statistics data class.
 */
data class CacheStats(
    val hitCount: Long,
    val missCount: Long,
    val loadSuccessCount: Long,
    val loadFailureCount: Long,
    val evictionCount: Long,
    val estimatedSize: Long,
    val hitRate: Double,
    val missRate: Double
)