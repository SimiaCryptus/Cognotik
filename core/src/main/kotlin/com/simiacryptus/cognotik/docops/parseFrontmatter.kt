package com.simiacryptus.cognotik.docops

fun parseFrontmatter(text: String): Map<String, Any> {
  val result = mutableMapOf<String, Any>()
  val lines = text.lines()
  var i = 0

  while (i < lines.size) {
    val line = lines[i]
    if (line.contains(":")) {
      val colonIndex = line.indexOf(":")
      val key = line.substring(0, colonIndex).trim()
      val valueAfterColon = line.substring(colonIndex + 1).trim()

      if (valueAfterColon.isEmpty()) {
        val listItems = mutableListOf<String>()
        i++
        while (i < lines.size && lines[i].trimStart().startsWith("- ")) {
          listItems.add(lines[i].trimStart().removePrefix("- ").trim())
          i++
        }
        if (listItems.isNotEmpty()) {
          result[key] = listItems
        }
        continue
      } else {
        result[key] = valueAfterColon
      }
    }
    i++
  }
  return result
}