package com.simiacryptus.cognotik.diff

 enum class PatchProcessors : PatchProcessor {
    // Full replacement - no patching, just replace entire content
    FullReplacement {
        override val label = "FullReplacement"
        override val matcher = FullReplacementProcessor()
    },

    // C-style languages (Java, JavaScript, C++, C#, etc.)
    CStyle {
        override val label = "CStyle"
        override val matcher = FuzzyPatchMatcher(
        )
    },

    // Python, YAML, and other indentation-based languages
    Indentation {
        override val label = "Indentation"
        override val matcher = FuzzyPatchMatcher(
          contextSize = 4 // More context for indentation-sensitive languages
        )
    },

    // Markdown, plain text, and other non-code formats
    Markdown {
        override val label = "Markdown"
        override val matcher = FuzzyPatchMatcher(
          enableFuzzyMatching = true,
            levenshteinThresholdDivisor = 3, // More lenient for prose
            minLineLengthForFuzzyMatch = 10 // Longer lines for prose
        )
    },

    // XML, HTML, and other markup languages
    Markup {
        override val label = "Markup"
        override val matcher = FuzzyPatchMatcher(
          contextSize = 2,
            enableFuzzyMatching = true
        )
    },

    // Lisp, Scheme, Clojure (heavy parentheses)
    Lisp {
        override val label = "Lisp"
        override val matcher = FuzzyPatchMatcher(
          // Higher weight for parens
            contextSize = 2
        )
    },

    // Ruby, Lua (end-based blocks)
    EndBased {
        override val label = "EndBased"
        override val matcher = FuzzyPatchMatcher(
          contextSize = 4
        )
    },

    // SQL and database query languages
    SQL {
        override val label = "SQL"
        override val matcher = FuzzyPatchMatcher(
          enableFuzzyMatching = true,
            levenshteinThresholdDivisor = 5, // Stricter matching for SQL
            contextSize = 3
        )
    },

    // JSON, TOML, and other data formats
    DataFormat {
        override val label = "DataFormat"
        override val matcher = FuzzyPatchMatcher(
          contextSize = 2,
            enableFuzzyMatching = false // Strict matching for data formats
        )
    },

    // Shell scripts (Bash, Zsh, etc.)
    Shell {
        override val label = "Shell"
        override val matcher = FuzzyPatchMatcher(
          enableFuzzyMatching = true,
            contextSize = 3
        )
    },

    // Configuration files (INI, properties, etc.)
    Config {
        override val label = "Config"
        override val matcher = FuzzyPatchMatcher(
          enableFuzzyMatching = true,
            contextSize = 2
        )
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
    
    override val label: String
        get() = matcher.label

    protected abstract val matcher: PatchProcessor

    override val patchFormatPrompt: String
        get() = matcher.patchFormatPrompt

    override fun generatePatch(oldCode: String, newCode: String) =
        matcher.generatePatch(oldCode, newCode)

    override fun applyPatch(source: String, patch: String) =
        matcher.applyPatch(source, patch)
}