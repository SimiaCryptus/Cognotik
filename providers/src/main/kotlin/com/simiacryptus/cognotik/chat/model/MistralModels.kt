package com.simiacryptus.cognotik.chat.model
    
    import com.simiacryptus.cognotik.CoreProviders
    import com.simiacryptus.cognotik.platform.model.ChatMessageModality
    import com.simiacryptus.cognotik.platform.model.ChatModel

/**
     * Mistral AI model catalog.
     *
     * Reference: https://docs.mistral.ai/models/overview
     *
     * Organized into:
     *  - Featured Models (latest flagship offerings)
     *  - Frontier Generalist Models
     *  - Frontier Specialist Models
     *  - Other Models
     *  - Legacy / Deprecated Models (retained for backward compatibility)
     */
    object MistralModels {
    
        // ============================================================
        // Featured Models
        // ============================================================
    
        /**
         * Mistral Medium 3.5 (v26.04) - Featured.
         * Frontier-class multimodal model optimized for agentic and coding use cases.
         */
        val MistralMedium3_5 = ChatModel(
          name = "MistralMedium3_5",
          modelId = "mistral-medium-2604",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0015,
          outputTokenPricePerK = 0.0045,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        // ============================================================
        // Frontier Generalist Models
        // ============================================================
    
        /**
         * Mistral Small 4 (v26.03) - Hybrid model unifying instruct, reasoning, and coding.
         */
        val MistralSmall4 = ChatModel(
          name = "MistralSmall4",
          modelId = "mistral-small-2603",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Mistral Large 3 (v25.12) - State-of-the-art, open-weight, general-purpose multimodal model.
         */
        val MistralLarge3 = ChatModel(
          name = "MistralLarge3",
          modelId = "mistral-large-2512",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.002,
          outputTokenPricePerK = 0.006,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Mistral Medium 3.1 (v25.08) - Frontier-class multimodal model released August 2025.
         */
        val MistralMedium3_1 = ChatModel(
          name = "MistralMedium3_1",
          modelId = "mistral-medium-2508",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0015,
          outputTokenPricePerK = 0.0045,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Mistral Small 3.2 (v25.06) - Legacy small model (deprecated April 2026).
         */
        val MistralSmall3_2 = ChatModel(
          name = "MistralSmall3_2",
          modelId = "mistral-small-2506",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Ministral 3 14B (v25.12) - Powerful model with best-in-class text and vision.
         */
        val Ministral3_14B = ChatModel(
          name = "Ministral3_14B",
          modelId = "ministral-14b-2512",
          maxTotalTokens = 32768,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.00009,
          outputTokenPricePerK = 0.00009,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Ministral 3 8B (v25.12) - Powerful and efficient model with text/vision capabilities.
         */
        val Ministral3_8B = ChatModel(
          name = "Ministral3_8B",
          modelId = "ministral-8b-2512",
          maxTotalTokens = 32768,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.00009,
          outputTokenPricePerK = 0.00009,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Ministral 3 3B (v25.12) - Tiny and efficient model with text/vision capabilities.
         */
        val Ministral3_3B = ChatModel(
          name = "Ministral3_3B",
          modelId = "ministral-3b-2512",
          maxTotalTokens = 32768,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.00004,
          outputTokenPricePerK = 0.00004,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Magistral Medium 1.2 (v25.09) - Frontier-class multimodal reasoning model.
         */
        val MagistralMedium1_2 = ChatModel(
          name = "MagistralMedium1_2",
          modelId = "magistral-medium-2509",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.002,
          outputTokenPricePerK = 0.006,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Magistral Small 1.2 (v25.09) - Smaller variant of the reasoning model.
         */
        val MagistralSmall1_2 = ChatModel(
          name = "MagistralSmall1_2",
          modelId = "magistral-small-2509",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        // ============================================================
        // Frontier Specialist Models
        // ============================================================
    
        /**
         * Leanstral (v26.03) - Open-source code agent for Lean 4 formal proof engineering.
         */
        val Leanstral = ChatModel(
          name = "Leanstral",
          modelId = "leanstral-2603",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Codestral (v25.08) - Cutting-edge language model for code completion.
         */
        val Codestral = ChatModel(
          name = "Codestral",
          modelId = "codestral-2508",
          maxTotalTokens = 262144,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0003,
          outputTokenPricePerK = 0.0009,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Devstral 2 (v25.12) - Frontier code agents model for software engineering tasks.
         */
        val Devstral2 = ChatModel(
          name = "Devstral2",
          modelId = "devstral-large-2512",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.002,
          outputTokenPricePerK = 0.006,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Devstral Small 2 (v25.12) - Smaller variant of Devstral 2.
         */
        val DevstralSmall2 = ChatModel(
          name = "DevstralSmall2",
          modelId = "labs-devstral-small-2512",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        // ============================================================
        // Other Models
        // ============================================================
    
        /**
         * Mistral Small Creative (v25.12) - Creative writing optimized model.
         */
        val MistralSmallCreative = ChatModel(
          name = "MistralSmallCreative",
          modelId = "labs-mistral-small-creative",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Mistral Medium 3 (v25.05) - Frontier-class multimodal model released May 2025.
         */
        val MistralMedium3 = ChatModel(
          name = "MistralMedium3",
          modelId = "mistral-medium-2505",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0015,
          outputTokenPricePerK = 0.0045,
          inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Devstral Medium 1.0 (v25.07) - Deprecated; replaced by Devstral 2.
         */
        val DevstralMedium1_0 = ChatModel(
          name = "DevstralMedium1_0",
          modelId = "devstral-medium-2507",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.002,
          outputTokenPricePerK = 0.006,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Mistral Nemo 12B (v24.07) - Best multilingual open source model.
         */
        val MistralNemo = ChatModel(
          name = "MistralNemo",
          modelId = "open-mistral-nemo",
          maxTotalTokens = 131071,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.00015,
          outputTokenPricePerK = 0.00015,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        // ============================================================
        // Legacy / Deprecated Models
        // (Retained for backward compatibility; see deprecation schedule
        //  at https://docs.mistral.ai/models/overview)
        // ============================================================
    
        /**
         * Magistral Medium 1.1 (v25.07) - Deprecated October 2025; use MagistralMedium1_2.
         */
        val MagistralMedium1_1 = ChatModel(
          name = "MagistralMedium1_1",
          modelId = "magistral-medium-2507",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.002,
          outputTokenPricePerK = 0.006,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        /**
         * Magistral Small 1.1 (v25.07) - Deprecated October 2025; use MagistralSmall1_2.
         */
        val MagistralSmall1_1 = ChatModel(
          name = "MagistralSmall1_1",
          modelId = "magistral-small-2507",
          maxTotalTokens = 131072,
          provider = CoreProviders.Mistral,
          inputTokenPricePerK = 0.0001,
          outputTokenPricePerK = 0.0003,
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
    
        val values = mapOf(
            // Featured
            "MistralMedium3_5" to MistralMedium3_5,
            // Frontier Generalist
            "MistralSmall4" to MistralSmall4,
            "MistralLarge3" to MistralLarge3,
            "MistralMedium3_1" to MistralMedium3_1,
            "MistralSmall3_2" to MistralSmall3_2,
            "Ministral3_14B" to Ministral3_14B,
            "Ministral3_8B" to Ministral3_8B,
            "Ministral3_3B" to Ministral3_3B,
            "MagistralMedium1_2" to MagistralMedium1_2,
            "MagistralSmall1_2" to MagistralSmall1_2,
            // Specialist
            "Leanstral" to Leanstral,
            "Codestral" to Codestral,
            "Devstral2" to Devstral2,
            "DevstralSmall2" to DevstralSmall2,
            // Other
            "MistralSmallCreative" to MistralSmallCreative,
            "MistralMedium3" to MistralMedium3,
            "DevstralMedium1_0" to DevstralMedium1_0,
            "MistralNemo" to MistralNemo,
            // Legacy / Deprecated
            "MagistralMedium1_1" to MagistralMedium1_1,
            "MagistralSmall1_1" to MagistralSmall1_1,
            // Legacy aliases for backward compatibility
            "MistralSmall" to MistralSmall3_2,
            "MistralMedium" to MistralMedium3_5,
            "MistralLarge" to MistralLarge3,
            "MagistralMedium" to MagistralMedium1_2,
            "MagistralSmall" to MagistralSmall1_2,
        )
    
    }