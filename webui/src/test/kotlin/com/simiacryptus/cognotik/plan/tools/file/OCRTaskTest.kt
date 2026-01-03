package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.OCRTask.OCRTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.TaskHarness
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object OCRTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }
    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        val harness = TaskHarness(
            taskType = OCRTask.OCR,
            executionConfig = OCRTaskExecutionConfigData(
                files = listOf("test_document.pdf"),
                task_description = "Convert the PDF to markdown"
            ),
            timeoutMinutes = 10,
            typeConfig = TaskTypeConfig(OCRTask.OCR.name),
        )

        val pdfPath = harness.workspace.resolve("test_document.pdf")
        val doc = PDDocument()
        try {
            // Page 1
            var page = PDPage()
            doc.addPage(page)
            var contentStream = PDPageContentStream(doc, page)
            contentStream.beginText()
//            contentStream.setFont(PDType1Font(
//                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD
//            ), 12f)
            contentStream.newLineAtOffset(100f, 700f)
            contentStream.showText("This is page 1 of the OCR test.")
            contentStream.endText()
            contentStream.close()

            // Page 2
            page = PDPage()
            doc.addPage(page)
            contentStream = PDPageContentStream(doc, page)
            contentStream.beginText()
//            contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12f)
            contentStream.newLineAtOffset(100f, 700f)
            contentStream.showText("This is page 2 containing secret code: 12345")
            contentStream.endText()
            contentStream.close()

            doc.save(pdfPath)
        } finally {
            doc.close()
        }

        harness.run()

        val outputPath = harness.workspace.resolve("test_document.md")
        assertTrue(outputPath.exists(), "Output markdown file should exist")

        val content = outputPath.readText()
        assertTrue(content.isNotBlank(), "Output content should not be blank")
    }

    //@Test
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