usageHandler: UsageListener
): ModelSchema.ChatResponse {
// Reasoning variants share a modelId; tell the proxy which variant was selected.
val effectiveRequest = model.reasoningLevel
?.takeIf { chatRequest.reasoning_effort.isNullOrBlank() }
?.let { chatRequest.copy(reasoning_effort = it.name) }
?: chatRequest
val token = submitAsyncJob(effectiveRequest, model)
return pollForResult(token, model)
}