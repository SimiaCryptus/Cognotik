package com.simiacryptus.cognotik.config

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.util.BrowseUtil
import org.jdesktop.swingx.JXTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.net.URI
import java.util.*
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class UsageTable(
    val usage: UsageInterface
) : JPanel(BorderLayout()) {

    private val buttonPanel = JPanel()
    val columnNames = arrayOf("Model", "Prompt", "Completion", "Cost")

    val rowData by lazy {
      val usageData = usage.getUserUsageSummary(AppSettingsState.localUser).map { entry ->
            listOf(
                entry.key,
                entry.value.prompt_tokens.toString(),
                entry.value.completion_tokens.toString(),
                String.format("%.2f", entry.value.cost)
            ).toMutableList()
        }

        val totalPromptTokens = usageData.sumOf { it[1].toInt() }
        val totalCompletionTokens = usageData.sumOf { it[2].toInt() }
        val totalCost = usageData.sumOf { it[3].toDouble() }

        (usageData + listOf(
            listOf(
                "TOTAL",
                totalPromptTokens.toString(),
                totalCompletionTokens.toString(),
                String.format("%.2f", totalCost)
            ).toMutableList()
        )).toMutableList()
    }

    private val dataModel by lazy {
        object : AbstractTableModel() {
            init {
                checkUsageThreshold()
            }

            override fun getColumnName(column: Int): String {
                return columnNames.get(column)
            }

            override fun getValueAt(row: Int, col: Int): Any {
                return rowData[row][col]
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {

                return row != rowData.size - 1
            }

            override fun getRowCount(): Int {
                return rowData.size
            }

            override fun getColumnCount(): Int {
                return columnNames.size
            }

            override fun setValueAt(value: Any, row: Int, col: Int) {

                if (row == rowData.size - 1) return
                val strings = rowData[row]
                strings[col] = value.toString()
                fireTableCellUpdated(row, col)
                checkUsageThreshold()
            }

        }
    }

    private val jtable by lazy { JBTable(dataModel) }

    private val scrollpane by lazy { JBScrollPane(jtable) }

    private val clearButton by lazy {
        JButton(object : AbstractAction("Clear") {
            override fun actionPerformed(e: ActionEvent?) {
                rowData.clear()
                usage.clear()
                this@UsageTable.parent.invalidate()
            }
        })
    }

    init {

        val totalRowRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                if (row == table?.model?.rowCount?.minus(1)) {
                    font = font.deriveFont(font.style or java.awt.Font.BOLD)
                }
                return c
            }
        }

        jtable.columnModel.getColumn(0).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(1).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(3).cellRenderer = DefaultTableCellRenderer()

        for (i in 0..3) {
            val column = jtable.columnModel.getColumn(i)
            column.cellRenderer = totalRowRenderer
        }

        val editor = object : JXTable.GenericEditor() {
            override fun isCellEditable(anEvent: EventObject?) = false
        }
        jtable.columnModel.getColumn(0).cellEditor = editor
        jtable.columnModel.getColumn(1).cellEditor = editor
        jtable.columnModel.getColumn(2).cellEditor = editor
        jtable.columnModel.getColumn(3).cellEditor = editor

        jtable.columnModel.getColumn(0).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(1).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(2).headerRenderer = DefaultTableCellRenderer()
        jtable.columnModel.getColumn(3).headerRenderer = DefaultTableCellRenderer()

        initCol(0)
        initCol(1)
        initCol(2)
        initCol(3)

        jtable.tableHeader.defaultRenderer = DefaultTableCellRenderer()

        add(scrollpane, BorderLayout.CENTER)
        buttonPanel.add(clearButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun checkUsageThreshold() {
        val settings = AppSettingsState.instance
        if (settings.feedbackOptOut || settings.feedbackRequested) {
            return
        }
        val totalTokens = rowData.dropLast(1).sumOf {
            (it[1].toIntOrNull() ?: 0) + (it[2].toIntOrNull() ?: 0)
        }
        if (totalTokens >= 1000000) {
            settings.feedbackRequested = true
            showFeedbackNotification()
        }
    }

    private fun showFeedbackNotification() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "You're Making Great Progress with Cognotik! 🚀",
                "You've processed over 10,000 tokens! Your feedback helps shape the future of this open-source tool. Share your experience?",
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("It's Great! 🎉") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                showReviewRequest()
            }
        })
        notification.addAction(object : AnAction("I Have Feedback 💭") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                showFeedbackDialog()
            }
        })
        notification.addAction(object : AnAction("Don't Ask Again") {
            override fun actionPerformed(e: AnActionEvent) {
                AppSettingsState.instance.feedbackOptOut = true
                notification.expire()
            }
        })
        notification.notify(null)
    }

    private fun showReviewRequest() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "Help Other Developers Discover Cognotik! 🌟",
                "Your positive experience can help others find this tool. Would you consider leaving a quick review? It takes less than a minute and makes a huge difference!",
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("Leave a Review ⭐") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://plugins.jetbrains.com/plugin/27289-cognotik/reviews"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Maybe Later") {
            override fun actionPerformed(e: AnActionEvent) {
                AppSettingsState.instance.feedbackRequested = false
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Share Feedback Instead 💬") {
            override fun actionPerformed(e: AnActionEvent) {
                showFeedbackDialog()
                notification.expire()
            }
        })
        notification.notify(null)
    }

    private fun showFeedbackDialog() {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Cognotik Feedback")
            .createNotification(
                "Your Feedback Shapes Cognotik's Future! 💡",
                """
                As an open-source project, your input directly influences our development priorities. Here's how you can contribute:
                
                💬 Share Your Thoughts:
                • Which features are most valuable to you?
                • What workflows could be smoother?
                • What new capabilities would you like to see?
                
                🔧 Troubleshooting Tips:
                • Review our documentation for setup guidance
                • Double-check your API key configuration
                • Experiment with different models for your use case
                • Adjust temperature and other model parameters
                
                Together, we're building better AI-powered development tools!
                """.trimIndent(),
                NotificationType.INFORMATION
            )
        notification.addAction(object : AnAction("Report an Issue 🐛") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik/issues/new"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Share Ideas 💬") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik/discussions/new?category=feedback"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Browse Docs 📚") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowseUtil.browse(URI("https://github.com/SimiaCryptus/Cognotik#readme"))
                notification.expire()
            }
        })
        notification.addAction(object : AnAction("Not Now") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
            }
        })
        notification.notify(null)
    }


    private fun initCol(idx: Int) {
        val headerRenderer = jtable.tableHeader.defaultRenderer
        val headerValue = jtable.columnModel.getColumn(idx).headerValue
        val headerComp = headerRenderer.getTableCellRendererComponent(jtable, headerValue, false, false, 0, idx)
        jtable.columnModel.getColumn(idx).preferredWidth = headerComp.preferredSize.width
    }

}