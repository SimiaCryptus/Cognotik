package com.simiacryptus.cognotik.diff

enum class PatchProcessors : PatchProcessor {
    Fuzzy { override val matcher = FuzzyPatchMatcher() };

    protected abstract val matcher: PatchProcessor

    override val patchFormatPrompt: String
        get() = matcher.patchFormatPrompt

    override fun generatePatch(oldCode: String, newCode: String) =
        matcher.generatePatch(oldCode, newCode)

    override fun applyPatch(source: String, patch: String) =
        matcher.applyPatch(source, patch)
}