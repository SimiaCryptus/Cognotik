package com.simiacryptus.cognotik.text.util

import java.io.InputStream

val String.isBinary: Boolean
  get() {
    val binary = this.toByteArray().filter { it < 0x20 || it > 0x7E }
    return binary.size > this.length / 10
  }
val InputStream.isBinary: Boolean
  get() {
    val binary = this.readBytes().filter { it < 0x20 || it > 0x7E }
    return binary.size > this.available() / 10
  }