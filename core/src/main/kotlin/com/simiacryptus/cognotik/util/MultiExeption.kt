package com.simiacryptus.cognotik.util

class MultiExeption(exceptions: Collection<Throwable>) : RuntimeException(
    exceptions.joinToString("\n\n") { "```text\n${(it.stackTraceToString())}\n```" }
)
