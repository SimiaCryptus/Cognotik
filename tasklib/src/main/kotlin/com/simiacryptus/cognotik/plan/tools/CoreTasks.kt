package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.CognotikPlugin
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.exampleInstance
import com.simiacryptus.cognotik.plan.OrchestrationConfig.TaskBreakdownResult
import com.simiacryptus.cognotik.plan.cognitive.*
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType.Companion.registerCognitiveMode
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.registerTaskType
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask
import com.simiacryptus.cognotik.plan.tools.file.DataIngestTask
import com.simiacryptus.cognotik.plan.tools.file.DiscussionTask
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask
import com.simiacryptus.cognotik.plan.tools.file.GenerateQRImageTask
import com.simiacryptus.cognotik.plan.tools.file.GenerateSpriteSheetTask
import com.simiacryptus.cognotik.plan.tools.file.IllustrateDocumentTask
import com.simiacryptus.cognotik.plan.tools.file.ImageDecompositionTask
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask
import com.simiacryptus.cognotik.plan.tools.file.ImageTableTask
import com.simiacryptus.cognotik.plan.tools.file.ImageVariationTask
import com.simiacryptus.cognotik.plan.tools.file.IterativeFileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.OCRTask
import com.simiacryptus.cognotik.plan.tools.file.PdfFormTask
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask
import com.simiacryptus.cognotik.plan.tools.file.SegmentedImageGenerationTask
import com.simiacryptus.cognotik.plan.tools.file.TiledImageGenerationTask
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask
import com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.reasoning.AbductiveReasoningTask
import com.simiacryptus.cognotik.plan.tools.reasoning.AbstractionLadderTask
import com.simiacryptus.cognotik.plan.tools.reasoning.AdversarialReasoningTask
import com.simiacryptus.cognotik.plan.tools.reasoning.AnalogicalReasoningTask
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.tools.reasoning.CausalInferenceTask
import com.simiacryptus.cognotik.plan.tools.reasoning.ChainOfThoughtTask
import com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintRelaxationTask
import com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintSatisfactionTask
import com.simiacryptus.cognotik.plan.tools.reasoning.CounterfactualAnalysisTask
import com.simiacryptus.cognotik.plan.tools.reasoning.DecisionTreeTask
import com.simiacryptus.cognotik.plan.tools.reasoning.DecompositionSynthesisTask
import com.simiacryptus.cognotik.plan.tools.reasoning.EntropyReductionTreeTask
import com.simiacryptus.cognotik.plan.tools.reasoning.FiniteStateMachineTask
import com.simiacryptus.cognotik.plan.tools.reasoning.FunctorialMappingTask
import com.simiacryptus.cognotik.plan.tools.reasoning.GeneticOptimizationTask
import com.simiacryptus.cognotik.plan.tools.reasoning.IsomorphismDiscoveryTask
import com.simiacryptus.cognotik.plan.tools.reasoning.LateralThinkingTask
import com.simiacryptus.cognotik.plan.tools.reasoning.MathematicalReasoningTask
import com.simiacryptus.cognotik.plan.tools.reasoning.MetaCognitiveReflectionTask
import com.simiacryptus.cognotik.plan.tools.reasoning.NeuralNetworkLayerTask
import com.simiacryptus.cognotik.plan.tools.reasoning.ProbabilisticReasoningTask
import com.simiacryptus.cognotik.plan.tools.reasoning.SocraticDialogueTask
import com.simiacryptus.cognotik.plan.tools.reasoning.StructuralInvariantAnalysisTask
import com.simiacryptus.cognotik.plan.tools.reasoning.SystemsThinkingTask
import com.simiacryptus.cognotik.plan.tools.reasoning.TableCompilationTask
import com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.run.RunToolTask
import com.simiacryptus.cognotik.plan.tools.run.SingleFixTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask
import com.simiacryptus.cognotik.plan.tools.session.JdbcSessionTask
import com.simiacryptus.cognotik.plan.tools.social.DialecticalReasoningTask
import com.simiacryptus.cognotik.plan.tools.social.EthicalReasoningTask
import com.simiacryptus.cognotik.plan.tools.social.GameTheoryTask
import com.simiacryptus.cognotik.plan.tools.social.LLMExperimentTask
import com.simiacryptus.cognotik.plan.tools.social.LLMPollSimulationTask
import com.simiacryptus.cognotik.plan.tools.social.MultiPerspectiveAnalysisTask
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.tools.social.PoliticalOptimizationTask
import com.simiacryptus.cognotik.plan.tools.writing.ArticleGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.BusinessProposalTask
import com.simiacryptus.cognotik.plan.tools.writing.ComicBookGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.CreateErbTemplateTask
import com.simiacryptus.cognotik.plan.tools.writing.DataTableCompilationTask
import com.simiacryptus.cognotik.plan.tools.writing.EmailCampaignTask
import com.simiacryptus.cognotik.plan.tools.writing.InteractiveStoryTask
import com.simiacryptus.cognotik.plan.tools.writing.IterativeGraphGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.JournalismReasoningTask
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.RenderErbTemplateTask
import com.simiacryptus.cognotik.plan.tools.writing.ReportGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.ResearchPaperGenerationTask
import com.simiacryptus.cognotik.plan.tools.writing.ScriptwritingTask
import com.simiacryptus.cognotik.plan.tools.writing.SoftwareDesignDocumentTask
import com.simiacryptus.cognotik.plan.tools.writing.TechnicalExplanationTask
import com.simiacryptus.cognotik.plan.tools.writing.TutorialGenerationTask
import kotlin.jvm.java

object CoreTasks : CognotikPlugin {

  @JvmStatic
  val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)

  @JvmStatic
  val Adaptive =
    CognitiveModeType("Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt)

  @JvmStatic
  val Waterfall =
    CognitiveModeType("Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt)

  @JvmStatic
  val Hierarchical =
    CognitiveModeType("Hierarchical", CognitiveModeConfig::class.java, inputCnt = HierarchicalPlanningMode.inputCnt)

  @JvmStatic
  val Parallel = CognitiveModeType("Parallel", ParallelModeConfig::class.java, inputCnt = ParallelMode.inputCnt)

  @JvmStatic
  val Protocol = CognitiveModeType("Protocol", ProtocolModeConfig::class.java, inputCnt = ProtocolMode.inputCnt)

  @JvmStatic
  val Council = CognitiveModeType("Council", CouncilModeConfig::class.java, inputCnt = CouncilMode.inputCnt)

  @JvmStatic
  val PersonaChat =
    CognitiveModeType("PersonaChat", PersonaChatConfig::class.java, inputCnt = PersonaChatMode.inputCnt)

  @JvmStatic
  val Coding = CognitiveModeType("Coding", CodingMode.CodingModeConfig::class.java)

  override fun init() {
    registerTaskType(AbductiveReasoningTask.AbductiveReasoning)
    registerTaskType(AbstractionLadderTask.AbstractionLadder)
    registerTaskType(AdversarialReasoningTask.AdversarialReasoning)
    registerTaskType(AnalogicalReasoningTask.AnalogicalReasoning)
    registerTaskType(ArticleGenerationTask.ArticleGeneration)
    registerTaskType(AutoFixTask.AutoFix)
    registerTaskType(BrainstormingTask.Brainstorming)
    registerTaskType(BusinessProposalTask.BusinessProposal)
    registerTaskType(CausalInferenceTask.CausalInference)
    registerTaskType(ChainOfThoughtTask.ChainOfThought)
    registerTaskType(ComicBookGenerationTask.ComicBookGeneration)
    registerTaskType(CommandSessionTask.CommandSession)
    registerTaskType(ConstraintRelaxationTask.ConstraintRelaxation)
    registerTaskType(ConstraintSatisfactionTask.ConstraintSatisfaction)
    registerTaskType(CounterfactualAnalysisTask.CounterfactualAnalysis)
    registerTaskType(CreateErbTemplateTask.CreateErbTemplate)
    registerTaskType(CrawlerAgentTask.CrawlerAgent)
    registerTaskType(DataIngestTask.DataIngest)
    registerTaskType(DataTableCompilationTask.DataTableCompilation)
    registerTaskType(DecisionTreeTask.DecisionTree)
    registerTaskType(DecompositionSynthesisTask.DecompositionSynthesis)
    registerTaskType(DialecticalReasoningTask.DialecticalReasoning)
    registerTaskType(DiscussionTask.Discussion)
    registerTaskType(EntropyReductionTreeTask.EntropyReductionTree)
    registerTaskType(EmailCampaignTask.EmailCampaign)
    registerTaskType(EthicalReasoningTask.EthicalReasoning)
    registerTaskType(FileAppendTask.FileAppend)
    registerTaskType(FileModificationTask.FileModification)
    registerTaskType(FileSearchTask.FileSearch)
    registerTaskType(FiniteStateMachineTask.FiniteStateMachine)
    registerTaskType(FunctorialMappingTask.FunctorialMapping)
    registerTaskType(GameEconomyTask.GameEconomy)
    registerTaskType(GameLevelDesignTask.GameLevelDesign)
    registerTaskType(GameMechanicsDesignTask.GameMechanicsDesign)
    registerTaskType(GameNarrativeDesignTask.GameNarrativeDesign)
    registerTaskType(GameTheoryTask.GameTheory)
    registerTaskType(GeneratePresentationTask.GeneratePresentation)
    registerTaskType(GenerateQRImageTask.GenerateQRImage)
    registerTaskType(GenerateSpriteSheetTask.GenerateSpriteSheet)
    registerTaskType(GeneticOptimizationTask.GeneticOptimization)
    registerTaskType(GitHubSearchTask.GitHubSearch)
    registerTaskType(IllustrateDocumentTask.IllustrateDocument)
    registerTaskType(ImageDecompositionTask.ImageDecomposition)
    registerTaskType(ImageGenerationTask.GenerateImage)
    registerTaskType(IterativeFileModificationTask.IterativeFileModification)
    registerTaskType(TiledImageGenerationTask.TiledImageGeneration)
    registerTaskType(ImageTableTask.ImageTable)
    registerTaskType(ImageVariationTask.ImageVariation)
    registerTaskType(InteractiveStoryTask.InteractiveStory)
    registerTaskType(IsomorphismDiscoveryTask.IsomorphismDiscovery)
    registerTaskType(IterativeGraphGenerationTask.IterativeGraphGeneration)
    registerTaskType(JdbcSessionTask.JdbcSession)
    registerTaskType(JournalismReasoningTask.JournalismReasoning)
    registerTaskType(LanguageServerTask.LanguageServer)
    registerTaskType(LateralThinkingTask.LateralThinking)
    registerTaskType(LLMExperimentTask.LLMExperiment)
    registerTaskType(LLMPollSimulationTask.LLMPollSimulation)
    registerTaskType(MathematicalReasoningTask.MathematicalReasoning)
    registerTaskType(MCPToolTask.MCPTool)
    registerTaskType(MetaCognitiveReflectionTask.MetaCognitiveReflection)
    registerTaskType(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
    registerTaskType(NarrativeGenerationTask.NarrativeGeneration)
    registerTaskType(NeuralNetworkLayerTask.NeuralNetworkLayer)
    registerTaskType(OCRTask.OCR)
    registerTaskType(PdfFormTask.PdfForm)
    registerTaskType(PersuasiveEssayTask.PersuasiveEssay)
    registerTaskType(PoliticalOptimizationTask.PoliticalOptimization)
    registerTaskType(ProbabilisticReasoningTask.ProbabilisticReasoning)
    registerTaskType(ReadDocumentsTask.ReadDocuments)
    registerTaskType(RenderErbTemplateTask.RenderErbTemplate)
    registerTaskType(ReportGenerationTask.ReportGeneration)
    registerTaskType(ResearchPaperGenerationTask.ResearchPaperGeneration)
    registerTaskType(RunCodeTask.RunCode)
    registerTaskType(RunToolTask.RunTool)
    registerTaskType(ScriptwritingTask.Scriptwriting)
    registerTaskType(SegmentedImageGenerationTask.SegmentedImageGeneration)
    registerTaskType(SingleFixTask.SingleFix)
    registerTaskType(SocraticDialogueTask.SocraticDialogue)
    registerTaskType(SoftwareDesignDocumentTask.SoftwareDesignDocument)
    registerTaskType(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
    registerTaskType(SubPlanTask.SubPlan)
    registerTaskType(SymbolsDbCodeTask.SymbolsDbCode)
    registerTaskType(SystemsThinkingTask.SystemsThinking)
    registerTaskType(TableCompilationTask.TableCompilation)
    registerTaskType(TechnicalExplanationTask.TechnicalExplanation)
    registerTaskType(TemporalReasoningTask.TemporalReasoning)
    registerTaskType(TutorialGenerationTask.TutorialGeneration)
    registerTaskType(WriteHtmlTask.WriteHtml)

    require(CognitiveSchemaStrategy.values().isNotEmpty())

    registerCognitiveMode(Chat) { config, session, user -> ConversationalMode(config, session, user) }
    registerCognitiveMode(Adaptive) { config, session, user -> AdaptivePlanningMode(config, session, user) }
    registerCognitiveMode(Waterfall) { config, session, user -> WaterfallMode(config, session, user) }
    registerCognitiveMode(Hierarchical) { config, session, user -> HierarchicalPlanningMode(config, session, user) }
    registerCognitiveMode(Parallel) { config, session, user -> ParallelMode(config, session, user) }
    registerCognitiveMode(Protocol) { config, session, user -> ProtocolMode(config, session, user) }
    registerCognitiveMode(Council) { config, session, user -> CouncilMode(config, session, user) }
    registerCognitiveMode(PersonaChat) { config, session, user -> PersonaChatMode(config, session, user) }
    registerCognitiveMode(Coding) { config, session, user -> CodingMode(config, session, user) }


    exampleInstance = TaskBreakdownResult(
      tasksByID = mapOf(
        "1" to AutoFixTask.AutoFixTaskExecutionConfigData(
          task_description = "Task 1", task_dependencies = listOf(), commands = mutableListOf(
            AutoFixTask.CommandWithWorkingDir(
              command = mutableListOf("echo", "Hello, World!"), working_dir = "."
            )
          )
        ), "2" to FileModificationTask.FileModificationTaskExecutionConfigData(
          task_description = "Task 2",
          task_dependencies = listOf("1"),
          related_files = listOf("input2.txt"),
          files = listOf("output2.txt"),
        )
      ),
    )

  }
}