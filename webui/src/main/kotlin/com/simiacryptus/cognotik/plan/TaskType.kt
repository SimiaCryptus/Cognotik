package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.tools.file.IterativeImageDecompositionTask
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask
import com.simiacryptus.cognotik.plan.tools.data.DataIngestTask
import com.simiacryptus.cognotik.plan.tools.file.*
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.reasoning.*
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.run.RunToolTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask
import com.simiacryptus.cognotik.plan.tools.session.JdbcSessionTask
import com.simiacryptus.cognotik.plan.tools.social.*
import com.simiacryptus.cognotik.plan.tools.writing.*
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
    name: String,
    val category: String,
    val taskClass: Class<out AbstractTask<out T,out U>>,
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
                taskType: TaskType<T, U>
            ) {
                try {
                    val constructor = taskType.getConstructor()
                    taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskExecutionConfig? ->
                        @Suppress("UNCHECKED_CAST")
                        constructor(settings, task as T?) as AbstractTask<TaskExecutionConfig, TaskTypeConfig>
                    }
                    register(taskType)
                } catch (e: NoSuchMethodException) {
                    throw RuntimeException("Failed to register task type: ${taskType.name}. Ensure that the task class has a constructor with parameters (OrchestrationConfig, ${taskType.executionConfigClass.name})", e)
                }

            }

            registerConstructor(AbductiveReasoningTask.AbductiveReasoning)
            registerConstructor(AbstractionLadderTask.AbstractionLadder)
            registerConstructor(AdversarialReasoningTask.AdversarialReasoning)
            registerConstructor(AnalogicalReasoningTask.AnalogicalReasoning)
            registerConstructor(ArticleGenerationTask.ArticleGeneration)
            registerConstructor(AutoFixTask.AutoFix)
            registerConstructor(BrainstormingTask.Brainstorming)
            registerConstructor(BusinessProposalTask.BusinessProposal)
            registerConstructor(CausalInferenceTask.CausalInference)
            registerConstructor(ChainOfThoughtTask.ChainOfThought)
            registerConstructor(ComicBookGenerationTask.ComicBookGeneration)
            registerConstructor(CommandSessionTask.CommandSession)
            registerConstructor(ConstraintRelaxationTask.ConstraintRelaxation)
            registerConstructor(ConstraintSatisfactionTask.ConstraintSatisfaction)
            registerConstructor(CounterfactualAnalysisTask.CounterfactualAnalysis)
            registerConstructor(CrawlerAgentTask.CrawlerAgent)
            registerConstructor(DataIngestTask.DataIngest)
            registerConstructor(DecisionTreeTask.DecisionTree)
            registerConstructor(DecompositionSynthesisTask.DecompositionSynthesis)
            registerConstructor(DialecticalReasoningTask.DialecticalReasoning)
            registerConstructor(DiscussionTask.Discussion)
            registerConstructor(EmailCampaignTask.EmailCampaign)
            registerConstructor(EthicalReasoningTask.EthicalReasoning)
            registerConstructor(FileAppendTask.FileAppend)
            registerConstructor(FileModificationTask.FileModification)
            registerConstructor(FileSearchTask.FileSearch)
            registerConstructor(FiniteStateMachineTask.FiniteStateMachine)
            registerConstructor(FunctorialMappingTask.FunctorialMapping)
            registerConstructor(GameEconomyTask.GameEconomy)
            registerConstructor(GameLevelDesignTask.GameLevelDesign)
            registerConstructor(GameMechanicsDesignTask.GameMechanicsDesign)
            registerConstructor(GameNarrativeDesignTask.GameNarrativeDesign)
            registerConstructor(GameTheoryTask.GameTheory)
            registerConstructor(GenerateImageTask.GenerateImage)
            registerConstructor(GeneratePresentationTask.GeneratePresentation)
            registerConstructor(GenerateQRImageTask.GenerateQRImage)
            registerConstructor(GenerateSpriteSheetTask.GenerateSpriteSheet)
            registerConstructor(GeneticOptimizationTask.GeneticOptimization)
            registerConstructor(GitHubSearchTask.GitHubSearch)
            registerConstructor(IllustrateDocumentTask.IllustrateDocument)
            registerConstructor(IterativeImageDecompositionTask.IterativeImageDecomposition)
            registerConstructor(ImageTableTask.ImageTable)
            registerConstructor(InteractiveStoryTask.InteractiveStory)
            registerConstructor(IsomorphismDiscoveryTask.IsomorphismDiscovery)
            registerConstructor(IterativeGraphGenerationTask.IterativeGraphGeneration)
            registerConstructor(JdbcSessionTask.JdbcSession)
            registerConstructor(JournalismReasoningTask.JournalismReasoning)
            registerConstructor(LanguageServerTask.LanguageServer)
            registerConstructor(LateralThinkingTask.LateralThinking)
            registerConstructor(LLMExperimentTask.LLMExperiment)
            registerConstructor(LLMPollSimulationTask.LLMPollSimulation)
            registerConstructor(MathematicalReasoningTask.MathematicalReasoning)
            registerConstructor(MCPToolTask.MCPTool)
            registerConstructor(MetaCognitiveReflectionTask.MetaCognitiveReflection)
            registerConstructor(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
            registerConstructor(NarrativeGenerationTask.NarrativeGeneration)
            registerConstructor(NeuralNetworkLayerTask.NeuralNetworkLayer)
            registerConstructor(OCRTask.OCR)
            registerConstructor(PdfFormTask.PdfForm)
            registerConstructor(PersuasiveEssayTask.PersuasiveEssay)
            registerConstructor(PoliticalOptimizationTask.PoliticalOptimization)
            registerConstructor(ProbabilisticReasoningTask.ProbabilisticReasoning)
            registerConstructor(ReadDocumentsTask.ReadDocuments)
            registerConstructor(ReportGenerationTask.ReportGeneration)
            registerConstructor(ResearchPaperGenerationTask.ResearchPaperGeneration)
            registerConstructor(RunCodeTask.RunCode)
            registerConstructor(RunToolTask.RunTool)
            registerConstructor(ScriptwritingTask.Scriptwriting)
            registerConstructor(SocraticDialogueTask.SocraticDialogue)
            registerConstructor(SoftwareDesignDocumentTask.SoftwareDesignDocument)
            registerConstructor(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
            registerConstructor(SubPlanTask.SubPlan)
            registerConstructor(SymbolsDbCodeTask.SymbolsDbCode)
            registerConstructor(SystemsThinkingTask.SystemsThinking)
            registerConstructor(TableCompilationTask.TableCompilation)
            registerConstructor(TechnicalExplanationTask.TechnicalExplanation)
            registerConstructor(TemporalReasoningTask.TemporalReasoning)
            registerConstructor(TutorialGenerationTask.TutorialGeneration)
            registerConstructor(WriteHtmlTask.WriteHtml)

            taskConstructors.toMap()
        }

        fun values(): List<TaskType<*, *>> {
            @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
            return values(TaskType::class.java)
        }

        fun OrchestrationConfig.getImpl(
            planTask: TaskExecutionConfig?
        ) = getImpl(
            taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
            cfg = planTask)

        fun <T : TaskExecutionConfig, U : TaskTypeConfig> OrchestrationConfig.getImpl(
            taskType: TaskType<T,U>, cfg: TaskExecutionConfig? = null
        ): AbstractTask<out T, U> {
            val constructor = taskConstructors[taskType]
            if (constructor == null) {
                throw RuntimeException("Unknown task type: ${taskType.name}")
            }
            val executionConfig: TaskExecutionConfig = cfg ?: try {
                taskType.executionConfigClass.getDeclaredConstructor().newInstance() as TaskExecutionConfig
            } catch (e: NoSuchMethodException) {
                throw RuntimeException("Task execution config class ${taskType.executionConfigClass.name} does not have a no-arg constructor. Please provide a planTask instance.", e)
            }
            return constructor(this, executionConfig) as AbstractTask<out T, U>
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

    fun getConstructor(): (OrchestrationConfig, @UnsafeVariance T?) -> AbstractTask<out T, out U> =
        taskClass.let { cls ->
            val method =
                cls.getDeclaredConstructor(OrchestrationConfig::class.java, executionConfigClass)
            method.isAccessible = true
            { settings: OrchestrationConfig, task: T? ->
                method.newInstance(settings, task) as AbstractTask<T, U>
            }
        }

}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)