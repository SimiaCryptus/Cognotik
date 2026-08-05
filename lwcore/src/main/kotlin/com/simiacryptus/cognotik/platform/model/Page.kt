package com.simiacryptus.cognotik.platform.model

/**
 * Cursor-based paging request (REVIEW.md §4.3).
 *
 * The default fallbacks in this module treat [cursor] as a decimal offset;
 * backend implementations are free to use opaque cursors instead.
 */
data class Page(
  val limit: Int = 100,
  val cursor: String? = null,
) {
  init {
    require(limit > 0) { "limit must be positive: $limit" }
  }

  /** Offset interpretation of [cursor] used by the in-memory fallbacks. */
  val offset: Int get() = cursor?.toIntOrNull() ?: 0

  companion object {
    val FIRST = Page()
  }
}

/**
 * One page of results.
 *
 * @property items the items in this page
 * @property nextCursor cursor to pass to the next [Page], or null when exhausted
 */
data class PageResult<T>(
  val items: List<T>,
  val nextCursor: String? = null,
) {
  val hasMore: Boolean get() = nextCursor != null
}

/** In-memory paging fallback for interfaces whose default `list*` returns everything. */
fun <T> List<T>.paginate(page: Page): PageResult<T> {
  val from = page.offset.coerceIn(0, size)
  val to = (from + page.limit).coerceAtMost(size)
  return PageResult(
    items = subList(from, to).toList(),
    nextCursor = if (to < size) to.toString() else null
  )
}