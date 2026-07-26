package com.npk.kfv.broker

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JStacktracePanel
import com.npk.swing.BindingHelper.bindModel
import com.npk.swing.View
import com.npk.swing.jscrollpane
import com.npk.swing.jtable
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextPane

internal class TopicConfigurationPanel(override val viewModel: TopicConfigurationViewModel) : JPanel(BorderLayout()), View<TopicConfigurationViewModel> {

    init {
        val table = jtable(viewModel.tableModel) { table ->
            table.columnModel.getColumn(1).let { column ->
                column.minWidth = 120
                column.maxWidth = column.minWidth
            }
            table.selectionModel.addListSelectionListener { event ->
                if (!event.valueIsAdjusting) {
                    val selectedRow = table.selectedRow
                    if (selectedRow >= 0) {
                        viewModel.selectRow(table.convertRowIndexToModel(selectedRow))
                    } else {
                        viewModel.selectRow(selectedRow)
                    }
                }
            }
        }
        val documentationLabel = JTextPane().also {
            it.border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(ApplicationMessages["broker.topic.configuration.documentation"]),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            )
            it.contentType = "text/html"
            it.minimumSize = Dimension(0, 100)
            it.isEditable = false
            it.bindModel(viewModel, TopicConfigurationViewModel::documentation)
        }

        val splitPanel = JSplitPane(JSplitPane.VERTICAL_SPLIT, jscrollpane(table), documentationLabel)
        splitPanel.resizeWeight = 0.8
        add(splitPanel, BorderLayout.CENTER)

        viewModel.addPropertyChangeListener(ACTION_RELOAD_TOPIC_CONFIG_PROPERTY, ::onLoadConfiguration)
    }

    private fun onLoadConfiguration(event: PropertyChangeEvent) {
        val topicName = event.newValue.toString()
        val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
            panel.progressText = ApplicationMessages["broker.topic.configuration.process", topicName]
            panel.start()
        }
        viewModel.listTopicConfiguration(topicName) { result ->
            busyPanel.stop()
            result.onFailure { e ->
                JStacktracePanel.showMessageDialog(
                    this,
                    ApplicationMessages["broker.topic.configuration"],
                    ApplicationMessages["broker.topic.configuration.failure"],
                    e,
                    false
                )
            }
        }
    }

}