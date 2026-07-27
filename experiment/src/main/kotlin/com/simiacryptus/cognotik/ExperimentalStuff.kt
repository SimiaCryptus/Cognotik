package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.apps.ResourceApps
import com.simiacryptus.cognotik.autofix.SingleFixTask
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.images.GenerateQRImageTask
import com.simiacryptus.cognotik.plan.tools.images.ImageDecompositionTask
import com.simiacryptus.cognotik.plan.tools.images.SegmentedImageGenerationTask
import com.simiacryptus.cognotik.plan.tools.images.TiledImageGenerationTask
import com.simiacryptus.cognotik.plan.tools.office.BusinessProposalTask
import com.simiacryptus.cognotik.plan.tools.office.ReadDocumentsTask

@Suppress("unused") class ExperimentalStuff : CognotikPlugin {

    override fun init() {

        require(com.simiacryptus.cognotik.plan.cognitive.CognitiveSchemaStrategy.values().isNotEmpty())

        // --- Data & File Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.FileAppendTask.FileAppend)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.FileSearchTask.FileSearch)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.DecisionTreeTask.DecisionTree)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.EntropyReductionTreeTask.EntropyReductionTree)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.JdbcSessionTask.JdbcSession)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.data.DataTableCompilationTask.DataTableCompilation)

        // --- Office & Document Task Types ---
        TaskType.registerTaskType(BusinessProposalTask.BusinessProposal)
        TaskType.registerTaskType(ReadDocumentsTask.ReadDocuments)

        // --- Reasoning Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.AbductiveReasoningTask.AbductiveReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.AbstractionLadderTask.AbstractionLadder)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.AdversarialReasoningTask.AdversarialReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.AnalogicalReasoningTask.AnalogicalReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.CausalInferenceTask.CausalInference)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.ChainOfThoughtTask.ChainOfThought)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintRelaxationTask.ConstraintRelaxation)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintSatisfactionTask.ConstraintSatisfaction)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.CounterfactualAnalysisTask.CounterfactualAnalysis)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.DecompositionSynthesisTask.DecompositionSynthesis)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.FiniteStateMachineTask.FiniteStateMachine)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.FunctorialMappingTask.FunctorialMapping)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.GeneticOptimizationTask.GeneticOptimization)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.IsomorphismDiscoveryTask.IsomorphismDiscovery)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.LateralThinkingTask.LateralThinking)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.MathematicalReasoningTask.MathematicalReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.MetaCognitiveReflectionTask.MetaCognitiveReflection)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.NeuralNetworkLayerTask.NeuralNetworkLayer)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.ProbabilisticReasoningTask.ProbabilisticReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.StructuralInvariantAnalysisTask.StructuralInvariantAnalysis)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.SystemsThinkingTask.SystemsThinking)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.TableCompilationTask.TableCompilation)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask.TemporalReasoning)

        // --- Writing & Content Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.ArticleGenerationTask.ArticleGeneration)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.EmailCampaignTask.EmailCampaign)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.InteractiveStoryTask.InteractiveStory)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.IterativeGraphGenerationTask.IterativeGraphGeneration)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.JournalismReasoningTask.JournalismReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.ReportGenerationTask.ReportGeneration)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.ResearchPaperGenerationTask.ResearchPaperGeneration)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.ScriptwritingTask.Scriptwriting)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.SoftwareDesignDocumentTask.SoftwareDesignDocument)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.writing.TechnicalExplanationTask.TechnicalExplanation)

        // --- Image & Media Task Types ---
        TaskType.registerTaskType(GenerateQRImageTask.GenerateQRImage)
        TaskType.registerTaskType(ImageDecompositionTask.ImageDecomposition)
        TaskType.registerTaskType(TiledImageGenerationTask.TiledImageGeneration)
        TaskType.registerTaskType(SegmentedImageGenerationTask.SegmentedImageGeneration)

        // --- Game Design Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask.GameEconomy)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask.GameLevelDesign)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask.GameMechanicsDesign)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask.GameNarrativeDesign)

        // --- Social & Ethical Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.file.DiscussionTask.Discussion)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.social.EthicalReasoningTask.EthicalReasoning)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.social.LLMExperimentTask.LLMExperiment)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.social.LLMPollSimulationTask.LLMPollSimulation)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.social.PoliticalOptimizationTask.PoliticalOptimization)

        // --- Code & Execution Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask.CommandSession)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask.LanguageServer)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.run.RunCodeTask.RunCode)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.run.RunToolTask.RunTool)
        TaskType.registerTaskType(SingleFixTask.SingleFix)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.run.SymbolsDbCodeTask.SymbolsDbCode)

        // --- Online & External Tool Task Types ---
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask.GitHubSearch)
        TaskType.registerTaskType(com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPTool)

        CognitiveModeType.registerCognitiveMode(Hierarchical) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.HierarchicalPlanningMode(config, session, user) }
        CognitiveModeType.registerCognitiveMode(Parallel) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.ParallelMode(config, session, user) }
        CognitiveModeType.registerCognitiveMode(Protocol) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.ProtocolMode(config, session, user) }
        CognitiveModeType.registerCognitiveMode(Council) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.CouncilMode(config, session, user) }
        CognitiveModeType.registerCognitiveMode(PersonaChat) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.PersonaChatMode(config, session, user) }
        CognitiveModeType.registerCognitiveMode(Coding) { config, session, user -> com.simiacryptus.cognotik.plan.cognitive.CodingMode(config, session, user) }

        ResourceApps("apps/experimental_apps.json", ExperimentalStuff::class.java.classLoader).init()

    }

    companion object {

        @JvmStatic
        val Hierarchical = CognitiveModeType(
            "Hierarchical", CognitiveModeConfig::class.java, inputCnt = com.simiacryptus.cognotik.plan.cognitive.HierarchicalPlanningMode.inputCnt
        )

        @JvmStatic
        val Parallel = CognitiveModeType("Parallel", com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig::class.java, inputCnt = com.simiacryptus.cognotik.plan.cognitive.ParallelMode.inputCnt)

        @JvmStatic
        val Protocol = CognitiveModeType("Protocol", com.simiacryptus.cognotik.plan.cognitive.ProtocolModeConfig::class.java, inputCnt = com.simiacryptus.cognotik.plan.cognitive.ProtocolMode.inputCnt)

        @JvmStatic
        val Council = CognitiveModeType("Council", com.simiacryptus.cognotik.plan.cognitive.CouncilModeConfig::class.java, inputCnt = com.simiacryptus.cognotik.plan.cognitive.CouncilMode.inputCnt)

        @JvmStatic
        val PersonaChat = CognitiveModeType("PersonaChat", com.simiacryptus.cognotik.plan.cognitive.PersonaChatConfig::class.java, inputCnt = com.simiacryptus.cognotik.plan.cognitive.PersonaChatMode.inputCnt)

        @JvmStatic
        val Coding = CognitiveModeType("Coding", com.simiacryptus.cognotik.plan.cognitive.CodingMode.CodingModeConfig::class.java)

    }
}