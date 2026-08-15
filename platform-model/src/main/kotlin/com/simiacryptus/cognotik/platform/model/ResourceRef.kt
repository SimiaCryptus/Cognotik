package com.simiacryptus.cognotik.platform.model

/**
 * Stable, serializable reference to an authorization subject.
 *
 * Replaces `Class<*>` as the authorization key (REVIEW.md §3.5) and adds the
 * instance scope required to express "user X may Delete *session S*".
 */
sealed interface ResourceRef {

  /**
   * The JVM class this reference was bridged from, when available.
   * Present only to support the deprecated `Class<*>`-keyed authorization API.
   */
  val applicationClass: Class<*>? get() = null

  /** An application, identified by a stable string id. */
  data class App(
    val id: String,
    override val applicationClass: Class<*>? = null
  ) : ResourceRef

  /** A specific session. */
  data class SessionRef(
    val id: Session,
    override val applicationClass: Class<*>? = null
  ) : ResourceRef

  /** A specific gift. */
  data class GiftRef(val id: GiftId) : ResourceRef

  /** Escape hatch for resource kinds not yet modelled. */
  data class Named(val type: String, val id: String) : ResourceRef

  companion object {
    /**
     * Bridge from the legacy `Class<*>` authorization key.
     *
     * @return an [App] reference, or null if [applicationClass] is null
     */
    fun of(applicationClass: Class<*>?): ResourceRef? =
      applicationClass?.let { App(it.name, it) }
  }
}