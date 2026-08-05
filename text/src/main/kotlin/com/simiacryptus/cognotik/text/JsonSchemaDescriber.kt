package com.simiacryptus.cognotik.text

import java.io.File
import java.math.BigInteger
import kotlin.collections.get
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.text.iterator

/**
 * CLI utility for loading JSON (data or JSON Schema) and printing a human-readable
 * description of its structure.
 *
 * Data mode (statistical, recursive):
 *  * schema summary (samples, nodes, depth, distinct shapes/keys)
 *  * recursive statistics (shapes repeating an ancestor are detected and not expanded)
 *  * polymorphic statistics with hierarchical descriptions
 *    * dictionaries
 *      * top-N values w/ per key sparsity statistics
 *      * schema per key (top-N least sparse)
 *    * arrays
 *      * size, sparsity statistics
 *      * item schema
 *    * values
 *      * per-primitive frequency metrics
 *      * top-N most common values
 *
 * Schema mode: walks a JSON Schema document (`$ref`/`$defs` aware, recursion safe)
 * and renders properties, dictionaries, arrays, combinators and constraints.
 *
 * Usage: `JsonSchemaDescriber [options] [file ...]` (stdin when no file is given).
 */
class JsonSchemaDescriber(
  private val config: Config = Config()
) {

  data class Config(
    /** How many "top" values / keys to show per node. */
    val topN: Int = 5,
    /** Maximum nesting depth that is expanded. */
    val maxDepth: Int = 16,
    /** Maximum number of keys/properties described in detail per object. */
    val maxDetailedKeys: Int = 24,
    /** Values longer than this are truncated when reported. */
    val maxValueChars: Int = 48,
    /** Upper bound on tracked distinct scalar values per node. */
    val maxDistinctValues: Int = 512,
    val indent: String = "  ",
    val detectRecursion: Boolean = true,
    val showSummary: Boolean = true,
  )

  enum class JsonType(val label: String) {
    OBJECT("object"), ARRAY("array"), STRING("string"),
    INTEGER("integer"), NUMBER("number"), BOOLEAN("boolean"), NULL("null")
  }

  /** Accumulated statistics for one logical position in the document tree. */
  class Stats {
    var occurrences = 0
    val typeCounts = LinkedHashMap<JsonType, Int>()

    var nullCount = 0
    var trueCount = 0
    var falseCount = 0

    var numericCount = 0
    var numericMin = Double.POSITIVE_INFINITY
    var numericMax = Double.NEGATIVE_INFINITY
    var numericSum = 0.0

    var stringCount = 0
    var stringLenMin = Int.MAX_VALUE
    var stringLenMax = 0
    var stringLenSum = 0L

    var objectCount = 0
    var objectKeysMin = Int.MAX_VALUE
    var objectKeysMax = 0
    var objectKeysSum = 0L

    /** key -> stats of the value at that key (occurrences == presence count). */
    val keys = LinkedHashMap<String, Stats>()

    var arrayCount = 0
    var arrayLenMin = Int.MAX_VALUE
    var arrayLenMax = 0
    var arrayLenSum = 0L
    var emptyArrays = 0
    var items: Stats? = null

    val valueCounts = LinkedHashMap<String, Int>()
    var valueCountsOverflow = false

    var recursiveOmissions = 0
    var depthOmissions = 0

    val scalarCount: Int get() = nullCount + trueCount + falseCount + numericCount + stringCount
    val isPolymorphic: Boolean get() = typeCounts.size > 1

    fun bump(type: JsonType) {
      typeCounts[type] = (typeCounts[type] ?: 0) + 1
    }
  }

  /** Result of a full pass over the samples. */
  class Analysis {
    val root = Stats()
    var samples = 0
    var nodes = 0L
    var maxDepth = 0
    val shapes = HashSet<String>()
    val keyNames = HashSet<String>()
    val recursiveShapes = HashSet<String>()
  }

  /*  ------------------------------------------------------------------ */
  /*  Public API                                                        */
  /*  ------------------------------------------------------------------ */

  /** Auto-detects schema documents vs. data samples. */
  fun describe(text: String, splitTopLevelArray: Boolean = false): String {
    val parsed = MiniJson.parseStream(text)
    val single = parsed.singleOrNull()
    return when {
      single != null && looksLikeSchema(single) -> describeSchemaDocument(single)
      splitTopLevelArray && single is List<*> -> describeData(single.toList())
      else -> describeData(parsed)
    }
  }

  fun describeDataJson(text: String, splitTopLevelArray: Boolean = false): String {
    val parsed = MiniJson.parseStream(text)
    val single = parsed.singleOrNull()
    return if (splitTopLevelArray && single is List<*>) describeData(single.toList())
    else describeData(parsed)
  }

  fun describeSchemaJson(text: String): String = describeSchemaDocument(MiniJson.parse(text))

  fun describeData(samples: List<Any?>): String = render(analyze(samples))

  fun analyze(samples: List<Any?>): Analysis {
    val analysis = Analysis()
    for (sample in samples) {
      analysis.samples++
      accumulate(sample, analysis.root, 0, ArrayList(), analysis)
    }
    return analysis
  }

  /*  ------------------------------------------------------------------ */
  /*  Data mode: accumulation                                           */
  /*  ------------------------------------------------------------------ */

  private fun accumulate(
    value: Any?,
    stats: Stats,
    depth: Int,
    shapePath: MutableList<String>,
    analysis: Analysis
  ) {
    stats.occurrences++
    analysis.nodes++
    analysis.maxDepth = max(analysis.maxDepth, depth)
    when (value) {
      null -> {
        stats.bump(JsonType.NULL)
        stats.nullCount++
        countValue(stats, "null")
      }

      is Boolean -> {
        stats.bump(JsonType.BOOLEAN)
        if (value) stats.trueCount++ else stats.falseCount++
        countValue(stats, value.toString())
      }

      is Number -> {
        val d = value.toDouble()
        val integral = when (value) {
          is Byte, is Short, is Int, is Long, is BigInteger -> true
          else -> d.isFinite() && floor(d) == d
        }
        stats.bump(if (integral) JsonType.INTEGER else JsonType.NUMBER)
        stats.numericCount++
        stats.numericMin = min(stats.numericMin, d)
        stats.numericMax = max(stats.numericMax, d)
        stats.numericSum += d
        countValue(stats, formatNumber(value))
      }

      is CharSequence -> {
        val s = value.toString()
        stats.bump(JsonType.STRING)
        stats.stringCount++
        stats.stringLenMin = min(stats.stringLenMin, s.length)
        stats.stringLenMax = max(stats.stringLenMax, s.length)
        stats.stringLenSum += s.length
        countValue(stats, "\"${escape(truncate(s))}\"")
      }

      is Map<*, *> -> {
        stats.bump(JsonType.OBJECT)
        stats.objectCount++
        val keyNames = value.keys.map { it?.toString() ?: "null" }
        stats.objectKeysMin = min(stats.objectKeysMin, keyNames.size)
        stats.objectKeysMax = max(stats.objectKeysMax, keyNames.size)
        stats.objectKeysSum += keyNames.size
        analysis.keyNames.addAll(keyNames)
        val shape = keyNames.sorted().joinToString(",")
        analysis.shapes.add(shape)
        if (config.detectRecursion && keyNames.isNotEmpty() && shapePath.contains(shape)) {
          stats.recursiveOmissions++
          analysis.recursiveShapes.add(shape)
          return
        }
        if (depth >= config.maxDepth) {
          stats.depthOmissions++
          return
        }
        shapePath.add(shape)
        for ((k, v) in value) {
          val name = k?.toString() ?: "null"
          val child = stats.keys.getOrPut(name) { Stats() }
          accumulate(v, child, depth + 1, shapePath, analysis)
        }
        shapePath.removeAt(shapePath.size - 1)
      }

      is Iterable<*> -> accumulateArray(value.toList(), stats, depth, shapePath, analysis)
      is Array<*> -> accumulateArray(value.toList(), stats, depth, shapePath, analysis)

      else -> {
        // Unknown JVM type: describe its string form.
        val s = value.toString()
        stats.bump(JsonType.STRING)
        stats.stringCount++
        stats.stringLenMin = min(stats.stringLenMin, s.length)
        stats.stringLenMax = max(stats.stringLenMax, s.length)
        stats.stringLenSum += s.length
        countValue(stats, "\"${escape(truncate(s))}\"")
      }
    }
  }

  private fun accumulateArray(
    list: List<Any?>,
    stats: Stats,
    depth: Int,
    shapePath: MutableList<String>,
    analysis: Analysis
  ) {
    stats.bump(JsonType.ARRAY)
    stats.arrayCount++
    stats.arrayLenMin = min(stats.arrayLenMin, list.size)
    stats.arrayLenMax = max(stats.arrayLenMax, list.size)
    stats.arrayLenSum += list.size
    if (list.isEmpty()) stats.emptyArrays++
    if (depth >= config.maxDepth) {
      if (list.isNotEmpty()) stats.depthOmissions++
      return
    }
    if (list.isEmpty()) return
    val items = stats.items ?: Stats().also { stats.items = it }
    for (element in list) accumulate(element, items, depth + 1, shapePath, analysis)
  }

  private fun countValue(stats: Stats, rendered: String) {
    val existing = stats.valueCounts[rendered]
    when {
      existing != null -> stats.valueCounts[rendered] = existing + 1
      stats.valueCounts.size < config.maxDistinctValues -> stats.valueCounts[rendered] = 1
      else -> stats.valueCountsOverflow = true
    }
  }

  /*  ------------------------------------------------------------------ */
  /*  Data mode: rendering                                              */
  /*  ------------------------------------------------------------------ */

  fun render(analysis: Analysis): String {
    val sb = StringBuilder()
    if (config.showSummary) {
      sb.appendLine("== JSON Data Structure Description ==")
      sb.appendLine("samples analyzed  : ${analysis.samples}")
      sb.appendLine("nodes visited     : ${analysis.nodes}")
      sb.appendLine("max nesting depth : ${analysis.maxDepth}")
      sb.appendLine("distinct shapes   : ${analysis.shapes.size}")
      sb.appendLine("distinct key names: ${analysis.keyNames.size}")
      sb.appendLine("recursive shapes  : ${analysis.recursiveShapes.size}")
      sb.appendLine()
    }
    renderStats(sb, "root", analysis.root, analysis.samples, 0)
    return sb.toString()
  }

  private fun renderStats(sb: StringBuilder, label: String, stats: Stats, parentCount: Int, level: Int) {
    val pad = config.indent.repeat(level)
    val child = level + 1
    val cpad = config.indent.repeat(child)

    val presence = when {
      parentCount <= 0 -> ""
      stats.occurrences >= parentCount -> " [always present]"
      else -> " [present ${pct(stats.occurrences, parentCount)}, sparsity ${
        pct(parentCount - stats.occurrences, parentCount)
      }]"
    }
    sb.append(pad).append(label).append(": ").append(typeSummary(stats))
      .append(" (n=${stats.occurrences})").append(presence)
    if (stats.isPolymorphic) sb.append(" [polymorphic]")
    sb.appendLine()

    /* primitives ------------------------------------------------- */
    if (stats.nullCount > 0 && stats.typeCounts.size > 1) {
      sb.appendLine("${cpad}nulls: ${stats.nullCount} (${pct(stats.nullCount, stats.occurrences)})")
    }
    if (stats.trueCount + stats.falseCount > 0) {
      val total = stats.trueCount + stats.falseCount
      sb.appendLine(
        "${cpad}boolean: true ${stats.trueCount} (${pct(stats.trueCount, total)}), " +
            "false ${stats.falseCount} (${pct(stats.falseCount, total)})"
      )
    }
    if (stats.stringCount > 0) {
      sb.appendLine(
        "${cpad}string length: min ${stats.stringLenMin}, " +
            "avg ${fmt(stats.stringLenSum.toDouble() / stats.stringCount)}, max ${stats.stringLenMax}"
      )
    }
    if (stats.numericCount > 0) {
      sb.appendLine(
        "${cpad}numeric range: min ${fmt(stats.numericMin)}, " +
            "avg ${fmt(stats.numericSum / stats.numericCount)}, max ${fmt(stats.numericMax)}"
      )
    }
    if (stats.scalarCount > 0 && stats.valueCounts.isNotEmpty()) {
      sb.appendLine(
        "${cpad}distinct values: ${stats.valueCounts.size}${if (stats.valueCountsOverflow) "+" else ""}"
      )
      val top = stats.valueCounts.entries.sortedByDescending { it.value }.take(config.topN)
      sb.appendLine("${cpad}top values: " + top.joinToString(", ") {
        "${it.key} x${it.value} (${pct(it.value, stats.scalarCount)})"
      })
    }

    /* arrays ----------------------------------------------------- */
    if (stats.arrayCount > 0) {
      val minLen = if (stats.arrayLenMin == Int.MAX_VALUE) 0 else stats.arrayLenMin
      sb.appendLine(
        "${cpad}array length: min $minLen, " +
            "avg ${fmt(stats.arrayLenSum.toDouble() / stats.arrayCount)}, max ${stats.arrayLenMax} " +
            "(items ${stats.arrayLenSum}, empty ${stats.emptyArrays} = ${
              pct(stats.emptyArrays, stats.arrayCount)
            })"
      )
      stats.items?.let { renderStats(sb, "items", it, 0, child) }
    }

    /* objects / dictionaries ------------------------------------- */
    if (stats.objectCount > 0) {
      val minKeys = if (stats.objectKeysMin == Int.MAX_VALUE) 0 else stats.objectKeysMin
      val avgKeys = stats.objectKeysSum.toDouble() / stats.objectCount
      sb.appendLine(
        "${cpad}keys per object: min $minKeys, avg ${fmt(avgKeys)}, max ${stats.objectKeysMax}; " +
            "distinct keys: ${stats.keys.size}"
      )
      if (stats.keys.size.toDouble() > maxOf(4.0, avgKeys * 3)) {
        sb.appendLine(
          "${cpad}dictionary-like: key names carry data " +
              "(${stats.keys.size} distinct keys vs ${fmt(avgKeys)} keys/object)"
        )
      }
      val sorted = stats.keys.entries.sortedWith(
        compareByDescending<Map.Entry<String, Stats>> { it.value.occurrences }.thenBy { it.key }
      )
      // Schema per key, least sparse (most frequently present) first.
      sorted.take(config.maxDetailedKeys).forEach { (key, keyStats) ->
        renderStats(sb, "\"${escape(truncate(key))}\"", keyStats, stats.objectCount, child)
      }
      val rest = sorted.drop(config.maxDetailedKeys)
      if (rest.isNotEmpty()) {
        val listed = rest.take(config.topN * 2).joinToString(", ") {
          "\"${escape(truncate(it.key))}\" (${pct(it.value.occurrences, stats.objectCount)})"
        }
        sb.appendLine(
          "${cpad}… ${rest.size} sparser key(s) omitted: $listed" +
              if (rest.size > config.topN * 2) ", …" else ""
        )
      }
    }

    if (stats.recursiveOmissions > 0) {
      sb.appendLine(
        "${cpad}recursion: shape repeats an ancestor; " +
            "${stats.recursiveOmissions} sub-tree(s) not expanded"
      )
    }
    if (stats.depthOmissions > 0) {
      sb.appendLine("${cpad}depth limit: ${stats.depthOmissions} sub-tree(s) not expanded")
    }
  }

  private fun typeSummary(stats: Stats): String {
    if (stats.typeCounts.isEmpty()) return "unknown"
    val entries = stats.typeCounts.entries.sortedByDescending { it.value }
    return if (entries.size == 1) entries.first().key.label
    else entries.joinToString(" | ") { "${it.key.label} ${pct(it.value, stats.occurrences)}" }
  }

  /*  ------------------------------------------------------------------ */
  /*  Schema mode                                                       */
  /*  ------------------------------------------------------------------ */

  fun looksLikeSchema(node: Any?): Boolean {
    val map = node as? Map<*, *> ?: return false
    if (map.containsKey("\$schema") || map.containsKey("\$defs") || map.containsKey("definitions")) return true
    val schemaTypes = setOf("object", "array", "string", "number", "integer", "boolean", "null")
    val typed = (map["type"] as? String) in schemaTypes || map["type"] is List<*>
    val markers = listOf(
      "properties", "required", "items", "prefixItems", "additionalProperties",
      "patternProperties", "allOf", "anyOf", "oneOf"
    )
    return typed && markers.any { map.containsKey(it) }
  }

  fun describeSchemaDocument(root: Any?): String {
    val sb = StringBuilder()
    sb.appendLine("== JSON Schema Description ==")
    (root as? Map<*, *>)?.let { map ->
      map["\$schema"]?.let { sb.appendLine("dialect: $it") }
      map["\$id"]?.let { sb.appendLine("id     : $it") }
      map["title"]?.let { sb.appendLine("title  : $it") }
      map["description"]?.let { sb.appendLine("summary: ${oneLine(it.toString())}") }
    }
    sb.appendLine()
    renderSchema(sb, "root", root, root, 0, ArrayList())
    return sb.toString()
  }

  private fun renderSchema(
    sb: StringBuilder,
    label: String,
    node: Any?,
    root: Any?,
    level: Int,
    refStack: MutableList<String>
  ) {
    val pad = config.indent.repeat(level)
    if (level > config.maxDepth) {
      sb.appendLine("$pad$label: … (depth limit)")
      return
    }
    if (node == null) {
      sb.appendLine("$pad$label: any")
      return
    }
    if (node is Boolean) {
      sb.appendLine("$pad$label: ${if (node) "any" else "never"}")
      return
    }
    val map = node as? Map<*, *> ?: run {
      sb.appendLine("$pad$label: <not a schema: ${node.javaClass.simpleName}>")
      return
    }

    val ref = map["\$ref"] as? String
    if (ref != null) {
      if (refStack.contains(ref)) {
        sb.appendLine("$pad$label: -> $ref (recursive)")
        return
      }
      val target = resolvePointer(ref, root)
      if (target == null) {
        sb.appendLine("$pad$label: -> $ref (unresolved)")
        return
      }
      sb.appendLine("$pad$label: -> $ref")
      refStack.add(ref)
      renderSchema(sb, shortRef(ref), target, root, level + 1, refStack)
      refStack.removeAt(refStack.size - 1)
      return
    }

    val types = schemaTypes(map)
    val annotations = schemaAnnotations(map)
    sb.append(pad).append(label).append(": ")
      .append(if (types.isEmpty()) "any" else types.joinToString(" | "))
    if (annotations.isNotEmpty()) sb.append("  {").append(annotations.joinToString(", ")).append("}")
    sb.appendLine()

    val child = level + 1
    val cpad = config.indent.repeat(child)

    (map["description"] as? String)?.let { sb.appendLine("$cpad# ${oneLine(it)}") }
    (map["enum"] as? List<*>)?.let { values ->
      val shown = values.take(config.topN * 2).joinToString(", ") { renderLiteral(it) }
      sb.appendLine(
        "${cpad}enum (${values.size}): $shown" +
            if (values.size > config.topN * 2) ", …" else ""
      )
    }
    if (map.containsKey("const")) sb.appendLine("${cpad}const: ${renderLiteral(map["const"])}")

    for (keyword in listOf("allOf", "anyOf", "oneOf")) {
      val variants = map[keyword] as? List<*> ?: continue
      sb.appendLine("$cpad$keyword — polymorphic, ${variants.size} variant(s):")
      variants.forEachIndexed { i, variant ->
        renderSchema(sb, "variant ${i + 1}", variant, root, child + 1, refStack)
      }
    }
    map["not"]?.let { renderSchema(sb, "not", it, root, child, refStack) }

    val properties = map["properties"] as? Map<*, *>
    val required = (map["required"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
    if (properties != null) {
      sb.appendLine("${cpad}properties (${properties.size}, required ${required.size}):")
      properties.entries.take(config.maxDetailedKeys).forEach { (rawKey, value) ->
        val name = rawKey?.toString() ?: "null"
        val suffix = if (name in required) " (required)" else " (optional)"
        renderSchema(sb, "\"${escape(name)}\"$suffix", value, root, child + 1, refStack)
      }
      if (properties.size > config.maxDetailedKeys) {
        sb.appendLine(
          "${config.indent.repeat(child + 1)}… " +
              "${properties.size - config.maxDetailedKeys} more property(ies)"
        )
      }
    }
    (map["patternProperties"] as? Map<*, *>)?.forEach { (pattern, value) ->
      renderSchema(sb, "pattern /${pattern}/", value, root, child, refStack)
    }
    if (map.containsKey("additionalProperties")) {
      val additional = map["additionalProperties"]
      if (additional == false) sb.appendLine("${cpad}additionalProperties: not allowed")
      else renderSchema(sb, "additionalProperties (dictionary values)", additional, root, child, refStack)
    }
    (map["prefixItems"] as? List<*>)?.forEachIndexed { i, value ->
      renderSchema(sb, "item[$i]", value, root, child, refStack)
    }
    map["items"]?.let { items ->
      if (items is List<*>) items.forEachIndexed { i, value ->
        renderSchema(sb, "item[$i]", value, root, child, refStack)
      } else renderSchema(sb, "items", items, root, child, refStack)
    }

    if (level == 0) {
      for (keyword in listOf("\$defs", "definitions")) {
        val defs = map[keyword] as? Map<*, *> ?: continue
        sb.appendLine("$cpad$keyword (${defs.size}):")
        defs.forEach { (name, value) ->
          val pointer = "#/$keyword/$name"
          if (refStack.contains(pointer)) return@forEach
          refStack.add(pointer)
          renderSchema(sb, name?.toString() ?: "null", value, root, child + 1, refStack)
          refStack.removeAt(refStack.size - 1)
        }
      }
    }
  }

  private fun schemaTypes(map: Map<*, *>): List<String> = when (val type = map["type"]) {
    null -> ArrayList<String>().apply {
      if (map.keys.any { it in setOf("properties", "required", "additionalProperties", "patternProperties") })
        add("object*")
      if (map.containsKey("items") || map.containsKey("prefixItems")) add("array*")
      if (map.containsKey("enum")) add("enum")
      if (map.containsKey("const")) add("const")
    }

    is List<*> -> type.map { it?.toString() ?: "null" }
    else -> listOf(type.toString())
  }

  private fun schemaAnnotations(map: Map<*, *>): List<String> {
    val keywords = listOf(
      "format", "pattern", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
      "multipleOf", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems",
      "minProperties", "maxProperties", "default", "readOnly", "writeOnly", "deprecated"
    )
    val out = ArrayList<String>()
    for (keyword in keywords) if (map.containsKey(keyword)) out.add("$keyword=${renderLiteral(map[keyword])}")
    (map["examples"] as? List<*>)?.let { out.add("examples=${it.size}") }
    return out
  }

  private fun resolvePointer(ref: String, root: Any?): Any? {
    if (!ref.startsWith("#")) return null
    val path = ref.removePrefix("#").trim('/')
    if (path.isEmpty()) return root
    var current: Any? = root
    for (rawSegment in path.split('/')) {
      val segment = rawSegment.replace("~1", "/").replace("~0", "~")
      current = when (current) {
        is Map<*, *> -> current[segment]
        is List<*> -> segment.toIntOrNull()?.let { current.getOrNull(it) }
        else -> null
      }
      if (current == null) return null
    }
    return current
  }

  private fun shortRef(ref: String) = ref.substringAfterLast('/').ifEmpty { ref }

  /*  ------------------------------------------------------------------ */
  /*  Formatting helpers                                                */
  /*  ------------------------------------------------------------------ */

  private fun pct(count: Int, total: Int): String =
    if (total <= 0) "0%" else fmt(100.0 * count / total) + "%"

  private fun fmt(value: Double): String = when {
    !value.isFinite() -> "n/a"
    value == floor(value) && abs(value) < 1e15 -> value.toLong().toString()
    else -> String.format("%.2f", value)
  }

  private fun formatNumber(value: Number): String = when (value) {
    is Double -> if (value.isFinite() && floor(value) == value && abs(value) < 1e15)
      value.toLong().toString() else value.toString()

    is Float -> formatNumber(value.toDouble())
    else -> value.toString()
  }

  private fun renderLiteral(value: Any?): String = when (value) {
    null -> "null"
    is CharSequence -> "\"${escape(truncate(value.toString()))}\""
    is Number -> formatNumber(value)
    is Boolean -> value.toString()
    is Map<*, *> -> "{…${value.size} key(s)}"
    is List<*> -> "[…${value.size} item(s)]"
    else -> truncate(value.toString())
  }

  private fun truncate(text: String): String =
    if (text.length <= config.maxValueChars) text
    else text.take(config.maxValueChars) + "…(${text.length})"

  private fun escape(text: String): String = buildString {
    for (c in text) when (c) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> append(c)
    }
  }

  private fun oneLine(text: String): String = truncate(text.lines().joinToString(" ") { it.trim() }.trim())

  /*  ------------------------------------------------------------------ */
  /*  CLI                                                               */
  /*  ------------------------------------------------------------------ */

  companion object {

    private val USAGE = """
                JsonSchemaDescriber — describe the structure of JSON data or a JSON Schema.

                Usage:
                  JsonSchemaDescriber [options] [file ...]      (reads stdin when no file is given)

                Options:
                  --schema           force JSON Schema mode
                  --data             force data (statistics) mode
                  --samples          treat a top-level array as a list of samples
                  --top-n=N          number of top values/keys to report (default 5)
                  --max-depth=N      maximum nesting depth expanded (default 16)
                  --max-keys=N       maximum keys described per object (default 24)
                  --max-value=N      truncate reported values to N chars (default 48)
                  --no-summary       omit the header summary block
                  --no-recursion     do not collapse recursive shapes
                  -h, --help         print this help
            """.trimIndent()

    @JvmStatic
    fun main(args: Array<String>) {
      var config = Config()
      var mode = "auto"
      var splitSamples = false
      val files = ArrayList<String>()

      for (arg in args) {
        when {
          arg == "-h" || arg == "--help" -> {
            println(USAGE); return
          }

          arg == "--schema" -> mode = "schema"
          arg == "--data" -> mode = "data"
          arg == "--samples" -> splitSamples = true
          arg == "--no-summary" -> config = config.copy(showSummary = false)
          arg == "--no-recursion" -> config = config.copy(detectRecursion = false)
          arg.startsWith("--top-n=") -> config = config.copy(topN = intArg(arg))
          arg.startsWith("--max-depth=") -> config = config.copy(maxDepth = intArg(arg))
          arg.startsWith("--max-keys=") -> config = config.copy(maxDetailedKeys = intArg(arg))
          arg.startsWith("--max-value=") -> config = config.copy(maxValueChars = intArg(arg))
          arg.startsWith("-") -> {
            System.err.println("Unknown option: $arg")
            System.err.println(USAGE)
            return
          }

          else -> files.add(arg)
        }
      }

      val describer = JsonSchemaDescriber(config)
      val text = if (files.isEmpty()) {
        System.`in`.readBytes().decodeToString()
      } else {
        files.joinToString("\n") { path ->
          val file = File(path)
          require(file.isFile) { "Not a file: $path" }
          file.readText()
        }
      }

      try {
        val output = when (mode) {
          "schema" -> describer.describeSchemaJson(text)
          "data" -> describer.describeDataJson(text, splitSamples)
          else -> describer.describe(text, splitSamples)
        }
        print(output)
      } catch (e: Exception) {
        System.err.println("Failed to describe input: ${e.message}")
        throw e
      }
    }

    private fun intArg(arg: String): Int =
      arg.substringAfter('=').trim().toIntOrNull()
        ?: throw IllegalArgumentException("Expected an integer in '$arg'")
  }
}