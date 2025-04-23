package utils

object StringUtils {

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
        return input?.toUpperCase() ?: ""
    }

    fun toLowerCase(input: String?): String {
        return input?.toLowerCase() ?: ""
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
        return input?.padStart((input.length + length) / 2, padChar[0])?.padEnd(length, padChar[0]) ?: ""
    }

    fun reverse(input: String?): String {
        return input?.reversed() ?: ""
    }

    fun isPalindrome(input: String?): Boolean {
        val sanitized = input?.filter { it.isLetterOrDigit() }?.lowercase() ?: ""
        return sanitized == sanitized.reversed()
    }

    fun capitalizeWords(input: String?): String {
        return input?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } } ?: ""
    }

    fun trimStart(input: String?): String {
        return input?.trimStart() ?: ""
    }

    fun trimEnd(input: String?): String {
        return input?.trimEnd() ?: ""
    }

    fun replace(input: String?, oldValue: String, newValue: String): String {
        return input?.replace(oldValue, newValue) ?: ""
    }

    fun substring(input: String?, startIndex: Int, endIndex: Int): String {
        return input?.substring(startIndex, endIndex) ?: ""
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

    fun format(format: String?, vararg args: Any?): String {
        return if (format != null) String.format(format, *args) else ""
    }

    fun removeWhitespace(input: String?): String {
        return input?.filterNot { it.isWhitespace() } ?: ""
    }

    fun replaceFirst(input: String?, oldValue: String, newValue: String): String {
        return input?.replaceFirst(oldValue, newValue) ?: ""
    }

    fun replaceLast(input: String?, oldValue: String, newValue: String): String {
        return input?.replaceLast(oldValue, newValue) ?: ""
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