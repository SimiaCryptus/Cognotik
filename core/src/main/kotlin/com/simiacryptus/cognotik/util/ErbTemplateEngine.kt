package com.simiacryptus.cognotik.util

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script

class ErbTemplateEngine {
  private val preamblePattern = Regex("""^---\s*\n<%#\s*\n(.*?)\n%>\s*\n---\s*\n?""", RegexOption.DOT_MATCHES_ALL)
  private val typeDeclarationPattern = Regex("""@type\s+(\w+)\s*=\s*\{(.*)\};?\s*$""", RegexOption.DOT_MATCHES_ALL)

  private val outputPattern = Regex("""<%=\s*(.+?)\s*%>""")
  private val blockPattern = Regex("""<%\s*(.*?)\s*%>""", RegexOption.DOT_MATCHES_ALL)
  private val functionPattern =
    Regex("""<%\s*def\s+(\w+)\s*\((.*?)\)\s*%>(.*?)<%\s*enddef\s*%>""", RegexOption.DOT_MATCHES_ALL)

  private val filters = mutableMapOf<String, (Any?, List<String>) -> String>(
    "escape" to { v, _ -> escapeLatex(v?.toString() ?: "") },
    "markdown" to { v, _ -> markdownToLatex(v?.toString() ?: "") },
    "upper" to { v, _ -> v?.toString()?.uppercase() ?: "" },
    "lower" to { v, _ -> v?.toString()?.lowercase() ?: "" },
    "join" to { v, args ->
      when (v) {
        is List<*> -> v.joinToString(args.firstOrNull() ?: ", ")
        is JsonArray -> v.map {
          when (it) {
            is JsonPrimitive -> it.asString
            else -> it.toString()
          }
        }.joinToString(args.firstOrNull() ?: ", ")

        else -> ""
      }
    },
    "default" to { v, args ->
      v?.toString()?.ifEmpty { args.firstOrNull() } ?: args.firstOrNull() ?: ""
    }
  )
  var strictValidation: Boolean = false
  private val groovyShell = GroovyShell()
  private val definedFunctions = mutableMapOf<String, GroovyFunction>()
  private var currentData: JsonObject? = null

  data class GroovyFunction(
    val name: String,
    val parameters: List<String>,
    val body: String,
    val script: Script
  )


  fun registerFilter(name: String, filter: (Any?, List<String>) -> String) {
    filters[name] = filter
  }

  fun render(template: String, data: JsonObject): String {
    log.debug("Starting template render with data keys: {}", data.keySet())
    val (schema, templateBody) = extractPreamble(template)
    if (schema != null) {
      log.debug("Found schema with fields: {}", schema.fields.keys)
      validateData(data, schema)
    }
    // Clear previously defined functions for this render
    definedFunctions.clear()
    log.trace("Cleared previously defined functions")
    // Store current data for use in Groovy functions
    currentData = data

    // First pass: extract and compile function definitions
    val templateWithoutFunctions = extractFunctions(templateBody)
    log.debug("Extracted {} function definitions", definedFunctions.size)

    val result = processBlocks(templateWithoutFunctions, Context(data))
    log.debug("Template render completed, output length: {}", result.length)
    // Clear current data after rendering
    currentData = null
    
    return result
  }

  private fun extractFunctions(template: String): String {
    var result = template
    var match = functionPattern.find(result)
    while (match != null) {
      val functionName = match.groupValues[1]
      val paramsStr = match.groupValues[2]
      val body = match.groupValues[3]
      log.debug("Extracting function '{}' with parameters: {}", functionName, paramsStr)
      val parameters = if (paramsStr.isBlank()) {
        emptyList()
      } else {
        paramsStr.split(",").map { it.trim() }
      }
      // Create Groovy script for the function
      val groovyScript = buildGroovyFunction(functionName, parameters, body)
      log.trace("Generated Groovy script for function '{}': {}", functionName, groovyScript.take(100))
      val script = groovyShell.parse(groovyScript)
      definedFunctions[functionName] = GroovyFunction(functionName, parameters, body, script)
      // Also register as a filter for easy use with pipe syntax
      registerFunctionAsFilter(functionName, parameters)
      log.debug("Registered function '{}' as filter", functionName)
      // Remove the function definition from template
      result = result.removeRange(match.range)
      match = functionPattern.find(result)
    }
    return result
  }

  private fun buildGroovyFunction(name: String, parameters: List<String>, body: String): String {
    val paramList = parameters.joinToString(", ")
    return """
      def $name($paramList) {
        $body
      }
      return this
    """.trimIndent()
  }

  private fun registerFunctionAsFilter(functionName: String, parameters: List<String>) {
    filters[functionName] = { value, args ->
      callFunction(functionName, value, args)
    }
  }

  fun callFunction(functionName: String, firstArg: Any?, additionalArgs: List<String> = emptyList()): String {
    log.debug("Calling function '{}' with firstArg: {}, additionalArgs: {}", functionName, firstArg, additionalArgs)
    val function = definedFunctions[functionName]
      ?: run {
        log.error("Function '{}' is not defined. Available functions: {}", functionName, definedFunctions.keys)
        throw IllegalArgumentException("Function '$functionName' is not defined")
      }
    val binding = Binding()
    // Bind the global data as 'data' variable
    currentData?.let { data ->
      binding.setVariable("data", convertToGroovyValue(data))
      log.trace("Bound global data to 'data' variable")
    }
    
    // Bind the first argument to the first parameter (if any)
    if (function.parameters.isNotEmpty()) {
      binding.setVariable(function.parameters[0], convertToGroovyValue(firstArg))
      log.trace("Bound first parameter '{}' to value: {}", function.parameters[0], firstArg)
    }
    // Bind additional arguments to remaining parameters
    for (i in additionalArgs.indices) {
      if (i + 1 < function.parameters.size) {
        binding.setVariable(function.parameters[i + 1], additionalArgs[i])
        log.trace("Bound parameter '{}' to value: {}", function.parameters[i + 1], additionalArgs[i])
      }
    }
    function.script.binding = binding
    val scriptInstance = function.script.run()
    // Call the function using Groovy's invokeMethod
    val allArgs = mutableListOf<Any?>()
    if (function.parameters.isNotEmpty()) {
      allArgs.add(convertToGroovyValue(firstArg))
    }
    additionalArgs.forEach { allArgs.add(it) }
    val result = if (allArgs.isEmpty()) {
      (scriptInstance as Script).invokeMethod(functionName, null)
    } else {
      (scriptInstance as Script).invokeMethod(functionName, allArgs.toTypedArray())
    }
    log.debug("Function '{}' returned: {}", functionName, result?.toString()?.take(100))
    return result?.toString() ?: ""
  }

  private fun convertToGroovyValue(value: Any?): Any? {
    return when (value) {
      is JsonPrimitive -> when {
        value.isBoolean -> value.asBoolean
        value.isNumber -> value.asNumber
        value.isString -> value.asString
        else -> value.toString()
      }

      is JsonArray -> value.map { convertToGroovyValue(it) }
      is JsonObject -> value.entrySet().associate { it.key to convertToGroovyValue(it.value) }
      is JsonNull -> null
      else -> value
    }
  }

  fun extractSchema(template: String): TypeSchema? {
    val (schema, _) = extractPreamble(template)
    return schema
  }

  private fun extractPreamble(template: String): Pair<TypeSchema?, String> {
    log.trace("Extracting preamble from template of length: {}", template.length)
    val match = preamblePattern.find(template)
    return if (match != null) {
      val preambleContent = match.groupValues[1]
      log.debug("Found preamble content of length: {}", preambleContent.length)
      val schema = parseTypeSchema(preambleContent)
      val templateBody = template.substring(match.range.last + 1)
      log.debug("Parsed schema with {} fields, template body length: {}", schema.fields.size, templateBody.length)
      schema to templateBody
    } else {
      log.trace("No preamble found in template")
      null to template
    }
  }

  private fun parseTypeSchema(preamble: String): TypeSchema {
    val fields = mutableMapOf<String, FieldType>()


    val trimmedPreamble = preamble.trim()
    log.trace("Parsing type schema from preamble: {}", trimmedPreamble.take(200))

    // Try to match @type declaration format
    val typeMatch = typeDeclarationPattern.find(trimmedPreamble)
    if (typeMatch != null) {
      val typeBody = typeMatch.groupValues[2].trim()
      log.debug("Found @type declaration, parsing body of length: {}", typeBody.length)
      return TypeSchema(parseObjectFields(typeBody))
    }

    // Fallback: parse as direct field declarations (legacy format)
    log.debug("Using legacy format for schema parsing")
    return TypeSchema(parseObjectFields(trimmedPreamble))
  }

  private fun parseObjectFields(content: String): Map<String, FieldType> {
    val fields = mutableMapOf<String, FieldType>()
    if (content.isBlank()) return fields

    var pos = 0
    val trimmed = content.trim()

    while (pos < trimmed.length) {
      // Skip whitespace and comments
      while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
      if (pos >= trimmed.length) break

      // Skip comments
      if (trimmed.substring(pos).startsWith("//")) {
        pos = trimmed.indexOf('\n', pos).let { if (it == -1) trimmed.length else it + 1 }
        continue
      }
      // Skip multi-line comments
      if (trimmed.substring(pos).startsWith("/*")) {
        val endComment = trimmed.indexOf("*/", pos)
        pos = if (endComment == -1) trimmed.length else endComment + 2
        continue
      }

      // Parse field name
      val fieldStart = pos
      while (pos < trimmed.length && (trimmed[pos].isLetterOrDigit() || trimmed[pos] == '_')) pos++
      if (pos == fieldStart) break

      val fieldName = trimmed.substring(fieldStart, pos)

      // Check for optional marker
      while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
      val optional = if (pos < trimmed.length && trimmed[pos] == '?') {
        pos++
        true
      } else false

      // Expect colon
      while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
      if (pos >= trimmed.length || trimmed[pos] != ':') break
      pos++

      // Skip whitespace after colon
      while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++

      // Parse type
      val (fieldType, newPos) = parseTypeAtPosition(trimmed, pos, optional)
      pos = newPos

      fields[fieldName] = fieldType

      // Skip semicolon if present
      while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
      if (pos < trimmed.length && trimmed[pos] == ';') pos++
    }

    return fields
  }

  private fun parseTypeAtPosition(content: String, startPos: Int, optional: Boolean): Pair<FieldType, Int> {
    var pos = startPos

    // Skip whitespace
    while (pos < content.length && content[pos].isWhitespace()) pos++

    return when {
      // Object type
      content[pos] == '{' -> {
        val (objectContent, endPos) = extractBracedContent(content, pos, '{', '}')
        val nestedFields = parseObjectFields(objectContent)
        FieldType.ObjectType(nestedFields, optional) to endPos
      }
      // Array type with Array<T> syntax
      content.substring(pos).startsWith("Array<") -> {
        pos += 6 // Skip "Array<"
        val (elementType, afterElement) = parseTypeAtPosition(content, pos, false)
        pos = afterElement
        while (pos < content.length && content[pos].isWhitespace()) pos++
        if (pos < content.length && content[pos] == '>') pos++
        // Check for [] suffix
        while (pos < content.length && content[pos].isWhitespace()) pos++
        if (pos + 1 < content.length && content.substring(pos, pos + 2) == "[]") {
          pos += 2
        }
        FieldType.ArrayType(elementType, optional) to pos
      }
      // Simple type or type with [] suffix
      else -> {
        val typeStart = pos
        while (pos < content.length && (content[pos].isLetterOrDigit() || content[pos] == '_')) pos++
        val typeName = content.substring(typeStart, pos)

        // Check for [] suffix (array shorthand)
        while (pos < content.length && content[pos].isWhitespace()) pos++
        if (pos + 1 < content.length && content.substring(pos, pos + 2) == "[]") {
          pos += 2
          val elementType = parseSimpleType(typeName, false)
          FieldType.ArrayType(elementType, optional) to pos
        } else if (pos < content.length && content[pos] == '|') {
          // Union type
          val types = mutableListOf(typeName)
          while (pos < content.length && content[pos] == '|') {
            pos++ // Skip '|'
            while (pos < content.length && content[pos].isWhitespace()) pos++
            val unionTypeStart = pos
            while (pos < content.length && (content[pos].isLetterOrDigit() || content[pos] == '_')) pos++
            types.add(content.substring(unionTypeStart, pos))
            while (pos < content.length && content[pos].isWhitespace()) pos++
          }
          FieldType.UnionType(types, optional) to pos
        } else {
          parseSimpleType(typeName, optional) to pos
        }
      }
    }
  }

  private fun parseSimpleType(typeName: String, optional: Boolean): FieldType {
    return when (typeName) {
      "string" -> FieldType.StringType(optional)
      "number" -> FieldType.NumberType(optional)
      "boolean" -> FieldType.BooleanType(optional)
      "any" -> FieldType.AnyType(optional)
      else -> FieldType.CustomType(typeName, optional)
    }
  }

  private fun extractBracedContent(
    content: String,
    startPos: Int,
    openBrace: Char,
    closeBrace: Char
  ): Pair<String, Int> {
    var pos = startPos
    if (pos >= content.length || content[pos] != openBrace) return "" to pos

    pos++ // Skip opening brace
    val contentStart = pos
    var depth = 1

    while (pos < content.length && depth > 0) {
      when (content[pos]) {
        openBrace -> depth++
        closeBrace -> depth--
      }
      if (depth > 0) pos++
    }

    val extractedContent = content.substring(contentStart, pos)
    if (pos < content.length && content[pos] == closeBrace) pos++ // Skip closing brace

    return extractedContent to pos
  }

  private fun validateData(data: JsonObject, schema: TypeSchema): List<ValidationError> {
    log.debug("Validating data against schema with {} fields", schema.fields.size)
    val errors = mutableListOf<ValidationError>()

    for ((fieldName, fieldType) in schema.fields) {
      val value = data[fieldName]
      val fieldErrors = validateField(fieldName, value, fieldType)
      if (fieldErrors.isNotEmpty()) {
        log.warn("Validation errors for field '{}': {}", fieldName, fieldErrors)
      }
      errors.addAll(fieldErrors)
    }

    if (strictValidation && errors.isNotEmpty()) {
      log.error("Strict validation failed with {} errors", errors.size)
      throw TemplateValidationException(errors)
    }
    log.debug("Validation completed with {} total errors", errors.size)

    return errors
  }

  private fun validateField(path: String, value: Any?, fieldType: FieldType): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    log.trace(
      "Validating field '{}' of type {} with value: {}",
      path,
      fieldType::class.simpleName,
      value?.toString()?.take(50)
    )

    if (value == null || value is JsonNull) {
      if (!fieldType.optional) {
        log.debug("Required field '{}' is missing", path)
        errors.add(ValidationError(path, "Required field is missing"))
      }
      return errors
    }

    when (fieldType) {
      is FieldType.StringType -> {
        if (value !is JsonPrimitive || !value.isString) {
          log.debug("Field '{}' expected string, got {}", path, value::class.simpleName)
          errors.add(ValidationError(path, "Expected string, got ${value::class.simpleName}"))
        }
      }

      is FieldType.NumberType -> {
        if (value !is JsonPrimitive || !value.isNumber) {
          log.debug("Field '{}' expected number, got {}", path, value::class.simpleName)
          errors.add(ValidationError(path, "Expected number, got ${value::class.simpleName}"))
        }
      }

      is FieldType.BooleanType -> {
        if (value !is JsonPrimitive || !value.isBoolean) {
          log.debug("Field '{}' expected boolean, got {}", path, value::class.simpleName)
          errors.add(ValidationError(path, "Expected boolean, got ${value::class.simpleName}"))
        }
      }

      is FieldType.ArrayType -> {
        if (value !is JsonArray) {
          log.debug("Field '{}' expected array, got {}", path, value::class.simpleName)
          errors.add(ValidationError(path, "Expected array, got ${value::class.simpleName}"))
        } else {
          log.trace("Validating array field '{}' with {} elements", path, value.size())
          value.forEachIndexed { index, element ->
            errors.addAll(validateField("$path[$index]", element, fieldType.elementType))
          }
        }
      }

      is FieldType.ObjectType -> {
        if (value !is JsonObject) {
          log.debug("Field '{}' expected object, got {}", path, value::class.simpleName)
          errors.add(ValidationError(path, "Expected object, got ${value::class.simpleName}"))
        } else {
          log.trace("Validating object field '{}' with {} nested fields", path, fieldType.fields.size)
          for ((nestedName, nestedType) in fieldType.fields) {
            errors.addAll(validateField("$path.$nestedName", value[nestedName], nestedType))
          }
        }
      }

      is FieldType.UnionType -> {
        // For union types, we just check if it's a valid JSON value
        // More sophisticated validation could check against each type in the union
        log.trace("Field '{}' is union type, skipping detailed validation", path)
      }

      is FieldType.AnyType -> {
        // Any type accepts all values
        log.trace("Field '{}' is any type, accepting value", path)
      }

      is FieldType.CustomType -> {
        // Custom types are not validated by default
        log.trace("Field '{}' is custom type '{}', skipping validation", path, fieldType.typeName)
      }
    }

    return errors
  }

  private fun processBlocks(template: String, context: Context): String {
    log.trace("Processing blocks in template of length: {}", template.length)
    val result = StringBuilder()
    var pos = 0

    while (pos < template.length) {
      val outputMatch = outputPattern.find(template, pos)
      val blockMatch = blockPattern.find(template, pos)

      // Find the earliest match
      val nextMatch = listOfNotNull(outputMatch, blockMatch)
        .minByOrNull { it.range.first }

      if (nextMatch == null || nextMatch.range.first > pos) {
        // Append literal text up to next match (or end)
        val endPos = nextMatch?.range?.first ?: template.length
        result.append(template.substring(pos, endPos))
        pos = endPos
      }

      if (nextMatch == null) break

      when {
        nextMatch == outputMatch -> {
          // <%= expression %>
          val expr = nextMatch.groupValues[1]
          log.trace("Processing output expression: {}", expr)
          result.append(evaluateExpression(expr, context))
          pos = nextMatch.range.last + 1
        }

        else -> {
          // <% control structure %>
          val directive = nextMatch.groupValues[1].trim()
          log.trace("Processing directive: {}", directive.take(50))
          pos = nextMatch.range.last + 1
          pos = processDirective(directive, template, pos, context, result)
        }
      }
    }
    log.trace("Block processing completed, result length: {}", result.length)

    return result.toString()
  }

  private fun processDirective(
    directive: String,
    template: String,
    startPos: Int,
    context: Context,
    result: StringBuilder
  ): Int {
    log.debug("Processing directive: {}", directive.take(100))
    return when {
      directive.startsWith("for ") -> {
        log.debug("Processing 'for' loop directive")
        processFor(directive, template, startPos, context, result)
      }

      directive.startsWith("if ") -> {
        log.debug("Processing 'if' conditional directive")
        processIf(directive, template, startPos, context, result)
      }

      else -> {
        log.warn("Unknown directive encountered: {}", directive.take(50))
        startPos
      }
    }
  }

  private fun processFor(
    directive: String,
    template: String,
    startPos: Int,
    context: Context,
    result: StringBuilder
  ): Int {
    // Parse: for item in collection
    val match = Regex("""for\s+(\w+)\s+in\s+(.+)""").find(directive)
      ?: run {
        log.error("Failed to parse 'for' directive: {}", directive)
        return startPos
      }

    val varName = match.groupValues[1]
    val collectionExpr = match.groupValues[2].trim()
    log.debug("For loop: variable='{}', collection='{}'", varName, collectionExpr)

    // Find matching <% end %> or <% endfor %>
    val (body, endPos) = extractBlock(template, startPos, "for")
    log.trace("Extracted for loop body of length: {}", body.length)

    // Get collection
    val collection = resolveValue(collectionExpr, context)
    log.debug("Resolved collection type: {}", collection?.let { it::class.simpleName } ?: "null")

    when (collection) {
      is JsonArray -> {
        log.debug("Iterating over JsonArray with {} elements", collection.size())
        collection.forEachIndexed { index, item ->
          val loopContext = context.child(
            mapOf(
              varName to item,
              "loop" to JsonObject().apply {
                addProperty("index", index)
                addProperty("first", index == 0)
                addProperty("last", index == collection.size() - 1)
              }
            ))
          log.trace("Processing for loop iteration {} of {}", index + 1, collection.size())
          result.append(processBlocks(body, loopContext))
        }
      }

      is JsonObject -> {
        log.debug("Iterating over JsonObject with {} entries", collection.size())
        collection.entrySet().forEachIndexed { index, (key, value) ->
          val loopContext = context.child(
            mapOf(
              varName to JsonObject().apply {
                addProperty("key", key)
                add("value", value)
              },
              "loop" to JsonObject().apply {
                addProperty("index", index)
                addProperty("first", index == 0)
                addProperty("last", index == collection.size() - 1)
              }
            ))
          log.trace("Processing for loop iteration {} of {} (key='{}')", index + 1, collection.size(), key)
          result.append(processBlocks(body, loopContext))
        }
      }

      null -> log.warn("Collection '{}' resolved to null, skipping for loop", collectionExpr)
      else -> log.warn(
        "Collection '{}' is not iterable (type: {}), skipping for loop",
        collectionExpr,
        collection::class.simpleName
      )
    }
    return endPos
  }

  private fun processIf(
    directive: String,
    template: String,
    startPos: Int,
    context: Context,
    result: StringBuilder
  ): Int {
    val condition = directive.removePrefix("if").trim()
    log.debug("Processing if condition: {}", condition)
    val (fullBlock, endPos) = extractBlock(template, startPos, "if")

    // Find matching else at depth 0
    val (thenBody, elseBody) = splitOnElse(fullBlock)
    log.trace("If block: then body length={}, else body length={}", thenBody.length, elseBody.length)

    val conditionResult = evaluateCondition(condition, context)
    log.debug("Condition '{}' evaluated to: {}", condition, conditionResult)

    if (conditionResult) {
      result.append(processBlocks(thenBody, context))
    } else {
      result.append(processBlocks(elseBody, context))
    }

    return endPos
  }
  private fun splitOnElse(block: String): Pair<String, String> {
    var depth = 0
    var pos = 0
    while (pos < block.length) {
      val match = blockPattern.find(block, pos) ?: break
      val directive = match.groupValues[1].trim()
      val directiveLower = directive.lowercase()
      when {
        directiveLower.startsWith("for ") || directiveLower.startsWith("if ") -> depth++
        directiveLower == "end" || directiveLower == "endfor" || directiveLower == "endif" -> depth--
        directiveLower == "else" && depth == 0 -> {
          // Found matching else at depth 0
          val thenBody = block.substring(0, match.range.first)
          val elseBody = block.substring(match.range.last + 1)
          return thenBody to elseBody
        }
      }
      pos = match.range.last + 1
    }
    // No else found
    return block to ""
  }

  private fun extractBlock(template: String, startPos: Int, blockType: String): Pair<String, Int> {
    var depth = 1
    var pos = startPos
    val endPatterns = listOf("end", "endfor", "endif")

    while (pos < template.length && depth > 0) {
      val match = blockPattern.find(template, pos) ?: break
      val directive = match.groupValues[1].trim()
      val directiveLower = directive.lowercase()

      when {
        directiveLower.startsWith("for ") || directiveLower.startsWith("if ") -> depth++
        endPatterns.any { directiveLower == it } -> depth--
      }


      if (depth == 0) {
        return template.substring(startPos, match.range.first) to (match.range.last + 1)
      }
      pos = match.range.last + 1
    }

    return template.substring(startPos) to template.length
  }

  private fun evaluateExpression(expr: String, context: Context): String {
    log.trace("Evaluating expression: {}", expr)
    // Handle filters: value | filter1 | filter2:arg
    val parts = expr.split("|").map { it.trim() }
    var value: Any? = resolveValueOrFunctionCall(parts[0], context)
    log.trace("Initial value for expression '{}': {}", parts[0], value?.toString()?.take(50))

    for (i in 1 until parts.size) {
      val filterExpr = parts[i]
      val filterParts = filterExpr.split(":", limit = 2)
      val filterName = filterParts[0].trim()
      val filterArgs = parseFilterArgs(filterParts.getOrNull(1))
      log.trace("Applying filter '{}' with args: {}", filterName, filterArgs)

      val filter = filters[filterName] ?: { v, _ -> v?.toString() ?: "" }
      value = filter(value, filterArgs)
      log.trace("Value after filter '{}': {}", filterName, value?.toString()?.take(50))
    }

    val result = value?.toString() ?: ""
    log.trace("Expression '{}' evaluated to: {}", expr, result.take(100))
    return result
  }

  private fun parseFilterArgs(argsStr: String?): List<String> {
    if (argsStr.isNullOrBlank()) return emptyList()
    val args = mutableListOf<String>()
    var current = StringBuilder()
    var inString = false
    var stringChar = ' '
    for (c in argsStr) {
      when {
        !inString && (c == '"' || c == '\'') -> {
          inString = true
          stringChar = c
        }

        inString && c == stringChar -> {
          inString = false
        }

        !inString && c == ',' -> {
          args.add(current.toString().trim())
          current = StringBuilder()
        }

        else -> {
          if (inString || !c.isWhitespace() || current.isNotEmpty()) {
            current.append(c)
          }
        }
      }
    }
    if (current.isNotEmpty()) {
      args.add(current.toString())
    }
    return args
  }

  private fun resolveValueOrFunctionCall(expr: String, context: Context): Any? {
    log.trace("Resolving value or function call: {}", expr)
    val functionCallPattern = Regex("""(\w+)\s*\((.*)\)""")
    val match = functionCallPattern.matchEntire(expr.trim())
    if (match != null) {
      val functionName = match.groupValues[1]
      val argsStr = match.groupValues[2]
      log.debug("Detected function call: {}({})", functionName, argsStr)
      // Check if it's a defined function
      if (definedFunctions.containsKey(functionName)) {
        val args = parseArguments(argsStr, context)
        log.debug("Calling defined function '{}' with {} arguments", functionName, args.size)
        return callFunctionWithArgs(functionName, args)
      }
    }
    return resolveValue(expr, context)
  }

  private fun parseArguments(argsStr: String, context: Context): List<Any?> {
    if (argsStr.isBlank()) return emptyList()
    val args = mutableListOf<Any?>()
    var current = StringBuilder()
    var depth = 0
    var inString = false
    var stringChar = ' '
    for (c in argsStr) {
      when {
        !inString && (c == '"' || c == '\'') -> {
          inString = true
          stringChar = c
          current.append(c)
        }

        inString && c == stringChar -> {
          inString = false
          current.append(c)
        }

        !inString && c == '(' -> {
          depth++
          current.append(c)
        }

        !inString && c == ')' -> {
          depth--
          current.append(c)
        }

        !inString && c == ',' && depth == 0 -> {
          args.add(resolveArgument(current.toString().trim(), context))
          current = StringBuilder()
        }

        else -> current.append(c)
      }
    }
    if (current.isNotEmpty()) {
      args.add(resolveArgument(current.toString().trim(), context))
    }
    return args
  }

  private fun resolveArgument(arg: String, context: Context): Any? {
    val trimmed = arg.trim()
    return when {
      trimmed.startsWith("\"") && trimmed.endsWith("\"") -> trimmed.removeSurrounding("\"")
      trimmed.startsWith("'") && trimmed.endsWith("'") -> trimmed.removeSurrounding("'")
      trimmed.matches(Regex("-?\\d+")) -> trimmed.toInt()
      trimmed.matches(Regex("-?\\d+\\.\\d+")) -> trimmed.toDouble()
      trimmed == "true" -> true
      trimmed == "false" -> false
      trimmed == "null" -> null
      else -> resolveValue(trimmed, context)
    }
  }

  private fun callFunctionWithArgs(functionName: String, args: List<Any?>): Any? {
    log.debug("Calling function '{}' with args: {}", functionName, args)
    val function = definedFunctions[functionName]
      ?: run {
        log.error("Function '{}' is not defined. Available: {}", functionName, definedFunctions.keys)
        throw IllegalArgumentException("Function '$functionName' is not defined")
      }
    val binding = Binding()
    // Bind the global data as 'data' variable
    currentData?.let { data ->
      binding.setVariable("data", convertToGroovyValue(data))
      log.trace("Bound global data to 'data' variable")
    }
    
    // Bind arguments to parameters
    for (i in args.indices) {
      if (i < function.parameters.size) {
        binding.setVariable(function.parameters[i], convertToGroovyValue(args[i]))
        log.trace("Bound parameter '{}' = {}", function.parameters[i], args[i])
      }
    }
    function.script.binding = binding
    val scriptInstance = function.script.run()
    val result = if (args.isEmpty()) {
      (scriptInstance as Script).invokeMethod(functionName, null)
    } else {
      (scriptInstance as Script).invokeMethod(functionName, args.map { convertToGroovyValue(it) }.toTypedArray())
    }
    log.debug("Function '{}' returned: {}", functionName, result?.toString()?.take(100))
    return result
  }

  private fun evaluateCondition(condition: String, context: Context): Boolean {
    log.trace("Evaluating condition: {}", condition)
    // Handle: value, !value, value == "string", value != "string"
    val trimmed = condition.trim()

    val result = when {
      trimmed.startsWith("!") -> {
        val innerCondition = trimmed.drop(1).trim()
        log.trace("Evaluating negated condition: {}", innerCondition)
        !evaluateCondition(innerCondition, context)
      }

      trimmed.contains("==") -> {
        val (left, right) = trimmed.split("==", limit = 2).map { it.trim() }
        val leftValue = evaluateArithmeticExpression(left, context)
        val rightValue = if (right.startsWith("\"") && right.endsWith("\"")) {
          right.removeSurrounding("\"")
        } else if (right.startsWith("'") && right.endsWith("'")) {
          right.removeSurrounding("'")
        } else {
          evaluateArithmeticExpression(right, context)
        }
        log.trace("Equality check: {} == {} -> {} == {}", left, right, leftValue, rightValue)
        leftValue?.toString() == rightValue?.toString()
      }

      trimmed.contains("!=") -> {
        val (left, right) = trimmed.split("!=", limit = 2).map { it.trim() }
        val leftValue = evaluateArithmeticExpression(left, context)
        val rightValue = if (right.startsWith("\"") && right.endsWith("\"")) {
          right.removeSurrounding("\"")
        } else if (right.startsWith("'") && right.endsWith("'")) {
          right.removeSurrounding("'")
        } else {
          evaluateArithmeticExpression(right, context)
        }
        log.trace("Inequality check: {} != {} -> {} != {}", left, right, leftValue, rightValue)
        leftValue?.toString() != rightValue?.toString()
      }

      else -> {
        val value = resolveValue(trimmed, context)
        log.trace("Truthiness check for '{}': value={}", trimmed, value)
        isTruthy(value)
      }
    }
    log.trace("Condition '{}' evaluated to: {}", condition, result)
    return result
  }

  private fun evaluateArithmeticExpression(expr: String, context: Context): Any? {
    val trimmed = expr.trim()
    // Check for modulo operation
    if (trimmed.contains("%")) {
      val parts = trimmed.split("%", limit = 2).map { it.trim() }
      val left = evaluateArithmeticExpression(parts[0], context)
      val right = evaluateArithmeticExpression(parts[1], context)
      val leftNum = when (left) {
        is Number -> left.toInt()
        is String -> left.toIntOrNull()
        else -> null
      }
      val rightNum = when (right) {
        is Number -> right.toInt()
        is String -> right.toIntOrNull()
        else -> null
      }
      return if (leftNum != null && rightNum != null && rightNum != 0) {
        leftNum % rightNum
      } else null
    }
    // Check if it's a numeric literal
    trimmed.toIntOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it }
    // Otherwise resolve as a variable path
    return resolveValue(trimmed, context)
  }

  private fun isTruthy(value: Any?): Boolean = when (value) {
    null -> false
    is JsonNull -> false
    is Boolean -> value
    is JsonPrimitive -> when {
      value.isBoolean -> value.asBoolean
      value.isString -> value.asString.isNotEmpty()
      value.isNumber -> true
      else -> false
    }

    is String -> value.isNotEmpty()
    is Number -> true
    is Collection<*> -> value.isNotEmpty()
    is JsonArray -> value.size() > 0
    is JsonObject -> value.entrySet().isNotEmpty()
    else -> true
  }

  private fun resolveValue(path: String, context: Context): Any? {
    log.trace("Resolving value for path: {}", path)
    val parts = path.trim().split(".")
    var current: Any? = context.resolve(parts[0])
    log.trace("Initial resolution of '{}': {}", parts[0], current?.toString()?.take(50))

    for (i in 1 until parts.size) {
      current = when (current) {
        is JsonObject -> current[parts[i]]
        is JsonPrimitive -> null
        else -> null
      }
      log.trace("After resolving '{}': {}", parts[i], current?.toString()?.take(50))
    }

    val result = when (current) {
      is JsonPrimitive -> when {
        current.isBoolean -> current.asBoolean
        current.isNumber -> current.asNumber
        else -> current.asString
      }

      is JsonNull -> null
      else -> current
    }
    log.trace("Final resolved value for '{}': {}", path, result?.toString()?.take(50))
    return result
  }

  private fun escapeLatex(text: String): String {
    val sb = StringBuilder()
    for (c in text) {
      when (c) {
        '\\' -> sb.append("\\textbackslash{}")
        '{' -> sb.append("\\{")
        '}' -> sb.append("\\}")
        '$' -> sb.append("\\$")
        '&' -> sb.append("\\&")
        '%' -> sb.append("\\%")
        '#' -> sb.append("\\#")
        '_' -> sb.append("\\_")
        '~' -> sb.append("\\textasciitilde{}")
        '^' -> sb.append("\\textasciicircum{}")
        '<' -> sb.append("\\textless{}")
        '>' -> sb.append("\\textgreater{}")
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  private fun markdownToLatex(text: String): String {
    var result = text
      .replace(Regex("""\*\*(.+?)\*\*""")) { "\\textbf{${it.groupValues[1]}}" }
      .replace(Regex("""\*(.+?)\*""")) { "\\textit{${it.groupValues[1]}}" }
      .replace(Regex("""`(.+?)`""")) { "\\texttt{${it.groupValues[1]}}" }
      .replace(Regex("""\[(.+?)]\((.+?)\)""")) { "\\href{${it.groupValues[2]}}{${it.groupValues[1]}}" }
    return result
  }

  private class Context(
    private val data: JsonObject,
    private val parent: Context? = null,
    private val locals: Map<String, Any?> = emptyMap()
  ) {
    fun resolve(key: String): Any? {
      return locals[key] ?: data[key] ?: parent?.resolve(key)
    }

    fun child(newLocals: Map<String, Any?>): Context {
      return Context(data, this, newLocals)
    }

    fun getAllVariables(): Map<String, Any?> {
      val result = mutableMapOf<String, Any?>()
      // Add parent variables first (so they can be overridden)
      parent?.getAllVariables()?.let { result.putAll(it) }
      // Add data object entries
      data.entrySet().forEach { result[it.key] = it.value }
      // Add locals (highest priority)
      result.putAll(locals)
      return result
    }
  }

  data class TypeSchema(
    val fields: Map<String, FieldType>
  ) {
    fun toTypeScript(): String {
      val sb = StringBuilder()
      sb.appendLine("interface TemplateData {")
      for ((name, type) in fields) {
        val optionalMarker = if (type.optional) "?" else ""
        sb.appendLine("  $name$optionalMarker: ${type.toTypeScript()};")
      }
      sb.appendLine("}")
      return sb.toString()
    }
  }

  sealed class FieldType(open val optional: Boolean) {
    abstract fun toTypeScript(): String
    data class StringType(override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = "string"
    }

    data class NumberType(override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = "number"
    }

    data class BooleanType(override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = "boolean"
    }

    data class AnyType(override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = "any"
    }

    data class ArrayType(val elementType: FieldType, override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = "${elementType.toTypeScript()}[]"
    }

    data class ObjectType(val fields: Map<String, FieldType>, override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript(): String {
        val fieldStrs = fields.map { (name, type) ->
          val opt = if (type.optional) "?" else ""
          "$name$opt: ${type.toTypeScript()}"
        }
        return "{ ${fieldStrs.joinToString("; ")} }"
      }
    }

    data class UnionType(val types: List<String>, override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = types.joinToString(" | ")
    }

    data class CustomType(val typeName: String, override val optional: Boolean) : FieldType(optional) {
      override fun toTypeScript() = typeName
    }
  }

  data class ValidationError(
    val path: String,
    val message: String
  ) {
    override fun toString() = "$path: $message"
  }

  class TemplateValidationException(val errors: List<ValidationError>) :
    RuntimeException("Template validation failed:\n${errors.joinToString("\n") { "  - $it" }}")

  companion object {
    private val log = LoggerFactory.getLogger(ErbTemplateEngine::class.java)
  }
}