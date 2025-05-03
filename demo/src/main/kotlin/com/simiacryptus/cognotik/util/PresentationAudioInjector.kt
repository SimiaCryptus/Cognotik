package com.simiacryptus.cognotik.util

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.AudioModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object PresentationAudioInjector {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    fun processPresentation(htmlFilePath: String, outputDir: String) {
        log.info("Processing presentation: htmlFilePath={}, outputDir={}", htmlFilePath, outputDir)
        val htmlContent = File(htmlFilePath).readText()
        val document = Jsoup.parse(htmlContent)
        val htmlFileName = File(htmlFilePath).nameWithoutExtension

        val sections = document.select("section")
        runBlocking {
            sections.mapIndexed { index, section ->
                async(Dispatchers.IO) {
                    val notes = section.select("aside.notes").text()
                    log.debug("Processing section {}: notes={}", index, notes)
                    if (notes.isNotBlank()) {
                        val mp3name = "${htmlFileName}_narration_$index.mp3"
                        val mp3FilePath = "$outputDir/$mp3name"
                        log.info("Generating audio file: {}", mp3FilePath)
                        generateAudioFile(notes, mp3FilePath)
                        injectAudioToSection(section, mp3name)
                    }
                }
            }.awaitAll()
        }
        log.info("Writing updated HTML content back to file: {}", htmlFilePath)

        File(htmlFilePath).writeText(document.html())
    }

    private fun generateAudioFile(text: String, filePath: String) {
        log.debug("Generating audio for text: {}", text)
        val mp3Bytes = OpenAIClient(workPool = Executors.newCachedThreadPool()).createSpeech(
            ApiModel.SpeechRequest(
                input = text,
                model = AudioModels.TTS.modelName,
                voice = "shimmer",
                speed = 1.0,
                response_format = "mp3"
            )
        ) ?: throw RuntimeException("No response")
        log.info("Audio generated successfully, writing to file: {}", filePath)
        FileOutputStream(filePath).use { it.write(mp3Bytes) }
    }

    private fun injectAudioToSection(section: Element, audioFilePath: String) {
        log.debug("Injecting audio reference into notes: audioFilePath={}", audioFilePath)
        val notesElement = section.selectFirst("aside.notes")
        notesElement?.attr("data-audio-src", audioFilePath)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("Starting PresentationAudioInjector with arguments: {}", args.joinToString())
        val htmlFileChooser = JFileChooser().apply {
            dialogTitle = "Select HTML File"
            fileFilter = FileNameExtensionFilter("HTML files", "html", "htm")
            isAcceptAllFileFilterUsed = false
        }
        if (htmlFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val htmlFile = htmlFileChooser.selectedFile
            val outputDir = htmlFile.parentFile
            log.info("HTML file selected: {}, Output directory: {}", htmlFile.absolutePath, outputDir.absolutePath)
            processPresentation(htmlFile.absolutePath, outputDir.absolutePath)
            println("Processing completed successfully.")
            log.info("Processing completed successfully.")
            System.exit(0)
        } else {
            println("HTML file selection cancelled.")
            log.warn("HTML file selection cancelled.")
            System.exit(1)
        }
    }
}