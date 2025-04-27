package com.simiacryptus.jopenai.util

import com.simiacryptus.jopenai.util.GPT4CodecData.bpeRegex
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import kotlin.math.min
import kotlin.math.pow
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

@Suppress("unused")
@OptIn(ExperimentalStdlibApi::class)
class GPT4Tokenizer(isCodex: Boolean = false) {
    private val log = LoggerFactory.getLogger(GPT4Tokenizer::class.java)

    class TextEncoder {
        fun encode(text: String): ByteArray {
            return text.map { c -> c.code }.map { i -> i.toByte() }.toByteArray()
        }

    }

    class TextDecoder {
        fun decode(bytes: ByteArray): String {
            return bytes.map { b -> b.toInt() }.joinToString("") { i ->
                chr(
                    i
                )
            }
        }
    }

    companion object {

        val codecJson =
            GPT4Tokenizer::class.java.getResourceAsStream("/gpt4.json")?.readAllBytes()?.toString(Charsets.UTF_8) ?: ""

        fun <E> List<E>.indexOf2(element: E, minIndex: Int): Int {
            for (i in minIndex until this.size) {
                if (this[i] == element) {
                    return i
                }
            }
            return -1
        }

        val range = { x: Int, y: Int ->
            val res = (Array(y, { i -> i + x })).toList()
            res
        }

        val ord = { x: String ->
            x[0].code
        }

        val chr = { n: Int ->
            n.toChar().toString()
        }

    }

    private val vocab: String
    private val nMergedSpaces: Int
    private val nVocab: Int

    private val encodings: HashMap<String, Int>
    private val decodings: HashMap<Int, String>

    private var byteEncoder: HashMap<Int, String>
    private val byteDecoder: HashMap<String, Int>

    private val bpeRanks: HashMap<Pair<String, String>, Int>
    private val cache: HashMap<String, String>
    private val encodeCache = HashMap<String, List<Int>>()

    private val textEncoder: TextEncoder
    private val textDecoder: TextDecoder

    init {
        log.info("Initializing GPT4Tokenizer")
        this.encodings = JsonUtil.fromJson(codecJson, typeOf<HashMap<String, Int>>().javaType)
        this.vocab = GPT4CodecData.bpeVocab
        this.nMergedSpaces = if (isCodex) 24 else 0
        this.nVocab = 50257 + this.nMergedSpaces
        this.decodings = HashMap()
        this.bpeRanks = HashMap()
        this.byteEncoder = HashMap()
        this.byteDecoder = HashMap()
        this.cache = HashMap()

        this.textEncoder = TextEncoder()
        this.textDecoder = TextDecoder()

        this.initialize()
    }

    private fun initialize() {
        log.debug("Initializing tokenizer data structures")
        if (this.vocab.length < 100) {
            throw Exception("Tokenizer vocab file did not load correctly")
        }

        val vocabLines = this.vocab.split("\n")
        val bpeMerges: List<Pair<String, String>> = vocabLines
            .subList(1, vocabLines.size - 1)
            .map { line -> line.split(Regex("(\\s+)")).filter { part -> part.trim().length > 0 } }
            .map { list -> Pair(list[0], list[1]) }

        if (this.nMergedSpaces > 0) {
            for (i in 1..this.nMergedSpaces) {
                for (j in 1..this.nMergedSpaces) {
                    if (i + j <= this.nMergedSpaces) {
                        bpeMerges.plus(Pair("\u0120".repeat(i), "\u0120".repeat(j)))
                    }
                }
            }

            for (i in 0..this.nMergedSpaces) {
                this.encodings["\u0120".repeat(i + 2)] = this.nVocab - this.nMergedSpaces + i
            }
        }

        for (key in this.encodings.keys) {
            this.decodings[encodings[key]!!] = key
        }

        this.byteEncoder = this.bytesToUnicode()

        this.byteEncoder.forEach { (key, value) ->
            this.byteDecoder[value] = key
        }

        this.zip(this.bpeRanks, bpeMerges, range(0, bpeMerges.size))
    }

    fun <X, Y> zip(map: HashMap<X, Y>, first: List<X>, second: List<Y>): HashMap<X, Y> {
        val length = first.size
        for (idx in 0 until length) {
            map[first[idx]] = second[idx]
        }
        return map
    }

    private fun bytesToUnicode(): HashMap<Int, String> {
        val bs = (range(
            ord(
                "!"
            ), ord("~") + 1
        ) +
                range(
                    ord(
                        "\\xa1"
                    ), ord("\\xac") + 1
                ) +
                range(
                    ord(
                        "\\xae"
                    ), ord("\\xff") + 1
                )).toMutableList()

        val cs: MutableList<Int> = bs.toMutableList()
        var n = 0

        for (b in 0 until 2.0.pow(8.0).toInt()) {
            if (!bs.contains(b)) {
                bs.add(b)
                cs.add(2.0.pow(8.0).toInt() + n)
                n = n + 1
            }
        }

        val csStr = cs.map { it -> chr(it) }

        val result = HashMap<Int, String>()
        zip(result, bs, csStr)
        return result
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        var prevChar = word[0]

        for (i in 1 until word.size) {
            val char = word[i]
            pairs.add(Pair(prevChar, char))
            prevChar = char
        }

        return pairs
    }

    private fun bpe(token: String): String {

        if (this.cache.containsKey(token)) {

            return this.cache[token]!!
        }

        var word = token.toCharArray().map { it.toString() }

        var pairs = this.getPairs(word)

        if (pairs.isEmpty()) {

            return token
        }

        while (true) {
            var minRank = Integer.MAX_VALUE
            var bigram: Pair<String, String> = Pair("", "")
            for (pair in pairs) {
                val rank = this.bpeRanks[pair]
                val realRank = rank ?: Integer.MAX_VALUE
                if (realRank < minRank) {
                    bigram = pair
                    minRank = realRank
                }
            }

            if (!this.bpeRanks.containsKey(bigram)) {
                break
            }

            val first = bigram.first
            val second = bigram.second
            val newWord: MutableList<String> = mutableListOf()
            var i = 0

            while (i < word.size) {
                val j = word.indexOf2(first, i)
                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }
                newWord.addAll(word.subList(i, j))
                i = j

                if (word[i] == first && i < word.size - 1 && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }

            word = newWord
            if (word.size == 1) {
                break
            } else {
                pairs = this.getPairs(newWord.toMutableList())
            }
        }

        val finalWord = word.joinToString(separator = " ")
        this.cache[token] = finalWord


        return finalWord
    }

    fun encode(text: String): MutableList<Int> {

        val bpeTokens: MutableList<Int> = mutableListOf()
        val matches = bpeRegex.toRegex().findAll(text).flatMap { it.groupValues }.toList().toTypedArray()

        for (token in matches) {
            var newTokens = this.encodeCache[token]
            if (newTokens == null) {
                val joinToString = token.toCharArray()
                    .map { this.byteEncoder[it.code] }
                    .joinToString(separator = "")
                val tokens = this.bpe(joinToString)
                newTokens = tokens
                    .split(" ")
                    .map { this.encodings[it]!! }
                this.encodeCache[token] = newTokens
            }
            for (i in newTokens.indices) {
                bpeTokens.add(newTokens[i])
            }
        }


        return bpeTokens
    }

    fun encodeUtf8(text: String): ByteArray {
        log.debug("Encoding text to UTF-8: {}", text)
        return this.textEncoder.encode(text)
    }

    fun decodeUtf8(bytes: ByteArray): String {
        log.debug("Decoding UTF-8 bytes")
        return this.textDecoder.decode(bytes)
    }

    fun decode(tokens: List<Int>): String {

        val text = tokens.map { x -> this.decodings[x] }.joinToString(separator = "")
        return String(
            text.toCharArray().map { this.byteDecoder[it.toString()]?.toByte() ?: 0 }.toTypedArray().toByteArray(),
            Charset.forName("UTF-8")
        )
    }

    fun estimateTokenCount(input: String): Int {

        if (input.length >= 0) {
            return input.length / 3
        }
        var count: Int = 0
        val matches = bpeRegex.toRegex().findAll(input).flatMap { it.groupValues }.toList().toTypedArray()
        for (token in matches) {
            var newToken = token.toCharArray()
                .map { this.byteEncoder[it.code] }
                .joinToString(separator = "")
            val newTokens = this.bpe(newToken).split(" ")
            count += newTokens.size
        }

        return count
    }

    fun chunkText(text: String, maxTokensPerChunk: Int): MutableList<Map<String, Any>> {

        val encoded = this.encode(text)
        val chunks: MutableList<Map<String, Any>> = mutableListOf()
        for (i in encoded.indices step maxTokensPerChunk) {
            val chunk = encoded.subList(i, min(i + maxTokensPerChunk, encoded.size))
            chunks.add(
                mapOf(
                    "text" to this.decode(chunk),
                    "bpe" to chunk
                )
            )

        }

        return chunks
    }
}