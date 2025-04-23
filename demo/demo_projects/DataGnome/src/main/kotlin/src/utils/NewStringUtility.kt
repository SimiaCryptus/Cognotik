package utils

object NewStringUtility {

    fun trim(input: String?): String {
        return input?.trim() ?: ""
    }

    fun split(input: String?, delimiter: String): List<String> {
        return input?.split(delimiter) ?: emptyList()
    }

    fun join(parts: List<String>?, delimiter: String): String {
        return parts?.joinToString(delimiter) ?: ""
    }

    fun toUpperCase(input: String?): String {
        return input?.uppercase() ?: ""
    }

    fun toLowerCase(input: String?): String {
        return input?.lowercase() ?: ""
    }

    fun capitalizeFirst(input: String?): String {
        return input?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: ""
    }

    fun toKebabCase(input: String?): String {
        return input?.split(" ")?.joinToString("-") { it.lowercase() } ?: ""
    }

    fun toSnakeCase(input: String?): String {
        return input?.split(" ")?.joinToString("_") { it.lowercase() } ?: ""
    }

    fun repeat(input: String?, times: Int): String {
        require(times >= 0) { "Repeat count must be non-negative" }
        return input?.repeat(times) ?: ""
    }

    fun contains(input: String?, substring: String): Boolean {
        return input?.contains(substring) ?: false
    }

    fun pad(input: String?, length: Int, padChar: String): String {
        return input?.padStart((input.length + length) / 2, padChar.first())
            ?.padEnd(length, padChar.first()) ?: ""
    }

    fun reverse(input: String?): String {
        return input?.reversed() ?: ""
    }

    fun isPalindrome(str: String?): Boolean {
        val sanitized = str?.filter { it.isLetterOrDigit() }?.lowercase() ?: ""
        return sanitized == sanitized.reversed()
    }

    fun capitalizeWords(input: String?): String {
        return input?.split(" ")?.joinToString(" ") { it.capitalize() } ?: ""
    }

    fun trimStart(input: String?): String {
        return input?.trimStart() ?: ""
    }

    fun trimEnd(input: String?): String {
        return input?.trimEnd() ?: ""
    }

    fun replace(input: String?, target: String, replacement: String): String {
        return input?.replace(target, replacement) ?: ""
    }

    fun substring(input: String?, start: Int, end: Int): String {
        return input?.substring(start, end) ?: ""
    }

    fun indexOf(input: String?, substring: String): Int {
        return input?.indexOf(substring) ?: -1
    }

    fun lastIndexOf(input: String?, substring: String): Int {
        return input?.lastIndexOf(substring) ?: -1
    }

    fun startsWith(input: String?, prefix: String): Boolean {
        return input?.startsWith(prefix) ?: false
    }

    fun endsWith(input: String?, suffix: String): Boolean {
        return input?.endsWith(suffix) ?: false
    }

    fun charAt(input: String?, index: Int): Char {
        return input?.get(index) ?: throw StringIndexOutOfBoundsException("Index out of bounds")
    }

    fun length(input: String?): Int {
        return input?.length ?: 0
    }

    fun format(template: String?, vararg args: Any?): String {
        return template?.format(*args) ?: ""
    }

    fun removeWhitespace(input: String?): String {
        return input?.filterNot { it.isWhitespace() } ?: ""
    }

    fun replaceFirst(input: String?, target: String, replacement: String): String {
        return input?.replaceFirst(target, replacement) ?: ""
    }

    fun replaceLast(input: String?, target: String, replacement: String): String {
        return input?.replaceLast(target, replacement) ?: ""
    }

    fun truncate(input: String?, maxLength: Int): String {
        return if (input != null && input.length > maxLength) input.substring(0, maxLength) else input ?: ""
    }

    fun wrapText(input: String?, maxLineLength: Int): String {
        if (input == null) return ""
        val words = input.split(" ")
        val wrappedText = StringBuilder()
        var currentLineLength = 0

        for (word in words) {
            if (currentLineLength + word.length > maxLineLength) {
                wrappedText.append("\n")
                currentLineLength = 0
            }
            if (currentLineLength > 0) {
                wrappedText.append(" ")
                currentLineLength++
            }
            wrappedText.append(word)
            currentLineLength += word.length
        }
        return wrappedText.toString()
    }
}