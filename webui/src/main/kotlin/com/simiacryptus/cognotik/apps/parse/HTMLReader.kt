package com.simiacryptus.cognotik.apps.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.awt.image.BufferedImage
import java.io.File

class HTMLReader(private val htmlFile: File) : DocumentParserApp.DocumentReader {
    private val document: Document = Jsoup.parse(htmlFile, "UTF-8")
    private val pages: List<String> = splitIntoPages(document.body().text())
    private lateinit var settings: DocumentParserApp.Settings

    fun configure(settings: DocumentParserApp.Settings) {
        this.settings = settings
    }

    override fun getPageCount(): Int = pages.size

    override fun getText(startPage: Int, endPage: Int): String {
        val text = pages.subList(startPage, endPage.coerceAtMost(pages.size)).joinToString("\n")
        return if (::settings.isInitialized && settings.addLineNumbers) {
            text.lines().mapIndexed { index, line ->
                "${(index + 1).toString().padStart(6)}: $line"
            }.joinToString("\n")
        } else text
    }

    override fun renderImage(pageIndex: Int, dpi: Float): BufferedImage {
        throw UnsupportedOperationException("HTML files do not support image rendering")
    }

    override fun close() {
        // No resources to close for HTML files
    }

    private fun splitIntoPages(text: String, maxChars: Int = 16000): List<String> {
        if (text.length <= maxChars) return listOf(text)

        // Split on paragraph boundaries when possible
        val paragraphs = text.split(Regex("\\n\\s*\\n"))

        val pages = mutableListOf<String>()
        var currentPage = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentPage.length + paragraph.length > maxChars) {
                if (currentPage.isNotEmpty()) {
                    pages.add(currentPage.toString())
                    currentPage = StringBuilder()
                }
                // If a single paragraph is longer than maxChars, split it
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