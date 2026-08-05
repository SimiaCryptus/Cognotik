package com.simiacryptus.cognotik.platform.model

/**
 * Typed event-bus key, replacing the `publish(topic: String, data: Any?)` +
 * blind-cast pattern flagged in REVIEW.md §3.8.
 *
 * @property name the wire topic name (compatible with the untyped String API)
 * @property payloadType runtime type used to validate/narrow payloads
 */
data class Topic<T : Any>(
  val name: String,
  val payloadType: Class<T>,
) {
  /** Narrows an untyped payload, returning null when the shape does not match. */
  fun cast(raw: Any?): T? = if (payloadType.isInstance(raw)) payloadType.cast(raw) else null

  override fun toString() = "$name<${payloadType.simpleName}>"

  companion object {
    inline fun <reified T : Any> of(name: String): Topic<T> = Topic(name, T::class.java)
  }
}