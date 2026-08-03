package com.npk.kfv.broker

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JStacktracePanel
import com.npk.swing.View
import com.npk.swing.jscrollpane
import com.npk.swing.jtable
import java.awt.BorderLayout
import java.beans.PropertyChangeEvent
import javax.swing.JPanel

internal class TopicACLPanel(override val viewModel: TopicACLViewModel) : JPanel(BorderLayout()), View<TopicACLViewModel> {

    init {
        val table = jtable(viewModel.tableModel) { table ->
            (2 .. 3).forEach { index ->
                table.columnModel.getColumn(index).let {
                    it.minWidth = viewModel.tableModel.getColumnWidth(index)
                    it.maxWidth = viewModel.tableModel.getColumnWidth(index)
                    it.resizable = false
                }
            }
        }
        add(jscrollpane(table), BorderLayout.CENTER)
        viewModel.addPropertyChangeListener(ACTION_RELOAD_TOPIC_ACL_PROPERTY, ::onLoadACL)
    }

    private fun onLoadACL(event: PropertyChangeEvent) {
        val topicName = event.newValue.toString()
        val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
            panel.progressText = ApplicationMessages["broker.topic.acl.process", topicName]
            panel.start()
        }
        viewModel.listTopicACL(topicName) { result ->
            busyPanel.stop()
            result.onFailure { e ->
                JStacktracePanel.showMessageDialog(
                    this,
                    ApplicationMessages["broker.topic.acl"],
                    ApplicationMessages["broker.topic.acl.failure"],
                    e,
                    false
                )
            }
        }
    }

}