var summaries: Map<Session, Map<String, ModelSchema.Usage>>? = null
var parents: Map<Session, Session?>? = null
// One metadata round trip: `total` and the page are both derived from it.
// (Previously the paged query, countSessions and listSessionPaths each
// re-listed every session of the user.)
var all = meta.querySessions(user, query)
if (rootsOnly) {
val p = usage.getParentSessions(user, all.map { it.id })
parents = p
all = all.filter { p[it.id] == null }
}
if (usageSort || minCost != null) {
val s = usage.getSessionUsageSummaryBulk(user, all.map { it.id })
summaries = s
if (minCost != null) all = all.filter { costOf(s[it.id]) >= minCost }
all = when (sortName) {
"COST_DESC" -> all.sortedByDescending { costOf(s[it.id]) }
"COST_ASC" -> all.sortedBy { costOf(s[it.id]) }
"TOKENS_DESC" -> all.sortedByDescending { tokensOf(s[it.id]) }
"TOKENS_ASC" -> all.sortedBy { tokensOf(s[it.id]) }
else -> all
}
}
val page = all.paginate(Page(limit, cursor))
val items: List<SessionListEntry> = page.items
val nextCursor: String? = page.nextCursor
val total: Int = all.size
val ids = items.map { it.id }