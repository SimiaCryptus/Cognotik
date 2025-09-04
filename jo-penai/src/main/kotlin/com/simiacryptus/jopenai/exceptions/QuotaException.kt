package com.simiacryptus.jopenai.exceptions

import com.simiacryptus.util.LoggerFactory

class QuotaException : AIServiceException("Quota exceeded", isFatal = true) {
    companion object {
        private val log = LoggerFactory.getLogger("QuotaLogger")
    }

    init {
        log.warn("QuotaException initialized: Quota exceeded")
    }
}