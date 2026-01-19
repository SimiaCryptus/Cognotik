package com.simiacryptus;

import com.simiacryptus.cognotik.chat.model.ChatModel;
import com.simiacryptus.cognotik.chat.model.GeminiModels;
import com.simiacryptus.cognotik.plan.OrchestrationConfig;
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode.WaterfallModeConfig;
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig;
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask;
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask;
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask;
import com.simiacryptus.cognotik.platform.Session;
import com.simiacryptus.cognotik.util.JsonUtil;
import com.simiacryptus.cognotik.util.PlanHarness;
import com.simiacryptus.cognotik.util.UnifiedHarness;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.simiacryptus.CognotikUtils.*;

@SuppressWarnings("unused")
public record CodeImplementer(String prompt, int port, boolean headless, int timeout, String workspaceRoot) {

    public void run() {
        log.info("Starting CodeImplementer with prompt: {}", this.prompt());
        try {
            ChatModel chatModel = GeminiModels.getGeminiFlash_30_Preview();
            Map<String, TaskTypeConfig> tasks = new HashMap<>();

            var fileModification = FileModificationTask.getFileModification();
            tasks.put(fileModification.getName(), new TaskTypeConfig(fileModification.getName(), fileModification.getName(), getChatModel(chatModel)));

//            var autoFixConfig = new AutoFixTask.AutoFixTaskTypeConfig();
//            autoFixConfig.setModel(getChatModel(chatModel));
//            tasks.put(AutoFixTask.getAutoFix().getName(), autoFixConfig);

            new PlanHarness(this.prompt(),
                    new WaterfallModeConfig(),
                    (model, session) -> getInterface(getChatModel(model.getModel()), session),
                    this.port(),
                    this.headless(),
                    false,
                    this.timeout(),
                    chatModel,
                    chatModel,
                    chatModel,
                    new File(this.workspaceRoot())) {
                @NotNull
                @Override
                public OrchestrationConfig newConfig(@NotNull Session session, @NotNull File tempDir) {
                    OrchestrationConfig config = super.newConfig(session, tempDir);
                    var brainstorming = BrainstormingTask.getBrainstorming();
                    config.getTaskSettings().put(brainstorming.getName(), new TaskTypeConfig(
                            brainstorming.getName(),
                            brainstorming.getName(),
                            getChatModel(getFastModel())
                    ));
                    SubPlanTask.SubPlanTaskTypeConfig subPlanTaskTypeConfig = new SubPlanTask.SubPlanTaskTypeConfig();
                    subPlanTaskTypeConfig.setCognitiveSettings(new WaterfallModeConfig());
                    subPlanTaskTypeConfig.setTaskSettings(tasks);
                    config.getTaskSettings().put(SubPlanTask.getSubPlan().getName(), subPlanTaskTypeConfig);
                    return config;
                }

                @NotNull
                @Override
                public File createTempDirectory() {
                    File workspace = new File(CodeImplementer.this.workspaceRoot(), "workspaces/" + "CodeImplementer" + "/test-" + System.currentTimeMillis());
                    //noinspection ResultOfMethodCallIgnored
                    workspace.mkdirs();
                    return workspace;
                }
            }.run();
        } catch (Exception e) {
            log.error("Error during CodeImplementer run", e);
        } finally {
            log.info("CodeImplementer run completed");
            log.info("Summary: " + JsonUtil.toJson(Map.of(
                    "prompt", this.prompt(),
                    "port", this.port(),
                    "headless", this.headless(),
                    "timeout", this.timeout(),
                    "workspaceRoot", this.workspaceRoot(),
                    "pwd", new File(".").getAbsolutePath()
            )));
        }
    }

    public static final String DEFAULT_PROMPT = "Build a fun and unique game using java and gradle.";
    public static final int DEFAULT_PORT = 8030;
    public static final String DEFAULT_WORKSPACE = ".";
    public static final int DEFAULT_TIMEOUT = 30;
    public static final boolean DEFAULT_HEADLESS = true;
    private static final Logger log = LoggerFactory.getLogger(CodeImplementer.class);

    public static void main(String[] args) {
        String prompt = getArg(args, 0, DEFAULT_PROMPT);
        int port = Integer.parseInt(getArg(args, 1, String.valueOf(DEFAULT_PORT)));
        String workspaceRoot = getArg(args, 2, DEFAULT_WORKSPACE);
        int timeout = Integer.parseInt(getArg(args, 3, String.valueOf(DEFAULT_TIMEOUT)));
        boolean headless = Boolean.parseBoolean(getArg(args, 4, String.valueOf(DEFAULT_HEADLESS)));
        configureEnvironmentalKeys();
        UnifiedHarness.configurePlatform();
        new CodeImplementer(prompt, port, headless, timeout, workspaceRoot).run();
    }


    private static String getArg(String[] args, int index, String defaultValue) {
        return args.length > index && args[index] != null && !args[index].isEmpty() ? args[index] : defaultValue;
    }

}