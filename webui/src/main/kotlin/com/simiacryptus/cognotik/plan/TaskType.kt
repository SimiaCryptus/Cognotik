package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.data.DataIngestTask
import com.simiacryptus.cognotik.plan.tools.data.DataIngestTask.Companion.DataIngest
import com.simiacryptus.cognotik.plan.tools.file.*
import com.simiacryptus.cognotik.plan.tools.file.AnalysisTask.Companion.Analysis
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.FileSearch
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.reasoning.*
import com.simiacryptus.cognotik.plan.tools.reasoning.ChainOfThoughtTask.Companion.ChainOfThought
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.run.RunToolTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.Companion.SubPlan
import com.simiacryptus.cognotik.plan.tools.social.*
import com.simiacryptus.cognotik.plan.tools.writing.*
import com.simiacryptus.cognotik.plan.tools.writing.ResearchPaperGenerationTask.Companion.ResearchPaperGeneration
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
    name: String,
    val category: String,
    val executionConfigClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    val description: String? = null,
    val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {
    companion object {

        private val taskConstructors by lazy {
            val taskConstructors: MutableMap<TaskType<*, *>, (OrchestrationConfig, TaskExecutionConfig?) -> AbstractTask<out TaskExecutionConfig, TaskTypeConfig>> =
                mutableMapOf()

            fun <T : TaskExecutionConfig, U : TaskTypeConfig> registerConstructor(
                taskType: TaskType<T, U>, constructor: (OrchestrationConfig, T?) -> AbstractTask<T, U>
            ) {
                taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskExecutionConfig? ->
                    constructor(settings, task as T?) as AbstractTask<TaskExecutionConfig, TaskTypeConfig>
                }
                register(taskType)
            }
            registerConstructor(GenerateSpriteSheetTask.GenerateSpriteSheet) { settings, task ->
                GenerateSpriteSheetTask(settings, task)
            }
            registerConstructor(FunctorialMappingTask.FunctorialMapping) { settings, task ->
                FunctorialMappingTask(settings, task)
            }
            registerConstructor(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis) { settings, task ->
                StructuralInvariantAnalysisTask(settings, task)
            }
            registerConstructor(IterativeGraphGenerationTask.IterativeGraphGeneration) { settings, task ->
                IterativeGraphGenerationTask(settings, task)
            }
            registerConstructor(IsomorphismDiscoveryTask.IsomorphismDiscovery) { settings, task ->
                IsomorphismDiscoveryTask(settings, task)
            }
            registerConstructor(TableCompilationTask.TableCompilation) { settings, task ->
                TableCompilationTask(settings, task)
            }
            registerConstructor(ImageTableTask.ImageTable) { settings, task ->
                ImageTableTask(settings, task)
            }
            registerConstructor(SoftwareDesignDocumentTask.SoftwareDesignDocument) { settings, task ->
                SoftwareDesignDocumentTask(settings, task)
            }
            registerConstructor(PoliticalOptimizationTask.PoliticalOptimization) { settings, task ->
                PoliticalOptimizationTask(settings, task)
            }
            registerConstructor(LLMPollSimulationTask.LLMPollSimulation) { settings, task ->
                LLMPollSimulationTask(settings, task)
            }
            registerConstructor(LLMExperimentTask.LLMExperiment) { settings, task ->
                LLMExperimentTask(settings, task)
            }
            registerConstructor(GameLevelDesignTask.GameLevelDesign) { settings, task ->
                GameLevelDesignTask(settings, task)
            }
            registerConstructor(GameNarrativeDesignTask.GameNarrativeDesign) { settings, task ->
                GameNarrativeDesignTask(settings, task)
            }
            registerConstructor(GameMechanicsDesignTask.GameMechanicsDesign) { settings, task ->
                GameMechanicsDesignTask(settings, task)
            }
            registerConstructor(GameEconomyTask.GameEconomy) { settings, task ->
                GameEconomyTask(settings, task)
            }
            registerConstructor(ResearchPaperGeneration) { settings, task ->
                ResearchPaperGenerationTask(settings, task)
            }
            registerConstructor(ChainOfThought) { settings, task ->
                ChainOfThoughtTask(settings, task)
            }
            registerConstructor(Analysis) { settings, task ->
                AnalysisTask(settings, task)
            }
            registerConstructor(CrawlerAgentTask.CrawlerAgent) { settings, task ->
                CrawlerAgentTask(settings, task)
            }
            registerConstructor(FileModification) { settings, task ->
                FileModificationTask(settings, task)
            }
            registerConstructor(FileSearch) { settings, task ->
                FileSearchTask(settings, task)
            }
            registerConstructor(GitHubSearchTask.GitHubSearch) { settings, task ->
                GitHubSearchTask(settings, task)
            }
            registerConstructor(RunCodeTask.RunCode) { settings, task ->
                RunCodeTask(settings, task)
            }
            registerConstructor(RunToolTask.RunTool) { settings, task ->
                RunToolTask(settings, task)
            }
//            registerConstructor(SeleniumSessionTask.SeleniumSession) { settings, task ->
//                SeleniumSessionTask(settings, task)
//            }
//            registerConstructor(CommandSessionTask.CommandSession) { settings, task ->
//                CommandSessionTask(settings, task)
//            }
            registerConstructor(AutoFixTask.AutoFix) { settings, task ->
                AutoFixTask(settings, task)
            }
            registerConstructor(MCPToolTask.MCPTool) { settings, task ->
                MCPToolTask(settings, task)
            }
            registerConstructor(WriteHtmlTask.WriteHtml) { settings, task ->
                WriteHtmlTask(settings, task)
            }
            registerConstructor(GeneratePresentationTask.GeneratePresentation) { settings, task ->
                GeneratePresentationTask(settings, task)
            }
            registerConstructor(MetaCognitiveReflectionTask.MetaCognitiveReflection) { settings, task ->
                MetaCognitiveReflectionTask(settings, task)
            }
            registerConstructor(CausalInferenceTask.CausalInference) { settings, task ->
                CausalInferenceTask(settings, task)
            }
            registerConstructor(AbstractionLadderTask.AbstractionLadder) { settings, task ->
                AbstractionLadderTask(settings, task)
            }
            registerConstructor(CounterfactualAnalysisTask.CounterfactualAnalysis) { settings, task ->
                CounterfactualAnalysisTask(settings, task)
            }
            registerConstructor(AnalogicalReasoningTask.AnalogicalReasoning) { settings, task ->
                AnalogicalReasoningTask(settings, task)
            }
            registerConstructor(SocraticDialogueTask.SocraticDialogue) { settings, task ->
                SocraticDialogueTask(settings, task)
            }
            registerConstructor(DecisionTreeTask.DecisionTree) { settings, task ->
                DecisionTreeTask(settings, task)
            }
            registerConstructor(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis) { settings, task ->
                MultiPerspectiveAnalysisTask(settings, task)
            }
            registerConstructor(ConstraintSatisfactionTask.ConstraintSatisfaction) { settings, task ->
                ConstraintSatisfactionTask(settings, task)
            }
            registerConstructor(DecompositionSynthesisTask.DecompositionSynthesis) { settings, task ->
                DecompositionSynthesisTask(settings, task)
            }
            registerConstructor(BrainstormingTask.Brainstorming) { settings, task ->
                BrainstormingTask(settings, task)
            }
            registerConstructor(FiniteStateMachineTask.FiniteStateMachine) { settings, task ->
                FiniteStateMachineTask(settings, task)
            }
            registerConstructor(GameTheoryTask.GameTheory) { settings, task ->
                GameTheoryTask(settings, task)
            }
            registerConstructor(AbductiveReasoningTask.AbductiveReasoning) { settings, task ->
                AbductiveReasoningTask(settings, task)
            }
            registerConstructor(AdversarialReasoningTask.AdversarialReasoning) { settings, task ->
                AdversarialReasoningTask(settings, task)
            }
            registerConstructor(ConstraintRelaxationTask.ConstraintRelaxation) { settings, task ->
                ConstraintRelaxationTask(settings, task)
            }
            registerConstructor(DialecticalReasoningTask.DialecticalReasoning) { settings, task ->
                DialecticalReasoningTask(settings, task)
            }
            registerConstructor(LateralThinkingTask.LateralThinking) { settings, task ->
                LateralThinkingTask(settings, task)
            }
            registerConstructor(ProbabilisticReasoningTask.ProbabilisticReasoning) { settings, task ->
                ProbabilisticReasoningTask(settings, task)
            }
            registerConstructor(SystemsThinkingTask.SystemsThinking) { settings, task ->
                SystemsThinkingTask(settings, task)
            }
            registerConstructor(TemporalReasoningTask.TemporalReasoning) { settings, task ->
                TemporalReasoningTask(settings, task)
            }
            registerConstructor(NarrativeGenerationTask.NarrativeGeneration) { settings, task ->
                NarrativeGenerationTask(settings, task)
            }
            registerConstructor(GeneticOptimizationTask.GeneticOptimization) { settings, task ->
                GeneticOptimizationTask(settings, task)
            }
            registerConstructor(MathematicalReasoningTask.MathematicalReasoning) { settings, task ->
                MathematicalReasoningTask(settings, task)
            }
            registerConstructor(NeuralNetworkLayerTask.NeuralNetworkLayer) { settings, task ->
                NeuralNetworkLayerTask(settings, task)
            }
            registerConstructor(SubPlan) { settings, task ->
                SubPlanTask(settings, task)
            }
            registerConstructor(EthicalReasoningTask.EthicalReasoning) { settings, task ->
                EthicalReasoningTask(settings, task)
            }
            registerConstructor(ArticleGenerationTask.ArticleGeneration) { settings, task ->
                ArticleGenerationTask(settings, task)
            }
            registerConstructor(PersuasiveEssayTask.PersuasiveEssay) { settings, task ->
                PersuasiveEssayTask(settings, task)
            }
            registerConstructor(BusinessProposalTask.BusinessProposal) { settings, task ->
                BusinessProposalTask(settings, task)
            }
            registerConstructor(EmailCampaignTask.EmailCampaign) { settings, task ->
                EmailCampaignTask(settings, task)
            }
            registerConstructor(InteractiveStoryTask.InteractiveStory) { settings, task ->
                InteractiveStoryTask(settings, task)
            }
            registerConstructor(JournalismReasoningTask.JournalismReasoning) { settings, task ->
                JournalismReasoningTask(settings, task)
            }
            registerConstructor(LanguageServerTask.LanguageServer) { settings, task ->
                LanguageServerTask(settings, task)
            }
            registerConstructor(TechnicalExplanationTask.TechnicalExplanation) { settings, task ->
                TechnicalExplanationTask(settings, task)
            }
            registerConstructor(TutorialGenerationTask.TutorialGeneration) { settings, task ->
                TutorialGenerationTask(settings, task)
            }
            registerConstructor(ReportGenerationTask.ReportGeneration) { settings, task ->
                ReportGenerationTask(settings, task)
            }
            registerConstructor(ScriptwritingTask.Scriptwriting) { settings, task ->
                ScriptwritingTask(settings, task)
            }
            registerConstructor(GenerateImageTask.GenerateImage) { settings, task ->
                GenerateImageTask(settings, task)
            }
            registerConstructor(IllustrateDocumentTask.IllustrateDocument) { settings, task ->
                IllustrateDocumentTask(settings, task)
            }
            registerConstructor(ComicBookGenerationTask.ComicBookGeneration) { settings, task ->
                ComicBookGenerationTask(settings, task)
            }
            registerConstructor(DataIngest) { settings, task ->
                DataIngestTask(settings, task)
            }

            registerConstructor(GenerateQRImageTask.GenerateQRImage) { settings, task ->
                GenerateQRImageTask(settings, task)
            }
            taskConstructors.toMap()
        }

        fun values(): List<TaskType<*, *>> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return values(TaskType::class.java)
        }

        fun getImpl(
            orchestrationConfig: OrchestrationConfig, planTask: TaskExecutionConfig?
        ) = getImpl(
            orchestrationConfig = orchestrationConfig,
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            cfg = planTask)

        fun getImpl(
            orchestrationConfig: OrchestrationConfig, taskType: TaskType<*, *>, cfg: TaskExecutionConfig? = null
        ): AbstractTask<out TaskExecutionConfig, TaskTypeConfig> {
            val constructor = taskConstructors[taskType]
            if (constructor == null) {
                throw RuntimeException("Unknown task type: ${taskType.name}")
            }
            val executionConfig: TaskExecutionConfig = cfg ?: try {
                taskType.executionConfigClass.getDeclaredConstructor().newInstance() as TaskExecutionConfig
            } catch (e: NoSuchMethodException) {
                throw RuntimeException("Task execution config class ${taskType.executionConfigClass.name} does not have a no-arg constructor. Please provide a planTask instance.")
            }
            return constructor(orchestrationConfig, executionConfig)
        }

        fun getAvailableTaskTypes(orchestrationConfig: OrchestrationConfig): List<TaskType<*, *>> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return orchestrationConfig.taskSettings.mapNotNull { x ->
                valueOf(
                    x.value.task_type ?: return@mapNotNull null
                )
            }
        }

        fun valueOf(name: String): TaskType<*, *> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return valueOf(TaskType::class.java, name)
        }

        private fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
    }

}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)