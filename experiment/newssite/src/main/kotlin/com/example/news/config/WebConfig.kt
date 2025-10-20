package com.example.news.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web configuration for the News API application.
 * Configures CORS settings to allow frontend applications to access the API.
 * Configures static resource serving from the /static/ directory.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    /**
     * Configure CORS mappings for all API endpoints.
     * This allows frontend applications running on different origins to access the API.
     *
     * Default configuration:
     * - Allows all origins (can be restricted in production)
     * - Allows common HTTP methods (GET, POST, PUT, DELETE, OPTIONS)
     * - Allows all headers
     * - Allows credentials (cookies, authorization headers)
     * - Sets max age for preflight requests to 1 hour
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*") // In production, specify exact origins like "https://yourdomain.com"
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600) // Cache preflight response for 1 hour
    }
    /**
     * Configure static resource handlers.
     * Serves static resources from classpath:/static/ for all non-API requests.
     */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(3600)
    }
}