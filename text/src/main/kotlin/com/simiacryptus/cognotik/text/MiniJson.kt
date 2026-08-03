package com.simiacryptus.cognotik.text

    /**
     * Minimal, dependency-free JSON reader.
     *
     * Produces plain JVM types:
     *  * object  -> [LinkedHashMap] (insertion ordered)
     *  * array   -> [ArrayList]
     *  * string  -> [String]
     *  * number  -> [Long] when integral, otherwise [Double]
     *  * boolean -> [Boolean]
     *  * null    -> `null`
     */
    object MiniJson {

        /** Parse exactly one JSON document. */
        fun parse(text: String): Any? {
            val p = Parser(text)
            val value = p.parseValue()
            p.skipWhitespace()
            require(p.eof()) { "Unexpected trailing content at offset ${p.pos}" }
            return value
        }

        /**
         * Parse a stream of JSON values: supports a single document, JSON-Lines,
         * concatenated documents and comma separated documents.
         */
        fun parseStream(text: String): List<Any?> {
            val p = Parser(text)
            val out = ArrayList<Any?>()
            while (true) {
                p.skipWhitespaceAndSeparators()
                if (p.eof()) break
                out.add(p.parseValue())
            }
            return out
        }

        private class Parser(private val src: String) {
            var pos = 0

            fun eof() = pos >= src.length

            fun skipWhitespace() {
                while (pos < src.length && src[pos].isWhitespace()) pos++
            }

            fun skipWhitespaceAndSeparators() {
                while (pos < src.length && (src[pos].isWhitespace() || src[pos] == ',' || src[pos] == ';')) pos++
            }

            fun parseValue(): Any? {
                skipWhitespace()
                if (eof()) fail("Unexpected end of input")
                return when (val c = src[pos]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't' -> literal("true", true)
                    'f' -> literal("false", false)
                    'n' -> literal("null", null)
                    else -> if (c == '-' || c == '+' || c.isDigit()) parseNumber() else fail("Unexpected character '$c'")
                }
            }

            private fun parseObject(): MutableMap<String, Any?> {
                expect('{')
                val map = LinkedHashMap<String, Any?>()
                skipWhitespace()
                if (!eof() && src[pos] == '}') { pos++; return map }
                while (true) {
                    skipWhitespace()
                    val key = parseString()
                    expect(':')
                    map[key] = parseValue()
                    skipWhitespace()
                    if (eof()) fail("Unterminated object")
                    when (src[pos]) {
                        ',' -> pos++
                        '}' -> { pos++; return map }
                        else -> fail("Expected ',' or '}' but found '${src[pos]}'")
                    }
                }
            }

            private fun parseArray(): MutableList<Any?> {
                expect('[')
                val list = ArrayList<Any?>()
                skipWhitespace()
                if (!eof() && src[pos] == ']') { pos++; return list }
                while (true) {
                    list.add(parseValue())
                    skipWhitespace()
                    if (eof()) fail("Unterminated array")
                    when (src[pos]) {
                        ',' -> pos++
                        ']' -> { pos++; return list }
                        else -> fail("Expected ',' or ']' but found '${src[pos]}'")
                    }
                }
            }

            fun parseString(): String {
                expect('"')
                val sb = StringBuilder()
                while (true) {
                    if (eof()) fail("Unterminated string")
                    val c = src[pos++]
                    when {
                        c == '"' -> return sb.toString()
                        c == '\\' -> {
                            if (eof()) fail("Unterminated escape sequence")
                            when (val e = src[pos++]) {
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                '/' -> sb.append('/')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (pos + 4 > src.length) fail("Truncated unicode escape")
                                    sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                                    pos += 4
                                }

                                else -> fail("Invalid escape '\\$e'")
                            }
                        }

                        else -> sb.append(c)
                    }
                }
            }

            private fun parseNumber(): Number {
                val start = pos
                if (!eof() && (src[pos] == '-' || src[pos] == '+')) pos++
                var fractional = false
                while (!eof()) {
                    val c = src[pos]
                    when {
                        c.isDigit() -> pos++
                        c == '.' || c == 'e' || c == 'E' -> { fractional = true; pos++ }
                        (c == '+' || c == '-') && (src[pos - 1] == 'e' || src[pos - 1] == 'E') -> pos++
                        else -> break
                    }
                }
                val text = src.substring(start, pos)
                if (text.isEmpty() || text == "-" || text == "+") fail("Invalid number")
                return if (fractional) text.toDouble() else (text.toLongOrNull() ?: text.toDouble())
            }

            private fun literal(text: String, value: Any?): Any? {
                if (!src.startsWith(text, pos)) fail("Invalid literal, expected '$text'")
                pos += text.length
                return value
            }

            private fun expect(c: Char) {
                skipWhitespace()
                if (eof() || src[pos] != c) fail("Expected '$c'")
                pos++
            }

            private fun fail(message: String): Nothing =
                throw IllegalArgumentException("$message (offset $pos)")
        }
    }