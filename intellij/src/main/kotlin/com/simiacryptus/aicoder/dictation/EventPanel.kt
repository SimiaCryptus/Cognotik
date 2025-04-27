package com.simiacryptus.aicoder.dictation

import com.simiacryptus.jopenai.audio.TranscriptionProcessor
import java.awt.*
import javax.swing.*

class EventPanel : JPanel() {
    companion object {
        private const val MAX_RECORDS = 100
    }

    init {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(15, 15, 15, 15)



        val listModel = DefaultListModel<TranscriptionProcessor.TranscriptionResult>()
        val transcriptionList = JList(listModel)
        transcriptionList.setCellRenderer(object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val result = value as TranscriptionProcessor.TranscriptionResult
                text = result.text
                return this
            }
        })
        val listScrollPane = JScrollPane(transcriptionList)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {

            add(JButton("Clear History").apply {
                addActionListener {
                    listModel.clear()
                }
            })
        }

        val transcriptionPanel = JPanel(GridLayout(0, 1, 5, 5)).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(180, 180, 180), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
                ),
                "Transcription Info"
            ).apply {
                titleFont = Font("Segoe UI", Font.BOLD, 16)
                titleColor = Color(60, 60, 60)
            }


            val details = JPanel(GridBagLayout()).apply {
                border = BorderFactory.createTitledBorder("Details")

            }
            val gbc = GridBagConstraints()
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.weightx = 0.0
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.anchor = GridBagConstraints.WEST

            details.add(JLabel("Text:", JLabel.RIGHT), gbc)
            gbc.gridy++
            details.add(JLabel("Prompt:", JLabel.RIGHT), gbc)
            gbc.gridy++
            details.add(JLabel("Processing Time (ms):", JLabel.RIGHT), gbc)
            gbc.gridy++
            details.add(JLabel("Audio Duration (s):", JLabel.RIGHT), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets.left = 10
            val textValue = JTextArea().apply {
                lineWrap = true
                wrapStyleWord = true
                isEditable = false



                border = BorderFactory.createLineBorder(Color(200, 200, 200))
            }
            val promptValue = JTextArea().apply {
                lineWrap = true
                wrapStyleWord = true
                isEditable = false

                foreground = Color.BLACK
                border = BorderFactory.createLineBorder(Color(200, 200, 200))
            }
            val processingTimeValue = JLabel()
            val durationValue = JLabel()
            gbc.gridy = 0
            details.add(JScrollPane(textValue).apply {
                preferredSize = Dimension(300, 100)
            }, gbc)
            gbc.gridy++
            details.add(JScrollPane(promptValue).apply {
                preferredSize = Dimension(300, 100)
            }, gbc)
            gbc.gridy++
            details.add(processingTimeValue, gbc)
            gbc.gridy++
            details.add(durationValue, gbc)

            val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, details).apply {
                dividerLocation = 200
                resizeWeight = 0.3
            }
            add(splitPane)

            details.putClientProperty("textValue", textValue)
            details.putClientProperty("promptValue", promptValue)
            details.putClientProperty("processingTimeValue", processingTimeValue)
            details.putClientProperty("durationValue", durationValue)
        }

        transcriptionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selected = transcriptionList.selectedValue
                selected?.let { result ->

                    val details = transcriptionPanel.components.first { it is JSplitPane }
                        .let { (it as JSplitPane).rightComponent as JPanel }
                    val textValue = details.getClientProperty("textValue") as JTextArea
                    val promptValue = details.getClientProperty("promptValue") as JTextArea
                    val processingTimeValue = details.getClientProperty("processingTimeValue") as JLabel
                    val durationValue = details.getClientProperty("durationValue") as JLabel

                    textValue.text = result.text
                    promptValue.text = result.prompt ?: "N/A"
                    processingTimeValue.text = "${result.processingTime}"
                    durationValue.text = String.format("%.2f", result.packet.duration)
                }
            }
        }
        add(transcriptionPanel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)

        DictationState.transctiption.addListener {
            val result = DictationState.recentTranscriptionResult ?: return@addListener
            SwingUtilities.invokeLater {

                if (listModel.size >= MAX_RECORDS) {
                    listModel.remove(0)
                }
                listModel.addElement(result)

                transcriptionList.selectedIndex = listModel.size() - 1
                transcriptionList.ensureIndexIsVisible(listModel.size() - 1)
            }
        }
    }

}