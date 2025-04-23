package com.simiacryptus.cognotik.core.util

class MultiExeption(exceptions: Collection<Throwable>) : RuntimeException(
  exceptions.joinToString("\n\n") { "```text\n${/*escapeHtml4*/(it.stackTraceToString())/*.indent("  ")*/}\n```" }
)
