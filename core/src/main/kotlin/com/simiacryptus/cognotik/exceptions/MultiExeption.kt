package com.simiacryptus.cognotik.exceptions

class MultiExeption(exceptions: Collection<Throwable>) : RuntimeException(
    exceptions.joinToString("\n\n") { "```text\n${(it.stackTraceToString())}\n```" }
)