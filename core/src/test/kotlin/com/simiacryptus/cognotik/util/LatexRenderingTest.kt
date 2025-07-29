package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for LaTeX output generation and syntax validation using pdflatex CLI.
 * These tests verify that generated LaTeX documents are syntactically valid
 * by attempting to compile them with pdflatex.
 */
class LatexRenderingTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var testLatexFile: File

    @BeforeEach
    fun setUp() {
        testLatexFile = tempDir.resolve("test_document.tex").toFile()
    }

    @Test
    fun `test basic LaTeX document generation and validation`() {
        // Generate a basic LaTeX document
        val title = "Test Document"
        val content = "This is a simple test document with some basic content."
        val author = "Test Author"
        
        val latexContent = LatexGenerator.generateDocument(title, content, author)
        
        // Verify the document contains expected LaTeX structure
        assertTrue(latexContent.contains("\\documentclass{article}"))
        assertTrue(latexContent.contains("\\title{$title}"))
        assertTrue(latexContent.contains("\\author{$author}"))
        assertTrue(latexContent.contains("\\begin{document}"))
        assertTrue(latexContent.contains("\\end{document}"))
        assertTrue(latexContent.contains(content))
        
        // Write to file and validate syntax with pdflatex
        testLatexFile.writeText(latexContent)
        assertPdfLatexCompiles(testLatexFile)
    }

    @Test
    fun `test LaTeX report generation with multiple sections`() {
        val title = "Technical Report"
        val sections = listOf(
            "Introduction" to "This report covers various aspects of our analysis.",
            "Methodology" to "We used the following approach for data collection.",
            "Results" to "The results show significant improvements in performance."
        )
        
        val latexContent = LatexGenerator.generateReport(title, sections)
        
        // Verify report structure
        assertTrue(latexContent.contains("\\documentclass{article}"))
        assertTrue(latexContent.contains("\\title{$title}"))
        sections.forEach { (sectionTitle, sectionContent) ->
            assertTrue(latexContent.contains("\\section{$sectionTitle}"))
            assertTrue(latexContent.contains(sectionContent))
        }
        
        // Write to file and validate syntax with pdflatex
        testLatexFile.writeText(latexContent)
        assertPdfLatexCompiles(testLatexFile)
    }

    @Test
    fun `test LaTeX document with code blocks`() {
        val title = "Code Documentation"
        val content = """
            Here is some sample code:
            
            ```kotlin
            fun main() {
                println("Hello World!")
            }
            ```
            
            And some inline code: `val x = 42`
        """.trimIndent()
        
        val latexContent = LatexGenerator.generateDocument(title, content)
        
        // Verify code formatting is included
        assertTrue(latexContent.contains("\\begin{lstlisting}"))
        assertTrue(latexContent.contains("\\end{lstlisting}"))
        assertTrue(latexContent.contains("\\texttt{"))
        
        // Write to file and validate syntax with pdflatex
        testLatexFile.writeText(latexContent)
        assertPdfLatexCompiles(testLatexFile)
    }

    @Test
    fun `test LaTeX character escaping`() {
        val title = "Special Characters & Symbols"
        val content = "This text contains special chars: $ & % # ^ _ ~ { } \\"
        
        val latexContent = LatexGenerator.generateDocument(title, content)
        
        // Verify special characters are properly escaped
        assertFalse(latexContent.contains("$ &"), "Unescaped $ and & should not be present")
        assertTrue(latexContent.contains("\\$"), "Dollar sign should be escaped")
        assertTrue(latexContent.contains("\\&"), "Ampersand should be escaped")
        assertTrue(latexContent.contains("\\%"), "Percent should be escaped")
        
        // Write to file and validate syntax with pdflatex
        testLatexFile.writeText(latexContent)
        assertPdfLatexCompiles(testLatexFile)
    }

    @Test
    fun `test empty content handling`() {
        val title = "Empty Document"
        val content = ""
        
        val latexContent = LatexGenerator.generateDocument(title, content)
        
        // Even empty documents should be valid LaTeX
        assertTrue(latexContent.contains("\\documentclass{article}"))
        assertTrue(latexContent.contains("\\begin{document}"))
        assertTrue(latexContent.contains("\\end{document}"))
        
        // Write to file and validate syntax with pdflatex
        testLatexFile.writeText(latexContent)
        assertPdfLatexCompiles(testLatexFile)
    }

    /**
     * Validates that a LaTeX file compiles successfully with pdflatex.
     * This is the core validation that ensures rendered output has valid syntax.
     */
    private fun assertPdfLatexCompiles(latexFile: File) {
        assertTrue(latexFile.exists(), "LaTeX file should exist")
        assertTrue(latexFile.canRead(), "LaTeX file should be readable")
        
        // Run pdflatex to validate syntax
        val workingDir = latexFile.parentFile
        val fileName = latexFile.nameWithoutExtension
        
        try {
            val process = ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",
                "-output-directory=${workingDir.absolutePath}",
                latexFile.absolutePath
            ).apply {
                directory(workingDir)
                redirectErrorStream(true)
            }.start()
            
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            
            if (exitCode != 0) {
                fail<Unit>("pdflatex compilation failed with exit code $exitCode. Output:\n$output")
            }
            
            // Verify PDF was created
            val pdfFile = File(workingDir, "$fileName.pdf")
            assertTrue(pdfFile.exists(), "PDF output file should be created: ${pdfFile.absolutePath}")
            assertTrue(pdfFile.length() > 0, "PDF file should not be empty")
            
        } catch (e: Exception) {
            fail<Unit>("Failed to execute pdflatex: ${e.message}")
        }
    }
}