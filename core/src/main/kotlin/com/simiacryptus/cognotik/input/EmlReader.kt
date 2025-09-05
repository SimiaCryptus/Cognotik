package com.simiacryptus.cognotik.input

import jakarta.mail.BodyPart
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.io.InputStream
import kotlin.io.path.createTempFile

class EmlReader(private val file: File) : DocumentReader {
    private var message: MimeMessage? = null
    private val tempFiles = mutableListOf<File>()
    init {
        FileInputStream(file).use { inputStream ->
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            message = MimeMessage(session, inputStream)
        }
    }
    override fun getText(): String {
        val message = this.message ?: return ""
        val result = StringBuilder()
        // Add email headers
        result.appendLine("From: ${message.from?.joinToString(", ") ?: ""}")
        result.appendLine("To: ${message.getRecipients(Message.RecipientType.TO)?.joinToString(", ") ?: ""}")
        result.appendLine("CC: ${message.getRecipients(Message.RecipientType.CC)?.joinToString(", ") ?: ""}")
        result.appendLine("Subject: ${message.subject ?: ""}")
        result.appendLine("Date: ${message.sentDate ?: ""}")
        result.appendLine()
        result.appendLine("--- Message Body ---")
        result.appendLine()
        // Process message content
        processContent(message.content, result)
        return result.toString()
    }
    private fun processContent(content: Any?, result: StringBuilder) {
        when (content) {
            is String -> {
                result.appendLine(content)
            }
            is MimeMultipart -> {
                processMultipart(content, result)
            }
            is InputStream -> {
                result.appendLine(content.bufferedReader().use { it.readText() })
            }
        }
    }
    private fun processMultipart(multipart: MimeMultipart, result: StringBuilder) {
        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            processPart(bodyPart, result)
        }
    }
    private fun processPart(part: BodyPart, result: StringBuilder) {
        val disposition = part.disposition
        val contentType = part.contentType.lowercase()
        when {
            disposition?.lowercase()?.contains("attachment") == true ||
            disposition?.lowercase()?.contains("inline") == true -> {
                processAttachment(part, result)
            }
            contentType.contains("text/plain") || contentType.contains("text/html") -> {
                processContent(part.content, result)
            }
            contentType.contains("multipart") -> {
                processContent(part.content, result)
            }
            else -> {
                // Try to process as attachment if it has a filename
                if (part.fileName != null) {
                    processAttachment(part, result)
                }
            }
        }
    }
    private fun processAttachment(part: BodyPart, result: StringBuilder) {
        val fileName = part.fileName ?: "attachment"
        result.appendLine()
        result.appendLine("--- Attachment: $fileName ---")
        try {
            // Create a temporary file for the attachment
            val tempFile = createTempFile(
                prefix = "eml_attachment_",
                suffix = "_$fileName"
            ).toFile()
            tempFiles.add(tempFile)
            // Save attachment to temp file
            part.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Use the file extension to determine the appropriate reader
            val attachmentReader = tempFile.getReader()
            attachmentReader.use {
                val attachmentText = it.getText()
                result.appendLine(attachmentText)
            }
        } catch (e: Exception) {
            result.appendLine("Error reading attachment: ${e.message}")
        }
        result.appendLine("--- End Attachment: $fileName ---")
        result.appendLine()
    }
    override fun close() {
        // Clean up temporary files
        tempFiles.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        tempFiles.clear()
    }
}