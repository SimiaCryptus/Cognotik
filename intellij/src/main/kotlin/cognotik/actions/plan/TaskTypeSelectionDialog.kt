package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.plan.TaskType
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class TaskTypeSelectionDialog(project: Project?) : DialogWrapper(project) {
    
    private var selectedTaskType: TaskType<*, *>? = null
    private val descriptionArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 8
        text = "Select a task type to see its description"
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
                        descriptionArea.text = "Select a task type to see its description"
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
            taskType.name.contains("Reasoning") || 
            taskType.name in listOf("ChainOfThought", "MetaCognitiveReflection", 
                "MultiPerspectiveAnalysis", "SocraticDialogue", "AnalogicalReasoning",
                "CounterfactualAnalysis", "AbstractionLadder", "ConstraintSatisfaction",
                "CausalInference", "DecompositionSynthesis") -> "Reasoning"
            
            taskType.name in listOf("Analysis", "FileModification", "FileSearch", 
                "WriteHtml", "GeneratePresentation") -> "File Operations"
            
            taskType.name in listOf("VectorSearch", "KnowledgeIndexing") -> "Knowledge Management"
            
            taskType.name in listOf("RunShellCommand", "RunCode", "CommandSession", 
                "SeleniumSession", "SelfHealing") -> "Execution & Automation"
            
            taskType.name in listOf("GitHubSearch", "CrawlerAgent") -> "Online & Search"
            
            taskType.name == "MCPTool" -> "Integration"
            
            else -> "Other"
        }
    }
    
    private fun updateDescription(taskType: TaskType<*, *>) {
        val description = buildString {
            append(taskType.description ?: "No description available")
            append("\n\n")
            
            // Parse HTML tooltip to plain text
            taskType.tooltipHtml?.let { html ->
                val plainText = html
                    .replace("<html>", "")
                    .replace("</html>", "")
                    .replace("<body[^>]*>".toRegex(), "")
                    .replace("</body>", "")
                    .replace("<h3>", "\n")
                    .replace("</h3>", "\n")
                    .replace("<p>", "\n")
                    .replace("</p>", "")
                    .replace("<b>", "")
                    .replace("</b>", "")
                    .replace("<ul>", "\n")
                    .replace("</ul>", "")
                    .replace("<li>", "  • ")
                    .replace("</li>", "\n")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                
                append(plainText)
            }
        }
        
        descriptionArea.text = description
        descriptionArea.caretPosition = 0
    }
    
    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JBScrollPane(taskTree).apply {
                    preferredSize = Dimension(300, 400)
                },
                JBScrollPane(descriptionArea).apply {
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