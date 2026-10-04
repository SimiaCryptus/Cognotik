val statusUrl = "$statusApiBase/sessions/$sessionId"
val info = workers.status(sessionId).copy(host = null, port = null)
val budgetFailure = future.insufficientBudget()
when {
budgetFailure != null && !ProxySupport.isHtmlNavigation(request) ->
ProxySupport.writeJson(
response, 402,
mapOf("error" to budgetFailure.message, "statusUrl" to statusUrl, "status" to info)
)
ProxySupport.isHtmlNavigation(request) -> {
response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE