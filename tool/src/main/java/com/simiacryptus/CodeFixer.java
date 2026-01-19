package com.simiacryptus;

import com.simiacryptus.cognotik.chat.model.ChatModel;
import com.simiacryptus.cognotik.chat.model.GeminiModels;
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig;
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask;
import com.simiacryptus.cognotik.util.TaskHarness;
import com.simiacryptus.cognotik.util.UnifiedHarness;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;

import static com.simiacryptus.CognotikUtils.*;

@SuppressWarnings("unused")
public record CodeFixer(String taskDescription, List<String> relatedFiles) {
    public static final String PROMPT = "Fix the build errors reported in build.log";
    public static final int PORT = 8030;

    public static void main(String[] args) {
        String taskDescription = args.length > 0 ? args[0] : PROMPT;
        List<String> relatedFiles = List.of(
                args.length > 1 ? args[1] : "build.log"
        );
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform();
        new CodeFixer(taskDescription, relatedFiles).run();
    }

    public void run() {
        ChatModel chatModel = GeminiModels.getGeminiFlash_30_Preview();

        var fileModification = FileModificationTask.getFileModification();
        FileModificationTask.FileModificationTaskExecutionConfigData  config = new FileModificationTask.FileModificationTaskExecutionConfigData();
        config.setTask_description(this.taskDescription());
        config.setRelated_files(this.relatedFiles());

        new TaskHarness<>(
                fileModification,
                new TaskTypeConfig(fileModification.getName(), fileModification.getName(), getChatModel(chatModel)),
                config,
                (model, session) -> getInterface(getChatModel(model.getModel()), session),
                PORT,
                true,
                false,
                30,
                chatModel,
                chatModel,
                chatModel,
                new File("."),
                0.0
        ) {
            @NotNull
            @Override
            public File createWorkspace() {
                File workspace = new File(".", "workspaces/" + "CodeFixer" + "/test-" + System.currentTimeMillis());
                //noinspection ResultOfMethodCallIgnored
                workspace.mkdirs();
                return workspace;
            }
        }.run();
    }

    private static Logger log = org.slf4j.LoggerFactory.getLogger(CodeFixer.class);


}