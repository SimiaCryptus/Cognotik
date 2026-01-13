package com.simiacryptus.cognotik.exceptions

import com.simiacryptus.cognotik.util.LoggerFactory

class InvalidModelException(model: String?) : AIServiceException("Invalid model: $model", isFatal = true) {
    companion object {
        private val log = LoggerFactory.getLogger("InvalidModelLogger")
    }

    init {
        if (model.isNullOrEmpty()) {
            log.warn("InvalidModelException thrown with no model specified")
        } else {
            log.error("InvalidModelException thrown for model: $model")
        }
    }
}