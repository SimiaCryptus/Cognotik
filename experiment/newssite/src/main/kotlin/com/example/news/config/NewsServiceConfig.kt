package com.example.news.config

import com.example.news.api.NewsApiService
import com.example.news.api.NewsServiceLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NewsServiceConfig {
    
    @Bean
    fun newsApiService(): NewsApiService {
        return NewsServiceLoader.createNewsService()
    }
}