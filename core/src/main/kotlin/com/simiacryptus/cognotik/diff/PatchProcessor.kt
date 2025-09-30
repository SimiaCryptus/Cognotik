package com.simiacryptus.cognotik.diff

interface PatchProcessor {
    val patchFormatPrompt: String
    fun generatePatch(oldCode: String, newCode: String): String
    fun applyPatch(source: String, patch: String): String
}