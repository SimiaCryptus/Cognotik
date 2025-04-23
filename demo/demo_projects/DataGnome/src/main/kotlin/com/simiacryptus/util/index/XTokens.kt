package com.simiacryptus.util.index

@JvmInline
value class XTokens(val asLong: Long) : Comparable<XTokens> {
    val asInt get() = asLong.toInt()
    override operator fun compareTo(other: XTokens) = asLong.compareTo(other.asLong)
    operator fun plus(other: XTokens) = XTokens(asLong + other.asLong)
    operator fun minus(other: XTokens) = XTokens(asLong - other.asLong)
    operator fun plus(other: Long) = XTokens(asLong + other)
    operator fun minus(other: Long) = XTokens(asLong - other)
    operator fun plus(other: Int) = XTokens(asLong + other)
    operator fun minus(other: Int) = XTokens(asLong - other)
    operator fun rem(other: XTokens) = XTokens(asLong % other.asLong)
    operator fun rem(other: Long) = XTokens(asLong % other)
}

infix fun XTokens.until(to: XTokens): Iterable<XTokens> = this.asLong.until(to.asLong).map { XTokens(it) }

val Int.tokens: XTokens get() = XTokens(this.toLong())
val Long.tokens: XTokens get() = XTokens(this)