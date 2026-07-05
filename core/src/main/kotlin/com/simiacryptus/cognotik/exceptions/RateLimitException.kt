package com.simiacryptus.cognotik.exceptions

class RateLimitException(
  org: String?,
  limit: Int,
  val delay: Long
) : AIServiceException("Rate limit exceeded: $org, limit: $limit, delay: $delay")