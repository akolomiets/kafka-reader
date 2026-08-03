package com.npk.kfv.broker

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TAB_TYPE
import com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TAB_TYPE_UNDERLINED
import com.formdev.flatlaf.icons.FlatSearchIcon
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.StopwatchTimer
import com.npk.kfv.UIHelper
import com.npk.kfv.components.JStacktracePanel
import com.npk.kfv.components.JStatusLabel
import com.npk.kfv.service.OffsetStrategy
import com.npk.kfv.service.SerializationType
import com.npk.swing.*
import com.npk.swing.BindingHelper.bind
import com.npk.swing.BindingHelper.bindModel
import com.npk.swing.BindingHelper.bindView
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.beans.PropertyChangeEvent
import java.time.Duration
import javax.swing.*
import javax.swing.table.TableColumn
import javax.swing.table.TableRowSorter
import javax.swing.text.DefaultCaret

internal class ConsumerPanel(override val viewModel: ConsumerViewModel) : JPanel(MigLayout("insets 0 0 10 0, gap 10")), View<ConsumerViewModel> {

    companion object {
        private const val PROTOTYPE_DISPLAY_VALUE = "XXXXXXXXXXXXXXXXXX"
    }

    private class TableColumnData(
        val column: TableColumn,
        val index: Int,
        var visible: Boolean
    )

    private val topicComboBox = JComboBox(viewModel.topicsComboBoxModel).also {
        it.prototypeDisplayValue = PROTOTYPE_DISPLAY_VALUE
        it.bind(viewModel, ConsumerViewModel::topic)
        AutoCompleteDecorator.decorate(it)
    }
    private val keyTypeComboBox = jcombobox(SerializationType.entries) { it.bind(viewModel, ConsumerViewModel::keyType) }
    private val valueTypeComboBox = jcombobox(SerializationType.entries) { it.bind(viewModel, ConsumerViewModel::valueType) }
    private val groupComboBox = JComboBox(viewModel.groupsComboBoxModel).also {
        it.prototypeDisplayValue = PROTOTYPE_DISPLAY_VALUE
        it.isEditable = true
        it.bind(viewModel, ConsumerViewModel::group)
        AutoCompleteDecorator.decorate(it)
    }
    private val autoCommitCheckbox = jcheckbox(ApplicationMessages["broker.consumer.autoCommit"]) {
        it.isEnabled = viewModel.group.isNotBlank()
        it.bind(viewModel, ConsumerViewModel::autoCommit)
    }
    private val startFromComboBox = jcombobox(OffsetStrategy.entries) { it.bind(viewModel, ConsumerViewModel::startFrom) }
    private val partitionsTextField = jtextfield { it.bind(viewModel, ConsumerViewModel::partitions) }

    private val consumedRecordsTable = jtable(viewModel.recordsTableModel) { table ->
        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selectedRow = table.selectedRow
                val enabled = if (selectedRow >= 0) {
                    viewModel.selectRecordsTableRow(table.convertRowIndexToModel(table.selectedRow))
                    true
                } else {
                    viewModel.selectRecordsTableRow(selectedRow)
                    false
                }

                formatValueComboBox.isEnabled = enabled
                exportValueButton.isEnabled = enabled
                formatKeyComboBox.isEnabled = enabled
                exportKeyButton.isEnabled = enabled
            }
        }
    }
    private val consumedRecordsTableColumns = Iterable { consumedRecordsTable.columnModel.columns.iterator() }.map { TableColumnData(it, it.modelIndex, true) }

    private val formatValueComboBox = jcombobox(ConsumerViewModel.FormatStrategy.entries) {
        it.isEnabled = false
        it.bindView(viewModel, ConsumerViewModel::recordValueFormatStrategy)
    }
    private val exportValueButton = jbutton(ApplicationImages.Icons.Export.icon, ::onSaveValueActionPerformed) {
        it.isEnabled = false
        it.isFocusable = false
        it.toolTipText = ApplicationMessages["broker.consumer.details.value.export"]
        it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    }

    private val formatKeyComboBox = jcombobox(ConsumerViewModel.FormatStrategy.entries) {
        it.isEnabled = false
        it.bindView(viewModel, ConsumerViewModel::recordKeyFormatStrategy)
    }
    private val exportKeyButton = jbutton(ApplicationImages.Icons.Export.icon, ::onSaveKeyActionPerformed) {
        it.isEnabled = false
        it.isFocusable = false
        it.toolTipText = ApplicationMessages["broker.consumer.details.key.export"]
        it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    }

    private val consumeButton = jbutton(ApplicationMessages["broker.consumer.startConsume"], ApplicationImages.Icons.Run.icon, ::onConsumeActionPerformed) {
        it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        it.isFocusable = false
    }

    private val consumingLabel = jlabel("")
    private val consumingStopwatchTimer = StopwatchTimer { seconds ->
        val hh = seconds / 3600
        val mm = (seconds % 3600) / 60
        val ss = seconds % 60
        consumingLabel.text = ApplicationMessages["broker.consumer.consuming", "%02d:%02d:%02d".format(hh, mm, ss)]
    }

    private val statusLabel = JStatusLabel()

    init {
        add(mainPanel(), "push, grow, wrap")
        add(consumeButton, "split")
        add(consumingLabel, "gapright 10")
        add(statusLabel, "growx, gapright 10")

        viewModel.addPropertyChangeListener(::propertyChangeListener)
    }

    private fun mainPanel() = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        resizeWeight = 0.7
        leftComponent = consumedRecordsPanel()
        rightComponent = consumedRecordDetailsPanel()
    }

    private fun consumedRecordsPanel() = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        leftComponent = jpanel(MigLayout("insets 10, wrap 1", "[fill,grow]", "[]5[]")) {
            it.preferredSize = Dimension(50, 0)
            +jlabel(ApplicationMessages["broker.consumer.topic"])
            +(topicComboBox to "wrap 10")
            +jlabel(ApplicationMessages["broker.consumer.key.type"])
            +(keyTypeComboBox to "wrap 10")
            +jlabel(ApplicationMessages["broker.consumer.value.type"])
            +(valueTypeComboBox to "wrap 10")
            +jlabel(ApplicationMessages["broker.consumer.group"])
            +(groupComboBox to "wrap 10")
            +(autoCommitCheckbox to "wrap 10")
            +jlabel(ApplicationMessages["broker.consumer.start.from"])
            +(startFromComboBox to "wrap 10")
            +jlabel(ApplicationMessages["broker.consumer.partitions"])
            +partitionsTextField
        }
        rightComponent = jpanel(BorderLayout()) {
            val toolbar = jtoolbar {
                val filterColumnsComboBox = jcombobox(listOf(
                    ApplicationMessages["table.topic.records[1]"],
                    ApplicationMessages["table.topic.records[2]"],
                    ApplicationMessages["table.topic.records[3]"],
                    ApplicationMessages["table.topic.records[4]"],
                    ApplicationMessages["table.topic.records[5]"],
                    ApplicationMessages["table.topic.records[6]"]
                )) { it.maximumSize = it.preferredSize }

                val filterTextField = jtextfield {
                    placeholderText = ApplicationMessages["broker.consumer.records.search"]
                    leadingIcon = FlatSearchIcon()
                    showClearButton = true
                    clearButtonCallback = { textComponent ->
                        textComponent.text = ""
                        textComponent.postActionEvent()
                    }
                    it.columns = 32
                    it.maximumSize = it.preferredSize
                }

                +jlabel(ApplicationMessages["broker.consumer.records.filter"])
                it.add(Box.createHorizontalStrut(10))
                +filterColumnsComboBox.also {
                    it.addItemListener { event ->
                        if (event.stateChange == ItemEvent.SELECTED) {
                            setTableFilter(filterColumnsComboBox.selectedItem?.toString().orEmpty(), filterTextField.text)
                        }
                    }
                }
                +filterTextField.also {
                    it.addActionListener { event ->
                        setTableFilter(filterColumnsComboBox.selectedItem?.toString().orEmpty(), filterTextField.text)
                    }
                }
                it.add(Box.createHorizontalGlue())
                +jbutton(ApplicationImages.Icons.Table.icon, ::onTableMenuActionPerformed) {
                    it.toolTipText = ApplicationMessages["broker.consumer.records.columns"]
                }
                +jbutton(ApplicationImages.Icons.Delete.icon, ::onTableCleanActionPerformed) {
                    it.toolTipText = ApplicationMessages["broker.consumer.records.clear"]
                }
            }
            +(toolbar to BorderLayout.NORTH)
            +(jscrollpane(consumedRecordsTable) to BorderLayout.CENTER)
        }
    }

    private fun consumedRecordDetailsPanel() = JTabbedPane().apply {
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)
        putClientProperty(TABBED_PANE_TAB_TYPE, TABBED_PANE_TAB_TYPE_UNDERLINED)

        minimumSize = Dimension(300, 0)

        addTab(ApplicationMessages["broker.consumer.details.value"], valuePanel())
        addTab(ApplicationMessages["broker.consumer.details.key"], keyPanel())
        addTab(ApplicationMessages["broker.consumer.details.headers"], headersPanel())
        addTab(ApplicationMessages["broker.consumer.details.metadata"], metadataPanel())
    }

    private fun valuePanel() = jpanel(MigLayout("insets 8 7 0 8, gap 10")) {
        +(jlabel(ApplicationMessages["broker.consumer.details.format"]) to "gapleft 10, split 2")
        +formatValueComboBox
        +(exportValueButton to "alignx right, wrap")

        val textArea = jtextarea {
            it.isEditable = false
            (it.caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
            it.bindModel(viewModel, ConsumerViewModel::recordValue)
        }
        +(jscrollpane(textArea) to "span, push, grow")
    }

    private fun keyPanel() = jpanel(MigLayout("insets 8 7 0 8, gap 10")) {
        +(jlabel(ApplicationMessages["broker.consumer.details.format"]) to "gapleft 10, split 2")
        +formatKeyComboBox
        +(exportKeyButton to "alignx right, wrap")

        val textArea = jtextarea {
            it.isEditable = false
            (it.caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
            it.bindModel(viewModel, ConsumerViewModel::recordKey)
        }
        +(jscrollpane(textArea) to "span, push, grow")
    }

    private fun headersPanel(): JComponent {
        val textArea = jtextarea {
            it.isEditable = false
            (it.caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
            it.bindModel(viewModel, ConsumerViewModel::recordHeaders)
        }
        return jscrollpane(textArea) { it.border = BorderFactory.createEmptyBorder(10, 9, 0, 10) }
    }

    private fun metadataPanel() = jpanel(MigLayout("insets 10, gap 10, wrap 2", "[][fill,grow]")) {
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.topic"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordTopic)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.partition"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordPartition)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.offset"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordOffset)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.timestamp"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordTimestamp)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.key.size"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordKeySize)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.value.size"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordValueSize)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.key.type"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordKeyType)
        }
        +jlabel(ApplicationMessages["broker.consumer.details.metadata.value.type"])
        +jtextfield {
            it.isEditable = false
            it.bindModel(viewModel, ConsumerViewModel::recordValueType)
        }
    }

    private fun propertyChangeListener(event: PropertyChangeEvent) {
        when (event.propertyName) {
            ConsumerViewModel::group.name -> {
                (!(event.newValue as String?).isNullOrEmpty()).let { enabled ->
                    autoCommitCheckbox.isEnabled = enabled
                    autoCommitCheckbox.isSelected = autoCommitCheckbox.isSelected && enabled
                }
            }
            ACTION_START_CONSUMING -> {
                setConsumerConfigComponentsEnabled(false)
                consumeButton.icon = ApplicationImages.Icons.Stop.icon
                consumeButton.text = ApplicationMessages["broker.consumer.stopConsume"]
                consumingStopwatchTimer.start()
            }
            ACTION_STOP_CONSUMING -> {
                setConsumerConfigComponentsEnabled(true)
                consumeButton.icon = ApplicationImages.Icons.Run.icon
                consumeButton.text = ApplicationMessages["broker.consumer.startConsume"]
                consumingStopwatchTimer.stop()
            }
        }
    }

    private fun onTableMenuActionPerformed(event: ActionEvent) {
        val popup = JPopupMenu()

        consumedRecordsTableColumns.forEach { columnData ->
            val menuItem = JCheckBoxMenuItem(columnData.column.headerValue.toString(), columnData.visible)
            menuItem.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", true)
            menuItem.addItemListener {
                columnData.visible = !columnData.visible
                if (columnData.visible) {
                    consumedRecordsTableColumns.forEachIndexed { index, item ->
                        if (index >= columnData.index) {
                            consumedRecordsTable.removeColumn(item.column)
                        }
                    }
                    consumedRecordsTableColumns.forEachIndexed { index, item ->
                        if (index >= columnData.index && item.visible) {
                            consumedRecordsTable.addColumn(item.column)
                        }
                    }
                } else {
                    consumedRecordsTable.removeColumn(columnData.column)
                }
            }
            popup.add(menuItem)
        }

        val component = event.source as JComponent
        popup.show(component, 0, component.height)
    }

    private fun onTableCleanActionPerformed(event: ActionEvent) {
        viewModel.clearTableData()
    }

    private fun onConsumeActionPerformed(event: ActionEvent) {
        try {
            if (!viewModel.isConsuming) {
                viewModel.startConsuming()
            } else {
                viewModel.stopConsuming()
            }
        } catch (e: Exception) {
            JStacktracePanel.showMessageDialog(this, ApplicationMessages["broker.consumer"], null, e)
        }
    }

    private fun onSaveValueActionPerformed(event: ActionEvent) {
        val fileChooser = UIHelper.createAnySaveFileChooser()
        if (fileChooser.showSaveDialog(SwingUtilities.windowForComponent(this)) == JFileChooser.APPROVE_OPTION) {
            viewModel.saveValueToFile(fileChooser.selectedFile) { result ->
                result
                    .onSuccess {
                        statusLabel.information(ApplicationMessages["broker.consumer.details.export.success", fileChooser.selectedFile.name], Duration.ofSeconds(10))
                    }
                    .onFailure { e ->
                        statusLabel.failure(ApplicationMessages["broker.consumer.details.export.failure"], e)
                    }
            }
        }
    }

    private fun onSaveKeyActionPerformed(event: ActionEvent) {
        val fileChooser = UIHelper.createAnySaveFileChooser()
        if (fileChooser.showSaveDialog(SwingUtilities.windowForComponent(this)) == JFileChooser.APPROVE_OPTION) {
            viewModel.saveKeyToFile(fileChooser.selectedFile) { result ->
                result
                    .onSuccess {
                        statusLabel.information(ApplicationMessages["broker.consumer.details.export.success", fileChooser.selectedFile.name], Duration.ofSeconds(10))
                    }
                    .onFailure { e ->
                        statusLabel.failure(ApplicationMessages["broker.consumer.details.export.failure"], e)
                    }
            }
        }
    }

    private fun setTableFilter(columnName: String, columnValue: String) {
        @Suppress("UNCHECKED_CAST")
        val rowSorter = consumedRecordsTable.rowSorter as TableRowSorter<KafkaConsumerRecordsTableModel>
        if (columnName.isEmpty() || columnValue.isEmpty()) {
            if (rowSorter.rowFilter != null) {
                rowSorter.rowFilter = null
            }
        } else {
            val columnIndex = consumedRecordsTableColumns.find { columnName == it.column.headerValue }?.index
            rowSorter.rowFilter = when (columnIndex) {
                // in list
                1, /* Partition */
                2 /* Offset */ -> {
                    val values: Set<String> = columnValue
                        .split(',', ';')
                        .filter { it.isNotBlank() }
                        .mapTo(mutableSetOf()) { it.trim() }
                    object : RowFilter<KafkaConsumerRecordsTableModel, Int>() {
                        override fun include(entry: Entry<out KafkaConsumerRecordsTableModel, out Int>): Boolean {
                            val value = entry.model.getValueAt(entry.identifier, columnIndex).toString()
                            return value in values
                        }
                    }
                }
                // contains
                0, /* Topic */
                3, /* Timestamp */
                4, /* Key */
                5 /* Value */ -> object : RowFilter<KafkaConsumerRecordsTableModel, Int>() {
                    override fun include(entry: Entry<out KafkaConsumerRecordsTableModel, out Int>): Boolean {
                        val value = entry.model.getValueAt(entry.identifier, columnIndex).toString()
                        return value.contains(columnValue, true)
                    }
                }
                else -> null
            }
        }
    }

    private fun setConsumerConfigComponentsEnabled(enabled: Boolean) {
        topicComboBox.isEnabled = enabled
        keyTypeComboBox.isEnabled = enabled
        valueTypeComboBox.isEnabled = enabled
        groupComboBox.isEnabled = enabled
        autoCommitCheckbox.isEnabled = enabled && viewModel.group.isNotBlank()
        startFromComboBox.isEnabled = enabled
        partitionsTextField.isEnabled = enabled
    }

}
