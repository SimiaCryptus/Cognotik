package com.simiacryptus.cognotik.docs

data class Settings(
    val dpi: Float = 120f,
    val maxPages: Int = Int.MAX_VALUE,
    val outputFormat: String = "PNG",
    val fileInputs: List<String>? = null,
    val showImages: Boolean = true,
    val pagesPerBatch: Int = 1,
    val saveImageFiles: Boolean = false,
    val saveTextFiles: Boolean = false,
    val saveFinalJson: Boolean = true,
    val fastMode: Boolean = true,
    val addLineNumbers: Boolean = false
)