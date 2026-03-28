package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.AdaptivePlanningConfig
import com.simiacryptus.cognotik.plan.cognitive.AdaptivePlanningMode
import com.simiacryptus.cognotik.plan.cognitive.CodingMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveSchemaStrategy
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode
import com.simiacryptus.cognotik.plan.cognitive.ConversationalModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CouncilMode
import com.simiacryptus.cognotik.plan.cognitive.CouncilModeConfig
import com.simiacryptus.cognotik.plan.cognitive.HierarchicalPlanningMode
import com.simiacryptus.cognotik.plan.cognitive.ParallelMode
import com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig
import com.simiacryptus.cognotik.plan.cognitive.PersonaChatConfig
import com.simiacryptus.cognotik.plan.cognitive.PersonaChatMode
import com.simiacryptus.cognotik.plan.cognitive.ProtocolMode
import com.simiacryptus.cognotik.plan.cognitive.ProtocolModeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.TaskType
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

object CoreTasks : CognotikPlugin {

  @JvmStatic
  val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)

  @JvmStatic
  val Adaptive = CognitiveModeType(
    "Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt
  )

  @JvmStatic
  val Waterfall = CognitiveModeType(
    "Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt
  )

  @JvmStatic
  val Hierarchical = CognitiveModeType(
    "Hierarchical", CognitiveModeConfig::class.java, inputCnt = HierarchicalPlanningMode.inputCnt
  )

  @JvmStatic
  val Parallel = CognitiveModeType("Parallel", ParallelModeConfig::class.java, inputCnt = ParallelMode.inputCnt)

  @JvmStatic
  val Protocol = CognitiveModeType("Protocol", ProtocolModeConfig::class.java, inputCnt = ProtocolMode.inputCnt)

  @JvmStatic
  val Council = CognitiveModeType("Council", CouncilModeConfig::class.java, inputCnt = CouncilMode.inputCnt)

  @JvmStatic
  val PersonaChat = CognitiveModeType("PersonaChat", PersonaChatConfig::class.java, inputCnt = PersonaChatMode.inputCnt)

  @JvmStatic
  val Coding = CognitiveModeType("Coding", CodingMode.CodingModeConfig::class.java)

  override fun init() {
    TaskType.registerTaskType(AbductiveReasoningTask.AbductiveReasoning)
    TaskType.registerTaskType(AbstractionLadderTask.AbstractionLadder)
    TaskType.registerTaskType(AdversarialReasoningTask.AdversarialReasoning)
    TaskType.registerTaskType(AnalogicalReasoningTask.AnalogicalReasoning)
    TaskType.registerTaskType(ArticleGenerationTask.ArticleGeneration)
    TaskType.registerTaskType(AutoFixTask.AutoFix)
    TaskType.registerTaskType(BrainstormingTask.Brainstorming)
    TaskType.registerTaskType(BusinessProposalTask.BusinessProposal)
    TaskType.registerTaskType(CausalInferenceTask.CausalInference)
    TaskType.registerTaskType(ChainOfThoughtTask.ChainOfThought)
    TaskType.registerTaskType(ComicBookGenerationTask.ComicBookGeneration)
    TaskType.registerTaskType(CommandSessionTask.CommandSession)
    TaskType.registerTaskType(ConstraintRelaxationTask.ConstraintRelaxation)
    TaskType.registerTaskType(ConstraintSatisfactionTask.ConstraintSatisfaction)
    TaskType.registerTaskType(CounterfactualAnalysisTask.CounterfactualAnalysis)
    TaskType.registerTaskType(CreateErbTemplateTask.CreateErbTemplate)
    TaskType.registerTaskType(CrawlerAgentTask.CrawlerAgent)
    TaskType.registerTaskType(DataIngestTask.DataIngest)
    TaskType.registerTaskType(DataTableCompilationTask.DataTableCompilation)
    TaskType.registerTaskType(DecisionTreeTask.DecisionTree)
    TaskType.registerTaskType(DecompositionSynthesisTask.DecompositionSynthesis)
    TaskType.registerTaskType(DialecticalReasoningTask.DialecticalReasoning)
    TaskType.registerTaskType(DiscussionTask.Discussion)
    TaskType.registerTaskType(EntropyReductionTreeTask.EntropyReductionTree)
    TaskType.registerTaskType(EmailCampaignTask.EmailCampaign)
    TaskType.registerTaskType(EthicalReasoningTask.EthicalReasoning)
    TaskType.registerTaskType(FileAppendTask.FileAppend)
    TaskType.registerTaskType(FileModificationTask.FileModification)
    TaskType.registerTaskType(FileSearchTask.FileSearch)
    TaskType.registerTaskType(FiniteStateMachineTask.FiniteStateMachine)
    TaskType.registerTaskType(FunctorialMappingTask.FunctorialMapping)
    TaskType.registerTaskType(GameEconomyTask.GameEconomy)
    TaskType.registerTaskType(GameLevelDesignTask.GameLevelDesign)
    TaskType.registerTaskType(GameMechanicsDesignTask.GameMechanicsDesign)
    TaskType.registerTaskType(GameNarrativeDesignTask.GameNarrativeDesign)
    TaskType.registerTaskType(GameTheoryTask.GameTheory)
    TaskType.registerTaskType(GeneratePresentationTask.GeneratePresentation)
    TaskType.registerTaskType(GenerateQRImageTask.GenerateQRImage)
    TaskType.registerTaskType(GenerateSpriteSheetTask.GenerateSpriteSheet)
    TaskType.registerTaskType(GeneticOptimizationTask.GeneticOptimization)
    TaskType.registerTaskType(GitHubSearchTask.GitHubSearch)
    TaskType.registerTaskType(IllustrateDocumentTask.IllustrateDocument)
    TaskType.registerTaskType(ImageDecompositionTask.ImageDecomposition)
    TaskType.registerTaskType(ImageGenerationTask.GenerateImage)
    TaskType.registerTaskType(IterativeFileModificationTask.IterativeFileModification)
    TaskType.registerTaskType(TiledImageGenerationTask.TiledImageGeneration)
    TaskType.registerTaskType(ImageTableTask.ImageTable)
    TaskType.registerTaskType(ImageVariationTask.ImageVariation)
    TaskType.registerTaskType(InteractiveStoryTask.InteractiveStory)
    TaskType.registerTaskType(IsomorphismDiscoveryTask.IsomorphismDiscovery)
    TaskType.registerTaskType(IterativeGraphGenerationTask.IterativeGraphGeneration)
    TaskType.registerTaskType(JdbcSessionTask.JdbcSession)
    TaskType.registerTaskType(JournalismReasoningTask.JournalismReasoning)
    TaskType.registerTaskType(LanguageServerTask.LanguageServer)
    TaskType.registerTaskType(LateralThinkingTask.LateralThinking)
    TaskType.registerTaskType(LLMExperimentTask.LLMExperiment)
    TaskType.registerTaskType(LLMPollSimulationTask.LLMPollSimulation)
    TaskType.registerTaskType(MathematicalReasoningTask.MathematicalReasoning)
    TaskType.registerTaskType(MCPToolTask.MCPTool)
    TaskType.registerTaskType(MetaCognitiveReflectionTask.MetaCognitiveReflection)
    TaskType.registerTaskType(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
    TaskType.registerTaskType(NarrativeGenerationTask.NarrativeGeneration)
    TaskType.registerTaskType(NeuralNetworkLayerTask.NeuralNetworkLayer)
    TaskType.registerTaskType(OCRTask.OCR)
    TaskType.registerTaskType(PdfFormTask.PdfForm)
    TaskType.registerTaskType(PersuasiveEssayTask.PersuasiveEssay)
    TaskType.registerTaskType(PoliticalOptimizationTask.PoliticalOptimization)
    TaskType.registerTaskType(ProbabilisticReasoningTask.ProbabilisticReasoning)
    TaskType.registerTaskType(ReadDocumentsTask.ReadDocuments)
    TaskType.registerTaskType(RenderErbTemplateTask.RenderErbTemplate)
    TaskType.registerTaskType(ReportGenerationTask.ReportGeneration)
    TaskType.registerTaskType(ResearchPaperGenerationTask.ResearchPaperGeneration)
    TaskType.registerTaskType(RunCodeTask.RunCode)
    TaskType.registerTaskType(RunToolTask.RunTool)
    TaskType.registerTaskType(ScriptwritingTask.Scriptwriting)
    TaskType.registerTaskType(SegmentedImageGenerationTask.SegmentedImageGeneration)
    TaskType.registerTaskType(SingleFixTask.SingleFix)
    TaskType.registerTaskType(SocraticDialogueTask.SocraticDialogue)
    TaskType.registerTaskType(SoftwareDesignDocumentTask.SoftwareDesignDocument)
    TaskType.registerTaskType(StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
    TaskType.registerTaskType(SubPlanTask.SubPlan)
    TaskType.registerTaskType(SymbolsDbCodeTask.SymbolsDbCode)
    TaskType.registerTaskType(SystemsThinkingTask.SystemsThinking)
    TaskType.registerTaskType(TableCompilationTask.TableCompilation)
    TaskType.registerTaskType(TechnicalExplanationTask.TechnicalExplanation)
    TaskType.registerTaskType(TemporalReasoningTask.TemporalReasoning)
    TaskType.registerTaskType(TutorialGenerationTask.TutorialGeneration)
    TaskType.registerTaskType(WriteHtmlTask.WriteHtml)

    require(CognitiveSchemaStrategy.values().isNotEmpty())

    CognitiveModeType.registerCognitiveMode(Chat) { config, session, user -> ConversationalMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Adaptive) { config, session, user -> AdaptivePlanningMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Waterfall) { config, session, user -> WaterfallMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Hierarchical) { config, session, user -> HierarchicalPlanningMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Parallel) { config, session, user -> ParallelMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Protocol) { config, session, user -> ProtocolMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Council) { config, session, user -> CouncilMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(PersonaChat) { config, session, user -> PersonaChatMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Coding) { config, session, user -> CodingMode(config, session, user) }


    OrchestrationConfig.exampleInstance = OrchestrationConfig.TaskBreakdownResult(
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