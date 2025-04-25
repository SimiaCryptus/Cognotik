package com.simiacryptus.cognotik.util

object StringSplitter {
  fun split(text: String, seperators: Map<String, Double>): Pair<String, String> {
    val splitAt = seperators.entries.map { (sep, weight) ->
      val splitPoint = (0 until (text.length - sep.length)).filter { i ->
        text.substring(i, i + sep.length) == sep
      }.map { i ->
        val a = i.toDouble() / text.length
        val b = 1.0 - a
        i to b * Math.log(a) + a * Math.log(b)
      }.maxByOrNull { it.second }
      if (null == splitPoint) null
      else sep to ((splitPoint.first + sep.length) to splitPoint.second / weight)
    }.filterNotNull().maxByOrNull { it.second.second }?.second?.first ?: (text.length / 2)
    return text.substring(0, splitAt) to text.substring(splitAt)
  }

  @JvmStatic
  fun main(args: Array<String>) {
    println(
      split(
        text = "This is a test. This is only a test. If this were a real emergency, you would be instructed to panic.",
        seperators = mapOf(
          "." to 2.0,
          " " to 1.0,
          ", " to 2.0,
        )
      ).toList().joinToString("\n")
    )
  }
}