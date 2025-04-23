package com.simiacryptus.util.index

@JvmInline
value class XChars(val asLong: Long) : Comparable<XChars> {
    val asInt get() = asLong.toInt()
    override operator fun compareTo(other: XChars) = asLong.compareTo(other.asLong)
    operator fun compareTo(other: Long) = asLong.compareTo(other)
    operator fun compareTo(other: Int) = asLong.compareTo(other)
    operator fun plus(other: XChars) = XChars(asLong + other.asLong)
    operator fun minus(other: XChars) = XChars(asLong - other.asLong)
    operator fun plus(other: Long) = XChars(asLong + other)
    operator fun plus(other: Int) = XChars(asLong + other)
    operator fun minus(other: Long) = XChars(asLong - other)
    operator fun minus(other: Int) = XChars(asLong - other)
    operator fun rem(other: XChars) = XChars(asLong % other.asLong)
    operator fun rem(other: Long) = XChars(asLong % other)
    operator fun rem(other: Int) = XChars(asLong % other)
}

infix fun XChars.until(to: XChars): Iterable<XChars> = this.asLong.until(to.asLong).map { XChars(it) }

val Int.chars: XChars get() = XChars(this.toLong())
val Long.chars: XChars get() = XChars(this)