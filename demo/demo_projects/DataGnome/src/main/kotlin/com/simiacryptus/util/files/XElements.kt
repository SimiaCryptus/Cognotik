package com.simiacryptus.util.files

@JvmInline
value class XElements(val asLong: Long) : Comparable<XElements> {
    val asInt get() = asLong.toInt()
    override operator fun compareTo(other: XElements) = asLong.compareTo(other.asLong)
    operator fun compareTo(other: Long) = asLong.compareTo(other)
    operator fun compareTo(other: Int) = asLong.compareTo(other)

    operator fun plus(other: XElements) = XElements(asLong + other.asLong)
    operator fun minus(other: XElements) = XElements(asLong - other.asLong)
    operator fun plus(other: Long) = XElements(asLong + other)
    operator fun minus(other: Long) = XElements(asLong - other)
    operator fun plus(other: Int) = XElements(asLong + other)
    operator fun minus(other: Int) = XElements(asLong - other)

    operator fun rem(other: XElements) = XElements(asLong % other.asLong)
    operator fun rem(other: Long) = XElements(asLong % other)
    operator fun rem(other: Int) = XElements(asLong % other)
    fun toInt(): Int {
        return asLong.toInt()
    }
}

infix fun XElements.until(to: XElements): Iterable<XElements> = this.asLong.until(to.asLong).map { XElements(it) }

val Int.elements: XElements get() = XElements(this.toLong())
val Long.elements: XElements get() = XElements(this)