package com.simiacryptus.cognotik.text.caveman

/** Swappable morphological reduction (spec 4.3 "select among alternate stemming algorithms"). */
interface Stemmer {
  val id: String
  fun stem(word: String): String
}

object NoOpStemmer : Stemmer {
  override val id: String = "none"
  override fun stem(word: String): String = word
}

/**
 * Classic Porter (1980) stemmer, ported to Kotlin with per-call state so that the
 * instance is thread-safe and byte-for-byte deterministic.
 */
class PorterStemmer : Stemmer {

  override val id: String = "porter"

  override fun stem(word: String): String {
    if (word.length < 3) return word
    var lettersOnly = true
    for (c in word) if (!c.isLetter()) {
      lettersOnly = false
      break
    }
    if (!lettersOnly) return word
    val lower = word.lowercase()
    val stemmed = Work(lower).stem()
    return if (word[0].isUpperCase() && stemmed.isNotEmpty()) {
      stemmed.substring(0, 1).uppercase() + stemmed.substring(1)
    } else {
      stemmed
    }
  }

  private class Work(word: String) {
    private var b: CharArray = CharArray(word.length + 4).also { arr ->
      for (i in word.indices) arr[i] = word[i]
    }
    private var k: Int = word.length - 1
    private var j: Int = 0
    private val k0: Int = 0

    fun stem(): String {
      step1ab()
      step1c()
      step2()
      step3()
      step4()
      step5()
      return String(b, k0, k - k0 + 1)
    }

    private fun cons(i: Int): Boolean = when (b[i]) {
      'a', 'e', 'i', 'o', 'u' -> false
      'y' -> i == k0 || !cons(i - 1)
      else -> true
    }

    /** Measure: number of vowel-consonant sequences between k0 and j. */
    private fun m(): Int {
      var n = 0
      var i = k0
      while (true) {
        if (i > j) return n
        if (!cons(i)) break
        i++
      }
      i++
      while (true) {
        while (true) {
          if (i > j) return n
          if (cons(i)) break
          i++
        }
        i++
        n++
        while (true) {
          if (i > j) return n
          if (!cons(i)) break
          i++
        }
        i++
      }
    }

    private fun vowelInStem(): Boolean {
      for (i in k0..j) if (!cons(i)) return true
      return false
    }

    private fun doubleC(index: Int): Boolean {
      if (index < k0 + 1) return false
      if (b[index] != b[index - 1]) return false
      return cons(index)
    }

    private fun cvc(i: Int): Boolean {
      if (i < k0 + 2 || !cons(i) || cons(i - 1) || !cons(i - 2)) return false
      val ch = b[i]
      return !(ch == 'w' || ch == 'x' || ch == 'y')
    }

    private fun ends(s: String): Boolean {
      val l = s.length
      val o = k - l + 1
      if (o < k0) return false
      for (i in 0 until l) if (b[o + i] != s[i]) return false
      j = k - l
      return true
    }

    private fun setTo(s: String) {
      val l = s.length
      val o = j + 1
      if (o + l > b.size) b = b.copyOf(o + l)
      for (i in 0 until l) b[o + i] = s[i]
      k = j + l
    }

    private fun replace(s: String) {
      if (m() > 0) setTo(s)
    }

    private fun step1ab() {
      if (b[k] == 's') {
        when {
          ends("sses") -> k -= 2
          ends("ies") -> setTo("i")
          b[k - 1] != 's' -> k--
        }
      }
      if (ends("eed")) {
        if (m() > 0) k--
      } else if ((ends("ed") || ends("ing")) && vowelInStem()) {
        k = j
        when {
          ends("at") -> setTo("ate")
          ends("bl") -> setTo("ble")
          ends("iz") -> setTo("ize")
          doubleC(k) -> {
            k--
            val ch = b[k]
            if (ch == 'l' || ch == 's' || ch == 'z') k++
          }

          m() == 1 && cvc(k) -> setTo("e")
        }
      }
    }

    private fun step1c() {
      if (ends("y") && vowelInStem()) b[k] = 'i'
    }

    private fun step2() {
      when {
        ends("ational") -> replace("ate")
        ends("tional") -> replace("tion")
        ends("enci") -> replace("ence")
        ends("anci") -> replace("ance")
        ends("izer") -> replace("ize")
        ends("bli") -> replace("ble")
        ends("alli") -> replace("al")
        ends("entli") -> replace("ent")
        ends("eli") -> replace("e")
        ends("ousli") -> replace("ous")
        ends("ization") -> replace("ize")
        ends("ation") -> replace("ate")
        ends("ator") -> replace("ate")
        ends("alism") -> replace("al")
        ends("iveness") -> replace("ive")
        ends("fulness") -> replace("ful")
        ends("ousness") -> replace("ous")
        ends("aliti") -> replace("al")
        ends("iviti") -> replace("ive")
        ends("biliti") -> replace("ble")
        ends("logi") -> replace("log")
      }
    }

    private fun step3() {
      when {
        ends("icate") -> replace("ic")
        ends("ative") -> replace("")
        ends("alize") -> replace("al")
        ends("iciti") -> replace("ic")
        ends("ical") -> replace("ic")
        ends("ful") -> replace("")
        ends("ness") -> replace("")
      }
    }

    private fun step4() {
      val matched = when {
        ends("al") -> true
        ends("ance") -> true
        ends("ence") -> true
        ends("er") -> true
        ends("ic") -> true
        ends("able") -> true
        ends("ible") -> true
        ends("ant") -> true
        ends("ement") -> true
        ends("ment") -> true
        ends("ent") -> true
        ends("ion") -> j >= k0 && (b[j] == 's' || b[j] == 't')
        ends("ou") -> true
        ends("ism") -> true
        ends("ate") -> true
        ends("iti") -> true
        ends("ous") -> true
        ends("ive") -> true
        ends("ize") -> true
        else -> false
      }
      if (matched && m() > 1) k = j
    }

    private fun step5() {
      j = k
      if (b[k] == 'e') {
        val a = m()
        if (a > 1 || (a == 1 && !cvc(k - 1))) k--
      }
      if (b[k] == 'l' && doubleC(k)) {
        j = k
        if (m() > 1) k--
      }
    }
  }
}

/**
 * Conservative alternative to Porter: collapses only plurals and the most common
 * inflections. Useful when Porter's over-stemming hurts prompt readability.
 */
class LightEnglishStemmer : Stemmer {

  override val id: String = "light-english"

  override fun stem(word: String): String {
    if (word.length < 4) return word
    val w = word.lowercase()
    val result = when {
      w.endsWith("sses") -> w.dropLast(2)
      w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
      w.endsWith("ss") -> w
      w.endsWith("s") && !w.endsWith("us") -> w.dropLast(1)
      w.endsWith("ing") && w.length > 5 -> deDouble(w.dropLast(3))
      w.endsWith("ed") && w.length > 4 -> deDouble(w.dropLast(2))
      else -> w
    }
    return if (word[0].isUpperCase()) result.substring(0, 1).uppercase() + result.substring(1) else result
  }

  private fun deDouble(value: String): String =
    if (value.length > 2 && value[value.length - 1] == value[value.length - 2] &&
      value[value.length - 1] !in "lsz"
    ) value.dropLast(1) else value
}