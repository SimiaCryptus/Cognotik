package com.simiacryptus.cognotik.input
import org.apache.poi.xwpf.usermodel.XWPFDocument

import java.io.File
import java.io.FileInputStream

class DocxReader(docxFile: File) : DocumentReader {
    private val document: XWPFDocument = XWPFDocument(FileInputStream(docxFile))
    
    override fun getText(): String {
        val text = StringBuilder()
        
        // Extract text from paragraphs
        document.paragraphs.forEach { paragraph ->
            text.append(paragraph.text)
            text.append("\n")
        }
        
        // Extract text from tables
        document.tables.forEach { table ->
            table.rows.forEach { row ->
                row.tableCells.forEach { cell ->
                    text.append(cell.text)
                    text.append("\t")
                }
                text.append("\n")
            }
        }
        
        return text.toString()
    }
    
    override fun close() {
        document.close()
    }
}