package com.simiacryptus.cognotik.diff

interface PatchProcessor {
    val label: String
    val patchFormatPrompt: String
    fun generatePatch(oldCode: String, newCode: String): String
    fun applyPatch(source: String, patch: String): String

    /**
     * Extracts code blocks from markdown-formatted text
     * @param response The markdown text containing code blocks
     * @return List of pairs containing (language, code content)
     */
    fun extractCodeBlocks(response: String): List<Pair<String, String>>

    /**
     * Gets the regex pattern that initiates a code block
     */
    fun getInitiatorPattern(): Regex
}