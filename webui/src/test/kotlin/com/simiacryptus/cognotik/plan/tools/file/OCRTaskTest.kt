package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.OCRTask.OCRTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit

object OCRTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

   //@org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        val harness = TaskHarness(
            taskType = OCRTask.OCR,
            executionConfig = OCRTaskExecutionConfigData(
                files = listOf("test_document.pdf"),
                task_description = "Convert the PDF to markdown",
                extract_figures = true,
                extract_metadata = true,
                extract_text = true,
            ),
            timeoutMinutes = 10,
            typeConfig = TaskTypeConfig(OCRTask.OCR.name),
        )

        val pdfPath = harness.dataDir.resolve("test_document.pdf")
         //writeTestPDF(pdfPath)
         File("/home/andrew/Downloads/US486986.pdf").copyTo(pdfPath, overwrite = true)

         harness.run()

        val outputPath = harness.dataDir.resolve("test_document.md")
        assertTrue(outputPath.exists(), "Output markdown file should exist")

        val content = outputPath.readText()
        assertTrue(content.isNotBlank(), "Output content should not be blank")
    }

    private fun writeTestPDF(pdfPath: File) {
        val doc = PDDocument()
        try {
            // Page 1
            var page = PDPage()
            doc.addPage(page)
            var contentStream = PDPageContentStream(doc, page)
            var image = BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB)
            var g = image.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, 600, 800)
            g.color = Color.BLACK
            g.font = Font("SansSerif", Font.BOLD, 18)
            g.drawString("This is page 1 of the OCR test.", 50, 100)
            g.dispose()
            var pdImage = LosslessFactory.createFromImage(doc, image)
            contentStream.drawImage(pdImage, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            contentStream.close()

            // Page 2
            page = PDPage()
            doc.addPage(page)
            contentStream = PDPageContentStream(doc, page)
            image = BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB)
            g = image.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, 600, 800)
            g.color = Color.BLACK
            g.font = Font("SansSerif", Font.BOLD, 18)
            g.drawString("This is page 2 containing secret code: 12345", 50, 100)
            g.dispose()
            pdImage = LosslessFactory.createFromImage(doc, image)
            contentStream.drawImage(pdImage, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            contentStream.close()

            doc.save(pdfPath)
        } finally {
            doc.close()
        }
    }

    //@org.junit.jupiter.api.Test
    @Timeout(30, unit = TimeUnit.MINUTES)
    fun test_convert() {
        TaskHarness(
            taskType = OCRTask.OCR,
            executionConfig = OCRTaskExecutionConfigData(
                files = listOf(fileSelectionPrompt()),
                task_description = "Convert the PDF to markdown"
            ),
            timeoutMinutes = 10,
            typeConfig = TaskTypeConfig(OCRTask.OCR.name),
        ).run()
    }

    private fun fileSelectionPrompt(): String {
        val fileChooser = javax.swing.JFileChooser()
        fileChooser.dialogTitle = "Select a PDF file for OCR processing"
        fileChooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        val result = fileChooser.showOpenDialog(null)
        return if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile.absolutePath
        } else {
            throw RuntimeException("File selection cancelled")
        }
    }
}