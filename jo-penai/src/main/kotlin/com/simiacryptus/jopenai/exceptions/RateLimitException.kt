package com.simiacryptus.jopenai.exceptions

class RateLimitException(
    private val org: String?,
    private val limit: Int,
    val delay: Long
) : AIServiceException("Rate limit exceeded: $org, limit: $limit, delay: $delay")