package com.simiacryptus;

import com.simiacryptus.cognotik.chat.model.ChatModel;
import com.simiacryptus.cognotik.chat.model.GeminiModels;
import com.simiacryptus.cognotik.util.DocProcessor;
import com.simiacryptus.cognotik.util.FileGenerator;
import com.simiacryptus.cognotik.util.UnifiedHarness;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.simiacryptus.CognotikUtils.configureEnvironmentalKeys;
import static com.simiacryptus.CognotikUtils.relativize;

public record DocumentationUpdater(
        String overwriteMode,
        String rootDir,
        String promptTemplate,
        int threads
) {
    public void run() {
        FileGenerator.OverwriteModes mode = FileGenerator.OverwriteModes.valueOf(overwriteMode);
        ChatModel chatModel = GeminiModels.getGeminiFlash_30_Preview();
        new DocProcessor() {}.run(
                new File(rootDir),
                new File(rootDir, "docs"),
                mode,
                (source, folder) -> new ArrayList<>(),
                (source, target) -> promptTemplate.contains("%s") ? promptTemplate.replace("%s", target.toString()) : promptTemplate + " (" + target + ")",
                threads,
                chatModel,
                chatModel
        );
    }

    public static final String DEFAULT_ROOT = ".";
    public static final String DEFAULT_PROMPT = "Update implementation file (%s) according to the standards documents";
    public static final int DEFAULT_THREADS = 4;
    public static final String DEFAULT_OVERWRITE_MODE = "PatchExisting";
    
    public static void main(String[] args) {
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform();
        new DocumentationUpdater(
                getArg(args, 0, DEFAULT_OVERWRITE_MODE),
                getArg(args, 1, DEFAULT_ROOT),
                getArg(args, 2, DEFAULT_PROMPT),
                Integer.parseInt(getArg(args, 3, String.valueOf(DEFAULT_THREADS)))
        ).run();
    }

    private static String getArg(String[] args, int index, String defaultValue) {
        return args.length > index && args[index] != null && !args[index].isEmpty() ? args[index] : defaultValue;
    }
}