package com.simiacryptus.cognotik.core.actors

abstract class ParsedResponse<T>(val clazz: Class<T>) {
  abstract val text: String
  abstract val obj: T
  override fun toString() = text
  open fun <V> map(cls: Class<V>, fn: (T) -> V): ParsedResponse<V> = MappedResponse(cls, fn, this)
}

class MappedResponse<F, T>(
  clazz: Class<T>,
  private val fn: (F) -> T,
  private val inner: ParsedResponse<F>
) : ParsedResponse<T>(clazz) {
  override val text: String
    get() = inner.text
  override val obj: T
    get() = fn(inner.obj)

  override fun <V> map(cls: Class<V>, fn: (T) -> V): ParsedResponse<V> {
    return MappedResponse(cls, fn, this)
  }
}