package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
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
            registerConstructor(GenerateSpriteSheetTask.GenerateSpriteSheet)
            registerConstructor(FunctorialMappingTask.FunctorialMapping)
            registerConstructor(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
            registerConstructor(IterativeGraphGenerationTask.IterativeGraphGeneration)
            registerConstructor(IsomorphismDiscoveryTask.IsomorphismDiscovery)
            registerConstructor(TableCompilationTask.TableCompilation)
            registerConstructor(JdbcSessionTask.JdbcSession)
            registerConstructor(ImageTableTask.ImageTable)
            registerConstructor(SoftwareDesignDocumentTask.SoftwareDesignDocument)
            registerConstructor(PoliticalOptimizationTask.PoliticalOptimization)
            registerConstructor(LLMPollSimulationTask.LLMPollSimulation)
            registerConstructor(LLMExperimentTask.LLMExperiment)
            registerConstructor(GameLevelDesignTask.GameLevelDesign)
            registerConstructor(GameNarrativeDesignTask.GameNarrativeDesign)
            registerConstructor(GameMechanicsDesignTask.GameMechanicsDesign)
            registerConstructor(GameEconomyTask.GameEconomy)
            registerConstructor(ResearchPaperGenerationTask.ResearchPaperGeneration)
            registerConstructor(ChainOfThoughtTask.ChainOfThought)
            registerConstructor(ReadDocumentsTask.ReadDocuments)
            registerConstructor(DiscussionTask.Discussion)
            registerConstructor(CrawlerAgentTask.CrawlerAgent)
            registerConstructor(FileModificationTask.FileModification)
            registerConstructor(FileSearchTask.FileSearch)
            registerConstructor(GitHubSearchTask.GitHubSearch)
            registerConstructor(RunCodeTask.RunCode)
            registerConstructor(RunToolTask.RunTool)
//            registerConstructor(SeleniumSessionTask.SeleniumSession)
            registerConstructor(CommandSessionTask.CommandSession)
            registerConstructor(AutoFixTask.AutoFix)
            registerConstructor(MCPToolTask.MCPTool)
            registerConstructor(WriteHtmlTask.WriteHtml)
            registerConstructor(GeneratePresentationTask.GeneratePresentation)
            registerConstructor(MetaCognitiveReflectionTask.MetaCognitiveReflection)
            registerConstructor(CausalInferenceTask.CausalInference)
            registerConstructor(AbstractionLadderTask.AbstractionLadder)
            registerConstructor(CounterfactualAnalysisTask.CounterfactualAnalysis)
            registerConstructor(AnalogicalReasoningTask.AnalogicalReasoning)
            registerConstructor(SocraticDialogueTask.SocraticDialogue)
            registerConstructor(DecisionTreeTask.DecisionTree)
            registerConstructor(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
            registerConstructor(ConstraintSatisfactionTask.ConstraintSatisfaction)
            registerConstructor(DecompositionSynthesisTask.DecompositionSynthesis)
            registerConstructor(BrainstormingTask.Brainstorming)
            registerConstructor(FiniteStateMachineTask.FiniteStateMachine)
            registerConstructor(GameTheoryTask.GameTheory)
            registerConstructor(AbductiveReasoningTask.AbductiveReasoning)
            registerConstructor(AdversarialReasoningTask.AdversarialReasoning)
            registerConstructor(ConstraintRelaxationTask.ConstraintRelaxation)
            registerConstructor(DialecticalReasoningTask.DialecticalReasoning)
            registerConstructor(LateralThinkingTask.LateralThinking)
            registerConstructor(ProbabilisticReasoningTask.ProbabilisticReasoning)
            registerConstructor(SystemsThinkingTask.SystemsThinking)
            registerConstructor(TemporalReasoningTask.TemporalReasoning)
            registerConstructor(NarrativeGenerationTask.NarrativeGeneration)
            registerConstructor(GeneticOptimizationTask.GeneticOptimization)
            registerConstructor(MathematicalReasoningTask.MathematicalReasoning)
            registerConstructor(NeuralNetworkLayerTask.NeuralNetworkLayer)
            registerConstructor(SubPlanTask.SubPlan)
            registerConstructor(EthicalReasoningTask.EthicalReasoning)
            registerConstructor(ArticleGenerationTask.ArticleGeneration)
            registerConstructor(PersuasiveEssayTask.PersuasiveEssay)
            registerConstructor(BusinessProposalTask.BusinessProposal)
            registerConstructor(EmailCampaignTask.EmailCampaign)
            registerConstructor(InteractiveStoryTask.InteractiveStory)
            registerConstructor(JournalismReasoningTask.JournalismReasoning)
            registerConstructor(LanguageServerTask.LanguageServer)
            registerConstructor(PdfFormTask.PdfForm)
            registerConstructor(TechnicalExplanationTask.TechnicalExplanation)
            registerConstructor(TutorialGenerationTask.TutorialGeneration)
            registerConstructor(ReportGenerationTask.ReportGeneration)
            registerConstructor(ScriptwritingTask.Scriptwriting)
            registerConstructor(GenerateImageTask.GenerateImage)
            registerConstructor(IllustrateDocumentTask.IllustrateDocument)
            registerConstructor(ComicBookGenerationTask.ComicBookGeneration)
            registerConstructor(DataIngestTask.DataIngest)
            registerConstructor(GenerateQRImageTask.GenerateQRImage)

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
                throw RuntimeException("Task execution config class ${taskType.executionConfigClass.name} does not have a no-arg constructor. Please provide a planTask instance.", e)
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