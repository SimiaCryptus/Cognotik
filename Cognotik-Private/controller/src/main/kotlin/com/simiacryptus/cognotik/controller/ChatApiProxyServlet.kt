if (model.isNullOrBlank()) throw InvalidRequestException("Missing required 'model' field in chat request")
val resolveFailures = mutableListOf<Exception>()
val reasoningEffort = chatRequest.reasoning_effort
val resolved = resolveChatModel(providers, userSettings, sessionId, model, user, resolveFailures, reasoningEffort)
?: run {
log.warn(