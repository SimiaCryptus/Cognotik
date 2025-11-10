package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class BusinessProposalTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: BusinessProposalTaskExecutionConfigData?
) : AbstractTask<BusinessProposalTask.BusinessProposalTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class BusinessProposalTaskExecutionConfigData(
        @Description("The title or name of the proposal")
        val proposal_title: String? = null,

        @Description("The type of proposal (e.g., 'project', 'investment', 'grant', 'partnership', 'rfp_response')")
        val proposal_type: String = "project",

        @Description("The primary objective or goal of the proposal")
        val objective: String? = null,

        @Description("The organization or individual submitting the proposal")
        val proposing_organization: String? = null,

        @Description("The target audience or decision-makers who will evaluate the proposal")
        val decision_makers: List<String>? = null,

        @Description("Budget range or financial scope (e.g., '$50,000-$100,000', 'under $1M')")
        val budget_range: String? = null,

        @Description("Project timeline or duration (e.g., '6 months', '2024-2025', 'Q1-Q3')")
        val timeline: String? = null,

        @Description("Key stakeholders and their interests")
        val stakeholders: Map<String, String>? = null,

        @Description("Whether to include detailed ROI calculations and financial projections")
        val include_roi_analysis: Boolean = true,

        @Description("Whether to include risk assessment and mitigation strategies")
        val include_risk_assessment: Boolean = true,

        @Description("Whether to include competitive analysis or alternatives comparison")
        val include_competitive_analysis: Boolean = true,

        @Description("Whether to include detailed timeline with milestones")
        val include_timeline_milestones: Boolean = true,

        @Description("Whether to include team/resource requirements")
        val include_resource_requirements: Boolean = true,

        @Description("Whether to include appendices and supporting documents")
        val include_appendices: Boolean = true,

        @Description("Urgency level of the opportunity (e.g., 'critical', 'high', 'moderate', 'low')")
        val urgency_level: String = "moderate",

        @Description("Tone of the proposal (e.g., 'formal', 'professional', 'persuasive', 'collaborative')")
        val tone: String = "professional",

        @Description("Target word count for the complete proposal")
        val target_word_count: Int = 3000,

        @Description("Number of revision passes for quality improvement")
        val revision_passes: Int = 1,

        @Description("Related files or research to incorporate")
        val related_files: List<String>? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = BusinessProposal.name,
        task_description = task_description ?: "Generate business proposal: '$proposal_title'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (proposal_title.isNullOrBlank()) {
                return "proposal_title must not be null or blank"
            }
            if (objective.isNullOrBlank()) {
                return "objective must not be null or blank"
            }
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            if (proposal_type.isBlank()) {
                return "proposal_type must not be blank"
            }
            if (urgency_level.isBlank()) {
                return "urgency_level must not be blank"
            }
            if (tone.isBlank()) {
                return "tone must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ProposalOutline(
        @Description("The proposal title")
        val title: String = "",
        @Description("Executive summary overview")
        val executive_summary: String = "",
        @Description("Problem statement or opportunity")
        val problem_statement: String = "",
        @Description("Proposed solution overview")
        val solution_overview: String = "",
        @Description("Main sections of the proposal")
        val sections: List<ProposalSection> = emptyList(),
        @Description("Key success metrics")
        val success_metrics: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (executive_summary.isBlank()) return "executive_summary must not be blank"
            if (problem_statement.isBlank()) return "problem_statement must not be blank"
            if (sections.isEmpty()) return "sections must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ProposalSection(
        @Description("Section title")
        val title: String = "",
        @Description("Section purpose")
        val purpose: String = "",
        @Description("Key points to cover")
        val key_points: List<String> = emptyList(),
        @Description("Estimated word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (purpose.isBlank()) return "purpose must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class StakeholderAnalysis(
        @Description("Stakeholder analyses")
        val stakeholders: List<StakeholderProfile> = emptyList()
    ) : ValidatedObject

    data class StakeholderProfile(
        @Description("Stakeholder name or role")
        val name: String = "",
        @Description("Their primary interests")
        val interests: List<String> = emptyList(),
        @Description("Their concerns or objections")
        val concerns: List<String> = emptyList(),
        @Description("How to address their needs")
        val addressing_strategy: String = "",
        @Description("Their influence level")
        val influence_level: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (name.isBlank()) return "name must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ROIAnalysis(
        @Description("Financial projections")
        val financial_projections: FinancialProjections = FinancialProjections(),
        @Description("Cost breakdown")
        val cost_breakdown: List<CostItem> = emptyList(),
        @Description("Expected benefits")
        val expected_benefits: List<Benefit> = emptyList(),
        @Description("ROI calculation summary")
        val roi_summary: String = "",
        @Description("Payback period")
        val payback_period: String = ""
    ) : ValidatedObject

    data class FinancialProjections(
        @Description("Total investment required")
        val total_investment: String = "",
        @Description("Year 1 projected return")
        val year_1_return: String = "",
        @Description("Year 2 projected return")
        val year_2_return: String = "",
        @Description("Year 3 projected return")
        val year_3_return: String = "",
        @Description("Break-even point")
        val break_even_point: String = ""
    ) : ValidatedObject

    data class CostItem(
        @Description("Cost category")
        val category: String = "",
        @Description("Amount")
        val amount: String = "",
        @Description("Justification")
        val justification: String = ""
    ) : ValidatedObject

    data class Benefit(
        @Description("Benefit type")
        val type: String = "",
        @Description("Description")
        val description: String = "",
        @Description("Quantifiable value")
        val quantifiable_value: String = "",
        @Description("Timeline to realize")
        val timeline: String = ""
    ) : ValidatedObject

    data class RiskAssessment(
        @Description("Identified risks")
        val risks: List<Risk> = emptyList(),
        @Description("Overall risk level")
        val overall_risk_level: String = ""
    ) : ValidatedObject

    data class Risk(
        @Description("Risk category")
        val category: String = "",
        @Description("Risk description")
        val description: String = "",
        @Description("Probability")
        val probability: String = "",
        @Description("Impact level")
        val impact: String = "",
        @Description("Mitigation strategy")
        val mitigation_strategy: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (description.isBlank()) return "description must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class CompetitiveAnalysis(
        @Description("Alternative approaches")
        val alternatives: List<Alternative> = emptyList(),
        @Description("Competitive advantages")
        val competitive_advantages: List<String> = emptyList(),
        @Description("Why this proposal is superior")
        val superiority_statement: String = ""
    ) : ValidatedObject

    data class Alternative(
        @Description("Alternative name")
        val name: String = "",
        @Description("Description")
        val description: String = "",
        @Description("Pros")
        val pros: List<String> = emptyList(),
        @Description("Cons")
        val cons: List<String> = emptyList(),
        @Description("Why our proposal is better")
        val comparison: String = ""
    ) : ValidatedObject

    data class TimelineMilestones(
        @Description("Project phases")
        val phases: List<ProjectPhase> = emptyList(),
        @Description("Critical path items")
        val critical_path: List<String> = emptyList()
    ) : ValidatedObject

    data class ProjectPhase(
        @Description("Phase name")
        val name: String = "",
        @Description("Duration")
        val duration: String = "",
        @Description("Key deliverables")
        val deliverables: List<String> = emptyList(),
        @Description("Dependencies")
        val dependencies: List<String> = emptyList()
    ) : ValidatedObject

    data class ProposalContent(
        @Description("Section title")
        val section_title: String = "",
        @Description("Section content")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("Key messages")
        val key_messages: List<String> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
BusinessProposal - Generate comprehensive business proposals with ROI analysis and risk assessment
  ** Specify the proposal title and objective
  ** Define proposal type (project, investment, grant, partnership, RFP response)
  ** Identify decision-makers and stakeholders
  ** Set budget range and timeline
  ** Enable ROI calculations and financial projections
  ** Include risk assessment and mitigation strategies
  ** Add competitive analysis and alternatives comparison
  ** Generate timeline with milestones
  ** Specify resource requirements
  ** Produces complete, persuasive business proposal
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // Create transcript file
        val transcriptStream = task.transcript("transcript")
        val proposalStream = task.transcript("proposal")
        transcriptStream?.let { stream ->
            stream.write("# Business Proposal Generation Transcript\n\n".toByteArray())
            stream.write("**Proposal:** ${executionConfig?.proposal_title}\n".toByteArray())
            stream.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            stream.write("---\n\n".toByteArray())
        }
        fun logToTranscript(message: String) {
            transcriptStream?.write("$message\n".toByteArray())
        }

        fun writeToProposal(message: String) {
            proposalStream?.write("$message\n".toByteArray())
        }

        val startTime = System.currentTimeMillis()
        log.info("Starting BusinessProposalTask for: '${executionConfig?.proposal_title}'")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            logToTranscript("## Configuration Validation Failed\n\n$validationError\n")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val proposalTitle = executionConfig?.proposal_title
        if (proposalTitle.isNullOrBlank()) {
            log.error("No proposal title specified")
            logToTranscript("## Error: No Proposal Title\n")
            task.safeComplete("CONFIGURATION ERROR: No proposal title specified", log)
            resultFn("CONFIGURATION ERROR: No proposal title specified")
            return
        }

        val api = orchestrationConfig.defaultChatter ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Business Proposal Generation")
            appendLine()
            appendLine("**Proposal:** $proposalTitle")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Type: ${executionConfig.proposal_type}")
            appendLine("- Objective: ${executionConfig.objective}")
            appendLine("- Proposing Organization: ${executionConfig.proposing_organization ?: "Not specified"}")
            appendLine("- Budget Range: ${executionConfig.budget_range ?: "Not specified"}")
            appendLine("- Timeline: ${executionConfig.timeline ?: "Not specified"}")
            appendLine("- Urgency: ${executionConfig.urgency_level}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Target Word Count: ${executionConfig.target_word_count}")
            appendLine()
            appendLine("## Analysis Components")
            appendLine("- ROI Analysis: ${if (executionConfig.include_roi_analysis) "✓" else "✗"}")
            appendLine("- Risk Assessment: ${if (executionConfig.include_risk_assessment) "✓" else "✗"}")
            appendLine("- Competitive Analysis: ${if (executionConfig.include_competitive_analysis) "✓" else "✗"}")
            appendLine("- Timeline & Milestones: ${if (executionConfig.include_timeline_milestones) "✓" else "✗"}")
            appendLine("- Resource Requirements: ${if (executionConfig.include_resource_requirements) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Strategic Analysis")
            appendLine("*Analyzing stakeholders and strategic positioning...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Business Proposal: $proposalTitle\n\n")
        // Load input files if specified
        val inputFileContent =
            super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)
        val messagesWithContext = if (inputFileContent.isNotBlank()) {
            messages + listOf(
                "## Input Files Context\n\n$inputFileContent"
            )
        } else {
            messages
        }
        // Include messages in context
        val messagesContext = if (messagesWithContext.isNotEmpty()) {
            buildString {
                appendLine("## User Input")
                appendLine()
                messagesWithContext.forEach { msg ->
                    appendLine(msg)
                    appendLine()
                }
            }
        } else {
            ""
        }


        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = task.ui.newTask(false)
                tabs["Research Context"] = contextTask.placeholder
                contextTask.add(
                    buildString {
                        appendLine("# Research Context")
                        appendLine()
                        if (priorContext.isNotBlank()) {
                            appendLine("## Prior Context")
                            appendLine(priorContext.truncateForDisplay(2000))
                            appendLine()
                        }
                        if (contextFiles.isNotBlank()) {
                            appendLine("## Related Files")
                            appendLine(contextFiles.truncateForDisplay(2000))
                        }
                        if (messagesContext.isNotBlank()) {
                            appendLine()
                            appendLine(messagesContext.truncateForDisplay(2000))
                        }
                    }.renderMarkdown
                )
                task.update()
            }

            // Phase 1: Stakeholder Analysis
            log.info("Phase 1: Analyzing stakeholders")
            logToTranscript("## Phase 1: Stakeholder Analysis\n\n")
            val stakeholderTask = task.ui.newTask(false)
            tabs["Stakeholder Analysis"] = stakeholderTask.placeholder

            stakeholderTask.add(
                buildString {
                    appendLine("# Stakeholder Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing decision-makers and stakeholders...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val stakeholderAgent = ParsedAgent(
                resultClass = StakeholderAnalysis::class.java,
                prompt = """
You are a strategic business analyst. Analyze the stakeholders for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}

Decision Makers: ${executionConfig.decision_makers?.joinToString(", ") ?: "Not specified"}
Known Stakeholders: ${executionConfig.stakeholders?.entries?.joinToString("\n") { (name, interest) -> "- $name: $interest" } ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

For each key stakeholder (decision-makers and influencers), provide:
- Name or role
- Their primary interests and priorities
- Potential concerns or objections they might have
- Strategy for addressing their needs in the proposal
- Their influence level (High/Medium/Low)

Consider:
- What motivates each stakeholder?
- What are their success criteria?
- What risks or concerns might they have?
- How can the proposal align with their goals?

Identify 3-5 key stakeholders who will influence the decision.
          """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = orchestrationConfig.parsingChatter
            )

            val stakeholderAnalysis = stakeholderAgent.answer(listOf("Analyze stakeholders")).obj
            log.debug("Analyzed ${stakeholderAnalysis.stakeholders.size} stakeholders")
            logToTranscript("Identified ${stakeholderAnalysis.stakeholders.size} key stakeholders\n\n")
            writeToProposal("## Key Stakeholders\n\n")

            val stakeholderContent = buildString {
                appendLine("## Key Stakeholders")
                appendLine()
                stakeholderAnalysis.stakeholders.forEach { stakeholder ->
                    val influenceIcon = when (stakeholder.influence_level.lowercase()) {
                        "high" -> "🔴"
                        "medium" -> "🟡"
                        else -> "🟢"
                    }
                    appendLine("### $influenceIcon ${stakeholder.name}")
                    appendLine()
                    appendLine("**Influence Level:** ${stakeholder.influence_level}")
                    appendLine()
                    appendLine("**Interests:**")
                    stakeholder.interests.forEach { interest ->
                        appendLine("- $interest")
                    }
                    appendLine()
                    if (stakeholder.concerns.isNotEmpty()) {
                        appendLine("**Concerns:**")
                        stakeholder.concerns.forEach { concern ->
                            appendLine("- $concern")
                        }
                        appendLine()
                    }
                    appendLine("**Addressing Strategy:** ${stakeholder.addressing_strategy}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            stakeholderTask.add(stakeholderContent.renderMarkdown)
            task.update()
            writeToProposal(stakeholderContent)

            overviewTask.add("✅ Phase 1 Complete: Stakeholder analysis finished\n".renderMarkdown)

            // Phase 2: ROI Analysis (if enabled)
            var roiAnalysis: ROIAnalysis? = null
            if (executionConfig.include_roi_analysis) {
                logToTranscript("## Phase 2: ROI Analysis\n\n")
                overviewTask.add("\n### Phase 2: ROI Analysis\n*Calculating financial projections and ROI...*\n".renderMarkdown)
                task.update()

                log.info("Phase 2: Performing ROI analysis")
                val roiTask = task.ui.newTask(false)
                tabs["ROI Analysis"] = roiTask.placeholder

                roiTask.add(
                    buildString {
                        appendLine("# ROI Analysis")
                        appendLine()
                        appendLine("**Status:** Calculating financial projections...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val roiAgent = ParsedAgent(
                    resultClass = ROIAnalysis::class.java,
                    prompt = """
You are a financial analyst. Create a comprehensive ROI analysis for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Budget Range: ${executionConfig.budget_range ?: "Not specified"}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Provide:
1. Financial Projections:
   - Total investment required
   - Year 1, 2, and 3 projected returns
   - Break-even point

2. Cost Breakdown:
   - Major cost categories (personnel, technology, operations, etc.)
   - Amount for each category
   - Justification for each cost

3. Expected Benefits:
   - Quantifiable benefits (revenue increase, cost savings, efficiency gains)
   - Timeline to realize each benefit
   - Both tangible and intangible benefits

4. ROI Summary:
   - Overall ROI calculation
   - Payback period
   - Key financial metrics

Be realistic and conservative in projections. Include assumptions.
If specific numbers aren't provided, use reasonable estimates based on the proposal type and industry standards.
          """.trimIndent(),
                    model = api,
                    temperature = 0.5,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                roiAnalysis = roiAgent.answer(listOf("Perform ROI analysis")).obj
                log.debug("ROI analysis complete")
                logToTranscript("ROI Analysis complete: ${roiAnalysis.roi_summary.take(200)}\n\n")

                val roiContent = buildString {
                    appendLine("## Financial Projections")
                    appendLine()
                    appendLine("| Metric | Value |")
                    appendLine("|--------|-------|")
                    appendLine("| Total Investment | ${roiAnalysis.financial_projections.total_investment} |")
                    appendLine("| Year 1 Return | ${roiAnalysis.financial_projections.year_1_return} |")
                    appendLine("| Year 2 Return | ${roiAnalysis.financial_projections.year_2_return} |")
                    appendLine("| Year 3 Return | ${roiAnalysis.financial_projections.year_3_return} |")
                    appendLine("| Break-Even Point | ${roiAnalysis.financial_projections.break_even_point} |")
                    appendLine()
                    appendLine("### Cost Breakdown")
                    appendLine()
                    roiAnalysis.cost_breakdown.forEach { cost ->
                        appendLine("**${cost.category}:** ${cost.amount}")
                        appendLine("- ${cost.justification}")
                        appendLine()
                    }
                    appendLine("### Expected Benefits")
                    appendLine()
                    roiAnalysis.expected_benefits.forEach { benefit ->
                        appendLine("**${benefit.type}**")
                        appendLine("- Description: ${benefit.description}")
                        appendLine("- Value: ${benefit.quantifiable_value}")
                        appendLine("- Timeline: ${benefit.timeline}")
                        appendLine()
                    }
                    appendLine("### ROI Summary")
                    appendLine()
                    appendLine(roiAnalysis.roi_summary)
                    appendLine()
                    appendLine("**Payback Period:** ${roiAnalysis.payback_period}")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                roiTask.add(roiContent.renderMarkdown)
                task.update()
                writeToProposal(roiContent)

                overviewTask.add("✅ Phase 2 Complete: ROI analysis finished\n".renderMarkdown)
            }

            // Phase 3: Risk Assessment (if enabled)
            var riskAssessment: RiskAssessment? = null
            if (executionConfig.include_risk_assessment) {
                logToTranscript("## Phase 3: Risk Assessment\n\n")
                overviewTask.add("\n### Phase 3: Risk Assessment\n*Identifying and mitigating risks...*\n".renderMarkdown)
                task.update()

                log.info("Phase 3: Performing risk assessment")
                val riskTask = task.ui.newTask(false)
                tabs["Risk Assessment"] = riskTask.placeholder

                riskTask.add(
                    buildString {
                        appendLine("# Risk Assessment")
                        appendLine()
                        appendLine("**Status:** Identifying risks and mitigation strategies...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val riskAgent = ParsedAgent(
                    resultClass = RiskAssessment::class.java,
                    prompt = """
You are a risk management expert. Identify and assess risks for this business proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Identify 5-7 key risks across categories:
- Technical risks
- Financial risks
- Operational risks
- Market/competitive risks
- Organizational/people risks
- Timeline/schedule risks

For each risk, provide:
- Category
- Clear description of the risk
- Probability (High/Medium/Low)
- Impact level (High/Medium/Low)
- Specific mitigation strategy

Also provide an overall risk level assessment (Low/Moderate/High/Critical).

Be realistic but not alarmist. Focus on actionable mitigation strategies.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                riskAssessment = riskAgent.answer(listOf("Assess risks")).obj
                log.debug("Identified ${riskAssessment.risks.size} risks")
                logToTranscript("Identified ${riskAssessment.risks.size} risks. Overall risk level: ${riskAssessment.overall_risk_level}\n\n")

                val riskContent = buildString {
                    appendLine("## Overall Risk Level: ${riskAssessment.overall_risk_level}")
                    appendLine()
                    appendLine("## Identified Risks")
                    appendLine()
                    riskAssessment.risks.forEach { risk ->
                        val riskIcon = when {
                            risk.probability.lowercase() == "high" && risk.impact.lowercase() == "high" -> "🔴"
                            risk.probability.lowercase() == "high" || risk.impact.lowercase() == "high" -> "🟡"
                            else -> "🟢"
                        }
                        appendLine("### $riskIcon ${risk.category}")
                        appendLine()
                        appendLine("**Description:** ${risk.description}")
                        appendLine()
                        appendLine("**Probability:** ${risk.probability} | **Impact:** ${risk.impact}")
                        appendLine()
                        appendLine("**Mitigation Strategy:**")
                        appendLine(risk.mitigation_strategy)
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                riskTask.add(riskContent.renderMarkdown)
                task.update()
                writeToProposal(riskContent)

                overviewTask.add("✅ Phase 3 Complete: Risk assessment finished\n".renderMarkdown)
            }

            // Phase 4: Competitive Analysis (if enabled)
            var competitiveAnalysis: CompetitiveAnalysis? = null
            if (executionConfig.include_competitive_analysis) {
                logToTranscript("## Phase 4: Competitive Analysis\n\n")
                overviewTask.add("\n### Phase 4: Competitive Analysis\n*Analyzing alternatives and competitive advantages...*\n".renderMarkdown)
                task.update()

                log.info("Phase 4: Performing competitive analysis")
                val competitiveTask = task.ui.newTask(false)
                tabs["Competitive Analysis"] = competitiveTask.placeholder

                competitiveTask.add(
                    buildString {
                        appendLine("# Competitive Analysis")
                        appendLine()
                        appendLine("**Status:** Analyzing alternatives and positioning...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val competitiveAgent = ParsedAgent(
                    resultClass = CompetitiveAnalysis::class.java,
                    prompt = """
You are a competitive strategy analyst. Analyze alternatives and competitive positioning for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Identify 3-4 alternative approaches or competing solutions, including:
- Status quo (doing nothing)
- Alternative vendors or approaches
- In-house vs. outsourced options
- Different implementation strategies

For each alternative:
- Name and brief description
- Pros (advantages)
- Cons (disadvantages)
- Why our proposal is better (specific comparison)

Also provide:
- List of competitive advantages of this proposal
- A clear superiority statement explaining why this proposal is the best choice

Be fair to alternatives but make a compelling case for this proposal.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                competitiveAnalysis = competitiveAgent.answer(listOf("Analyze competition")).obj
                log.debug("Analyzed ${competitiveAnalysis.alternatives.size} alternatives")
                logToTranscript("Analyzed ${competitiveAnalysis.alternatives.size} alternative approaches\n\n")

                val competitiveContent = buildString {
                    appendLine("## Competitive Advantages")
                    appendLine()
                    competitiveAnalysis.competitive_advantages.forEach { advantage ->
                        appendLine("- $advantage")
                    }
                    appendLine()
                    appendLine("## Alternative Approaches")
                    appendLine()
                    competitiveAnalysis.alternatives.forEach { alt ->
                        appendLine("### ${alt.name}")
                        appendLine()
                        appendLine(alt.description)
                        appendLine()
                        appendLine("**Pros:**")
                        alt.pros.forEach { pro ->
                            appendLine("- $pro")
                        }
                        appendLine()
                        appendLine("**Cons:**")
                        alt.cons.forEach { con ->
                            appendLine("- $con")
                        }
                        appendLine()
                        appendLine("**Why Our Proposal is Better:**")
                        appendLine(alt.comparison)
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("## Why This Proposal is Superior")
                    appendLine()
                    appendLine(competitiveAnalysis.superiority_statement)
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                competitiveTask.add(competitiveContent.renderMarkdown)
                task.update()
                writeToProposal(competitiveContent)

                overviewTask.add("✅ Phase 4 Complete: Competitive analysis finished\n".renderMarkdown)
            }

            // Phase 5: Timeline & Milestones (if enabled)
            var timelineMilestones: TimelineMilestones? = null
            if (executionConfig.include_timeline_milestones) {
                logToTranscript("## Phase 5: Timeline & Milestones\n\n")
                overviewTask.add("\n### Phase 5: Timeline & Milestones\n*Creating project timeline...*\n".renderMarkdown)
                task.update()

                log.info("Phase 5: Creating timeline and milestones")
                val timelineTask = task.ui.newTask(false)
                tabs["Timeline & Milestones"] = timelineTask.placeholder

                timelineTask.add(
                    buildString {
                        appendLine("# Timeline & Milestones")
                        appendLine()
                        appendLine("**Status:** Creating project timeline...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val timelineAgent = ParsedAgent(
                    resultClass = TimelineMilestones::class.java,
                    prompt = """
You are a project management expert. Create a detailed timeline with milestones for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Timeline: ${executionConfig.timeline ?: "Not specified"}

${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(2000)}\n" else ""}

Create a project timeline with:
1. 4-6 major phases (e.g., Planning, Design, Implementation, Testing, Launch, Optimization)
2. For each phase:
   - Name
   - Duration
   - Key deliverables
   - Dependencies on other phases

3. Critical path items (tasks that must be completed on time to avoid delays)

Be realistic about timelines. Include buffer time for unexpected issues.
Ensure phases flow logically and dependencies are clear.
          """.trimIndent(),
                    model = api,
                    temperature = 0.5,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                timelineMilestones = timelineAgent.answer(listOf("Create timeline")).obj
                log.debug("Created timeline with ${timelineMilestones.phases.size} phases")
                logToTranscript("Created project timeline with ${timelineMilestones.phases.size} phases\n\n")

                val timelineContent = buildString {
                    appendLine("## Project Phases")
                    appendLine()
                    timelineMilestones.phases.forEachIndexed { index, phase ->
                        appendLine("### Phase ${index + 1}: ${phase.name}")
                        appendLine()
                        appendLine("**Duration:** ${phase.duration}")
                        appendLine()
                        appendLine("**Key Deliverables:**")
                        phase.deliverables.forEach { deliverable ->
                            appendLine("- $deliverable")
                        }
                        appendLine()
                        if (phase.dependencies.isNotEmpty()) {
                            appendLine("**Dependencies:**")
                            phase.dependencies.forEach { dep ->
                                appendLine("- $dep")
                            }
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("## Critical Path")
                    appendLine()
                    timelineMilestones.critical_path.forEach { item ->
                        appendLine("- $item")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                timelineTask.add(timelineContent.renderMarkdown)
                task.update()
                writeToProposal(timelineContent)

                overviewTask.add("✅ Phase 5 Complete: Timeline created\n".renderMarkdown)
            }

            // Phase 6: Create Proposal Outline
            logToTranscript("## Phase 6: Proposal Structure\n\n")
            overviewTask.add("\n### Phase 6: Proposal Structure\n*Creating detailed outline...*\n".renderMarkdown)
            task.update()

            log.info("Phase 6: Creating proposal outline")
            val outlineTask = task.ui.newTask(false)
            tabs["Proposal Outline"] = outlineTask.placeholder

            outlineTask.add(
                buildString {
                    appendLine("# Proposal Outline")
                    appendLine()
                    appendLine("**Status:** Creating detailed structure...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val wordsPerSection = executionConfig.target_word_count / 8 // Rough estimate for 8 main sections

            val outlineAgent = ParsedAgent(
                resultClass = ProposalOutline::class.java,
                prompt = """
You are a business proposal expert. Create a detailed outline for this proposal.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Target Word Count: ${executionConfig.target_word_count}

Stakeholder Analysis Summary:
${stakeholderAnalysis.stakeholders.take(3).joinToString("\n") { "- ${it.name}: ${it.interests.firstOrNull() ?: ""}" }}

${if (roiAnalysis != null) "ROI Summary: ${roiAnalysis.roi_summary.take(200)}" else ""}
${if (riskAssessment != null) "Risk Level: ${riskAssessment.overall_risk_level}" else ""}

Create a comprehensive outline with:
1. Title
2. Executive Summary (compelling 1-paragraph overview)
3. Problem Statement (what opportunity or challenge this addresses)
4. Solution Overview (high-level description of the proposal)
5. Main sections (6-8 sections covering):
   - Background/Context
   - Proposed Solution (detailed)
   - Implementation Approach
   ${if (roiAnalysis != null) "- Financial Analysis" else ""}
   ${if (riskAssessment != null) "- Risk Management" else ""}
   ${if (competitiveAnalysis != null) "- Competitive Positioning" else ""}
   ${if (timelineMilestones != null) "- Timeline & Milestones" else ""}
   ${if (executionConfig.include_resource_requirements) "- Resource Requirements" else ""}
   - Conclusion & Next Steps

For each section:
- Clear title
- Purpose (what this section accomplishes)
- 3-5 key points to cover
- Estimated word count (~$wordsPerSection words per section)

6. Success metrics (how success will be measured)

Tailor the outline to the ${executionConfig.proposal_type} proposal type and ${executionConfig.tone} tone.
        """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = orchestrationConfig.parsingChatter
            )

            val outline = outlineAgent.answer(listOf("Create outline")).obj
            log.debug("Outline created with ${outline.sections.size} sections")
            logToTranscript("Created outline with ${outline.sections.size} main sections\n\n")

            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("### Executive Summary")
                appendLine(outline.executive_summary)
                appendLine()
                appendLine("### Problem Statement")
                appendLine(outline.problem_statement)
                appendLine()
                appendLine("### Solution Overview")
                appendLine(outline.solution_overview)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Main Sections")
                appendLine()
                outline.sections.forEach { section ->
                    appendLine("#### ${section.title}")
                    appendLine()
                    appendLine("**Purpose:** ${section.purpose}")
                    appendLine()
                    appendLine("**Key Points:**")
                    section.key_points.forEach { point ->
                        appendLine("- $point")
                    }
                    appendLine()
                    appendLine("**Est. Words:** ${section.estimated_word_count}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("### Success Metrics")
                appendLine()
                outline.success_metrics.forEach { metric ->
                    appendLine("- $metric")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown)
            task.update()
            writeToProposal(outlineContent)

            overviewTask.add("✅ Phase 6 Complete: Outline created\n".renderMarkdown)

            // Phase 7: Write Proposal Sections
            logToTranscript("## Phase 7: Content Generation\n\n")
            overviewTask.add("\n### Phase 7: Content Generation\n*Writing proposal sections...*\n".renderMarkdown)
            task.update()

            log.info("Phase 7: Writing proposal sections")
            val proposalSections = mutableListOf<ProposalContent>()
            var cumulativeWordCount = 0

            // Write Executive Summary
            val execSummaryTask = task.ui.newTask(false)
            tabs["Executive Summary"] = execSummaryTask.placeholder

            execSummaryTask.add(
                buildString {
                    appendLine("# Executive Summary")
                    appendLine()
                    appendLine("**Status:** Writing executive summary...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val execSummaryAgent = ParsedAgent(
                resultClass = ProposalContent::class.java,
                prompt = """
You are a business proposal writer. Write a compelling executive summary.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Tone: ${executionConfig.tone}

Outline Summary: ${outline.executive_summary}

Write an executive summary (300-400 words) that:
1. Opens with a strong hook that captures attention
2. Clearly states the problem or opportunity
3. Presents the proposed solution at a high level
4. Highlights key benefits and ROI
5. Mentions critical success factors
6. Ends with a clear call to action or next steps

Make it compelling and persuasive. Decision-makers should understand the value immediately.
Target audience: ${executionConfig.decision_makers?.joinToString(", ") ?: "Senior executives"}
        """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = orchestrationConfig.parsingChatter
            )

            var execSummary = execSummaryAgent.answer(listOf("Write executive summary")).obj
            proposalSections.add(execSummary)
            cumulativeWordCount += execSummary.word_count
            logToTranscript("Executive Summary written: ${execSummary.word_count} words\n")

            val execSummaryContent = buildString {
                appendLine("## Executive Summary")
                appendLine()
                appendLine(execSummary.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${execSummary.word_count}")
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            execSummaryTask.add(
                execSummaryContent.renderMarkdown
            )
            task.update()
            writeToProposal(execSummaryContent)

            resultBuilder.append("## Executive Summary\n\n")
            resultBuilder.append(execSummary.content)
            resultBuilder.append("\n\n")

            overviewTask.add("- Executive Summary ✅ (${execSummary.word_count} words)\n".renderMarkdown)
            task.update()

            // Write each main section
            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Writing section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")
                logToTranscript("Writing section: ${sectionOutline.title}\n")

                overviewTask.add("- ${sectionOutline.title} ".renderMarkdown)
                task.update()

                val sectionTask = task.ui.newTask(false)
                tabs[sectionOutline.title] = sectionTask.placeholder

                sectionTask.add(
                    buildString {
                        appendLine("# ${sectionOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing section...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                // Build context from previous sections
                val previousContext = if (proposalSections.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Sections Summary")
                        proposalSections.takeLast(2).forEach { prevSection ->
                            appendLine("**${prevSection.section_title}:** ${prevSection.key_messages.firstOrNull() ?: ""}")
                            appendLine()
                        }
                    }
                } else {
                    "This is the first main section after the executive summary."
                }

                // Determine if this section should incorporate analysis results
                val analysisContext = buildString {
                    when {
                        sectionOutline.title.contains("Financial", ignoreCase = true) && roiAnalysis != null -> {
                            appendLine("## ROI Analysis to Incorporate")
                            appendLine("ROI Summary: ${roiAnalysis.roi_summary}")
                            appendLine("Payback Period: ${roiAnalysis.payback_period}")
                            appendLine()
                        }

                        sectionOutline.title.contains("Risk", ignoreCase = true) && riskAssessment != null -> {
                            appendLine("## Risk Assessment to Incorporate")
                            appendLine("Overall Risk Level: ${riskAssessment.overall_risk_level}")
                            riskAssessment.risks.take(3).forEach { risk ->
                                appendLine("- ${risk.category}: ${risk.description.take(100)}")
                            }
                            appendLine()
                        }

                        sectionOutline.title.contains(
                            "Competitive",
                            ignoreCase = true
                        ) && competitiveAnalysis != null -> {
                            appendLine("## Competitive Analysis to Incorporate")
                            appendLine(competitiveAnalysis.superiority_statement.take(200))
                            appendLine()
                        }

                        sectionOutline.title.contains("Timeline", ignoreCase = true) && timelineMilestones != null -> {
                            appendLine("## Timeline to Incorporate")
                            timelineMilestones.phases.take(3).forEach { phase ->
                                appendLine("- ${phase.name}: ${phase.duration}")
                            }
                            appendLine()
                        }
                    }
                }

                val sectionAgent = ParsedAgent(
                    resultClass = ProposalContent::class.java,
                    prompt = """
You are a business proposal writer. Write the "${sectionOutline.title}" section.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Tone: ${executionConfig.tone}

Section Purpose: ${sectionOutline.purpose}

Key Points to Cover:
${sectionOutline.key_points.joinToString("\n") { "- $it" }}

Target Word Count: ${sectionOutline.estimated_word_count}

$previousContext

$analysisContext

${if (contextFiles.isNotBlank()) "Additional Context:\n${contextFiles.truncateForDisplay(1000)}\n" else ""}

Write a well-structured section that:
1. Opens with a clear topic statement
2. Develops each key point with supporting details
3. Uses concrete examples and data where appropriate
4. Maintains a ${executionConfig.tone} tone
5. Connects to the overall proposal objective
6. Transitions smoothly to the next section

Make it persuasive and professional. Use clear, concise language.
Aim for approximately ${sectionOutline.estimated_word_count} words.
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                var sectionContent = sectionAgent.answer(listOf("Write section")).obj
                proposalSections.add(sectionContent)
                cumulativeWordCount += sectionContent.word_count
                logToTranscript("Section '${sectionOutline.title}' completed: ${sectionContent.word_count} words\n")

                sectionTask.add(
                    buildString {
                        appendLine("## ${sectionOutline.title}")
                        appendLine()
                        appendLine(sectionContent.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Word Count:** ${sectionContent.word_count}")
                        if (sectionContent.key_messages.isNotEmpty()) {
                            appendLine()
                            appendLine("**Key Messages:**")
                            sectionContent.key_messages.forEach { msg ->
                                appendLine("- $msg")
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    }.renderMarkdown
                )
                task.update()
                writeToProposal(sectionContent.content)

                resultBuilder.append("## ${sectionOutline.title}\n\n")
                resultBuilder.append(sectionContent.content)
                resultBuilder.append("\n\n")

                overviewTask.add("✅ (${sectionContent.word_count} words)\n".renderMarkdown)
                task.update()
            }

            overviewTask.add("✅ Phase 7 Complete: All sections written\n".renderMarkdown)

            // Phase 8: Conclusion & Next Steps
            logToTranscript("\n## Phase 8: Conclusion & Next Steps\n\n")
            overviewTask.add("\n### Phase 8: Conclusion\n*Writing conclusion and next steps...*\n".renderMarkdown)
            task.update()

            log.info("Phase 8: Writing conclusion")
            val conclusionTask = task.ui.newTask(false)
            tabs["Conclusion"] = conclusionTask.placeholder

            conclusionTask.add(
                buildString {
                    appendLine("# Conclusion & Next Steps")
                    appendLine()
                    appendLine("**Status:** Writing conclusion...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val conclusionAgent = ParsedAgent(
                resultClass = ProposalContent::class.java,
                prompt = """
You are a business proposal writer. Write a compelling conclusion and next steps section.

Proposal: $proposalTitle
Type: ${executionConfig.proposal_type}
Objective: ${executionConfig.objective}
Urgency Level: ${executionConfig.urgency_level}

Success Metrics:
${outline.success_metrics.joinToString("\n") { "- $it" }}

Write a conclusion (200-300 words) that:
1. Summarizes the key value proposition
2. Reinforces why this proposal is the best choice
3. Reiterates the urgency (${executionConfig.urgency_level} urgency)
4. Provides clear, specific next steps
5. Includes a call to action
6. Expresses confidence and readiness to proceed

Make it action-oriented and compelling. The reader should feel motivated to move forward.
        """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = orchestrationConfig.parsingChatter
            )

            var conclusion = conclusionAgent.answer(listOf("Write conclusion")).obj
            cumulativeWordCount += conclusion.word_count
            logToTranscript("Conclusion written: ${conclusion.word_count} words\n\n")

            val conclusionContent = buildString {
                appendLine("## Conclusion & Next Steps")
                appendLine()
                appendLine(conclusion.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${conclusion.word_count}")
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            conclusionTask.add(
                conclusionContent.renderMarkdown
            )
            task.update()
            writeToProposal(conclusionContent)

            resultBuilder.append("## Conclusion & Next Steps\n\n")
            resultBuilder.append(conclusion.content)
            resultBuilder.append("\n\n")

            overviewTask.add("✅ Phase 8 Complete: Conclusion written (${conclusion.word_count} words)\n".renderMarkdown)

            // Phase 9: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                logToTranscript("## Phase 9: Revision Process\n\n")
                overviewTask.add("\n### Phase 9: Revision\n*Refining and polishing...*\n".renderMarkdown)
                task.update()

                log.info("Phase 9: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = task.ui.newTask(false)
                tabs["Revision"] = revisionTask.placeholder

                revisionTask.add(
                    buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val fullProposal = resultBuilder.toString()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")
                    logToTranscript("Performing revision pass ${passNum + 1}/${executionConfig.revision_passes}\n")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert business proposal editor. Review and improve this proposal.

Current Proposal:
$fullProposal

Focus on:
1. Strengthening persuasive language and value proposition
2. Ensuring logical flow and coherence
3. Improving clarity and conciseness
4. Verifying alignment with ${executionConfig.tone} tone
5. Enhancing professional presentation
6. Ensuring all stakeholder concerns are addressed
7. Maximizing impact on decision-makers

Maintain:
- All key points and data
- The proposal structure
- Approximate word count ($cumulativeWordCount words)
- The ${executionConfig.tone} tone

Provide the complete revised proposal.
            """.trimIndent(),
                        model = api,
                        temperature = 0.6
                    )

                    val revisedProposal = revisionAgent.answer(listOf("Revise the proposal"))
                    resultBuilder.clear()
                    resultBuilder.append(revisedProposal)

                    revisionTask.add(
                        buildString {
                            appendLine("## Revision Pass ${passNum + 1}")
                            appendLine()
                            appendLine("✅ Complete")
                            appendLine()
                        }.renderMarkdown
                    )
                    task.update()
                }

                overviewTask.add("✅ Phase 9 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
            }

            // Phase 10: Final Assembly
            logToTranscript("\n## Phase 10: Final Assembly\n\n")
            overviewTask.add("\n### Phase 10: Final Assembly\n*Compiling complete proposal...*\n".renderMarkdown)
            task.update()

            log.info("Phase 10: Assembling final proposal")
            val finalTask = task.ui.newTask(false)
            tabs["Complete Proposal"] = finalTask.placeholder

            val finalProposal = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("**Prepared by:** ${executionConfig.proposing_organization ?: "Your Organization"}")
                appendLine()
                appendLine("**Date:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(resultBuilder.toString())
                appendLine()
                if (executionConfig.include_appendices) {
                    appendLine("---")
                    appendLine()
                    appendLine("## Appendices")
                    appendLine()
                    appendLine("### Appendix A: Detailed Financial Projections")
                    appendLine("*[Include detailed spreadsheets and financial models]*")
                    appendLine()
                    appendLine("### Appendix B: Technical Specifications")
                    appendLine("*[Include technical documentation and specifications]*")
                    appendLine()
                    appendLine("### Appendix C: Team Biographies")
                    appendLine("*[Include key team member profiles and qualifications]*")
                    appendLine()
                    appendLine("### Appendix D: References and Case Studies")
                    appendLine("*[Include relevant case studies and client references]*")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("**Total Word Count:** $cumulativeWordCount")
                appendLine()
                appendLine("**Target Word Count:** ${executionConfig.target_word_count}")
                appendLine()
                appendLine("**Completion:** ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
            }

            finalTask.add(finalProposal.renderMarkdown)
            task.update()
            writeToProposal(finalProposal)

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            logToTranscript("\n## Generation Complete\n\nTotal time: ${totalTime / 1000.0}s\nTotal words: $cumulativeWordCount\n")

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Generation Complete")
                    appendLine()
                    appendLine("**Statistics:**")
                    appendLine("- Total Word Count: $cumulativeWordCount")
                    appendLine("- Target Word Count: ${executionConfig.target_word_count}")
                    appendLine("- Completion: ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
                    appendLine("- Number of Sections: ${proposalSections.size}")
                    appendLine("- Stakeholders Analyzed: ${stakeholderAnalysis.stakeholders.size}")
                    if (roiAnalysis != null) appendLine("- ROI Analysis: ✓ Included")
                    if (riskAssessment != null) appendLine("- Risk Assessment: ✓ Included (${riskAssessment.risks.size} risks)")
                    if (competitiveAnalysis != null) appendLine("- Competitive Analysis: ✓ Included")
                    if (timelineMilestones != null) appendLine("- Timeline: ✓ Included (${timelineMilestones.phases.size} phases)")
                    appendLine("- Revision Passes: ${executionConfig.revision_passes}")
                    appendLine("- Total Time: ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Business Proposal Summary: ${outline.title}")
                appendLine()
                appendLine("A complete business proposal of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Objective:** ${executionConfig.objective}")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                val proposalFile = "proposal_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
                val (proposalLink, _) = Pair(task.linkTo(proposalFile), task.resolveUserFile(proposalFile))
                val transcriptFile = "proposal_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
                val (transcriptLink, _) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
                appendLine(
                    "- **Complete Proposal:** [View](${proposalLink}) | [HTML](${proposalLink.removeSuffix(".md")}.html) | [PDF](${
                        proposalLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine(
                    "- **Transcript:** [View](${transcriptLink}) | [HTML](${transcriptLink.removeSuffix(".md")}.html) | [PDF](${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.pdf)"
                )
                appendLine()
                appendLine("**Key Components:**")
                appendLine("- Executive Summary")
                appendLine("- ${outline.sections.size} main sections")
                if (roiAnalysis != null) appendLine("- ROI Analysis with financial projections")
                if (riskAssessment != null) appendLine("- Risk Assessment (${riskAssessment.overall_risk_level} risk level)")
                if (competitiveAnalysis != null) appendLine("- Competitive Analysis")
                if (timelineMilestones != null) appendLine("- Timeline with ${timelineMilestones.phases.size} phases")
                appendLine("- Conclusion with next steps")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Word Count: $cumulativeWordCount / ${executionConfig.target_word_count}")
                appendLine("- Sections: ${proposalSections.size}")
                appendLine("- Generation Time: ${totalTime / 1000.0}s")
            }

            log.info("BusinessProposalTask completed: words=$cumulativeWordCount, sections=${proposalSections.size}, time=${totalTime}ms")

            task.safeComplete(
                "Business proposal generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during business proposal generation", e)
            logToTranscript("\n## Error Occurred\n\n${e.message}\n")
            task.error(e)

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Business Proposal Generation")
                appendLine()
                appendLine("**Proposal:** $proposalTitle")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (resultBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
        proposalStream?.close()
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related context files")

        return buildString {
            appendLine("## Related Research Files")
            appendLine()
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        log.debug("Successfully loaded context file: $file")
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText().truncateForDisplay(1500))
                        appendLine("```")
                        appendLine()
                    } else {
                        log.warn("Context file not found: $file")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
                }
            }
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(BusinessProposalTask::class.java)
        val BusinessProposal = TaskType(
            "BusinessProposal",
            BusinessProposalTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate comprehensive business proposals with ROI analysis and risk assessment",
            """
              Generates complete, professional business proposals for various purposes.
              <ul>
                <li>Performs stakeholder analysis to understand decision-makers</li>
                <li>Creates detailed ROI analysis with financial projections</li>
                <li>Conducts risk assessment with mitigation strategies</li>
                <li>Analyzes competitive alternatives and positioning</li>
                <li>Develops timeline with milestones and dependencies</li>
                <li>Writes compelling executive summary and sections</li>
                <li>Includes optional revision passes for quality</li>
                <li>Supports multiple proposal types (project, investment, grant, partnership, RFP)</li>
                <li>Ideal for project proposals, funding requests, vendor responses, and business plans</li>
              </ul>
            """
        )
    }
}