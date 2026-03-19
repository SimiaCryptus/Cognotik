package com.simiacryptus.cognotik.exceptions

import com.simiacryptus.cognotik.util.LoggerFactory

class QuotaException : AIServiceException("Quota exceeded", isFatal = true) {
  companion object {
    private val log = LoggerFactory.getLogger("QuotaLogger")
  }

  init {
    log.warn("QuotaException initialized: Quota exceeded")
  }
}