package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NeuralNetworkLayerTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: NeuralNetworkLayerTaskExecutionConfigData?
) : AbstractTask<NeuralNetworkLayerTask.NeuralNetworkLayerTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(NeuralNetworkLayerTask::class.java)
        @JvmStatic val NeuralNetworkLayer = TaskType(
            name = "NeuralNetworkLayer",
            category = "Writing",
            taskClass = NeuralNetworkLayerTask::class.java,
            executionConfigClass = NeuralNetworkLayerTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Design and analyze neural network layers with formal mathematical definitions and intuitive explanations",
            tooltipHtml = """
                          Comprehensive neural network layer design and analysis tool with both rigorous mathematics and intuitive explanations.
                          <ul>
                              <li>Executive summary with key insights</li>
                              <li>Intuitive explanations with real-world analogies</li>
                              <li>Visual conceptual diagrams</li>
                              <li>Formal mathematical definition of the layer function</li>
                              <li>Forward pass implementation with detailed equations</li>
                              <li>Backward pass (gradient) derivation and implementation</li>
                              <li>Higher-order derivative analysis (Hessian, etc.)</li>
                              <li>Lyapunov stability analysis for training dynamics</li>
                              <li>Lipschitz continuity and gradient flow analysis</li>
                              <li>Numerical stability considerations</li>
                              <li>Reference implementations in multiple languages</li>
                              <li>Computational complexity analysis</li>
                              <li>Memory footprint estimation</li>
                              <li>Originality and novelty assessment</li>
                              <li>Practical use cases and applications</li>
                          </ul>
                      """,
        )
    }

    class NeuralNetworkLayerTaskExecutionConfigData(
        @Description("Name of the layer type (e.g., 'Attention', 'Convolution', 'BatchNorm', 'Custom')")
        var layer_name: String? = null,
        @Description("Mathematical description of the layer's forward function")
        var forward_function_description: String? = null,
        @Description("Input tensor shape specification (e.g., '[batch, channels, height, width]')")
        var input_shape: String? = null,
        @Description("Output tensor shape specification")
        var output_shape: String? = null,
        @Description("List of learnable parameters with their shapes")
        var parameters: List<String>? = null,
        @Description("Activation function if applicable (e.g., 'relu', 'sigmoid', 'tanh', 'none')")
        var activation: String? = "none",
        @Description("Whether to include higher-order derivative analysis")
        var include_higher_order: Boolean = true,
        @Description("Whether to include Lyapunov stability analysis")
        var include_lyapunov: Boolean = true,
        @Description("Whether to include Lipschitz analysis")
        var include_lipschitz: Boolean = true,
        @Description("Target implementation languages, e.g. 'tensorflow.js', 'pseudocode'")
        var implementation_languages: List<String>? = listOf("tensorflow.js"),
        @Description("Whether to include numerical stability analysis")
        var include_numerical_stability: Boolean = true,
        @Description("Whether to generate test cases")
        var generate_tests: Boolean = true,
        @Description("Analysis depth: 'basic', 'standard', 'comprehensive'")
        var analysis_depth: String = "standard",

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = NeuralNetworkLayer.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (layer_name.isNullOrBlank()) {
                return "layer_name must not be blank"
            }
            if (forward_function_description.isNullOrBlank()) {
                return "forward_function_description must not be blank"
            }
            if (analysis_depth !in listOf("basic", "standard", "comprehensive")) {
                return "analysis_depth must be 'basic', 'standard', or 'comprehensive'"
            }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class ExecutiveSummary(
        @Description("One-sentence description of what the layer does")
        val one_liner: String = "",
        @Description("Key mathematical insight in plain language")
        val key_insight: String = "",
        @Description("Primary strengths of this layer")
        val strengths: List<String> = emptyList(),
        @Description("Primary limitations or weaknesses")
        val limitations: List<String> = emptyList(),
        @Description("When to use this layer (decision criteria)")
        val when_to_use: String = "",
        @Description("When NOT to use this layer")
        val when_not_to_use: String = "",
        @Description("Computational cost summary (low/medium/high)")
        val computational_cost: String = "",
        @Description("Training difficulty (easy/moderate/hard)")
        val training_difficulty: String = "",
        @Description("Recommended for beginners? (yes/no/with_caution)")
        val beginner_friendly: String = ""
    )

    data class IntuitiveExplanation(
        @Description("Real-world analogy explaining what the layer does")
        val analogy: String = "",
        @Description("Step-by-step walkthrough in plain language")
        val plain_language_walkthrough: String = "",
        @Description("Visual description of information flow")
        val information_flow_description: String = "",
        @Description("What problem does this layer solve?")
        val problem_solved: String = "",
        @Description("How does it solve the problem? (mechanism)")
        val solution_mechanism: String = "",
        @Description("Common misconceptions about this layer")
        val common_misconceptions: List<String> = emptyList(),
        @Description("Intuitive explanation of why gradients work this way")
        val gradient_intuition: String = "",
        @Description("Mental model for understanding the layer")
        val mental_model: String = ""
    )

    data class ConceptualDiagram(
        @Description("ASCII art or text-based diagram of the layer")
        val ascii_diagram: String = "",
        @Description("Description of data flow through the layer")
        val data_flow_description: String = "",
        @Description("Mermaid diagram syntax for visualization")
        val mermaid_diagram: String = "",
        @Description("Description of parameter roles")
        val parameter_roles: Map<String, String> = emptyMap()
    )

    data class LayerDefinition(
        @Description("Formal mathematical notation for the forward function")
        val forward_equation: String = "",
        @Description("LaTeX representation of the forward function")
        val forward_latex: String = "",
        @Description("Domain constraints on inputs")
        val domain_constraints: List<String> = emptyList(),
        @Description("Range/codomain of the output")
        val range_description: String = "",
        @Description("Parameter initialization recommendations")
        val initialization_recommendations: List<String> = emptyList()
    )

    data class GradientDerivation(
        @Description("Gradient with respect to input (dL/dx)")
        val gradient_input: String = "",
        @Description("LaTeX for input gradient")
        val gradient_input_latex: String = "",
        @Description("Gradients with respect to each parameter")
        val parameter_gradients: Map<String, String> = emptyMap(),
        @Description("LaTeX for parameter gradients")
        val parameter_gradients_latex: Map<String, String> = emptyMap(),
        @Description("Chain rule application explanation")
        val chain_rule_explanation: String = "",
        @Description("Computational graph description")
        val computational_graph: String = ""
    )

    data class HigherOrderAnalysis(
        @Description("Hessian matrix structure")
        val hessian_structure: String = "",
        @Description("Hessian eigenvalue bounds")
        val hessian_eigenvalue_bounds: String = "",
        @Description("Second derivative expressions")
        val second_derivatives: Map<String, String> = emptyMap(),
        @Description("Curvature analysis")
        val curvature_analysis: String = "",
        @Description("Fisher information matrix if applicable")
        val fisher_information: String = "",
        @Description("Natural gradient considerations")
        val natural_gradient_notes: String = ""
    )

    data class StabilityAnalysis(
        @Description("Lyapunov function candidate")
        val lyapunov_function: String = "",
        @Description("Lyapunov stability conditions")
        val stability_conditions: List<String> = emptyList(),
        @Description("Equilibrium points analysis")
        val equilibrium_analysis: String = "",
        @Description("Basin of attraction description")
        val basin_of_attraction: String = "",
        @Description("Convergence rate bounds")
        val convergence_rate: String = "",
        @Description("Potential instability modes")
        val instability_modes: List<String> = emptyList()
    )

    data class LipschitzAnalysis(
        @Description("Lipschitz constant of the forward function")
        val forward_lipschitz: String = "",
        @Description("Lipschitz constant of the gradient")
        val gradient_lipschitz: String = "",
        @Description("Spectral norm bounds")
        val spectral_norm_bounds: String = "",
        @Description("Gradient flow analysis")
        val gradient_flow: String = "",
        @Description("Smoothness properties")
        val smoothness_properties: List<String> = emptyList()
    )

    data class NumericalStability(
        @Description("Potential overflow conditions")
        val overflow_conditions: List<String> = emptyList(),
        @Description("Potential underflow conditions")
        val underflow_conditions: List<String> = emptyList(),
        @Description("Numerical precision recommendations")
        val precision_recommendations: List<String> = emptyList(),
        @Description("Stabilization techniques")
        val stabilization_techniques: List<String> = emptyList(),
        @Description("Gradient clipping recommendations")
        val gradient_clipping: String = ""
    )

    data class Implementation(
        @Description("Language of implementation")
        val language: String = "",
        @Description("Forward pass code")
        val forward_code: String = "",
        @Description("Backward pass code")
        val backward_code: String = "",
        @Description("Parameter initialization code")
        val initialization_code: String = "",
        @Description("Dependencies/imports")
        val dependencies: List<String> = emptyList()
    )

    data class ComplexityAnalysis(
        @Description("Time complexity of forward pass")
        val forward_time_complexity: String = "",
        @Description("Time complexity of backward pass")
        val backward_time_complexity: String = "",
        @Description("Space complexity")
        val space_complexity: String = "",
        @Description("Memory bandwidth requirements")
        val memory_bandwidth: String = "",
        @Description("Parallelization potential")
        val parallelization_notes: String = ""
    )

    data class OriginalityAnalysis(
        @Description("Assessment of the layer's novelty compared to existing architectures")
        val novelty_assessment: String = "",
        @Description("List of similar or related existing layers/architectures")
        val related_architectures: List<String> = emptyList(),
        @Description("Key innovations or unique aspects of this layer")
        val key_innovations: List<String> = emptyList(),
        @Description("Comparison with baseline/standard approaches")
        val baseline_comparison: String = "",
        @Description("Potential research contributions")
        val research_contributions: List<String> = emptyList(),
        @Description("Limitations compared to existing approaches")
        val limitations: List<String> = emptyList()
    )

    data class UseCaseAnalysis(
        @Description("Primary application domains for this layer")
        val primary_domains: List<String> = emptyList(),
        @Description("Specific tasks where this layer excels")
        val optimal_tasks: List<String> = emptyList(),
        @Description("Tasks where this layer may not be suitable")
        val unsuitable_tasks: List<String> = emptyList(),
        @Description("Recommended network architectures to use this layer in")
        val recommended_architectures: List<String> = emptyList(),
        @Description("Example use case scenarios with descriptions")
        val example_scenarios: List<String> = emptyList(),
        @Description("Integration considerations when adding to existing networks")
        val integration_notes: String = "",
        @Description("Scaling considerations for different problem sizes")
        val scaling_considerations: String = "",
        @Description("Industry applications")
        val industry_applications: List<String> = emptyList()
    )

    data class PracticalGuidance(
        @Description("Hyperparameter tuning recommendations")
        val hyperparameter_tuning: List<String> = emptyList(),
        @Description("Common pitfalls and how to avoid them")
        val common_pitfalls: List<String> = emptyList(),
        @Description("Debugging tips specific to this layer")
        val debugging_tips: List<String> = emptyList(),
        @Description("Performance optimization strategies")
        val optimization_strategies: List<String> = emptyList(),
        @Description("Monitoring and diagnostics recommendations")
        val monitoring_recommendations: List<String> = emptyList(),
        @Description("Best practices for production deployment")
        val production_best_practices: List<String> = emptyList()
    )

    override fun promptSegment(): String {
        return """
 NeuralNetworkLayer - Design and analyze neural network layers with comprehensive explanations
  ** Specify the layer name and forward function description
  ** Define input/output shapes and parameters
  ** Configure analysis options (higher-order, Lyapunov, Lipschitz)
  ** Select implementation languages
  ** The task will generate:
     - Executive summary with key insights and decision criteria
     - Intuitive explanations with real-world analogies
     - Visual conceptual diagrams
     - Formal mathematical definition with LaTeX
     - Forward pass equations and implementation
     - Backward pass (gradient) derivation and implementation
     - Higher-order derivative analysis (Hessian, curvature)
     - Lyapunov stability analysis for training dynamics
     - Lipschitz continuity and gradient flow analysis
     - Numerical stability considerations
     - Reference implementations
     - Complexity analysis
     - Originality analysis comparing to existing architectures
     - Use case analysis with application domains and scenarios
     - Practical guidance for implementation and deployment
  ** Useful for:
     - Learning about neural network layers (beginners to experts)
     - Designing custom neural network layers
     - Understanding existing layer mathematics
     - Analyzing training stability
     - Optimizing layer implementations
     - Research and documentation
     - Evaluating novelty for research papers
     - Identifying practical applications
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        task.ui.pool.submit {
            val transcript = task.transcript()
            try {
            val startTime = System.currentTimeMillis()
                log.info("Starting NeuralNetworkLayerTask for layer: ${executionConfig?.layer_name}")

            executionConfig?.validate()?.let { errorMessage ->
                log.error("NeuralNetworkLayerTask configuration validation failed: $errorMessage")
                task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
                resultFn("VALIDATION ERROR: $errorMessage")
                return@submit
            }

            val layerName = executionConfig?.layer_name ?: ""
            val forwardDesc = executionConfig?.forward_function_description ?: ""
            val inputShape = executionConfig?.input_shape ?: ""
            val outputShape = executionConfig?.output_shape ?: ""
            val parameters = executionConfig?.parameters ?: emptyList()
            val activation = executionConfig?.activation ?: "none"
            val includeHigherOrder = executionConfig?.include_higher_order ?: true
            val includeLyapunov = executionConfig?.include_lyapunov ?: true
            val includeLipschitz = executionConfig?.include_lipschitz ?: true
            val languages = executionConfig?.implementation_languages ?: listOf("tensorflow.js")
            val includeNumerical = executionConfig?.include_numerical_stability ?: true
            val generateTests = executionConfig?.generate_tests ?: true
            val analysisDepth = executionConfig?.analysis_depth ?: "standard"

            val tabs = TabbedDisplay(task)
            val api = defaultSmart

            // Overview tab
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder
            val overviewContent = buildString {
                appendLine("# Neural Network Layer Analysis: $layerName")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Layer Specification")
                appendLine()
                appendLine("| Property | Value |")
                appendLine("|----------|-------|")
                appendLine("| Layer Name | $layerName |")
                appendLine("| Input Shape | $inputShape |")
                appendLine("| Output Shape | $outputShape |")
                appendLine("| Activation | $activation |")
                appendLine("| Analysis Depth | $analysisDepth |")
                appendLine()
                appendLine("## Forward Function Description")
                appendLine()
                appendLine(forwardDesc)
                appendLine()
                if (parameters.isNotEmpty()) {
                    appendLine("## Parameters")
                    appendLine()
                    parameters.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Generating executive summary...")
            }
            overviewTask.add(overviewContent.renderMarkdown)
            task.update()
                transcript?.write("## Layer Specification\n$overviewContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate executive summary first
            val summaryTask = task.newTask()
            tabs["Executive Summary"] = summaryTask.placeholder
            val summary = generateExecutiveSummary(layerName, forwardDesc, inputShape, outputShape, parameters, api)
            val summaryContent = buildString {
                appendLine("# Executive Summary")
                appendLine()
                appendLine("## ${summary.one_liner}")
                appendLine()
                appendLine("### Key Insight")
                appendLine()
                appendLine("> ${summary.key_insight}")
                appendLine()
                appendLine("### Quick Decision Guide")
                appendLine()
                appendLine("| Aspect | Assessment |")
                appendLine("|--------|------------|")
                appendLine("| Computational Cost | ${summary.computational_cost} |")
                appendLine("| Training Difficulty | ${summary.training_difficulty} |")
                appendLine("| Beginner Friendly | ${summary.beginner_friendly} |")
                appendLine()
                appendLine("### ✅ Strengths")
                appendLine()
                summary.strengths.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### ⚠️ Limitations")
                appendLine()
                summary.limitations.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### When to Use")
                appendLine()
                appendLine(summary.when_to_use)
                appendLine()
                appendLine("### When NOT to Use")
                appendLine()
                appendLine(summary.when_not_to_use)
            }
            summaryTask.add(summaryContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Executive summary complete".renderMarkdown)
            overviewTask.add("\n- ⏳ Generating intuitive explanation...".renderMarkdown)
                transcript?.write("\n## Executive Summary\n$summaryContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate intuitive explanation
            val intuitiveTask = task.newTask()
            tabs["Intuitive Explanation"] = intuitiveTask.placeholder
            val intuitive = generateIntuitiveExplanation(layerName, forwardDesc, summary, api)
            val intuitiveContent = buildString {
                appendLine("# Intuitive Explanation")
                appendLine()
                appendLine("## Real-World Analogy")
                appendLine()
                appendLine(intuitive.analogy)
                appendLine()
                appendLine("## What Problem Does This Solve?")
                appendLine()
                appendLine(intuitive.problem_solved)
                appendLine()
                appendLine("## How Does It Work?")
                appendLine()
                appendLine(intuitive.solution_mechanism)
                appendLine()
                appendLine("## Plain Language Walkthrough")
                appendLine()
                appendLine(intuitive.plain_language_walkthrough)
                appendLine()
                appendLine("## Information Flow")
                appendLine()
                appendLine(intuitive.information_flow_description)
                appendLine()
                appendLine("## Mental Model")
                appendLine()
                appendLine(intuitive.mental_model)
                appendLine()
                appendLine("## Understanding Gradients")
                appendLine()
                appendLine(intuitive.gradient_intuition)
                appendLine()
                if (intuitive.common_misconceptions.isNotEmpty()) {
                    appendLine("## ⚠️ Common Misconceptions")
                    appendLine()
                    intuitive.common_misconceptions.forEach { appendLine("- $it") }
                }
            }
            intuitiveTask.add(intuitiveContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Intuitive explanation complete".renderMarkdown)
            overviewTask.add("\n- ⏳ Creating conceptual diagram...".renderMarkdown)
                transcript?.write("\n## Intuitive Explanation\n$intuitiveContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate conceptual diagram
            val diagramTask = task.newTask()
            tabs["Conceptual Diagram"] = diagramTask.placeholder
            val diagram = generateConceptualDiagram(layerName, forwardDesc, inputShape, outputShape, parameters, api)
            val diagramContent = buildString {
                appendLine("# Conceptual Diagram")
                appendLine()
                appendLine("## Layer Architecture")
                appendLine()
                appendLine("```")
                appendLine(diagram.ascii_diagram)
                appendLine("```")
                appendLine()
                appendLine("## Data Flow")
                appendLine()
                appendLine(diagram.data_flow_description)
                appendLine()
                if (diagram.mermaid_diagram.isNotBlank()) {
                    appendLine("## Visual Flow Diagram")
                    appendLine()
                    appendLine("```mermaid")
                    appendLine(diagram.mermaid_diagram)
                    appendLine("```")
                    appendLine()
                }
                if (diagram.parameter_roles.isNotEmpty()) {
                    appendLine("## Parameter Roles")
                    appendLine()
                    diagram.parameter_roles.forEach { (param, role) ->
                        appendLine("### $param")
                        appendLine()
                        appendLine(role)
                        appendLine()
                    }
                }
            }
            diagramTask.add(diagramContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Conceptual diagram complete".renderMarkdown)
            overviewTask.add("\n- ⏳ Generating formal definition...".renderMarkdown)
                transcript?.write("\n## Conceptual Diagram\n$diagramContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate formal definition
            val definitionTask = task.newTask()
            tabs["Formal Definition"] = definitionTask.placeholder
            val definition =
                generateLayerDefinition(layerName, forwardDesc, inputShape, outputShape, parameters, activation, api)
            val definitionContent = buildString {
                appendLine("# Formal Definition")
                appendLine()
                appendLine("## Forward Function")
                appendLine()
                appendLine("$${definition.forward_latex}$")
                appendLine()
                appendLine("**Notation:** ${definition.forward_equation}")
                appendLine()
                appendLine("## Domain Constraints")
                appendLine()
                definition.domain_constraints.forEach { appendLine("- $it") }
                appendLine()
                appendLine("## Range")
                appendLine()
                appendLine(definition.range_description)
                appendLine()
                appendLine("## Parameter Initialization")
                appendLine()
                definition.initialization_recommendations.forEach { appendLine("- $it") }
            }
            definitionTask.add(definitionContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Formal definition complete".renderMarkdown)
                transcript?.write("\n## Formal Definition\n$definitionContent\n".toByteArray(StandardCharsets.UTF_8))

            // Generate gradient derivation
            overviewTask.add("\n- ⏳ Deriving gradients...".renderMarkdown)
            task.update()
            val gradientTask = task.newTask()
            tabs["Gradients"] = gradientTask.placeholder
            val gradients = generateGradientDerivation(layerName, definition, parameters, api)
            val gradientContent = buildString {
                appendLine("# Gradient Derivation (Backward Pass)")
                appendLine()
                appendLine("## Chain Rule Application")
                appendLine()
                appendLine(gradients.chain_rule_explanation)
                appendLine()
                appendLine("## Gradient with Respect to Input")
                appendLine()
                appendLine("$${gradients.gradient_input_latex}$")
                appendLine()
                appendLine("**Expression:** ${gradients.gradient_input}")
                appendLine()
                appendLine("## Parameter Gradients")
                appendLine()
                gradients.parameter_gradients_latex.forEach { (param, latex) ->
                    appendLine("### ∂L/∂$param")
                    appendLine()
                    appendLine("$${latex}$")
                    appendLine()
                    appendLine("**Expression:** ${gradients.parameter_gradients[param]}")
                    appendLine()
                }
                appendLine("## Computational Graph")
                appendLine()
                appendLine("```")
                appendLine(gradients.computational_graph)
                appendLine("```")
            }
            gradientTask.add(gradientContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Gradient derivation complete".renderMarkdown)
                transcript?.write("\n## Gradient Derivation\n$gradientContent\n".toByteArray(StandardCharsets.UTF_8))

            // Higher-order analysis
            if (includeHigherOrder && analysisDepth != "basic") {
                overviewTask.add("\n- ⏳ Analyzing higher-order derivatives...".renderMarkdown)
                task.update()
                val higherOrderTask = task.newTask()
                tabs["Higher-Order Analysis"] = higherOrderTask.placeholder
                val higherOrder = generateHigherOrderAnalysis(layerName, definition, gradients, analysisDepth, api)
                val higherOrderContent = buildString {
                    appendLine("# Higher-Order Derivative Analysis")
                    appendLine()
                    appendLine("## Hessian Structure")
                    appendLine()
                    appendLine(higherOrder.hessian_structure)
                    appendLine()
                    appendLine("## Eigenvalue Bounds")
                    appendLine()
                    appendLine(higherOrder.hessian_eigenvalue_bounds)
                    appendLine()
                    appendLine("## Second Derivatives")
                    appendLine()
                    higherOrder.second_derivatives.forEach { (name, expr) ->
                        appendLine("### $name")
                        appendLine()
                        appendLine("$${expr}$")
                        appendLine()
                    }
                    appendLine("## Curvature Analysis")
                    appendLine()
                    appendLine(higherOrder.curvature_analysis)
                    appendLine()
                    if (higherOrder.fisher_information.isNotBlank()) {
                        appendLine("## Fisher Information Matrix")
                        appendLine()
                        appendLine(higherOrder.fisher_information)
                        appendLine()
                    }
                    if (higherOrder.natural_gradient_notes.isNotBlank()) {
                        appendLine("## Natural Gradient Considerations")
                        appendLine()
                        appendLine(higherOrder.natural_gradient_notes)
                    }
                }
                higherOrderTask.add(higherOrderContent.renderMarkdown)
                task.update()
                overviewTask.add("\n- ✅ Higher-order analysis complete".renderMarkdown)
                transcript?.write("\n## Higher-Order Analysis\n$higherOrderContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Lyapunov stability analysis
            if (includeLyapunov && analysisDepth != "basic") {
                overviewTask.add("\n- ⏳ Performing Lyapunov stability analysis...".renderMarkdown)
                task.update()
                val stabilityTask = task.newTask()
                tabs["Stability Analysis"] = stabilityTask.placeholder
                val stability = generateStabilityAnalysis(layerName, definition, gradients, api)
                val stabilityContent = buildString {
                    appendLine("# Lyapunov Stability Analysis")
                    appendLine()
                    appendLine("## Lyapunov Function Candidate")
                    appendLine()
                    appendLine("$${stability.lyapunov_function}$")
                    appendLine()
                    appendLine("## Stability Conditions")
                    appendLine()
                    stability.stability_conditions.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Equilibrium Analysis")
                    appendLine()
                    appendLine(stability.equilibrium_analysis)
                    appendLine()
                    appendLine("## Basin of Attraction")
                    appendLine()
                    appendLine(stability.basin_of_attraction)
                    appendLine()
                    appendLine("## Convergence Rate")
                    appendLine()
                    appendLine(stability.convergence_rate)
                    appendLine()
                    if (stability.instability_modes.isNotEmpty()) {
                        appendLine("## Potential Instability Modes")
                        appendLine()
                        stability.instability_modes.forEach { appendLine("- ⚠️ $it") }
                    }
                }
                stabilityTask.add(stabilityContent.renderMarkdown)
                task.update()
                overviewTask.add("\n- ✅ Stability analysis complete".renderMarkdown)
                transcript?.write("\n## Stability Analysis\n$stabilityContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Lipschitz analysis
            if (includeLipschitz) {
                overviewTask.add("\n- ⏳ Analyzing Lipschitz properties...".renderMarkdown)
                task.update()
                val lipschitzTask = task.newTask()
                tabs["Lipschitz Analysis"] = lipschitzTask.placeholder
                val lipschitz = generateLipschitzAnalysis(layerName, definition, gradients, api)
                val lipschitzContent = buildString {
                    appendLine("# Lipschitz Continuity Analysis")
                    appendLine()
                    appendLine("## Forward Function Lipschitz Constant")
                    appendLine()
                    appendLine("$${lipschitz.forward_lipschitz}$")
                    appendLine()
                    appendLine("## Gradient Lipschitz Constant (Smoothness)")
                    appendLine()
                    appendLine("$${lipschitz.gradient_lipschitz}$")
                    appendLine()
                    appendLine("## Spectral Norm Bounds")
                    appendLine()
                    appendLine(lipschitz.spectral_norm_bounds)
                    appendLine()
                    appendLine("## Gradient Flow Analysis")
                    appendLine()
                    appendLine(lipschitz.gradient_flow)
                    appendLine()
                    appendLine("## Smoothness Properties")
                    appendLine()
                    lipschitz.smoothness_properties.forEach { appendLine("- $it") }
                }
                lipschitzTask.add(lipschitzContent.renderMarkdown)
                task.update()
                overviewTask.add("\n- ✅ Lipschitz analysis complete".renderMarkdown)
                transcript?.write("\n## Lipschitz Analysis\n$lipschitzContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Numerical stability
            if (includeNumerical) {
                overviewTask.add("\n- ⏳ Analyzing numerical stability...".renderMarkdown)
                task.update()
                val numericalTask = task.newTask()
                tabs["Numerical Stability"] = numericalTask.placeholder
                val numerical = generateNumericalStability(layerName, definition, api)
                val numericalContent = buildString {
                    appendLine("# Numerical Stability Analysis")
                    appendLine()
                    appendLine("## Overflow Conditions")
                    appendLine()
                    numerical.overflow_conditions.forEach { appendLine("- ⚠️ $it") }
                    appendLine()
                    appendLine("## Underflow Conditions")
                    appendLine()
                    numerical.underflow_conditions.forEach { appendLine("- ⚠️ $it") }
                    appendLine()
                    appendLine("## Precision Recommendations")
                    appendLine()
                    numerical.precision_recommendations.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("## Stabilization Techniques")
                    appendLine()
                    numerical.stabilization_techniques.forEach { appendLine("- ✅ $it") }
                    appendLine()
                    appendLine("## Gradient Clipping")
                    appendLine()
                    appendLine(numerical.gradient_clipping)
                }
                numericalTask.add(numericalContent.renderMarkdown)
                task.update()
                overviewTask.add("\n- ✅ Numerical stability analysis complete".renderMarkdown)
                transcript?.write("\n## Numerical Stability\n$numericalContent\n".toByteArray(StandardCharsets.UTF_8))
            }

            // Generate implementations
            overviewTask.add("\n- ⏳ Generating implementations...".renderMarkdown)
            task.update()
            val implTask = task.newTask()
            tabs["Implementations"] = implTask.placeholder
            val implementations = languages.map { lang ->
                generateImplementation(layerName, definition, gradients, parameters, lang, api)
            }
            val implContent = buildString {
                appendLine("# Reference Implementations")
                appendLine()
                implementations.forEach { impl ->
                    appendLine("## ${impl.language.uppercase()}")
                    appendLine()
                    if (impl.dependencies.isNotEmpty()) {
                        appendLine("### Dependencies")
                        appendLine()
                        appendLine("```${impl.language}")
                        impl.dependencies.forEach { appendLine(it) }
                        appendLine("```")
                        appendLine()
                    }
                    appendLine("### Forward Pass")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.forward_code)
                    appendLine("```")
                    appendLine()
                    appendLine("### Backward Pass")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.backward_code)
                    appendLine("```")
                    appendLine()
                    appendLine("### Initialization")
                    appendLine()
                    appendLine("```${impl.language}")
                    appendLine(impl.initialization_code)
                    appendLine("```")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
            implTask.add(implContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Implementations generated".renderMarkdown)
                transcript?.write("\n## Implementations\n$implContent\n".toByteArray(StandardCharsets.UTF_8))

            // Complexity analysis
            val complexityTask = task.newTask()
            tabs["Complexity"] = complexityTask.placeholder
            val complexity = generateComplexityAnalysis(layerName, definition, inputShape, outputShape, parameters, api)
            val complexityContent = buildString {
                appendLine("# Computational Complexity Analysis")
                appendLine()
                appendLine("## Time Complexity")
                appendLine()
                appendLine("| Pass | Complexity |")
                appendLine("|------|------------|")
                appendLine("| Forward | ${complexity.forward_time_complexity} |")
                appendLine("| Backward | ${complexity.backward_time_complexity} |")
                appendLine()
                appendLine("## Space Complexity")
                appendLine()
                appendLine(complexity.space_complexity)
                appendLine()
                appendLine("## Memory Bandwidth")
                appendLine()
                appendLine(complexity.memory_bandwidth)
                appendLine()
                appendLine("## Parallelization")
                appendLine()
                appendLine(complexity.parallelization_notes)
            }
            complexityTask.add(complexityContent.renderMarkdown)
            task.update()
                transcript?.write("\n## Complexity Analysis\n$complexityContent\n".toByteArray(StandardCharsets.UTF_8))

            // Originality analysis
            overviewTask.add("\n- ⏳ Analyzing originality...".renderMarkdown)
            task.update()
            val originalityTask = task.newTask()
            tabs["Originality"] = originalityTask.placeholder
            val originality = generateOriginalityAnalysis(layerName, definition, forwardDesc, api)
            val originalityContent = buildString {
                appendLine("# Originality Analysis")
                appendLine()
                appendLine("## Novelty Assessment")
                appendLine()
                appendLine(originality.novelty_assessment)
                appendLine()
                appendLine("## Related Architectures")
                appendLine()
                if (originality.related_architectures.isNotEmpty()) {
                    originality.related_architectures.forEach { appendLine("- $it") }
                } else {
                    appendLine("No closely related architectures identified.")
                }
                appendLine()
                appendLine("## Key Innovations")
                appendLine()
                if (originality.key_innovations.isNotEmpty()) {
                    originality.key_innovations.forEach { appendLine("- ✨ $it") }
                } else {
                    appendLine("Standard implementation without novel innovations.")
                }
                appendLine()
                appendLine("## Baseline Comparison")
                appendLine()
                appendLine(originality.baseline_comparison)
                appendLine()
                appendLine("## Potential Research Contributions")
                appendLine()
                if (originality.research_contributions.isNotEmpty()) {
                    originality.research_contributions.forEach { appendLine("- 📚 $it") }
                } else {
                    appendLine("Primarily an engineering contribution.")
                }
                appendLine()
                appendLine("## Limitations")
                appendLine()
                if (originality.limitations.isNotEmpty()) {
                    originality.limitations.forEach { appendLine("- ⚠️ $it") }
                } else {
                    appendLine("No significant limitations identified.")
                }
            }
            originalityTask.add(originalityContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Originality analysis complete".renderMarkdown)
                transcript?.write("\n## Originality Analysis\n$originalityContent\n".toByteArray(StandardCharsets.UTF_8))

            // Use case analysis
            overviewTask.add("\n- ⏳ Analyzing use cases...".renderMarkdown)
            task.update()
            val useCaseTask = task.newTask()
            tabs["Use Cases"] = useCaseTask.placeholder
            val useCases = generateUseCaseAnalysis(layerName, definition, forwardDesc, inputShape, outputShape, api)
            val useCaseContent = buildString {
                appendLine("# Use Case Analysis")
                appendLine()
                appendLine("## Primary Application Domains")
                appendLine()
                if (useCases.primary_domains.isNotEmpty()) {
                    useCases.primary_domains.forEach { appendLine("- 🎯 $it") }
                } else {
                    appendLine("General-purpose layer applicable across domains.")
                }
                appendLine()
                appendLine("## Optimal Tasks")
                appendLine()
                appendLine("Tasks where this layer excels:")
                appendLine()
                if (useCases.optimal_tasks.isNotEmpty()) {
                    useCases.optimal_tasks.forEach { appendLine("- ✅ $it") }
                } else {
                    appendLine("Suitable for general neural network tasks.")
                }
                appendLine()
                appendLine("## Unsuitable Tasks")
                appendLine()
                appendLine("Tasks where this layer may not be the best choice:")
                appendLine()
                if (useCases.unsuitable_tasks.isNotEmpty()) {
                    useCases.unsuitable_tasks.forEach { appendLine("- ❌ $it") }
                } else {
                    appendLine("No specific contraindications identified.")
                }
                appendLine()
                appendLine("## Recommended Architectures")
                appendLine()
                if (useCases.recommended_architectures.isNotEmpty()) {
                    useCases.recommended_architectures.forEach { appendLine("- 🏗️ $it") }
                } else {
                    appendLine("Can be integrated into most standard architectures.")
                }
                appendLine()
                appendLine("## Example Scenarios")
                appendLine()
                if (useCases.example_scenarios.isNotEmpty()) {
                    useCases.example_scenarios.forEachIndexed { index, scenario ->
                        appendLine("### Scenario ${index + 1}")
                        appendLine()
                        appendLine(scenario)
                        appendLine()
                    }
                } else {
                    appendLine("See primary domains for general application guidance.")
                }
                appendLine()
                appendLine("## Integration Notes")
                appendLine()
                appendLine(useCases.integration_notes.ifBlank { "Standard integration procedures apply." })
                appendLine()
                appendLine("## Scaling Considerations")
                appendLine()
                appendLine(useCases.scaling_considerations.ifBlank { "Scales linearly with input dimensions." })
                appendLine()
                appendLine("## Industry Applications")
                appendLine()
                if (useCases.industry_applications.isNotEmpty()) {
                    useCases.industry_applications.forEach { appendLine("- 🏭 $it") }
                } else {
                    appendLine("Applicable across various industries using deep learning.")
                }
            }
            useCaseTask.add(useCaseContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Use case analysis complete".renderMarkdown)
                transcript?.write("\n## Use Case Analysis\n$useCaseContent\n".toByteArray(StandardCharsets.UTF_8))

            // Practical guidance
            overviewTask.add("\n- ⏳ Generating practical guidance...".renderMarkdown)
            task.update()
            val guidanceTask = task.newTask()
            tabs["Practical Guidance"] = guidanceTask.placeholder
            val numerical = if (includeNumerical) {
                generateNumericalStability(layerName, definition, api)
            } else {
                NumericalStability(
                    overflow_conditions = emptyList(),
                    underflow_conditions = emptyList(),
                    precision_recommendations = emptyList(),
                    stabilization_techniques = emptyList(),
                    gradient_clipping = "N/A"
                )
            }
            val guidance = generatePracticalGuidance(layerName, definition, summary, numerical, api)
            val guidanceContent = buildString {
                appendLine("# Practical Guidance")
                appendLine()
                appendLine("## Hyperparameter Tuning")
                appendLine()
                if (guidance.hyperparameter_tuning.isNotEmpty()) {
                    guidance.hyperparameter_tuning.forEach { appendLine("- $it") }
                } else {
                    appendLine("Standard hyperparameter tuning approaches apply.")
                }
                appendLine()
                appendLine("## ⚠️ Common Pitfalls")
                appendLine()
                if (guidance.common_pitfalls.isNotEmpty()) {
                    guidance.common_pitfalls.forEach { appendLine("- $it") }
                } else {
                    appendLine("No specific pitfalls identified beyond standard neural network training issues.")
                }
                appendLine()
                appendLine("## 🔧 Debugging Tips")
                appendLine()
                if (guidance.debugging_tips.isNotEmpty()) {
                    guidance.debugging_tips.forEach { appendLine("- $it") }
                } else {
                    appendLine("Use standard debugging techniques: gradient checking, visualization, unit tests.")
                }
                appendLine()
                appendLine("## ⚡ Performance Optimization")
                appendLine()
                if (guidance.optimization_strategies.isNotEmpty()) {
                    guidance.optimization_strategies.forEach { appendLine("- $it") }
                } else {
                    appendLine("Standard optimization techniques apply: batching, GPU acceleration, mixed precision.")
                }
                appendLine()
                appendLine("## 📊 Monitoring & Diagnostics")
                appendLine()
                if (guidance.monitoring_recommendations.isNotEmpty()) {
                    guidance.monitoring_recommendations.forEach { appendLine("- $it") }
                } else {
                    appendLine("Monitor standard metrics: loss, gradients, activations, parameter norms.")
                }
                appendLine()
                appendLine("## 🚀 Production Best Practices")
                appendLine()
                if (guidance.production_best_practices.isNotEmpty()) {
                    guidance.production_best_practices.forEach { appendLine("- $it") }
                } else {
                    appendLine("Follow standard ML deployment practices: versioning, monitoring, A/B testing.")
                }
            }
            guidanceTask.add(guidanceContent.renderMarkdown)
            task.update()
            overviewTask.add("\n- ✅ Practical guidance complete".renderMarkdown)
                transcript?.write("\n## Practical Guidance\n$guidanceContent\n".toByteArray(StandardCharsets.UTF_8))

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Analysis Complete")
                appendLine()
                appendLine("| Metric | Value |")
                appendLine("|--------|-------|")
                appendLine("| Total Time | ${totalTime / 1000}s |")
                appendLine("| Sections Generated | ${tabs.size} |")
                appendLine("| Implementation Languages | ${languages.joinToString(", ")} |")
                appendLine()
                appendLine("## Configuration Summary")
                appendLine()
                appendLine("| Setting | Value |")
                appendLine("|---------|-------|")
                appendLine("| Layer Name | $layerName |")
                appendLine("| Input Shape | $inputShape |")
                appendLine("| Output Shape | $outputShape |")
                appendLine("| Activation | $activation |")
                appendLine("| Analysis Depth | $analysisDepth |")
                appendLine("| Higher-Order Analysis | $includeHigherOrder |")
                appendLine("| Lyapunov Analysis | $includeLyapunov |")
                appendLine("| Lipschitz Analysis | $includeLipschitz |")
                appendLine("| Numerical Stability | $includeNumerical |")
                appendLine("| Generate Tests | $generateTests |")
            }
            overviewTask.add(finalOverview.renderMarkdown)
            task.update()
                transcript?.write("\n## Final Summary\n$finalOverview\n".toByteArray(StandardCharsets.UTF_8))

            val resultMessage = buildString {
                appendLine("# $layerName Layer Analysis Complete")
                appendLine()
                appendLine("## Quick Summary")
                appendLine()
                appendLine("**${summary.one_liner}**")
                appendLine()
                appendLine("> ${summary.key_insight}")
                appendLine()
                appendLine("## Key Details")
                appendLine()
                appendLine("- **Forward Function:** $${definition.forward_latex}$")
                appendLine("- **Computational Cost:** ${summary.computational_cost}")
                appendLine("- **Training Difficulty:** ${summary.training_difficulty}")
                appendLine("- **Beginner Friendly:** ${summary.beginner_friendly}")
                appendLine()
                appendLine("## Analogy")
                appendLine()
                appendLine(intuitive.analogy.take(200) + "...")
                appendLine()
                appendLine("See tabs for complete analysis including:")
                appendLine("- Executive summary and decision guide")
                appendLine("- Intuitive explanations with analogies")
                appendLine("- Conceptual diagrams")
                appendLine("- Formal mathematics and gradients")
                appendLine("- Stability and complexity analysis")
                appendLine("- Implementations in ${languages.joinToString(", ")}")
                appendLine("- Originality assessment")
                appendLine("- Use cases and practical guidance")
            }

            task.complete("Neural network layer analysis complete in ${totalTime / 1000}s")
            resultFn(resultMessage)

            } catch (e: Exception) {
                task.error(e)
                log.error("Error during NeuralNetworkLayerTask execution for layer: ${executionConfig?.layer_name}", e)
                transcript?.write(
                    "\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                resultFn("ERROR: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }

    private fun generateExecutiveSummary(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ExecutiveSummary {
        return try {
            ParsedAgent(
                resultClass = ExecutiveSummary::class.java,
                prompt = """
You are an expert in neural networks. Create an executive summary for a layer.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Provide a concise executive summary:
1. One-sentence description of what the layer does
2. Key mathematical insight in plain language
3. Primary strengths (3-5 bullet points)
4. Primary limitations (3-5 bullet points)
5. When to use this layer (decision criteria)
6. When NOT to use this layer
7. Computational cost assessment (low/medium/high)
8. Training difficulty (easy/moderate/hard)
9. Is it beginner-friendly? (yes/no/with_caution)

Be concise, practical, and actionable. Focus on helping users make informed decisions.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ExecutiveSummaryGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate executive summary")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate executive summary", e)
            ExecutiveSummary(
                one_liner = "$layerName: A neural network layer",
                key_insight = "Transforms input data according to learned parameters",
                strengths = listOf("Learnable parameters", "Differentiable"),
                limitations = listOf("Requires training data"),
                when_to_use = "When you need to learn transformations from data",
                when_not_to_use = "When hand-crafted features are sufficient",
                computational_cost = "medium",
                training_difficulty = "moderate",
                beginner_friendly = "with_caution"
            )
        }
    }

    private fun generateIntuitiveExplanation(
        layerName: String,
        forwardDesc: String,
        summary: ExecutiveSummary,
        api: ChatInterface
    ): IntuitiveExplanation {
        return try {
            ParsedAgent(
                resultClass = IntuitiveExplanation::class.java,
                prompt = """
You are an expert educator in neural networks. Explain a layer intuitively.

## Layer: $layerName

## Description
$forwardDesc

## Key Insight
${summary.key_insight}

## Instructions
Provide intuitive explanations:
1. Real-world analogy (compare to everyday objects/processes)
2. Plain language walkthrough (step-by-step, no math jargon)
3. Visual description of information flow
4. What problem does this layer solve?
5. How does it solve the problem? (mechanism)
6. Common misconceptions about this layer
7. Intuitive explanation of why gradients work this way
8. Mental model for understanding the layer

Use simple language. Avoid mathematical notation. Focus on building intuition.
Think of explaining to a smart high school student or non-technical stakeholder.
                """.trimIndent(),
                model = api,
                temperature = 0.4,
                name = "IntuitiveExplanationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate intuitive explanation")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate intuitive explanation", e)
            IntuitiveExplanation(
                analogy = "Think of this layer like a filter that learns what patterns to look for in the data.",
                plain_language_walkthrough = "The layer takes input, applies learned transformations, and produces output.",
                information_flow_description = "Data flows through the layer, being transformed along the way.",
                problem_solved = "Learning useful representations from data",
                solution_mechanism = "Adjusting parameters based on feedback from training"
            )
        }
    }

    private fun generateConceptualDiagram(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ConceptualDiagram {
        return try {
            ParsedAgent(
                resultClass = ConceptualDiagram::class.java,
                prompt = """
You are an expert in visual communication of neural networks. Create conceptual diagrams.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Create visual representations:
1. ASCII art diagram showing layer structure (boxes, arrows)
2. Description of data flow through the layer
3. Mermaid diagram syntax for flowchart visualization
4. Description of each parameter's role

Make diagrams clear and informative. Use standard conventions.
For ASCII art, use boxes (┌─┐│└┘), arrows (→), and labels.
For Mermaid, use flowchart syntax with clear node labels.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ConceptualDiagramGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate conceptual diagram")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate conceptual diagram", e)
            ConceptualDiagram(
                ascii_diagram = """
                    Input → [$layerName] → Output
                """.trimIndent(),
                data_flow_description = "Data flows from input through the layer to output",
                mermaid_diagram = """
                    graph LR
                        A[Input] --> B[$layerName]
                        B --> C[Output]
                """.trimIndent()
            )
        }
    }

    private fun generatePracticalGuidance(
        layerName: String,
        definition: LayerDefinition,
        summary: ExecutiveSummary,
        numerical: NumericalStability,
        api: ChatInterface
    ): PracticalGuidance {
        return try {
            ParsedAgent(
                resultClass = PracticalGuidance::class.java,
                prompt = """
You are an expert ML engineer. Provide practical guidance for implementing a layer.

## Layer: $layerName

## Summary
${summary.one_liner}

Training Difficulty: ${summary.training_difficulty}

## Numerical Considerations
Overflow: ${numerical.overflow_conditions.joinToString("; ")}
Underflow: ${numerical.underflow_conditions.joinToString("; ")}

## Instructions
Provide practical implementation guidance:
1. Hyperparameter tuning recommendations (learning rate, initialization, etc.)
2. Common pitfalls and how to avoid them
3. Debugging tips specific to this layer
4. Performance optimization strategies
5. Monitoring and diagnostics recommendations
6. Best practices for production deployment

Be specific and actionable. Draw from real-world experience.
Focus on issues practitioners actually encounter.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "PracticalGuidanceGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate practical guidance")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate practical guidance", e)
            PracticalGuidance(
                hyperparameter_tuning = listOf("Start with standard learning rates (1e-3 to 1e-4)"),
                common_pitfalls = listOf("Forgetting to normalize inputs", "Not monitoring gradients"),
                debugging_tips = listOf("Check gradient magnitudes", "Visualize activations"),
                optimization_strategies = listOf("Use GPU acceleration", "Batch operations"),
                monitoring_recommendations = listOf("Track loss curves", "Monitor gradient norms"),
                production_best_practices = listOf("Version models", "Monitor inference latency")
            )
        }
    }

    private fun generateLayerDefinition(
        layerName: String,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        activation: String,
        api: ChatInterface
    ): LayerDefinition {
        return try {
            ParsedAgent(
                resultClass = LayerDefinition::class.java,
                prompt = """
You are an expert in neural network mathematics. Define a neural network layer formally.

## Layer: $layerName

## Description
$forwardDesc

## Specifications
- Input Shape: $inputShape
- Output Shape: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}
- Activation: $activation

## Instructions
Provide a formal mathematical definition including:
1. The forward function in precise mathematical notation
2. LaTeX representation suitable for rendering
3. Domain constraints (valid input ranges, shapes)
4. Range/codomain description
5. Parameter initialization recommendations (Xavier, He, etc.)

Be rigorous and use standard mathematical notation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "LayerDefinitionGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate formal definition")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate layer definition", e)
            LayerDefinition(
                forward_equation = forwardDesc,
                forward_latex = "y = f(x)",
                domain_constraints = listOf("x ∈ ℝ^n"),
                range_description = "y ∈ ℝ^m",
                initialization_recommendations = listOf("Xavier initialization recommended")
            )
        }
    }

    private fun generateGradientDerivation(
        layerName: String,
        definition: LayerDefinition,
        parameters: List<String>,
        api: ChatInterface
    ): GradientDerivation {
        return try {
            ParsedAgent(
                resultClass = GradientDerivation::class.java,
                prompt = """
You are an expert in neural network backpropagation. Derive the gradients for a layer.

## Layer: $layerName

## Forward Function
${definition.forward_equation}

LaTeX: ${definition.forward_latex}

## Parameters
${parameters.joinToString("\n") { "- $it" }.ifEmpty { "None" }}

## Instructions
Derive the backward pass gradients:
1. Gradient with respect to input (∂L/∂x) for backpropagation
2. Gradient with respect to each parameter (∂L/∂θ) for optimization
3. Show the chain rule application step by step
4. Describe the computational graph

Assume upstream gradient ∂L/∂y is given. Use standard notation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "GradientDerivationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Derive gradients")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate gradient derivation", e)
            GradientDerivation(
                gradient_input = "∂L/∂x = ∂L/∂y · ∂y/∂x",
                gradient_input_latex = "\\frac{\\partial L}{\\partial x} = \\frac{\\partial L}{\\partial y} \\cdot \\frac{\\partial y}{\\partial x}",
                chain_rule_explanation = "Apply chain rule from output to input"
            )
        }
    }

    private fun generateHigherOrderAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        analysisDepth: String,
        api: ChatInterface
    ): HigherOrderAnalysis {
        return try {
            ParsedAgent(
                resultClass = HigherOrderAnalysis::class.java,
                prompt = """
You are an expert in optimization theory. Analyze higher-order derivatives of a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## First Derivatives
Input gradient: ${gradients.gradient_input_latex}

## Analysis Depth: $analysisDepth

## Instructions
Analyze higher-order derivatives:
1. Hessian matrix structure (sparsity, block structure)
2. Eigenvalue bounds of the Hessian
3. Key second derivative expressions
4. Curvature analysis (convexity, saddle points)
5. Fisher information matrix if applicable
6. Natural gradient considerations

Focus on implications for optimization and training dynamics.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "HigherOrderAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze higher-order derivatives")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate higher-order analysis", e)
            HigherOrderAnalysis(
                hessian_structure = "Analysis not available",
                curvature_analysis = "See gradient derivation for first-order information"
            )
        }
    }

    private fun generateStabilityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        api: ChatInterface
    ): StabilityAnalysis {
        return try {
            ParsedAgent(
                resultClass = StabilityAnalysis::class.java,
                prompt = """
You are an expert in dynamical systems and neural network training dynamics. Analyze stability.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
${gradients.gradient_input_latex}

## Instructions
Perform Lyapunov stability analysis for training dynamics:
1. Propose a Lyapunov function candidate (typically loss-based)
2. Derive stability conditions
3. Analyze equilibrium points (optimal parameters)
4. Describe basin of attraction
5. Bound convergence rates
6. Identify potential instability modes (exploding/vanishing gradients)

Consider gradient descent dynamics: θ_{t+1} = θ_t - η∇L(θ_t)
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "StabilityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze stability")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate stability analysis", e)
            StabilityAnalysis(
                lyapunov_function = "V(θ) = L(θ) - L(θ*)",
                stability_conditions = listOf("Learning rate η < 2/L where L is Lipschitz constant"),
                convergence_rate = "Linear convergence for strongly convex loss"
            )
        }
    }

    private fun generateLipschitzAnalysis(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        api: ChatInterface
    ): LipschitzAnalysis {
        return try {
            ParsedAgent(
                resultClass = LipschitzAnalysis::class.java,
                prompt = """
You are an expert in functional analysis and neural networks. Analyze Lipschitz properties.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
${gradients.gradient_input_latex}

## Instructions
Analyze Lipschitz continuity:
1. Compute/bound Lipschitz constant of forward function
2. Compute/bound Lipschitz constant of gradient (smoothness)
3. Analyze spectral norm bounds
4. Describe gradient flow properties
5. List smoothness properties

These are crucial for:
- Generalization bounds
- Adversarial robustness
- Training stability
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "LipschitzAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze Lipschitz properties")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate Lipschitz analysis", e)
            LipschitzAnalysis(
                forward_lipschitz = "L_f ≤ ||W||_2 (spectral norm of weights)",
                gradient_lipschitz = "L_g bounded by second derivatives",
                gradient_flow = "Gradient magnitude bounded by Lipschitz constant"
            )
        }
    }

    private fun generateNumericalStability(
        layerName: String,
        definition: LayerDefinition,
        api: ChatInterface
    ): NumericalStability {
        return try {
            ParsedAgent(
                resultClass = NumericalStability::class.java,
                prompt = """
You are an expert in numerical computing. Analyze numerical stability of a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Instructions
Analyze numerical stability:
1. Identify overflow conditions (large values)
2. Identify underflow conditions (small values, division)
3. Recommend numerical precision (float16, float32, float64)
4. Suggest stabilization techniques (log-space, normalization)
5. Recommend gradient clipping strategies

Consider both forward and backward passes.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "NumericalStabilityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze numerical stability")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate numerical stability analysis", e)
            NumericalStability(
                overflow_conditions = listOf("Large input values may cause overflow"),
                underflow_conditions = listOf("Small gradients may underflow"),
                precision_recommendations = listOf("float32 recommended for training"),
                stabilization_techniques = listOf("Batch normalization", "Gradient clipping")
            )
        }
    }

    private fun generateImplementation(
        layerName: String,
        definition: LayerDefinition,
        gradients: GradientDerivation,
        parameters: List<String>,
        language: String,
        api: ChatInterface
    ): Implementation {
        return try {
            ParsedAgent(
                resultClass = Implementation::class.java,
                prompt = """
You are an expert programmer. Implement a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Gradients
Input: ${gradients.gradient_input_latex}
Parameters: ${gradients.parameter_gradients_latex}

## Parameters
${parameters.joinToString("\n") { "- $it" }.ifEmpty { "None" }}

## Target Language: $language

## Instructions
Provide a complete implementation:
1. Forward pass function
2. Backward pass function (computing gradients)
3. Parameter initialization function
4. Required imports/dependencies

Use idiomatic code for the target language. Include comments explaining the mathematics.
For Python, use NumPy. For Tensorflow.js, use tfjs. For pseudocode, be clear and mathematical.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ImplementationGenerator",
                parsingChatter = defaultFast
            ).answer(listOf("Generate implementation")).obj.copy(language = language)
        } catch (e: Exception) {
            log.warn("Failed to generate implementation for $language", e)
            Implementation(
                language = language,
                forward_code = "# Forward pass implementation\ndef forward(x):\n    return x  # TODO",
                backward_code = "# Backward pass implementation\ndef backward(grad_output):\n    return grad_output  # TODO",
                initialization_code = "# Parameter initialization\ndef init_params():\n    pass  # TODO"
            )
        }
    }

    private fun generateComplexityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        inputShape: String,
        outputShape: String,
        parameters: List<String>,
        api: ChatInterface
    ): ComplexityAnalysis {
        return try {
            ParsedAgent(
                resultClass = ComplexityAnalysis::class.java,
                prompt = """
You are an expert in computational complexity. Analyze a neural network layer's complexity.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Shapes
- Input: $inputShape
- Output: $outputShape
- Parameters: ${parameters.joinToString(", ").ifEmpty { "None" }}

## Instructions
Analyze computational complexity:
1. Time complexity of forward pass (Big-O notation)
2. Time complexity of backward pass
3. Space complexity (activations, gradients)
4. Memory bandwidth requirements
5. Parallelization potential (GPU, distributed)

Express in terms of batch size (B), dimensions, etc.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "ComplexityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze complexity")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate complexity analysis", e)
            ComplexityAnalysis(
                forward_time_complexity = "O(n)",
                backward_time_complexity = "O(n)",
                space_complexity = "O(n) for activations",
                memory_bandwidth = "Memory bound for large tensors",
                parallelization_notes = "Highly parallelizable on GPU"
            )
        }
    }

    private fun generateOriginalityAnalysis(
        layerName: String,
        definition: LayerDefinition,
        forwardDesc: String,
        api: ChatInterface
    ): OriginalityAnalysis {
        return try {
            ParsedAgent(
                resultClass = OriginalityAnalysis::class.java,
                prompt = """
You are an expert in deep learning research and neural network architectures. Analyze the originality of a layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Description
$forwardDesc

## Instructions
Analyze the originality and novelty of this layer:
1. Assess overall novelty compared to existing published architectures
2. List similar or related existing layers (e.g., from papers, frameworks)
3. Identify key innovations or unique aspects
4. Compare with baseline/standard approaches (what does this do differently?)
5. Identify potential research contributions (theoretical, empirical, practical)
6. Note limitations compared to existing approaches

Be specific about related work (cite paper names/years if applicable).
Consider both theoretical novelty and practical innovation.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "OriginalityAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze originality")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate originality analysis", e)
            OriginalityAnalysis(
                novelty_assessment = "Unable to assess novelty automatically. Manual review recommended.",
                related_architectures = listOf("Standard neural network layers"),
                baseline_comparison = "Compare with standard implementations in PyTorch/TensorFlow"
            )
        }
    }

    private fun generateUseCaseAnalysis(
        layerName: String,
        definition: LayerDefinition,
        forwardDesc: String,
        inputShape: String,
        outputShape: String,
        api: ChatInterface
    ): UseCaseAnalysis {
        return try {
            ParsedAgent(
                resultClass = UseCaseAnalysis::class.java,
                prompt = """
You are an expert in applied deep learning. Analyze use cases for a neural network layer.

## Layer: $layerName

## Forward Function
${definition.forward_latex}

## Description
$forwardDesc

## Shapes
- Input: $inputShape
- Output: $outputShape

## Instructions
Analyze practical use cases:
1. Identify primary application domains (NLP, CV, audio, tabular, etc.)
2. List specific tasks where this layer excels
3. List tasks where this layer may not be suitable
4. Recommend network architectures to use this layer in
5. Provide concrete example scenarios with descriptions
6. Note integration considerations when adding to existing networks
7. Discuss scaling considerations for different problem sizes
8. List industry applications (healthcare, finance, autonomous vehicles, etc.)

Be practical and specific. Consider both research and production use cases.
                """.trimIndent(),
                model = api,
                temperature = 0.3,
                name = "UseCaseAnalyzer",
                parsingChatter = defaultFast
            ).answer(listOf("Analyze use cases")).obj
        } catch (e: Exception) {
            log.warn("Failed to generate use case analysis", e)
            UseCaseAnalysis(
                primary_domains = listOf("General deep learning applications"),
                optimal_tasks = listOf("Tasks matching the layer's input/output structure"),
                integration_notes = "Standard integration with common deep learning frameworks",
                scaling_considerations = "Performance depends on input dimensions and batch size"
            )
        }
    }
}