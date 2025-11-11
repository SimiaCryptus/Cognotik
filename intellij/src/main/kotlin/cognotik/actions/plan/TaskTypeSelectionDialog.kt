package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.plan.TaskType
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.*

class TaskTypeSelectionDialog(project: Project?) : DialogWrapper(project) {

    private var selectedTaskType: TaskType<*, *>? = null
    private val descriptionPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        text = "<html><body><p>Select a task type to see its description</p></body></html>"
    }

    private val taskTree: JTree

    init {
        val root = DefaultMutableTreeNode("Task Types")
        val treeModel = DefaultTreeModel(root)

        // Group task types by package
        val tasksByPackage = TaskType.values()
            .groupBy { getPackageGroup(it) }
            .toSortedMap()

        tasksByPackage.forEach { (packageName, tasks) ->
            val packageNode = DefaultMutableTreeNode(packageName)
            root.add(packageNode)

            tasks.sortedBy { it.name }.forEach { taskType ->
                val taskNode = DefaultMutableTreeNode(TaskTypeNode(taskType))
                packageNode.add(taskNode)
            }
        }

        taskTree = JTree(treeModel).apply {
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            isRootVisible = false
            showsRootHandles = true

            // Custom renderer to show task type names
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree?,
                    value: Any?,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getTreeCellRendererComponent(
                        tree, value, sel, expanded, leaf, row, hasFocus
                    )

                    if (value is DefaultMutableTreeNode) {
                        val userObject = value.userObject
                        when (userObject) {
                            is TaskTypeNode -> {
                                text = userObject.taskType.name
                                toolTipText = userObject.taskType.description
                            }

                            is String -> {
                                text = userObject
                                toolTipText = null
                            }
                        }
                    }

                    return component
                }
            }

            // Add selection listener to update description
            addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {
                    val node = lastSelectedPathComponent as? DefaultMutableTreeNode
                    val userObject = node?.userObject

                    if (userObject is TaskTypeNode) {
                        selectedTaskType = userObject.taskType
                        updateDescription(userObject.taskType)
                    } else {
                        selectedTaskType = null
                        descriptionPane.text =
                            "<html><body><p>Select a task type to see its description</p></body></html>"
                    }
                }
            })
            // Add double-click listener to select and OK
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y)
                        if (path != null) {
                            val node = path.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is TaskTypeNode) {
                                selectedTaskType = userObject.taskType
                                doOKAction()
                            }
                        }
                    }
                }
            })
        }

        // Expand all package nodes by default
        for (i in 0 until root.childCount) {
            taskTree.expandPath(TreePath(arrayOf(root, root.getChildAt(i))))
        }

        init()
        title = "Select Task Type"
    }

    private fun getPackageGroup(taskType: TaskType<*, *>): String {
        return when {
            taskType.name in listOf(
                "SocraticDialogue", "AnalogicalReasoning",
                "CounterfactualAnalysis", "AbstractionLadder", "ConstraintSatisfaction",
                "CausalInference", "DecompositionSynthesis", "AbductiveReasoning",
                "AdversarialReasoning", "ConstraintRelaxation", "LateralThinking",
                "ProbabilisticReasoning", "SystemsThinking", "TemporalReasoning",
                "FiniteStateMachine", "Brainstorming",
                "ChainOfThought", "MetaCognitiveReflection", "GeneticOptimization",
            ) -> "Reasoning"

            taskType.name in listOf(
                "PoliticalOptimization", "MultiPerspectiveAnalysis", "DialecticalReasoning",
                "GameTheory", "EthicalReasoning", "LLMExperiment",
                "LLMPollSimulation", "PersuasiveEssay",
            ) -> "Social"

            taskType.name in listOf(
                "GameLevelDesign", "GameNarrativeDesign", "GameMechanicsDesign", "GameEconomy",
            ) -> "Gaming"

            taskType.name in listOf(
            "NarrativeGeneration", "NarrativeReasoning", "ArticleGeneration",
            "TechnicalExplanation", "TutorialGeneration",
            "BusinessProposal", "EmailCampaign", "InteractiveStory",
            "ReportGeneration", "Scriptwriting", "JournalismReasoning",
            "ResearchPaperGeneration",
          ) -> "Writing"

          taskType.name in listOf(
                "Analysis", "FileModification", "FileSearch",
                "WriteHtml", "GeneratePresentation", "GenerateImage",
              "IllustrateDocument",
            ) -> "File Operations"

            taskType.name in listOf("VectorSearch", "KnowledgeIndexing") -> "Knowledge Management"

            taskType.name in listOf(
                "RunShellCommand", "RunCode", "CommandSession",
                "SeleniumSession", "SelfHealing"
            ) -> "Execution & Automation"

            taskType.name in listOf("GitHubSearch", "CrawlerAgent", "MCPTool") -> "Online & Search"
            taskType.name in listOf("SubPlanning") -> "Planning & Orchestration"


            else -> "Other"
        }
    }

    private fun updateDescription(taskType: TaskType<*, *>) {
        descriptionPane.text = buildString {
            this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
            this.append("<h3 style='margin-top: 0;'>${taskType.name}</h3>")
            this.append("<p><b>Description:</b> ${taskType.description ?: "No description available"}</p>")
            taskType.tooltipHtml?.let { html ->
                // Extract content between body tags or use as-is if no body tags
                val content = if (html.contains("<body")) {
                    html.substringAfter("<body", "")
                        .substringAfter(">", "")
                        .substringBeforeLast("</body>", html)
                } else {
                    html.replace("<html>", "").replace("</html>", "")
                }
                this.append(content)
            }
            this.append("</body></html>")
        }
        descriptionPane.caretPosition = 0
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(
                JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    JBScrollPane(taskTree).apply {
                        preferredSize = Dimension(300, 400)
                    },
                    JBScrollPane(descriptionPane).apply {
                        preferredSize = Dimension(400, 400)
                    }
                ).apply {
                    dividerLocation = 300
                    resizeWeight = 0.4
                })
                .align(Align.FILL)
        }.resizableRow()
    }.apply {
        preferredSize = Dimension(750, 450)
    }

    override fun doOKAction() {
        if (selectedTaskType == null) {
            JOptionPane.showMessageDialog(
                contentPane,
                "Please select a task type",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        super.doOKAction()
    }

    fun getSelectedTaskType(): TaskType<*, *>? = selectedTaskType

    private data class TaskTypeNode(val taskType: TaskType<*, *>) {
        override fun toString(): String = taskType.name
    }
}