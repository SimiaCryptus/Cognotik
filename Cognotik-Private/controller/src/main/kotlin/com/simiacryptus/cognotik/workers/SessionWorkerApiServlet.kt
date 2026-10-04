"restart" -> {
val future = workers.restart(sessionId, user, app)
future.insufficientBudget()?.let {
return ProxySupport.writeJson(
resp, 402, mapOf("error" to it.message, "status" to view(workers.status(sessionId), admin))
)
}
ProxySupport.writeJson(resp, 202, view(workers.status(sessionId), admin))
}