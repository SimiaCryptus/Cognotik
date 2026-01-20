package com.simiacryptus.cognotik.docs

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

class HTMLReader(htmlFile: File) : PaginatedDocumentReader {
    private val document: Document = Jsoup.parse(htmlFile, "UTF-8")
    private val fullText: String = document.body().text()
    private val pages: List<String> by lazy { splitIntoPages(fullText) }
    private var settings: Settings? = null

    fun configure(settings: Settings) {
        this.settings = settings
    }

    override fun getText(): String {
        return if (settings?.addLineNumbers == true) {
            fullText.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else fullText
    }


    override fun getPageCount(): Int = pages.size

    override fun getText(startPage: Int, endPage: Int): String {
        val text = pages.subList(startPage, endPage.coerceAtMost(pages.size)).joinToString("\n")
        return if (settings?.addLineNumbers == true) {
            text.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else text
    }


    override fun close() {

    }

    private fun splitIntoPages(text: String, maxChars: Int = 16000): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val paragraphs = text.split(Regex("\\n\\s*\\n"))

        val pages = mutableListOf<String>()
        var currentPage = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentPage.length + paragraph.length > maxChars) {
                if (currentPage.isNotEmpty()) {
                    pages.add(currentPage.toString())
                    currentPage = StringBuilder()
                }

                if (paragraph.length > maxChars) {
                    val words = paragraph.split(" ")
                    var currentChunk = StringBuilder()

                    for (word in words) {
                        if (currentChunk.length + word.length > maxChars) {
                            pages.add(currentChunk.toString())
                            currentChunk = StringBuilder()
                        }
                        if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                        currentChunk.append(word)
                    }
                    if (currentChunk.isNotEmpty()) {
                        currentPage.append(currentChunk)
                    }
                } else {
                    currentPage.append(paragraph)
                }
            } else {
                if (currentPage.isNotEmpty()) currentPage.append("\n\n")
                currentPage.append(paragraph)
            }
        }

        if (currentPage.isNotEmpty()) {
            pages.add(currentPage.toString())
        }

        return pages
    }
}