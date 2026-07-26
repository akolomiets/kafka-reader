package com.npk.kfv.broker

import com.npk.swing.jcombobox
import org.apache.kafka.common.record.internal.CompressionType
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.plaf.basic.BasicComboBoxRenderer

internal const val ACTION_REPAINT_TOPICS_PROPERTY =         "action:repaintTopics"
internal const val ACTION_REPAINT_GROUPS_PROPERTY =         "action:repaintGroups"
internal const val ACTION_RELOAD_TOPICS_PROPERTY =          "action:reloadTopics"
internal const val ACTION_RELOAD_TOPIC_CONFIG_PROPERTY =    "action:reloadTopicConfiguration"
internal const val ACTION_RELOAD_TOPIC_ACL_PROPERTY =       "action:reloadTopicACL"
internal const val ACTION_RELOAD_GROUPS_PROPERTY =          "action:reloadGroups"
internal const val ACTION_VIEW_DETAILS_PROPERTY =           "action:viewDetails"
internal const val ACTION_VIEW_DETAILS_TOPIC_PROPERTY =     "action:viewDetailsTopic"
internal const val ACTION_VIEW_DETAILS_GROUP_PROPERTY =     "action:viewDetailsGroup"
internal const val ACTION_START_CONSUMING =                 "action:startConsuming"
internal const val ACTION_STOP_CONSUMING =                  "action:stopConsuming"

internal fun compressioncombobox(block: (JComboBox<CompressionType>) -> Unit = {}) = jcombobox(CompressionType.entries) {
    it.renderer = object : BasicComboBoxRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component =
            super.getListCellRendererComponent(list, (value as CompressionType).name, index, isSelected, cellHasFocus)
    }
    block(it)
}
