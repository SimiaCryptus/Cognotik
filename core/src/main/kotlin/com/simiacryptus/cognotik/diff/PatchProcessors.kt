package com.simiacryptus.cognotik.diff

enum class PatchProcessors : PatchProcessor {
  // Full replacement - no patching, just replace entire content
  FullReplacement {
    override val label = "FullReplacement"
    override val matcher = FullReplacementProcessor()
  },

  // Strict mode - exact matching only, no fuzzy logic
  Strict {
    override val label = "Strict"
    override val matcher = FuzzyPatchMatcher(
      enableFuzzyMatching = false,
      enableSnippetPatching = false,
      contextSize = 5
    )
  },

  // Lenient mode - maximum fuzzy matching
  Lenient {
    override val label = "Lenient"
    override val matcher = FuzzyPatchMatcher(
      enableFuzzyMatching = true,
      levenshteinThresholdDivisor = 2, // Very lenient
      minLineLengthForFuzzyMatch = 3,
      enableSnippetPatching = true,
      snippetMatchThreshold = 0.6, // Lower threshold
      requireAnchorMatch = false,
      contextSize = 2
    )
  },

  // Default/Fuzzy - balanced configuration
  Fuzzy {
    override val label = "Fuzzy"
    override val matcher = FuzzyPatchMatcher()
  };

  override val label: String get() = matcher.label

  protected abstract val matcher: PatchProcessor

  override val patchFormatPrompt: String
    get() = matcher.patchFormatPrompt

  override fun generatePatch(oldCode: String, newCode: String) =
    matcher.generatePatch(oldCode, newCode)

  override fun applyPatch(source: String, patch: String) =
    matcher.applyPatch(source, patch)
}