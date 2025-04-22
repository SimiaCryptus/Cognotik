package com.simiacryptus.jopenai.exceptions
import java.io.IOException
import org.slf4j.LoggerFactory
class RequestOverloadException(
    message: String = "That model is currently overloaded with other requests."
) : IOException(message) {
    companion object {
        private val log = LoggerFactory.getLogger(RequestOverloadException::class.java)
    }
    init {
        log.debug("RequestOverloadException initialized with message: $message")
    }
}