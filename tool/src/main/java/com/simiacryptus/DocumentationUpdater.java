package com.simiacryptus;

import com.simiacryptus.cognotik.chat.model.AnthropicModels;
import com.simiacryptus.cognotik.chat.model.ChatModel;
import com.simiacryptus.cognotik.util.DocProcessor;
import com.simiacryptus.cognotik.util.UpdateModes;
import com.simiacryptus.cognotik.util.UnifiedHarness;

import java.io.File;
import java.util.ArrayList;

import static com.simiacryptus.cognotik.util.CognotikUtils.configureEnvironmentalKeys;

public record DocumentationUpdater(
        String overwriteMode,
        String rootDir,
        int threads
) {
    public void run() {
        UpdateModes mode = UpdateModes.valueOf(overwriteMode);
        //ChatModel chatModel = GeminiModels.getGeminiFlash_30_Preview();
        ChatModel chatModel = AnthropicModels.getClaude45Haiku();
        new DocProcessor(
                new File(rootDir),
                new File(rootDir),
                mode,
                ( source, folder) -> new ArrayList<>(),
                chatModel,
                chatModel,
                false,
                false,
                new File(rootDir, ".doc-processor-cache/url-cache"),
                true).run();
    }

    public static final String DEFAULT_ROOT = ".";
    public static final int DEFAULT_THREADS = 4;
    public static final String DEFAULT_OVERWRITE_MODE = UpdateModes.PatchToUpdate.name();
    
    public static void main(String[] args) {
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform();
        new DocumentationUpdater(
                getArg(args, 0, DEFAULT_OVERWRITE_MODE),
                getArg(args, 1, DEFAULT_ROOT),
                Integer.parseInt(getArg(args, 3, String.valueOf(DEFAULT_THREADS)))
        ).run();
    }

    private static String getArg(String[] args, int index, String defaultValue) {
        return args.length > index && args[index] != null && !args[index].isEmpty() ? args[index] : defaultValue;
    }
}