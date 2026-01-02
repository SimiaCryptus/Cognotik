package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.tools.data.toFile
import com.simiacryptus.cognotik.plan.tools.file.PdfFormTask.PdfFormExecutionConfig
import com.simiacryptus.cognotik.plan.tools.file.PdfFormTask.PdfFormTypeConfig
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object PdfFormTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        val harness = TaskTestHarness(
            taskType = PdfFormTask.PdfForm,
            typeConfig = PdfFormTypeConfig(
                template_file = "template.pdf"
            ),
            executionConfig = PdfFormExecutionConfig(
                output_file = "output.pdf",
                fields = mapOf("name" to "John Doe"),
                task_description = "Fill the name field in the PDF form"
            ),
            timeoutMinutes = 10,
        )

        // Create a simple PDF template with a form field in the harness workspace
        val templatePath = harness.workspace.resolve("template.pdf")
        val doc = PDDocument()
        try {
            val page = PDPage()
            doc.addPage(page)
            
            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm
            
            val textBox = PDTextField(acroForm)
            textBox.partialName = "name"
            acroForm.fields.add(textBox)
            
            doc.save(templatePath.toFile())
        } finally {
            doc.close()
        }

        harness.run()

        // Verify that the output file was created
        assertTrue(harness.workspace.resolve("output.pdf").exists(), "The output PDF file should exist after task execution")
    }
}