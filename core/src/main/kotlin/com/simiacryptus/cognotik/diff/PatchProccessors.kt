package com.simiacryptus.cognotik.diff

enum class PatchProccessors : PatchProccessor {
    Iterative {
        override val patchFormatPrompt: String
            get() = IterativePatchUtil.patchFormatPrompt

        override fun generatePatch(oldCode: String, newCode: String) =
            IterativePatchUtil.generatePatch(oldCode, newCode)

        override fun applyPatch(source: String, patch: String) =
            IterativePatchUtil.applyPatch(source, patch)
    };
}