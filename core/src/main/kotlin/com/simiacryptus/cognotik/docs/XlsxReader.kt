package com.simiacryptus.cognotik.docs

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

class XlsxReader(xlsxFile: File) : DocumentReader {
  private val workbook: XSSFWorkbook = XSSFWorkbook(FileInputStream(xlsxFile))
  override fun getText(): String {
    val text = StringBuilder()
    for (sheetIndex in 0 until workbook.numberOfSheets) {
      val sheet = workbook.getSheetAt(sheetIndex)
      text.append("Sheet: ${sheet.sheetName}\n")
      text.append("=".repeat(50))
      text.append("\n")
      sheet.forEach { row ->
        row.forEach { cell ->
          val cellValue = when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try {
              cell.stringCellValue
            } catch (e: Exception) {
              cell.numericCellValue.toString()
            }

            else -> ""
          }
          text.append(cellValue)
          text.append("\t")
        }
        text.append("\n")
      }
      text.append("\n")
    }
    return text.toString()
  }

  override fun close() {
    workbook.close()
  }
}