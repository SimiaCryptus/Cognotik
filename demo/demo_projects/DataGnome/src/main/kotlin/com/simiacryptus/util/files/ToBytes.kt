package com.simiacryptus.util.files

import java.nio.ByteBuffer

@Suppress("unused")
fun Int.toBytes(): ByteArray {
    val byteArray = ByteArray(4)
    ByteBuffer.wrap(byteArray).putInt(this)
    return byteArray
}