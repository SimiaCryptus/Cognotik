package com.simiacryptus;

import com.simiacryptus.cognotik.chat.model.ChatModel;
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig;
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask;
import com.simiacryptus.cognotik.util.PlanHarness;
import com.simiacryptus.cognotik.util.TaskHarness;
import com.simiacryptus.cognotik.util.UnifiedHarness;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static com.simiacryptus.cognotik.platform.model.UserKt.defaultUser;
import static com.simiacryptus.cognotik.util.CognotikUtils.*;

@SuppressWarnings("unused")
public record CodeFixer(String taskDescription, List<String> relatedFiles, ChatModel chatModel) {
    public static final String PROMPT = "Fix the build errors reported in build.log";
    public static final int PORT = 8030;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CodeFixer.class);

    public static void main(String[] args) {
        String taskDescription = args.length > 0 ? args[0] : PROMPT;
        List<String> relatedFiles = List.of(
                args.length > 1 ? args[1] : "build.log"
        );

        PlanHarness.initDynamicEnums();
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform(defaultUser);
        ChatModel chatModel = null;
        if(chatModel == null) throw new IllegalStateException("ChatModel not configured");
        new CodeFixer(taskDescription, relatedFiles, chatModel).run();
    }

    public void run() {

        var fileModification = FileModificationTask.getFileModification();
        FileModificationTask.FileModificationTaskExecutionConfigData config = new FileModificationTask.FileModificationTaskExecutionConfigData();
        config.setTask_description(this.taskDescription());
        config.setRelated_files(this.relatedFiles());

        new TaskHarness<>(
                fileModification,
                new TaskTypeConfig(fileModification.getName(), fileModification.getName(), getChatModel(chatModel)),
                config,
                (model, session, user) -> getInterface(getChatModel(Objects.requireNonNull(model.getModel())), session),
                PORT,
                true,
                false,
                30,
                chatModel,
                chatModel,
                chatModel,
                chatModel,
                new File("."),
                0.0,
                defaultUser
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


}