package com.simiacryptus.util.files

@JvmInline
value class XBytes(val asLong: Long) : Comparable<XBytes> {
    val bytesAsInt get() = asLong.toInt()
    override operator fun compareTo(other: XBytes) = asLong.compareTo(other.asLong)
    operator fun compareTo(other: Long) = asLong.compareTo(other)
    operator fun compareTo(other: Int) = asLong.compareTo(other)
    operator fun plus(other: XBytes) = XBytes(asLong + other.asLong)
    operator fun minus(other: XBytes) = XBytes(asLong - other.asLong)
    operator fun plus(other: Long) = XBytes(asLong + other)
    operator fun minus(other: Long) = XBytes(asLong - other)

    operator fun plus(other: Int) = XBytes(asLong + other)
    operator fun minus(other: Int) = XBytes(asLong - other)
    operator fun rem(other: XBytes) = XBytes(asLong % other.asLong)
    operator fun rem(other: Long) = XBytes(asLong % other)

}

infix fun XBytes.until(to: XBytes): Iterable<XBytes> = this.asLong.until(to.asLong).map { XBytes(it) }

val Int.bytes: XBytes get() = XBytes(this.toLong())
val Long.bytes: XBytes get() = XBytes(this)