package com.simiacryptus;

import com.simiacryptus.cognotik.util.FileGenerator;
import com.simiacryptus.cognotik.util.OverwriteModes;
import com.simiacryptus.cognotik.util.UnifiedHarness;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static com.simiacryptus.CognotikUtils.configureEnvironmentalKeys;
import static com.simiacryptus.CognotikUtils.relativize;

public record CodeReviewer(
        String docsArg,
        String overwriteMode,
        String rootDir,
        String srcDir,
        String promptTemplate,
        int threads
) {
    public void run() {
        new FileGenerator() {}.run(
                new File(rootDir),
                new File(srcDir),
                (root, folder) -> Arrays.stream(Objects.requireNonNull(folder.listFiles())).map(file -> relativize(root, file)).toList(),
                (source) -> source,
                OverwriteModes.valueOf(overwriteMode),
                (source) -> Arrays.asList(docsArg.split(",")),
                (source, target) -> promptTemplate.contains("%s") ? promptTemplate.replace("%s", target.toString()) : promptTemplate + " (" + target + ")",
                threads
        );
    }

    public static final String DEFAULT_ROOT = ".";
    public static final String DEFAULT_SRC = "src/main/java";
    public static final String DEFAULT_PROMPT = "Update implementation file (%s) according to the standards documents";
    public static final String DEFAULT_DOCS = "docs/best_practices.md";
    public static final int DEFAULT_THREADS = 4;
    public static final String DEFAULT_OVERWRITE_MODE = "PatchExisting";
    
    public static void main(String[] args) {
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform();
        new CodeReviewer(
                getArg(args, 3, DEFAULT_DOCS),
                getArg(args, 5, DEFAULT_OVERWRITE_MODE),
                getArg(args, 0, DEFAULT_ROOT),
                getArg(args, 1, DEFAULT_SRC),
                getArg(args, 2, DEFAULT_PROMPT),
                Integer.parseInt(getArg(args, 4, String.valueOf(DEFAULT_THREADS)))
        ).run();
    }

    private static String getArg(String[] args, int index, String defaultValue) {
        return args.length > index && args[index] != null && !args[index].isEmpty() ? args[index] : defaultValue;
    }
}