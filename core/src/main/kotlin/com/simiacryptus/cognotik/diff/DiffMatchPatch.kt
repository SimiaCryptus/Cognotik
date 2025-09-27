package com.simiacryptus.cognotik.diff

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/*
* Functions for diff, match and patch.
* Computes the difference between two texts to create a patch.
* Applies the patch onto another text, allowing for errors.
*
* @author fraser@google.com (Neil Fraser)
*/
/**
 * Class containing the diff, match and patch methods.
 * Also contains the behaviour settings.
 */
open class DiffMatchPatch {


    /**
     * Number of seconds to map a diff before giving up (0 for infinity).
     */
    var Diff_Timeout: Float = 1.0f

    /**
     * Cost of an empty edit operation in terms of edit characters.
     */
    private var Diff_EditCost: Short = 4

    /**
     * Chunk size for context length.
     */
    private var Patch_Margin: Short = 4

    /**
     * The number of bits in an int.
     */
    private val Match_MaxBits: Short = 32

    /**
     * Internal class for returning results from diff_linesToChars().
     * Other less paranoid languages just use a three-element array.
     */
    protected class LinesToCharsResult(
        var chars1: String, var chars2: String,
        var lineArray: List<String>
    )

    /**
     * The data structure representing a diff is a Linked list of Diff objects:
     * {Diff(Operation.DELETE, "Hello"), Diff(Operation.INSERT, "Goodbye"),
     * Diff(Operation.EQUAL, " world.")}
     * which means: delete "Hello", add "Goodbye" and keep " world."
     */
    enum class Operation {
        DELETE, INSERT, EQUAL
    }

    /**
     * Find the differences between two texts.
     * Run a faster, slightly less optimal diff.
     * This method allows the 'checklines' of diff_main() to be optional.
     * Most of the time checklines is wanted, so default to true.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return Linked List of Diff objects.
     */
    @JvmOverloads
    fun diff_main(text1: String?, text2: String?, checklines: Boolean = true): LinkedList<Diff> {

        val deadline: Long
        if (Diff_Timeout <= 0) {
            deadline = Long.MAX_VALUE
        } else {
            deadline = System.currentTimeMillis() + (Diff_Timeout * 1000).toLong()
        }
        return diff_main(text1, text2, checklines, deadline)
    }

    /**
     * Find the differences between two texts.  Simplifies the problem by
     * stripping any common prefix or suffix off the texts before diffing.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     * line-level diff first to identify the changed areas.
     * If true, then run a faster slightly less optimal diff.
     * @param deadline Time when the diff should be complete by.  Used
     * internally for recursive calls.  Users should set DiffTimeout instead.
     * @return Linked List of Diff objects.
     */
    fun diff_main(text1: String?, text2: String?, checklines: Boolean, deadline: Long): LinkedList<Diff> {

        var text1 = text1
        var text2 = text2
        if (text1 == null || text2 == null) {
            throw IllegalArgumentException("Null inputs. (diff_main)")
        }

        val diffs: LinkedList<Diff>
        if (text1 == text2) {
            diffs = LinkedList()
            if (text1.length != 0) {
                diffs.add(Diff(Operation.EQUAL, text1))
            }
            return diffs
        }

        var commonlength = diff_commonPrefix(text1, text2)
        val commonprefix = text1.substring(0, commonlength)
        text1 = text1.substring(commonlength)
        text2 = text2.substring(commonlength)

        commonlength = diff_commonSuffix(text1, text2)
        val commonsuffix = text1.substring(text1.length - commonlength)
        text1 = text1.substring(0, text1.length - commonlength)
        text2 = text2.substring(0, text2.length - commonlength)

        diffs = diff_compute(text1, text2, checklines, deadline)

        if (commonprefix.length != 0) {
            diffs.addFirst(Diff(Operation.EQUAL, commonprefix))
        }
        if (commonsuffix.length != 0) {
            diffs.addLast(Diff(Operation.EQUAL, commonsuffix))
        }

        diff_cleanupMerge(diffs)
        return diffs
    }

    /**
     * Find the differences between two texts.  Assumes that the texts do not
     * have any common prefix or suffix.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param checklines Speedup flag.  If false, then don't run a
     * line-level diff first to identify the changed areas.
     * If true, then run a faster slightly less optimal diff.
     * @param deadline Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diff_compute(text1: String, text2: String, checklines: Boolean, deadline: Long): LinkedList<Diff> {
        var diffs = LinkedList<Diff>()

        if (text1.length == 0) {

            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }

        if (text2.length == 0) {

            diffs.add(Diff(Operation.DELETE, text1))
            return diffs
        }

        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        val i = longtext.indexOf(shorttext)
        if (i != -1) {

            val op = if ((text1.length > text2.length)) Operation.DELETE else Operation.INSERT
            diffs.add(Diff(op, longtext.substring(0, i)))
            diffs.add(Diff(Operation.EQUAL, shorttext))
            diffs.add(Diff(op, longtext.substring(i + shorttext.length)))
            return diffs
        }

        if (shorttext.length == 1) {


            diffs.add(Diff(Operation.DELETE, text1))
            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }

        val hm = diff_halfMatch(text1, text2)
        if (hm != null) {

            val text1_a = hm[0]
            val text1_b = hm[1]
            val text2_a = hm[2]
            val text2_b = hm[3]
            val mid_common = hm[4]

            val diffs_a = diff_main(
                text1_a, text2_a,
                checklines, deadline
            )
            val diffs_b = diff_main(
                text1_b, text2_b,
                checklines, deadline
            )

            diffs = diffs_a
            diffs.add(Diff(Operation.EQUAL, mid_common))
            diffs.addAll(diffs_b)
            return diffs
        }

        if ((checklines && text1.length > 100) && text2.length > 100) {
            return diff_lineMode(text1, text2, deadline)
        }

        return diff_bisect(text1, text2, deadline)
    }

    /**
     * Do a quick line-level diff on both strings, then rediff the parts for
     * greater accuracy.
     * This speedup can produce non-minimal diffs.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param deadline Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diff_lineMode(
        text1: String, text2: String,
        deadline: Long
    ): LinkedList<Diff> {

        var text1 = text1
        var text2 = text2
        val a = diff_linesToChars(text1, text2)
        text1 = a.chars1
        text2 = a.chars2
        val linearray = a.lineArray

        val diffs = diff_main(text1, text2, false, deadline)

        diff_charsToLines(diffs, linearray)

        diff_cleanupSemantic(diffs)


        diffs.add(Diff(Operation.EQUAL, ""))
        var count_delete = 0
        var count_insert = 0
        var text_delete = ""
        var text_insert = ""
        val pointer = diffs.listIterator()
        var thisDiff: Diff? = pointer.next()
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    count_insert++
                    text_insert += thisDiff.text
                }

                Operation.DELETE -> {
                    count_delete++
                    text_delete += thisDiff.text
                }

                Operation.EQUAL -> {

                    if (count_delete >= 1 && count_insert >= 1) {

                        pointer.previous()
                        var j = 0
                        while (j < count_delete + count_insert) {
                            pointer.previous()
                            pointer.remove()
                            j++
                        }
                        for (subDiff: Diff in diff_main(
                            text_delete, text_insert, false,
                            deadline
                        )) {
                            pointer.add(subDiff)
                        }
                    }
                    count_insert = 0
                    count_delete = 0
                    text_delete = ""
                    text_insert = ""
                }

                null -> TODO()
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        diffs.removeLast()


        return diffs
    }

    /**
     * Find the 'middle snake' of a diff, split the problem in two
     * and return the recursively constructed diff.
     * See Myers 1986 paper: An O(ND) Difference Algorithm and Its Variations.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private fun diff_bisect(
        text1: String, text2: String,
        deadline: Long
    ): LinkedList<Diff> {

        val text1_length = text1.length
        val text2_length = text2.length
        val max_d = (text1_length + text2_length + 1) / 2
        val v_offset = max_d
        val v_length = 2 * max_d
        val v1 = IntArray(v_length)
        val v2 = IntArray(v_length)
        for (x in 0 until v_length) {
            v1[x] = -1
            v2[x] = -1
        }
        v1[v_offset + 1] = 0
        v2[v_offset + 1] = 0
        val delta = text1_length - text2_length


        val front = (delta % 2 != 0)


        var k1start = 0
        var k1end = 0
        var k2start = 0
        var k2end = 0
        for (d in 0 until max_d) {

            if (System.currentTimeMillis() > deadline) {
                break
            }

            var k1 = -d + k1start
            while (k1 <= d - k1end) {
                val k1_offset = v_offset + k1
                var x1: Int
                if (k1 == -d || (k1 != d && v1[k1_offset - 1] < v1[k1_offset + 1])) {
                    x1 = v1[k1_offset + 1]
                } else {
                    x1 = v1[k1_offset - 1] + 1
                }
                var y1 = x1 - k1
                while ((x1 < text1_length) && y1 < text2_length && text1[x1] == text2[y1]) {
                    x1++
                    y1++
                }
                v1[k1_offset] = x1
                if (x1 > text1_length) {

                    k1end += 2
                } else if (y1 > text2_length) {

                    k1start += 2
                } else if (front) {
                    val k2_offset = v_offset + delta - k1
                    if ((k2_offset >= 0 && k2_offset < v_length) && v2[k2_offset] != -1) {

                        val x2 = text1_length - v2[k2_offset]
                        if (x1 >= x2) {

                            return diff_bisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k1 += 2
            }

            var k2 = -d + k2start
            while (k2 <= d - k2end) {
                val k2_offset = v_offset + k2
                var x2: Int
                if (k2 == -d || (k2 != d && v2[k2_offset - 1] < v2[k2_offset + 1])) {
                    x2 = v2[k2_offset + 1]
                } else {
                    x2 = v2[k2_offset - 1] + 1
                }
                var y2 = x2 - k2
                while ((x2 < text1_length) && y2 < text2_length && (text1[text1_length - x2 - 1]
                            == text2[text2_length - y2 - 1])
                ) {
                    x2++
                    y2++
                }
                v2[k2_offset] = x2
                if (x2 > text1_length) {

                    k2end += 2
                } else if (y2 > text2_length) {

                    k2start += 2
                } else if (!front) {
                    val k1_offset = v_offset + delta - k2
                    if (((k1_offset >= 0) && k1_offset < v_length) && v1[k1_offset] != -1) {
                        val x1 = v1[k1_offset]
                        val y1 = v_offset + x1 - k1_offset

                        x2 = text1_length - x2
                        if (x1 >= x2) {

                            return diff_bisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k2 += 2
            }
        }


        val diffs = LinkedList<Diff>()
        diffs.add(Diff(Operation.DELETE, text1))
        diffs.add(Diff(Operation.INSERT, text2))
        return diffs
    }

    /**
     * Given the location of the 'middle snake', split the diff in two parts
     * and recurse.
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param x Index of split point in text1.
     * @param y Index of split point in text2.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private fun diff_bisectSplit(
        text1: String, text2: String,
        x: Int, y: Int, deadline: Long
    ): LinkedList<Diff> {
        val text1a = text1.substring(0, x)
        val text2a = text2.substring(0, y)
        val text1b = text1.substring(x)
        val text2b = text2.substring(y)

        val diffs = diff_main(text1a, text2a, false, deadline)
        val diffsb = diff_main(text1b, text2b, false, deadline)

        diffs.addAll(diffsb)
        return diffs
    }

    /**
     * Split two texts into a list of strings.  Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     * @param text1 First string.
     * @param text2 Second string.
     * @return An object containing the encoded text1, the encoded text2 and
     * the List of unique strings.  The zeroth element of the List of
     * unique strings is intentionally blank.
     */
    private fun diff_linesToChars(text1: String, text2: String): LinesToCharsResult {
        val lineArray: MutableList<String> = ArrayList()
        val lineHash: MutableMap<String, Int> = HashMap()




        lineArray.add("")

        val chars1 = diff_linesToCharsMunge(text1, lineArray, lineHash, 40000)
        val chars2 = diff_linesToCharsMunge(text2, lineArray, lineHash, 65535)
        return LinesToCharsResult(chars1, chars2, lineArray)
    }

    /**
     * Split a text into a list of strings.  Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     * @param text String to encode.
     * @param lineArray List of unique strings.
     * @param lineHash Map of strings to indices.
     * @param maxLines Maximum length of lineArray.
     * @return Encoded string.
     */
    private fun diff_linesToCharsMunge(
        text: String, lineArray: MutableList<String>,
        lineHash: MutableMap<String, Int>, maxLines: Int
    ): String {
        var lineStart = 0
        var lineEnd = -1
        var line: String
        val chars = StringBuilder()



        while (lineEnd < text.length - 1) {
            lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd == -1) {
                lineEnd = text.length - 1
            }
            line = text.substring(lineStart, lineEnd + 1)

            if (lineHash.containsKey(line)) {
                chars.append((lineHash[line] as Int).toChar().toString())
            } else {
                if (lineArray.size == maxLines) {


                    line = text.substring(lineStart)
                    lineEnd = text.length
                }
                lineArray.add(line)
                lineHash[line] = lineArray.size - 1
                chars.append((lineArray.size - 1).toChar().toString())
            }
            lineStart = lineEnd + 1
        }
        return chars.toString()
    }

    /**
     * Rehydrate the text in a diff from a string of line hashes to real lines of
     * text.
     * @param diffs List of Diff objects.
     * @param lineArray List of unique strings.
     */
    private fun diff_charsToLines(
        diffs: List<Diff>,
        lineArray: List<String>
    ) {
        var text: StringBuilder
        for (diff: Diff in diffs) {
            text = StringBuilder()
            for (j in 0 until diff.text!!.length) {
                text.append(lineArray[diff.text!![j].code])
            }
            diff.text = text.toString()
        }
    }

    /**
     * Determine the common prefix of two strings
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the start of each string.
     */
    fun diff_commonPrefix(text1: String?, text2: String?): Int {

        val n = min(text1!!.length.toDouble(), text2!!.length.toDouble()).toInt()
        for (i in 0 until n) {
            if (text1[i] != text2[i]) {
                return i
            }
        }
        return n
    }

    /**
     * Determine the common suffix of two strings
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of each string.
     */
    fun diff_commonSuffix(text1: String?, text2: String?): Int {

        val text1_length = text1!!.length
        val text2_length = text2!!.length
        val n = min(text1_length.toDouble(), text2_length.toDouble()).toInt()
        for (i in 1..n) {
            if (text1[text1_length - i] != text2[text2_length - i]) {
                return i - 1
            }
        }
        return n
    }

    /**
     * Determine if the suffix of one string is the prefix of another.
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of the first
     * string and the start of the second string.
     */
    private fun diff_commonOverlap(text1: String?, text2: String?): Int {

        var text1 = text1
        var text2 = text2
        val text1_length = text1!!.length
        val text2_length = text2!!.length

        if (text1_length == 0 || text2_length == 0) {
            return 0
        }

        if (text1_length > text2_length) {
            text1 = text1.substring(text1_length - text2_length)
        } else if (text1_length < text2_length) {
            text2 = text2.substring(0, text1_length)
        }
        val text_length = min(text1_length.toDouble(), text2_length.toDouble()).toInt()

        if (text1 == text2) {
            return text_length
        }


        var best = 0
        var length = 1
        while (true) {
            val pattern = text1.substring(text_length - length)
            val found = text2.indexOf(pattern)
            if (found == -1) {
                return best
            }
            length += found
            if (found == 0 || text1.substring(text_length - length) == text2.substring(0, length)) {
                best = length
                length++
            }
        }
    }

    /**
     * Do the two texts share a substring which is at least half the length of
     * the longer text?
     * This speedup can produce non-minimal diffs.
     * @param text1 First string.
     * @param text2 Second string.
     * @return Five element String array, containing the prefix of text1, the
     * suffix of text1, the prefix of text2, the suffix of text2 and the
     * common middle.  Or null if there was no match.
     */
    private fun diff_halfMatch(text1: String, text2: String): Array<String>? {
        if (Diff_Timeout <= 0) {

            return null
        }
        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        if (longtext.length < 4 || shorttext.length * 2 < longtext.length) {
            return null

        }

        val hm1 = diff_halfMatchI(
            longtext, shorttext,
            (longtext.length + 3) / 4
        )

        val hm2 = diff_halfMatchI(
            longtext, shorttext,
            (longtext.length + 1) / 2
        )
        val hm: Array<String>?
        if (hm1 == null && hm2 == null) {
            return null
        } else if (hm2 == null) {
            hm = hm1
        } else if (hm1 == null) {
            hm = hm2
        } else {

            hm = if (hm1[4].length > hm2[4].length) hm1 else hm2
        }

        if (text1.length > text2.length) {
            return hm

        } else {
            return arrayOf(hm!![2], hm[3], hm[0], hm[1], hm[4])
        }
    }

    /**
     * Does a substring of shorttext exist within longtext such that the
     * substring is at least half the length of longtext?
     * @param longtext Longer string.
     * @param shorttext Shorter string.
     * @param i Start index of quarter length substring within longtext.
     * @return Five element String array, containing the prefix of longtext, the
     * suffix of longtext, the prefix of shorttext, the suffix of shorttext
     * and the common middle.  Or null if there was no match.
     */
    private fun diff_halfMatchI(longtext: String, shorttext: String, i: Int): Array<String>? {

        val seed = longtext.substring(i, i + longtext.length / 4)
        var j = -1
        var best_common = ""
        var best_longtext_a = ""
        var best_longtext_b = ""
        var best_shorttext_a = ""
        var best_shorttext_b = ""
        while ((shorttext.indexOf(seed, j + 1).also { j = it }) != -1) {
            val prefixLength = diff_commonPrefix(
                longtext.substring(i),
                shorttext.substring(j)
            )
            val suffixLength = diff_commonSuffix(
                longtext.substring(0, i),
                shorttext.substring(0, j)
            )
            if (best_common.length < suffixLength + prefixLength) {
                best_common = (shorttext.substring(j - suffixLength, j)
                        + shorttext.substring(j, j + prefixLength))
                best_longtext_a = longtext.substring(0, i - suffixLength)
                best_longtext_b = longtext.substring(i + prefixLength)
                best_shorttext_a = shorttext.substring(0, j - suffixLength)
                best_shorttext_b = shorttext.substring(j + prefixLength)
            }
        }
        if (best_common.length * 2 >= longtext.length) {
            return arrayOf(
                best_longtext_a, best_longtext_b,
                best_shorttext_a, best_shorttext_b, best_common
            )
        } else {
            return null
        }
    }

    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     * @param diffs LinkedList of Diff objects.
     */
    fun diff_cleanupSemantic(diffs: LinkedList<Diff>) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities = ArrayDeque<Diff>()

        var lastEquality: String? = null

        var pointer = diffs.listIterator()

        var length_insertions1 = 0
        var length_deletions1 = 0

        var length_insertions2 = 0
        var length_deletions2 = 0
        var thisDiff: Diff? = pointer.next()
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {

                equalities.add(thisDiff)
                length_insertions1 = length_insertions2
                length_deletions1 = length_deletions2
                length_insertions2 = 0
                length_deletions2 = 0
                lastEquality = thisDiff.text
            } else {

                if (thisDiff.operation == Operation.INSERT) {
                    length_insertions2 += thisDiff.text!!.length
                } else {
                    length_deletions2 += thisDiff.text!!.length
                }


                if (lastEquality != null && (lastEquality.length
                            <= max(length_insertions1.toDouble(), length_deletions1.toDouble()))
                    && (lastEquality.length
                            <= max(length_insertions2.toDouble(), length_deletions2.toDouble()))
                ) {


                    while (thisDiff !== equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    pointer.set(Diff(Operation.DELETE, lastEquality))

                    pointer.add(Diff(Operation.INSERT, lastEquality))

                    equalities.pop()

                    if (!equalities.isEmpty()) {

                        equalities.pop()
                    }
                    if (equalities.isEmpty()) {

                        while (pointer.hasPrevious()) {
                            pointer.previous()
                        }
                    } else {
                        thisDiff = equalities.peek()
                    }

                    length_insertions1 = 0

                    length_insertions2 = 0
                    length_deletions1 = 0
                    length_deletions2 = 0
                    lastEquality = null
                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        if (changes) {
            diff_cleanupMerge(diffs)
        }
        diff_cleanupSemanticLossless(diffs)






        pointer = diffs.listIterator()
        var prevDiff: Diff? = null
        thisDiff = null
        if (pointer.hasNext()) {
            prevDiff = pointer.next()
            if (pointer.hasNext()) {
                thisDiff = pointer.next()
            }
        }
        while (thisDiff != null) {
            if (prevDiff!!.operation == Operation.DELETE &&
                thisDiff.operation == Operation.INSERT
            ) {
                val deletion = prevDiff.text
                val insertion = thisDiff.text
                val overlap_length1 = this.diff_commonOverlap(deletion, insertion)
                val overlap_length2 = this.diff_commonOverlap(insertion, deletion)
                if (overlap_length1 >= overlap_length2) {
                    if (overlap_length1 >= deletion!!.length / 2.0 ||
                        overlap_length1 >= insertion!!.length / 2.0
                    ) {

                        pointer.previous()
                        pointer.add(
                            Diff(
                                Operation.EQUAL,
                                insertion!!.substring(0, overlap_length1)
                            )
                        )
                        prevDiff.text =
                            deletion.substring(0, deletion.length - overlap_length1)
                        thisDiff.text = insertion.substring(overlap_length1)


                    }
                } else {
                    if (overlap_length2 >= deletion!!.length / 2.0 ||
                        overlap_length2 >= insertion!!.length / 2.0
                    ) {


                        pointer.previous()
                        pointer.add(
                            Diff(
                                Operation.EQUAL,
                                deletion.substring(0, overlap_length2)
                            )
                        )
                        prevDiff.operation = Operation.INSERT
                        prevDiff.text =
                            insertion!!.substring(0, insertion.length - overlap_length2)
                        thisDiff.operation = Operation.DELETE
                        thisDiff.text = deletion.substring(overlap_length2)


                    }
                }
                thisDiff = if (pointer.hasNext()) pointer.next() else null
            }
            prevDiff = thisDiff
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Look for single edits surrounded on both sides by equalities
     * which can be shifted sideways to align the edit to a word boundary.
     * e.g: The c<ins>at c</ins>ame. -> The <ins>cat </ins>came.
     * @param diffs LinkedList of Diff objects.
     */
    private fun diff_cleanupSemanticLossless(diffs: LinkedList<Diff>) {
        var equality1: String
        var edit: String
        var equality2: String
        var commonString: String
        var commonOffset: Int
        var score: Int
        var bestScore: Int
        var bestEquality1: String?
        var bestEdit: String?
        var bestEquality2: String?

        val pointer = diffs.listIterator()
        var prevDiff = if (pointer.hasNext()) pointer.next() else null
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        var nextDiff = if (pointer.hasNext()) pointer.next() else null

        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL &&
                nextDiff.operation == Operation.EQUAL
            ) {

                equality1 = prevDiff.text!!
                edit = thisDiff!!.text!!
                equality2 = nextDiff.text!!

                commonOffset = diff_commonSuffix(equality1, edit)
                if (commonOffset != 0) {
                    commonString = edit.substring(edit.length - commonOffset)
                    equality1 = equality1.substring(0, equality1.length - commonOffset)
                    edit = commonString + edit.substring(0, edit.length - commonOffset)
                    equality2 = commonString + equality2
                }

                bestEquality1 = equality1
                bestEdit = edit
                bestEquality2 = equality2
                bestScore = (diff_cleanupSemanticScore(equality1, edit)
                        + diff_cleanupSemanticScore(edit, equality2))
                while (((edit.length != 0) && equality2.length != 0) && edit[0] == equality2[0]) {
                    equality1 += edit[0]
                    edit = edit.substring(1) + equality2[0]
                    equality2 = equality2.substring(1)
                    score = (diff_cleanupSemanticScore(equality1, edit)
                            + diff_cleanupSemanticScore(edit, equality2))

                    if (score >= bestScore) {
                        bestScore = score
                        bestEquality1 = equality1
                        bestEdit = edit
                        bestEquality2 = equality2
                    }
                }

                if (prevDiff.text != bestEquality1) {

                    if (bestEquality1!!.length != 0) {
                        prevDiff.text = bestEquality1
                    } else {
                        pointer.previous()

                        pointer.previous()

                        pointer.previous()

                        pointer.remove()

                        pointer.next()

                        pointer.next()

                    }
                    thisDiff.text = bestEdit
                    if (bestEquality2!!.length != 0) {
                        nextDiff.text = bestEquality2
                    } else {
                        pointer.remove()

                        nextDiff = thisDiff
                        thisDiff = prevDiff
                    }
                }
            }
            prevDiff = thisDiff
            thisDiff = nextDiff
            nextDiff = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Given two strings, compute a score representing whether the internal
     * boundary falls on logical boundaries.
     * Scores range from 6 (best) to 0 (worst).
     * @param one First string.
     * @param two Second string.
     * @return The score.
     */
    private fun diff_cleanupSemanticScore(one: String?, two: String?): Int {
        if (one!!.length == 0 || two!!.length == 0) {

            return 6
        }


        val char1 = one[one.length - 1]
        val char2 = two[0]
        val nonAlphaNumeric1 = !Character.isLetterOrDigit(char1)
        val nonAlphaNumeric2 = !Character.isLetterOrDigit(char2)
        val whitespace1 = nonAlphaNumeric1 && Character.isWhitespace(char1)
        val whitespace2 = nonAlphaNumeric2 && Character.isWhitespace(char2)
        val lineBreak1 = (whitespace1
                && Character.getType(char1) == Character.CONTROL.toInt())
        val lineBreak2 = (whitespace2
                && Character.getType(char2) == Character.CONTROL.toInt())
        val blankLine1 = lineBreak1 && BLANKLINEEND.matcher(one).find()
        val blankLine2 = lineBreak2 && BLANKLINESTART.matcher(two).find()

        if (blankLine1 || blankLine2) {

            return 5
        } else if (lineBreak1 || lineBreak2) {

            return 4
        } else if (nonAlphaNumeric1 && !whitespace1 && whitespace2) {

            return 3
        } else if (whitespace1 || whitespace2) {

            return 2
        } else if (nonAlphaNumeric1 || nonAlphaNumeric2) {

            return 1
        }
        return 0
    }

    private val BLANKLINEEND
            : Pattern = Pattern.compile("\\n\\r?\\n\\Z", Pattern.DOTALL)
    private val BLANKLINESTART
            : Pattern = Pattern.compile("\\A\\r?\\n\\r?\\n", Pattern.DOTALL)

    /**
     * Reduce the number of edits by eliminating operationally trivial equalities.
     * @param diffs LinkedList of Diff objects.
     */
    fun diff_cleanupEfficiency(diffs: LinkedList<Diff>) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities = ArrayDeque<Diff>()

        var lastEquality: String? = null

        val pointer = diffs.listIterator()

        var pre_ins = false

        var pre_del = false

        var post_ins = false

        var post_del = false
        var thisDiff: Diff? = pointer.next()
        var safeDiff = thisDiff

        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {

                if (thisDiff.text!!.length < Diff_EditCost && (post_ins || post_del)) {

                    equalities.push(thisDiff)
                    pre_ins = post_ins
                    pre_del = post_del
                    lastEquality = thisDiff.text
                } else {

                    equalities.clear()
                    lastEquality = null
                    safeDiff = thisDiff
                }
                post_del = false
                post_ins = post_del
            } else {

                if (thisDiff.operation == Operation.DELETE) {
                    post_del = true
                } else {
                    post_ins = true
                }
                /*
                         * Five types to be split:
                         * <ins>A</ins><del>B</del>XY<ins>C</ins><del>D</del>
                         * <ins>A</ins>X<ins>C</ins><del>D</del>
                         * <ins>A</ins><del>B</del>X<ins>C</ins>
                         * <ins>A</del>X<ins>C</ins><del>D</del>
                         * <ins>A</ins><del>B</del>X<del>C</del>
                         */
                if (lastEquality != null
                    && ((pre_ins && pre_del && post_ins && post_del)
                            || ((lastEquality.length < Diff_EditCost / 2)
                            && ((if (pre_ins) 1 else 0) + (if (pre_del) 1 else 0)
                            + (if (post_ins) 1 else 0) + (if (post_del) 1 else 0)) == 3))
                ) {


                    while (thisDiff !== equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    pointer.set(Diff(Operation.DELETE, lastEquality))

                    pointer.add(Diff(Operation.INSERT, lastEquality).also {
                        thisDiff = it
                    })

                    equalities.pop()

                    lastEquality = null
                    if (pre_ins && pre_del) {

                        post_del = true
                        post_ins = post_del
                        equalities.clear()
                        safeDiff = thisDiff
                    } else {
                        if (!equalities.isEmpty()) {

                            equalities.pop()
                        }
                        if (equalities.isEmpty()) {


                            thisDiff = safeDiff
                        } else {

                            thisDiff = equalities.peek()
                        }
                        post_del = false
                        post_ins = post_del
                    }

                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        if (changes) {
            diff_cleanupMerge(diffs)
        }
    }

    /**
     * Reorder and merge like edit sections.  Merge equalities.
     * Any edit section can move as long as it doesn't cross an equality.
     * @param diffs LinkedList of Diff objects.
     */
    private fun diff_cleanupMerge(diffs: LinkedList<Diff>) {
        diffs.add(Diff(Operation.EQUAL, ""))

        var pointer = diffs.listIterator()
        var count_delete = 0
        var count_insert = 0
        var text_delete: String? = ""
        var text_insert: String? = ""
        var thisDiff: Diff? = pointer.next()
        var prevEqual: Diff? = null
        var commonlength: Int
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    count_insert++
                    text_insert += thisDiff.text
                    prevEqual = null
                }

                Operation.DELETE -> {
                    count_delete++
                    text_delete += thisDiff.text
                    prevEqual = null
                }

                Operation.EQUAL -> {
                    if (count_delete + count_insert > 1) {
                        val both_types = count_delete != 0 && count_insert != 0

                        pointer.previous()

                        while (count_delete-- > 0) {
                            pointer.previous()
                            pointer.remove()
                        }
                        while (count_insert-- > 0) {
                            pointer.previous()
                            pointer.remove()
                        }
                        if (both_types) {

                            commonlength = diff_commonPrefix(text_insert, text_delete)
                            if (commonlength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous()
                                    assert(
                                        thisDiff.operation == Operation.EQUAL
                                    ) { "Previous diff should have been an equality." }
                                    thisDiff.text += text_insert!!.substring(0, commonlength)
                                    pointer.next()
                                } else {
                                    pointer.add(
                                        Diff(
                                            Operation.EQUAL,
                                            text_insert!!.substring(0, commonlength)
                                        )
                                    )
                                }
                                text_insert = text_insert.substring(commonlength)
                                text_delete = text_delete!!.substring(commonlength)
                            }

                            commonlength = diff_commonSuffix(text_insert, text_delete)
                            if (commonlength != 0) {
                                thisDiff = pointer.next()
                                thisDiff.text = text_insert!!.substring(
                                    text_insert.length
                                            - commonlength
                                ) + thisDiff.text
                                text_insert = text_insert.substring(
                                    0, text_insert.length
                                            - commonlength
                                )
                                text_delete = text_delete!!.substring(
                                    0, text_delete.length
                                            - commonlength
                                )
                                pointer.previous()
                            }
                        }

                        if (text_delete!!.length != 0) {
                            pointer.add(Diff(Operation.DELETE, text_delete))
                        }
                        if (text_insert!!.length != 0) {
                            pointer.add(Diff(Operation.INSERT, text_insert))
                        }

                        thisDiff = if (pointer.hasNext()) pointer.next() else null
                    } else if (prevEqual != null) {

                        prevEqual.text += thisDiff.text
                        pointer.remove()
                        thisDiff = pointer.previous()
                        pointer.next()

                    }
                    count_insert = 0
                    count_delete = 0
                    text_delete = ""
                    text_insert = ""
                    prevEqual = thisDiff
                }

                null -> TODO()
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        if (diffs.last.text!!.length == 0) {
            diffs.removeLast()

        }

        /*
             * Second pass: look for single edits surrounded on both sides by equalities
             * which can be shifted sideways to eliminate an equality.
             * e.g: A<ins>BA</ins>C -> <ins>AB</ins>AC
             */
        var changes = false


        pointer = diffs.listIterator()
        var prevDiff = if (pointer.hasNext()) pointer.next() else null
        thisDiff = if (pointer.hasNext()) pointer.next() else null
        var nextDiff = if (pointer.hasNext()) pointer.next() else null

        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL &&
                nextDiff.operation == Operation.EQUAL
            ) {

                if (thisDiff!!.text!!.endsWith(prevDiff.text!!)) {

                    thisDiff.text = (prevDiff.text
                            + thisDiff.text!!.substring(
                        0, thisDiff.text!!.length
                                - prevDiff.text!!.length
                    ))
                    nextDiff.text = prevDiff.text + nextDiff.text
                    pointer.previous()

                    pointer.previous()

                    pointer.previous()

                    pointer.remove()

                    pointer.next()

                    thisDiff = pointer.next()

                    nextDiff = if (pointer.hasNext()) pointer.next() else null
                    changes = true
                } else if (thisDiff.text!!.startsWith(nextDiff.text!!)) {

                    prevDiff.text += nextDiff.text
                    thisDiff.text = (thisDiff.text!!.substring(nextDiff.text!!.length)
                            + nextDiff.text)
                    pointer.remove()

                    nextDiff = if (pointer.hasNext()) pointer.next() else null
                    changes = true
                }
            }
            prevDiff = thisDiff
            thisDiff = nextDiff
            nextDiff = if (pointer.hasNext()) pointer.next() else null
        }

        if (changes) {
            diff_cleanupMerge(diffs)
        }
    }

    /**
     * Increase the context until it is unique,
     * but don't let the pattern expand beyond Match_MaxBits.
     * @param patch The patch to grow.
     * @param text Source text.
     */
    private fun patch_addContext(patch: Patch, text: String) {
        if (text.length == 0) {
            return
        }
        var pattern = text.substring(patch.start2, patch.start2 + patch.length1)
        var padding = 0


        while ((text.indexOf(pattern) != text.lastIndexOf(pattern)
                    && pattern.length < Match_MaxBits - Patch_Margin - Patch_Margin)
        ) {
            padding += Patch_Margin.toInt()
            pattern = text.substring(
                max(0.0, (patch.start2 - padding).toDouble()).toInt(),
                min(text.length.toDouble(), (patch.start2 + patch.length1 + padding).toDouble()).toInt()
            )
        }

        padding += Patch_Margin.toInt()

        val prefix = text.substring(
            max(0.0, (patch.start2 - padding).toDouble()).toInt(),
            patch.start2
        )
        if (prefix.length != 0) {
            patch.diffs.addFirst(Diff(Operation.EQUAL, prefix))
        }

        val suffix = text.substring(
            patch.start2 + patch.length1,
            min(text.length.toDouble(), (patch.start2 + patch.length1 + padding).toDouble()).toInt()
        )
        if (suffix.length != 0) {
            patch.diffs.addLast(Diff(Operation.EQUAL, suffix))
        }

        patch.start1 -= prefix.length
        patch.start2 -= prefix.length

        patch.length1 += prefix.length + suffix.length
        patch.length2 += prefix.length + suffix.length
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is ignored, diffs are the delta between text1 and text2.
     * @param text1 Old text
     * @param text2 Ignored.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of Patch objects.
     */
    @Deprecated("Prefer patch_make(String text1, LinkedList<Diff> diffs).")
    fun patch_make(
        text1: String?, text2: String?,
        diffs: LinkedList<Diff>?
    ): LinkedList<Patch> {
        return patch_make(text1, diffs)
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is not provided, diffs are the delta between text1 and text2.
     * @param text1 Old text.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of Patch objects.
     */
    fun patch_make(text1: String?, diffs: LinkedList<Diff>?): LinkedList<Patch> {
        if (text1 == null || diffs == null) {
            throw IllegalArgumentException("Null inputs. (patch_make)")
        }

        val patches = LinkedList<Patch>()
        if (diffs.isEmpty()) {
            return patches

        }
        var patch = Patch()
        var char_count1 = 0

        var char_count2 = 0


        var prepatch_text: String = text1
        var postpatch_text: String = text1
        for (aDiff: Diff in diffs) {
            if (patch.diffs.isEmpty() && aDiff.operation != Operation.EQUAL) {

                patch.start1 = char_count1
                patch.start2 = char_count2
            }

            when (aDiff.operation) {
                Operation.INSERT -> {
                    patch.diffs.add(aDiff)
                    patch.length2 += aDiff.text!!.length
                    postpatch_text = (postpatch_text.substring(0, char_count2)
                            + aDiff.text + postpatch_text.substring(char_count2))
                }

                Operation.DELETE -> {
                    patch.length1 += aDiff.text!!.length
                    patch.diffs.add(aDiff)
                    postpatch_text = (postpatch_text.substring(0, char_count2)
                            + postpatch_text.substring(char_count2 + aDiff.text!!.length))
                }

                Operation.EQUAL -> {
                    if ((aDiff.text!!.length <= 2 * Patch_Margin
                                ) && !patch.diffs.isEmpty() && (aDiff !== diffs.last)
                    ) {

                        patch.diffs.add(aDiff)
                        patch.length1 += aDiff.text!!.length
                        patch.length2 += aDiff.text!!.length
                    }

                    if (aDiff.text!!.length >= 2 * Patch_Margin && !patch.diffs.isEmpty()) {

                        if (!patch.diffs.isEmpty()) {
                            patch_addContext(patch, prepatch_text)
                            patches.add(patch)
                            patch = Patch()




                            prepatch_text = postpatch_text
                            char_count1 = char_count2
                        }
                    }
                }

                null -> TODO()
            }

            if (aDiff.operation != Operation.INSERT) {
                char_count1 += aDiff.text!!.length
            }
            if (aDiff.operation != Operation.DELETE) {
                char_count2 += aDiff.text!!.length
            }
        }

        if (!patch.diffs.isEmpty()) {
            patch_addContext(patch, prepatch_text)
            patches.add(patch)
        }

        return patches
    }

    /**
     * Take a list of patches and return a textual representation.
     * @param patches List of Patch objects.
     * @return Text representation of patches.
     */
    fun patch_toText(patches: List<Patch?>): String {
        val text = StringBuilder()
        for (aPatch: Patch? in patches) {
            text.append(aPatch)
        }
        return text.toString()
    }

    /**
     * Parse a textual representation of patches and return a List of Patch
     * objects.
     * @param textline Text representation of patches.
     * @return List of Patch objects.
     * @throws IllegalArgumentException If invalid input.
     */
    @Throws(IllegalArgumentException::class)
    fun patch_fromText(textline: String): List<Patch> {
        val patches: MutableList<Patch> = LinkedList()
        if (textline.length == 0) {
            return patches
        }
        val textList = listOf(*textline.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
        val text = LinkedList(textList)
        var patch: Patch
        val patchHeader = Pattern.compile("^@@ -(\\d+),?(\\d*) \\+(\\d+),?(\\d*) @@$")
        var m: Matcher
        var sign: Char
        var line: String
        while (!text.isEmpty()) {
            m = patchHeader.matcher(text.first)
            if (!m.matches()) {
                throw IllegalArgumentException(
                    "Invalid patch string: " + text.first
                )
            }
            patch = Patch()
            patches.add(patch)
            patch.start1 = m.group(1).toInt()
            if (m.group(2).length == 0) {
                patch.start1--
                patch.length1 = 1
            } else if ((m.group(2) == "0")) {
                patch.length1 = 0
            } else {
                patch.start1--
                patch.length1 = m.group(2).toInt()
            }

            patch.start2 = m.group(3).toInt()
            if (m.group(4).length == 0) {
                patch.start2--
                patch.length2 = 1
            } else if ((m.group(4) == "0")) {
                patch.length2 = 0
            } else {
                patch.start2--
                patch.length2 = m.group(4).toInt()
            }
            text.removeFirst()

            while (!text.isEmpty()) {
                try {
                    sign = text.first[0]
                } catch (e: IndexOutOfBoundsException) {

                    text.removeFirst()
                    continue
                }
                line = text.first.substring(1)
                line = line.replace("+", "%2B")

                try {
                    line = URLDecoder.decode(line, "UTF-8")
                } catch (e: UnsupportedEncodingException) {

                    throw Error("This system does not support UTF-8.", e)
                } catch (e: IllegalArgumentException) {

                    throw IllegalArgumentException(
                        "Illegal escape in patch_fromText: $line", e
                    )
                }
                if (sign == '-') {

                    patch.diffs.add(Diff(Operation.DELETE, line))
                } else if (sign == '+') {

                    patch.diffs.add(Diff(Operation.INSERT, line))
                } else if (sign == ' ') {

                    patch.diffs.add(Diff(Operation.EQUAL, line))
                } else if (sign == '@') {

                    break
                } else {

                    throw IllegalArgumentException(
                        "Invalid patch mode '$sign' in: $line"
                    )
                }
                text.removeFirst()
            }
        }
        return patches
    }

    /**
     * Class representing one diff operation.
     */
    class Diff// Construct a diff with the specified operation and text.
    /**
     * Constructor.  Initializes the diff with the provided values.
     * @param operation One of INSERT, DELETE or EQUAL.
     * @param text The text being applied.
     */(
        /**
         * One of: INSERT, DELETE or EQUAL.
         */
        var operation: Operation?,
        /**
         * The text associated with this diff operation.
         */
        var text: String?
    ) {
        /**
         * Display a human-readable version of this Diff.
         * @return text version.
         */
        override fun toString(): String {
            val prettyText = text!!.replace('\n', '\u00b6')
            return "Diff(" + this.operation + ",\"" + prettyText + "\")"
        }

        /**
         * Create a numeric hash value for a Diff.
         * This function is not used by DMP.
         * @return Hash value.
         */
        override fun hashCode(): Int {
            val prime = 31
            var result = operation?.hashCode() ?: 0
            result += prime * (text?.hashCode() ?: 0)
            return result
        }

        /**
         * Is this Diff equivalent to another Diff?
         * @param obj Another Diff to compare against.
         * @return true or false.
         */
        override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as Diff
            if (operation != other.operation) {
                return false
            }
            if (text == null) {
                if (other.text != null) {
                    return false
                }
            } else if (text != other.text) {
                return false
            }
            return true
        }
    }

    /**
     * Class representing one patch operation.
     */
    class Patch {
        var diffs: LinkedList<Diff> = LinkedList()
        var start1: Int = 0
        var start2: Int = 0
        var length1: Int = 0
        var length2: Int = 0

        /**
         * Emulate GNU diff's format.
         * Header: @@ -382,8 +481,9 @@
         * Indices are printed as 1-based, not 0-based.
         * @return The GNU diff string.
         */
        override fun toString(): String {
            val coords1: String
            val coords2: String
            if (this.length1 == 0) {
                coords1 = start1.toString() + ",0"
            } else if (this.length1 == 1) {
                coords1 = (this.start1 + 1).toString()
            } else {
                coords1 = (this.start1 + 1).toString() + "," + this.length1
            }
            if (this.length2 == 0) {
                coords2 = start2.toString() + ",0"
            } else if (this.length2 == 1) {
                coords2 = (this.start2 + 1).toString()
            } else {
                coords2 = (this.start2 + 1).toString() + "," + this.length2
            }
            val text = StringBuilder()
            text.append("@@ -").append(coords1).append(" +").append(coords2)
                .append(" @@\n")

            for (aDiff: Diff in this.diffs) {
                when (aDiff.operation) {
                    Operation.INSERT -> text.append('+')
                    Operation.DELETE -> text.append('-')
                    Operation.EQUAL -> text.append(' ')
                    null -> TODO()
                }
                try {
                    text.append(URLEncoder.encode(aDiff.text, "UTF-8").replace('+', ' '))
                        .append("\n")
                } catch (e: UnsupportedEncodingException) {

                    throw Error("This system does not support UTF-8.", e)
                }
            }
            return unescapeForEncodeUriCompatability(text.toString())
        }
    }

    companion object : DiffMatchPatch() {
        /**
         * Unescape selected chars for compatability with JavaScript's encodeURI.
         * In speed critical applications this could be dropped since the
         * receiving application will certainly decode these fine.
         * Note that this function is case-sensitive.  Thus "%3f" would not be
         * unescaped.  But this is ok because it is only called with the output of
         * URLEncoder.encode which returns uppercase hex.
         *
         * Example: "%3F" -> "?", "%24" -> "$", etc.
         *
         * @param str The string to escape.
         * @return The escaped string.
         */
        private fun unescapeForEncodeUriCompatability(str: String): String {
            return str.replace("%21", "!").replace("%7E", "~")
                .replace("%27", "'").replace("%28", "(").replace("%29", ")")
                .replace("%3B", ";").replace("%2F", "/").replace("%3F", "?")
                .replace("%3A", ":").replace("%40", "@").replace("%26", "&")
                .replace("%3D", "=").replace("%2B", "+").replace("%24", "$")
                .replace("%2C", ",").replace("%23", "#")
        }
    }
}