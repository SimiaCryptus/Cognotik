package com.simiacryptus.cognotik.exceptions

class ModelMaxException(
    modelMax: Int,
    val request: Int,
    val messages: Int,
    completion: Int
) : AIServiceException(
    message = "Model max exceeded: $modelMax, request: $request, messages: $messages, completion: $completion",
    isFatal = true
)