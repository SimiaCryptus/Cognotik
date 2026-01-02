package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.plan.TaskType
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.*

class TaskTypeSelectionDialog(
    project: Project?,
    private val allowMultipleSelection: Boolean = false
) : DialogWrapper(project) {

    private val selectedTaskTypes = mutableSetOf<TaskType<*, *>>()
    private val searchField = SearchTextField(false)
    private val descriptionPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        text = if (allowMultipleSelection) {
            "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
        } else {
            "<html><body><p>Select a task type to see its description</p></body></html>"
        }
    }

    private val taskTree: JTree
    var isQuickSelect = false
        private set


    init {
        val root = DefaultMutableTreeNode("Task Types")
        val treeModel = DefaultTreeModel(root)




        taskTree = JTree(treeModel).apply {
            selectionModel.selectionMode = if (allowMultipleSelection) {
                TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            } else {
                TreeSelectionModel.SINGLE_TREE_SELECTION
            }
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

                    selectedTaskTypes.clear()

                    val paths = selectionPaths
                    if (paths != null) {
                        paths.forEach { path ->
                            val node = path.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is TaskTypeNode) {
                                selectedTaskTypes.add(userObject.taskType)
                            }
                        }
                    }

                    if (selectedTaskTypes.isNotEmpty()) {
                        updateDescription(selectedTaskTypes.toList())
                    } else {
                        descriptionPane.text = if (allowMultipleSelection) {
                            "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
                        } else {
                            "<html><body><p>Select a task type to see its description</p></body></html>"
                        }
                    }
                }
            })

            // Add double-click listener to select and OK
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = taskTree.getPathForLocation(e.x, e.y)
                        if (path != null) {
                            val node = path.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is TaskTypeNode) {
                                selectedTaskTypes.clear()
                                selectedTaskTypes.add(userObject.taskType)
                                isQuickSelect = true
                                doOKAction()
                                e.consume()
                            }
                        }
                    }
                }
            })
        }

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateTreeModel(searchField.text)
            }
        })

        updateTreeModel("")

        init()
        title = if (allowMultipleSelection) "Select Task Types" else "Select Task Type"
    }

    override fun getDimensionServiceKey(): String = "TaskTypeSelectionDialog"
    private fun updateTreeModel(filter: String) {
        val root = DefaultMutableTreeNode("Task Types")
        val filterText = filter.trim().lowercase()
        val tasksByPackage = TaskType.values()
            .filter {
                if (filterText.isEmpty()) true
                else it.name.lowercase().contains(filterText) ||
                        (it.description?.lowercase()?.contains(filterText) == true) ||
                        it.category.lowercase().contains(filterText)
            }
            .groupBy { it.category }
            .toSortedMap()
        tasksByPackage.forEach { (packageName, tasks) ->
            val packageNode = DefaultMutableTreeNode(packageName)
            root.add(packageNode)
            tasks.sortedBy { it.name }.forEach { taskType ->
                val taskNode = DefaultMutableTreeNode(TaskTypeNode(taskType))
                packageNode.add(taskNode)
            }
        }
        val model = DefaultTreeModel(root)
        taskTree.model = model
        // Expand all package nodes
        for (i in 0 until root.childCount) {
            taskTree.expandPath(TreePath(arrayOf(root, root.getChildAt(i))))
        }
    }


    private fun updateDescription(taskTypes: List<TaskType<*, *>>) {
        if (taskTypes.isEmpty()) {
            descriptionPane.text = if (allowMultipleSelection) {
                "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
            } else {
                "<html><body><p>Select a task type to see its description</p></body></html>"
            }
            return
        }

        if (taskTypes.size == 1) {
            val taskType = taskTypes[0]
            descriptionPane.text = buildString {
                this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
                this.append("<h3 style='margin-top: 0;'>${taskType.name}</h3>")
                this.append("<p><b>Description:</b> ${taskType.description ?: "No description available"}</p>")
                taskType.tooltipHtml?.let { html ->
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
        } else {
            // Multiple tasks selected - show summary
            descriptionPane.text = buildString {
                this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
                this.append("<h3 style='margin-top: 0;'>${taskTypes.size} Tasks Selected</h3>")
                this.append("<ul>")
                taskTypes.sortedBy { it.name }.forEach { taskType ->
                    this.append("<li><b>${taskType.name}</b>: ${taskType.description ?: "No description"}</li>")
                }
                this.append("</ul>")
                this.append("</body></html>")
            }
        }
        descriptionPane.caretPosition = 0
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(searchField).align(Align.FILL)
        }
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
        if (selectedTaskTypes.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                if (allowMultipleSelection) "Please select one or more task types" else "Please select a task type",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        super.doOKAction()
    }

    fun getSelectedTaskTypes(): List<TaskType<*, *>> = selectedTaskTypes.toList()

    @Deprecated("Use getSelectedTaskTypes() instead", ReplaceWith("getSelectedTaskTypes().firstOrNull()"))
    fun getSelectedTaskType(): TaskType<*, *>? = selectedTaskTypes.firstOrNull()

    private data class TaskTypeNode(val taskType: TaskType<*, *>) {
        override fun toString(): String = taskType.name
    }
}