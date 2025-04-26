package com.simiacryptus.jopenai.exceptions

class ModelMaxException(
    private val modelMax: Int,
    val request: Int,
    val messages: Int,
    private val completion: Int
) : AIServiceException(
    message = "Model max exceeded: $modelMax, request: $request, messages: $messages, completion: $completion",
    isFatal = true
)