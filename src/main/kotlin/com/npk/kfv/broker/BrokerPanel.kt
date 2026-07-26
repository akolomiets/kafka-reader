package com.npk.kfv.broker

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JCardPanel
import com.npk.kfv.components.JStacktracePanel
import com.npk.kfv.components.JStatusLabel
import com.npk.kfv.service.OffsetStrategy
import com.npk.swing.*
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.*
import javax.swing.tree.*

class BrokerPanel(override val viewModel: BrokerPanelViewModel) : JSplitPane(JSplitPane.HORIZONTAL_SPLIT), View<BrokerPanelViewModel> {

    companion object {
        private const val NO_DATA_PANEL =           "NoDataPanel"
        private const val TOPICS_PANEL =            "TopicsPanel"
        private const val TOPIC_DETAILS_PANEL =     "TopicDetailsPanel"
        private const val GROUPS_PANEL =            "GroupsPanel"
        private const val GROUP_DETAILS_PANEL =     "GroupDetailsPanel"

        private const val TOPIC_PARTITIONS_PANEL =  "TopicPartitionsPanel"
        private const val TOPIC_CONFIG_PANEL =      "TopicConfigPanel"
        private const val TOPIC_ACL_PANEL =         "TopicACLPanel"
        private const val TOPIC_CONSUMER_PANEL =    "TopicConsumerPanel"
        private const val TOPIC_PRODUCER_PANEL =    "TopicProducerPanel"
    }

    private class SimpleMutableTreeNode(userObject: Any, allowsChildren: Boolean = true) : DefaultMutableTreeNode(userObject, allowsChildren) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return Objects.equals(this.toString(), other.toString())
        }
        override fun hashCode(): Int {
            return toString().hashCode()
        }
    }

    private enum class RootTreeNode(val text: String) {
        Topics(ApplicationMessages["broker.topics"]),
        Groups(ApplicationMessages["broker.groups"])
    }

    private val topicAddToFavoriteButton = jtogglebutton(ApplicationImages.Icons.Star.icon, ::onToggleTopicFavorite) { it.toolTipText = ApplicationMessages["broker.addToFavorite"] }
    private val topicCreateButton = jbutton(ApplicationImages.Icons.Plus.icon, ::onCreateTopic) { it.toolTipText = ApplicationMessages["broker.createTopic"] }
    private val topicDeleteButton = jbutton(ApplicationImages.Icons.Minus.icon, ::onDeleteTopic) { it.toolTipText = ApplicationMessages["broker.deleteTopic"] }
    private val topicClearButton = jbutton(ApplicationImages.Icons.Clear.icon, ::onClearTopic) { it.toolTipText = ApplicationMessages["broker.clearTopic"] }
    private val groupAddToFavoriteButton = jtogglebutton(ApplicationImages.Icons.Star.icon, ::onToggleGroupFavorite) { it.toolTipText = ApplicationMessages["broker.addToFavorite"] }
    private val groupResetOffsetButton = jbutton(ApplicationImages.Icons.Reset.icon, ::onResetOffset) { it.toolTipText = ApplicationMessages["broker.resetOffset"] }
    private val groupDeleteButton = jbutton(ApplicationImages.Icons.Minus.icon, ::onDeleteGroup) { it.toolTipText = ApplicationMessages["broker.deleteGroup"] }

    private val kafkaBrokerDataTree = JTree()
    private val kafkaBrokerDataCard = JCardPanel()
    private val kafkaTopicDataCard = JCardPanel()

    private val reloadStatusLabel = JStatusLabel(false)

    init {
        constructUI()
        initListeners()
    }

    private fun constructUI() {
        val topicsPanel = jpanel(MigLayout("insets 0, gap 0")) {
            it.minimumSize = Dimension(180, 0)
            it.preferredSize = Dimension(280, 0)

            val kafkaBrokersListToolBar = jtoolbar {
                +topicAddToFavoriteButton
                +topicCreateButton
                +topicDeleteButton
                +topicClearButton
                +groupAddToFavoriteButton
                +groupResetOffsetButton
                +groupDeleteButton
                disableToolbarButtons()
            }
            +(kafkaBrokersListToolBar to "wrap")

            kafkaBrokerDataTree.apply {
                isRootVisible = false
                showsRootHandles = true
                expandsSelectedPaths = true
                cellRenderer = object : DefaultTreeCellRenderer() {
                    override fun getTreeCellRendererComponent(tree: JTree, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
                        return if (value is SimpleMutableTreeNode) {
                            val userObject = value.getUserObject()
                            if (userObject is RootTreeNode) {
                                super.getTreeCellRendererComponent(tree, userObject.text, sel, expanded, leaf, row, hasFocus)
                            } else {
                                when (val parentUserObject = (value.parent as? SimpleMutableTreeNode)?.userObject) {
                                    is RootTreeNode -> {
                                        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus).also {
                                            val isFavorite = when (parentUserObject) {
                                                RootTreeNode.Topics -> viewModel.isTopicFavorite(value.userObject.toString())
                                                RootTreeNode.Groups -> viewModel.isGroupFavorite(value.userObject.toString())
                                            }
                                            if (isFavorite) {
                                                (it as DefaultTreeCellRenderer).icon = ApplicationImages.Icons.Star.icon
                                            }
                                        }
                                    }
                                    else -> super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                                }
                            }
                        } else {
                            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                        }
                    }
                }
                model = DefaultTreeModel(SimpleMutableTreeNode(viewModel.name))
                selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
                selectionModel.addTreeSelectionListener { event ->
                    event.path.let { path ->
                        if (path.pathCount > 1) {
                            val rootTreeNode = (path.getPathComponent(1) as SimpleMutableTreeNode).getUserObject() as RootTreeNode
                            val childNode = if (path.pathCount == 3) (path.lastPathComponent as SimpleMutableTreeNode).userObject as String else null

                            (rootTreeNode == RootTreeNode.Topics).let { visible ->
                                topicAddToFavoriteButton.isVisible = visible
                                topicCreateButton.isVisible = visible
                                topicDeleteButton.isVisible = visible
                                topicClearButton.isVisible = visible
                                if (visible) {
                                    topicCreateButton.isEnabled = true
                                    (path.pathCount > 2).let { enabled ->
                                        topicAddToFavoriteButton.isEnabled = enabled
                                        topicDeleteButton.isEnabled = enabled
                                        topicClearButton.isEnabled = enabled
                                    }
                                }
                            }
                            (rootTreeNode == RootTreeNode.Groups).let { visible ->
                                groupAddToFavoriteButton.isVisible = visible
                                groupResetOffsetButton.isVisible = visible
                                groupDeleteButton.isVisible = visible
                                if (visible) {
                                    (path.pathCount > 2).let { enabled ->
                                        groupAddToFavoriteButton.isEnabled = enabled
                                        groupResetOffsetButton.isEnabled = enabled
                                        groupDeleteButton.isEnabled = enabled
                                    }
                                }
                            }

                            when (rootTreeNode) {
                                RootTreeNode.Topics -> {
                                    val topicName = childNode.orEmpty()
                                    topicAddToFavoriteButton.isSelected = topicName.isNotEmpty() && viewModel.isTopicFavorite(topicName)
                                    viewModel.topic = topicName
                                    viewModel.group = ""
                                    kafkaBrokerDataCard.showCard(if (childNode == null) TOPICS_PANEL else TOPIC_DETAILS_PANEL)
                                }
                                RootTreeNode.Groups -> {
                                    val groupName = childNode.orEmpty()
                                    groupAddToFavoriteButton.isSelected = groupName.isNotEmpty() && viewModel.isGroupFavorite(groupName)
                                    viewModel.topic = ""
                                    viewModel.group = groupName
                                    kafkaBrokerDataCard.showCard(if (childNode == null) GROUPS_PANEL else GROUP_DETAILS_PANEL)
                                }
                            }
                        } else {
                            disableToolbarButtons()
                        }
                    }
                }
            }
            +(JScrollPane(kafkaBrokerDataTree) to "push, grow")
        }

        kafkaTopicDataCard.add(jscrollpane(jtable(viewModel.topicPartitionsTableModel)), TOPIC_PARTITIONS_PANEL)
        kafkaTopicDataCard.add(TopicConfigurationPanel(viewModel.topicConfigurationViewModel), TOPIC_CONFIG_PANEL)
        kafkaTopicDataCard.add(TopicACLPanel(viewModel.topicACLViewModel), TOPIC_ACL_PANEL)
        kafkaTopicDataCard.add(ConsumerPanel(viewModel.consumerViewModel), TOPIC_CONSUMER_PANEL)
        kafkaTopicDataCard.add(ProducerPanel(viewModel.producerViewModel), TOPIC_PRODUCER_PANEL)

        val kafkaTopicDetailsPanel = jpanel(BorderLayout()) {
            +(jtoolbar {
                it.orientation = JToolBar.VERTICAL
                it.putClientProperty(FlatClientProperties.STYLE, "hoverButtonGroupBackground: \$ToolBar.background")

                val partitionsButton = jtogglebutton(ApplicationImages.Icons.Items.icon, ::onPartitionsPanel) {
                    it.toolTipText = ApplicationMessages["broker.topic.partitions"]
                }
                val configurationButton = jtogglebutton(ApplicationImages.Icons.Config.icon, ::onConfigurationPanel) {
                    it.toolTipText = ApplicationMessages["broker.topic.configuration"]
                }
                val aclButton = jtogglebutton(ApplicationImages.Icons.ServiceAccount.icon, ::onACLPanel) {
                    it.toolTipText = ApplicationMessages["broker.topic.acl"]
                }
                val consumerButton = jtogglebutton(ApplicationImages.Icons.Consumer.icon, ::onConsumerPanel) { btn ->
                    btn.toolTipText = ApplicationMessages["broker.consumer"]
                    viewModel.addPropertyChangeListener { event ->
                        when (event.propertyName) {
                            ACTION_START_CONSUMING -> btn.icon = ApplicationImages.Icons.Consuming.icon
                            ACTION_STOP_CONSUMING -> btn.icon = ApplicationImages.Icons.Consumer.icon
                        }
                    }
                }
                val producerButton = jtogglebutton(ApplicationImages.Icons.Producer.icon, ::onProducerPanel) {
                    it.toolTipText = ApplicationMessages["broker.producer"]
                }

                +partitionsButton.also { it.doClick() }
                +configurationButton
                +aclButton
                it.addSeparator()
                +consumerButton
                +producerButton

                buttonGroup {
                    +partitionsButton
                    +configurationButton
                    +aclButton
                    +consumerButton
                    +producerButton
                }
            } to BorderLayout.WEST)
            +(kafkaTopicDataCard to BorderLayout.CENTER)
        }

        val noDataPanel = jpanel(GridBagLayout()) {
            val constraints = GridBagConstraints().apply { insets = Insets(10, 0, 0, 0) }

            val reloadButton = jbutton(ApplicationMessages["frame.reload"], ApplicationImages.Icons.Refresh.icon, ::onReloadData) {
                it.isFocusable = false
                it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            }

            +(jlabel(ApplicationMessages["broker.nodata"]) to constraints.apply { gridy++ })
            +(reloadButton to constraints.apply { gridy++ })
            +(reloadStatusLabel to constraints.apply { gridy++ })

            +(jlabel(ApplicationMessages.randomQuote) to constraints.apply { insets = Insets(20, 0, 0, 0); gridy++ })
        }

        kafkaBrokerDataCard.add(noDataPanel, NO_DATA_PANEL)
        kafkaBrokerDataCard.add(FilterTablePanel(viewModel.topicsFilterTableViewModel), TOPICS_PANEL)
        kafkaBrokerDataCard.add(kafkaTopicDetailsPanel, TOPIC_DETAILS_PANEL)
        kafkaBrokerDataCard.add(FilterTablePanel(viewModel.groupsFilterTableViewModel), GROUPS_PANEL)
        kafkaBrokerDataCard.add(jscrollpane(jtable(viewModel.groupDetailsTableModel)), GROUP_DETAILS_PANEL)

        add(topicsPanel)
        add(kafkaBrokerDataCard)
    }

    private fun initListeners() {
        viewModel.addPropertyChangeListener { event ->
            when (event.propertyName) {
                ACTION_REPAINT_TOPICS_PROPERTY,
                ACTION_REPAINT_GROUPS_PROPERTY -> {
                    // FIXME: Repaint node after mark/unmark node as favorite
                    // val rootNode = kafkaBrokerDataTree.model.root as SimpleMutableTreeNode
                    // val childNode = TODO - find modified node
                    // (kafkaBrokerDataTree.model as DefaultTreeModel).nodeChanged(childNode)
                    kafkaBrokerDataTree.repaint()
                }
                ACTION_RELOAD_TOPICS_PROPERTY -> reloadData(event.newValue as Boolean, false)
                ACTION_RELOAD_GROUPS_PROPERTY -> reloadData(false, event.newValue as Boolean)
                ACTION_VIEW_DETAILS_TOPIC_PROPERTY -> {
                    val rootNode = kafkaBrokerDataTree.model.root as SimpleMutableTreeNode
                    val topicNodePath = TreePath(arrayOf(rootNode, rootNode.firstChild, SimpleMutableTreeNode(event.newValue, false)))

                    kafkaBrokerDataTree.requestFocus()
                    kafkaBrokerDataTree.selectionPath = topicNodePath
                    kafkaBrokerDataTree.scrollPathToVisible(topicNodePath)
                }
                ACTION_VIEW_DETAILS_GROUP_PROPERTY -> {
                    val rootNode = kafkaBrokerDataTree.model.root as SimpleMutableTreeNode
                    val groupNodePath = TreePath(arrayOf(rootNode, rootNode.lastChild, SimpleMutableTreeNode(event.newValue, false)))

                    kafkaBrokerDataTree.requestFocus()
                    kafkaBrokerDataTree.selectionPath = groupNodePath
                    kafkaBrokerDataTree.scrollPathToVisible(groupNodePath)
                }
            }
        }
    }

    //region Actions

    fun onReloadData(event: ActionEvent) {
        reloadData(true, true)
    }

    private fun onPartitionsPanel(event: ActionEvent) {
        kafkaTopicDataCard.showCard(TOPIC_PARTITIONS_PANEL)
        viewModel.selectedTopicDataCard = TOPIC_PARTITIONS_PANEL
    }

    private fun onConfigurationPanel(event: ActionEvent) {
        kafkaTopicDataCard.showCard(TOPIC_CONFIG_PANEL)
        viewModel.selectedTopicDataCard = TOPIC_CONFIG_PANEL
    }

    private fun onACLPanel(event: ActionEvent) {
        kafkaTopicDataCard.showCard(TOPIC_ACL_PANEL)
        viewModel.selectedTopicDataCard = TOPIC_ACL_PANEL
    }

    private fun onConsumerPanel(event: ActionEvent) {
        kafkaTopicDataCard.showCard(TOPIC_CONSUMER_PANEL)
        viewModel.selectedTopicDataCard = TOPIC_CONSUMER_PANEL
    }

    private fun onProducerPanel(event: ActionEvent) {
        kafkaTopicDataCard.showCard(TOPIC_PRODUCER_PANEL)
        viewModel.selectedTopicDataCard = TOPIC_PRODUCER_PANEL
    }

    private fun reloadData(topics: Boolean, groups: Boolean) {
        if (!topics && !groups) return

        fun done(selectionPath: TreePath?, e: Throwable?) {
            if (e != null) {
                showNoDataPanelWithError(e)
            } else {
                (kafkaBrokerDataTree.model as DefaultTreeModel).reload()
                kafkaBrokerDataTree.requestFocus()
                if (selectionPath != null) {
                    kafkaBrokerDataTree.selectionPath = selectionPath
                    kafkaBrokerDataTree.scrollPathToVisible(selectionPath)
                } else {
                    kafkaBrokerDataTree.setSelectionRow(0)
                }
            }
        }

        var error: Throwable? = null
        var topicsDone = !topics
        var groupsDone = !groups

        val selectionPath = kafkaBrokerDataTree.selectionPath

        val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
            panel.progressText = ApplicationMessages["frame.reload.process"]
            panel.start()
        }
        if (topics) {
            viewModel.cleanTopicData()
            viewModel.listTopicsInfo { result ->
                SwingUtilities.invokeLater {
                    result
                        .onSuccess { topicDescriptions ->
                            val treeModel = kafkaBrokerDataTree.model as DefaultTreeModel
                            val rootNode = treeModel.root as SimpleMutableTreeNode
                            if (rootNode.childCount > 0) {
                                val child = rootNode.firstChild as SimpleMutableTreeNode
                                if (child.userObject == RootTreeNode.Topics) {
                                    child.removeFromParent()
                                }
                            }

                            val treeNode = SimpleMutableTreeNode(RootTreeNode.Topics)
                            topicDescriptions.forEach { (topic) -> treeNode.add(SimpleMutableTreeNode(topic, false)) }
                            rootNode.insert(treeNode, 0)

                            val topics = topicDescriptions.map { (topic) -> topic }
                            viewModel.consumerViewModel.updateTopics(topics)
                            viewModel.producerViewModel.updateTopics(topics)
                            viewModel.topicsFilterTableViewModel.tableModel.tableData = topicDescriptions
                            viewModel.topicPartitionsTableModel.tableData = topicDescriptions
                        }
                        .onFailure { e -> error = e }

                    topicsDone = true
                    if (groupsDone) {
                        busyPanel.stop()
                        done(selectionPath, error)
                    }
                }
            }
        }
        if (groups) {
            viewModel.cleanGroupData()
            viewModel.listConsumerGroupsInfo { result ->
                SwingUtilities.invokeLater {
                    result
                        .onSuccess { groupDescriptions ->
                            val treeModel = kafkaBrokerDataTree.model as DefaultTreeModel
                            val rootNode = treeModel.root as SimpleMutableTreeNode
                            if (rootNode.childCount > 0) {
                                val child = rootNode.lastChild as SimpleMutableTreeNode
                                if (child.userObject == RootTreeNode.Groups) {
                                    child.removeFromParent()
                                }
                            }

                            val treeNode = SimpleMutableTreeNode(RootTreeNode.Groups)
                            groupDescriptions.forEach { treeNode.add(SimpleMutableTreeNode(it.name, false)) }
                            rootNode.add(treeNode)

                            viewModel.consumerViewModel.updateGroups(groupDescriptions.map { it.name })
                            viewModel.groupsFilterTableViewModel.tableModel.tableData = groupDescriptions
                            viewModel.groupDetailsTableModel.tableData = groupDescriptions
                        }
                        .onFailure { e -> error = e }

                    groupsDone = true
                    if (topicsDone) {
                        busyPanel.stop()
                        done(selectionPath, error)
                    }
                }
            }
        }
    }

    private fun onToggleTopicFavorite(event: ActionEvent) {
        kafkaBrokerDataTree.selectionPath?.let { path ->
            if (path.pathCount == 3) {
                val topicName = (path.lastPathComponent as SimpleMutableTreeNode).userObject.toString()
                if (viewModel.isTopicFavorite(topicName)) {
                    viewModel.removeTopicFromFavorite(topicName)
                } else {
                    viewModel.addTopicToFavorite(topicName)
                }
                (kafkaBrokerDataTree.model as DefaultTreeModel).nodeChanged(path.lastPathComponent as TreeNode)
            }
        }
    }

    private fun onCreateTopic(event: ActionEvent) {
        val topicNameTextField = jtextfield { field ->
            showClearButton = true
            field.columns = 24
            field.document.addDocumentListener(DocumentListenerFunction {
                field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR.takeIf { field.text.isEmpty() })
            })
            field.text = "new-topic"
        }
        val partitionsNumberField = jnumberfield {
            placeholderText = ApplicationMessages["broker.createTopic.default"]
        }
        val replicationFactorField = jnumberfield {
            placeholderText = ApplicationMessages["broker.createTopic.default"]
        }

        val component = jpanel(MigLayout("insets 0 10 0 0, gap 10")) {
            +jlabel(ApplicationMessages["broker.createTopic.name"])
            +(topicNameTextField to "pushx, growx, wrap")
            +jlabel(ApplicationMessages["broker.createTopic.partitions"])
            +(partitionsNumberField to "growx, wrap")
            +jlabel(ApplicationMessages["broker.createTopic.repFactor"])
            +(replicationFactorField to "growx")
        }
        val option = JOptionPane.showConfirmDialog(
            this,
            component,
            ApplicationMessages["broker.createTopic"],
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (option == JOptionPane.OK_OPTION) {
            val newTopicName = topicNameTextField.text
            if (newTopicName.isNotBlank()) {
                val partitionsNum = partitionsNumberField.text.takeIf { it.isNotBlank() }?.toInt()
                val replicationFactor = replicationFactorField.text.takeIf { it.isNotBlank() }?.toShort()

                val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
                    panel.progressText = ApplicationMessages["broker.createTopic.process", newTopicName]
                    panel.start()
                }
                viewModel.createTopic(newTopicName, partitionsNum, replicationFactor) { result ->
                    busyPanel.stop()
                    result
                        .onSuccess {
                            JOptionPane.showMessageDialog(
                                this,
                                ApplicationMessages["broker.createTopic.success", newTopicName],
                                ApplicationMessages["broker.createTopic"],
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                        .onFailure { e ->
                            JStacktracePanel.showMessageDialog(
                                this,
                                ApplicationMessages["broker.createTopic"],
                                ApplicationMessages["broker.createTopic.failure"],
                                e,
                                false
                            )
                        }
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    ApplicationMessages["broker.createTopic.name.error"],
                    ApplicationMessages["broker.createTopic"],
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun onDeleteTopic(event: ActionEvent) {
        val topicName = viewModel.topic
        val option = JOptionPane.showConfirmDialog(
            this,
            ApplicationMessages["broker.deleteTopic.message", topicName],
            ApplicationMessages["broker.deleteTopic"],
            JOptionPane.YES_NO_OPTION
        )
        if (option == JOptionPane.YES_OPTION) {
            val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
                panel.progressText = ApplicationMessages["broker.deleteTopic.process", topicName]
                panel.start()
            }
            viewModel.deleteTopic(topicName) { result ->
                busyPanel.stop()
                result
                    .onSuccess {
                        JOptionPane.showMessageDialog(
                            this,
                            ApplicationMessages["broker.deleteTopic.success", topicName],
                            ApplicationMessages["broker.deleteTopic"],
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    .onFailure { e ->
                        JStacktracePanel.showMessageDialog(
                            this,
                            ApplicationMessages["broker.deleteTopic"],
                            ApplicationMessages["broker.deleteTopic.failure"],
                            e,
                            false
                        )
                    }
            }
        }
    }

    private fun onClearTopic(event: ActionEvent) {
        val topicName = viewModel.topic
        val option = JOptionPane.showConfirmDialog(
            this,
            ApplicationMessages["broker.clearTopic.message", topicName],
            ApplicationMessages["broker.clearTopic"],
            JOptionPane.YES_NO_OPTION
        )
        if (option == JOptionPane.YES_OPTION) {
            val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
                panel.progressText = ApplicationMessages["broker.clearTopic.process", topicName]
                panel.start()
            }
            viewModel.clearTopic(topicName) { result ->
                busyPanel.stop()
                result
                    .onSuccess {
                        JOptionPane.showMessageDialog(
                            this,
                            ApplicationMessages["broker.clearTopic.success", topicName],
                            ApplicationMessages["broker.clearTopic"],
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    .onFailure { e ->
                        JStacktracePanel.showMessageDialog(
                            this,
                            ApplicationMessages["broker.clearTopic"],
                            ApplicationMessages["broker.clearTopic.failure"],
                            e,
                            false
                        )
                    }
            }
        }
    }

    private fun onToggleGroupFavorite(event: ActionEvent) {
        kafkaBrokerDataTree.selectionPath?.let { path ->
            if (path.pathCount == 3) {
                val groupName = (path.lastPathComponent as SimpleMutableTreeNode).userObject.toString()
                if (viewModel.isGroupFavorite(groupName)) {
                    viewModel.removeGroupFromFavorite(groupName)
                } else {
                    viewModel.addGroupToFavorite(groupName)
                }
                (kafkaBrokerDataTree.model as DefaultTreeModel).nodeChanged(path.lastPathComponent as TreeNode)
            }
        }
    }

    private fun onResetOffset(event: ActionEvent) {
        val topicNameTextField = jcombobox(viewModel.groupDetailsTableModel.topicNames.toList())
        val strategyTextField = jcombobox(OffsetStrategy.entries)
        val component = jpanel(MigLayout("insets 0 10 0 0, gap 10")) {
            +jlabel(ApplicationMessages["broker.resetOffset.topic"])
            +(topicNameTextField to "pushx, growx, wrap")
            +jlabel(ApplicationMessages["broker.resetOffset.strategy"])
            +(strategyTextField to "growx")
        }
        val option = JOptionPane.showConfirmDialog(
            this,
            component,
            ApplicationMessages["broker.resetOffset"],
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (option == JOptionPane.OK_OPTION) {
            val topicName = topicNameTextField.selectedItem as String
            val offsetStrategy = strategyTextField.selectedItem as OffsetStrategy

            val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
                panel.progressText = ApplicationMessages["broker.resetOffset.process", topicName]
                panel.start()
            }
            viewModel.resetOffsetGroup(viewModel.group, topicName, offsetStrategy) { result ->
                busyPanel.stop()
                result
                    .onSuccess {
                        JOptionPane.showMessageDialog(
                            this,
                            ApplicationMessages["broker.resetOffset.success", topicName],
                            ApplicationMessages["broker.resetOffset"],
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    .onFailure { e ->
                        JStacktracePanel.showMessageDialog(
                            this,
                            ApplicationMessages["broker.resetOffset"],
                            ApplicationMessages["broker.resetOffset.failure"],
                            e,
                            false
                        )
                    }
            }
        }
    }

    private fun onDeleteGroup(event: ActionEvent) {
        val consumerGroup = viewModel.group
        val option = JOptionPane.showConfirmDialog(
            this,
            ApplicationMessages["broker.deleteGroup.message", consumerGroup],
            ApplicationMessages["broker.deleteGroup"],
            JOptionPane.YES_NO_OPTION
        )
        if (option == JOptionPane.YES_OPTION) {
            val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
                panel.progressText = ApplicationMessages["broker.deleteGroup.process", consumerGroup]
                panel.start()
            }
            viewModel.deleteConsumerGroup(consumerGroup) { result ->
                busyPanel.stop()
                result
                    .onSuccess {
                        JOptionPane.showMessageDialog(
                            this,
                            ApplicationMessages["broker.deleteGroup.success", consumerGroup],
                            ApplicationMessages["broker.deleteGroup"],
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    .onFailure { e ->
                        JStacktracePanel.showMessageDialog(
                            this,
                            ApplicationMessages["broker.deleteGroup"],
                            ApplicationMessages["broker.deleteGroup.failure"],
                            e,
                            false
                        )
                    }
            }
        }
    }

    //endregion

    fun dispose() {
        viewModel.consumerViewModel.stopConsuming()
    }

    private fun disableToolbarButtons() {
        topicAddToFavoriteButton.isVisible = true
        topicAddToFavoriteButton.isEnabled = false
        topicCreateButton.isVisible = false
        topicDeleteButton.isVisible = false
        topicClearButton.isVisible = false
        groupAddToFavoriteButton.isVisible = false
        groupResetOffsetButton.isVisible = false
        groupDeleteButton.isVisible = false
    }

    private fun showNoDataPanelWithError(e: Throwable) {
        viewModel.cleanTopicData()
        viewModel.cleanGroupData()

        val treeModel = kafkaBrokerDataTree.model as DefaultTreeModel
        val rootNode = treeModel.root as SimpleMutableTreeNode
        rootNode.removeAllChildren()
        treeModel.reload()

        reloadStatusLabel.failure("Data reloading problem", e)
        kafkaBrokerDataCard.showCard(NO_DATA_PANEL)
        disableToolbarButtons()
    }

}