package com.simiacryptus.util.files

import java.nio.ByteBuffer

fun ByteArray.toInt(): Int {
    return ByteBuffer.wrap(this).int
}