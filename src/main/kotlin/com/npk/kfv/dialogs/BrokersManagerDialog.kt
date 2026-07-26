package com.npk.kfv.dialogs

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.ApplicationPrefs
import com.npk.kfv.UIHelper
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JCardPanel
import com.npk.kfv.components.JFileField
import com.npk.kfv.components.JStatusLabel
import com.npk.kfv.service.*
import com.npk.kfv.service.ConfigBrokerView.ViewType
import com.npk.swing.*
import com.npk.swing.BindingHelper.bind
import com.npk.swing.BindingHelper.bindModel
import net.miginfocom.swing.MigLayout
import org.apache.kafka.common.config.ConfigDef
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.nio.file.Files
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class BrokersManagerDialog(owner: Window, override val viewModel: BrokersManagerViewModel, selectedIndex: Int = -1) : JDialog(owner), View<BrokersManagerViewModel> {

    companion object {
        private const val NO_BROKER_PANEL =     "NoBrokerPanel"
        private const val BROKER_CONFIG_PANEL = "BrokerConfigPanel"
    }

    private val addNewAction = jaction(::onAddNewActionPerformed) {
        icon = ApplicationImages.Icons.Plus.icon
        tooltip = ApplicationMessages["brokersManagerDialog.addNew"]
    }
    private val removeAction = jaction(::onRemoveActionPerformed) {
        icon = ApplicationImages.Icons.Minus.icon
        tooltip = ApplicationMessages["brokersManagerDialog.remove"]
        isEnabled = false
    }
    private val duplicateAction = jaction(::onDuplicateActionPerformed) {
        icon = ApplicationImages.Icons.Copy.icon
        tooltip = ApplicationMessages["brokersManagerDialog.duplicate"]
        isEnabled = false
    }
    private val importAction = jaction(::onImportActionPerformed) {
        icon = ApplicationImages.Icons.Import.icon
        tooltip = ApplicationMessages["brokersManagerDialog.import"]
    }
    private val testConnectionAction = jaction(::onTestConnectionActionPerformed) {
        name = ApplicationMessages["brokersManagerDialog.test"]
        icon = ApplicationImages.Icons.Test.icon
    }

    private val kafkaBrokerDetailsCard = JCardPanel()
    private val configurationSourceCard = JCardPanel()
    private val sslTypeCard = JCardPanel()
    private val propsSourceCard = JCardPanel()

    private val kafkaBrokersTree = JTree().apply {
        showsRootHandles = false
        cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(tree: JTree, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
                val label = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus) as DefaultTreeCellRenderer
                if (!leaf) {
                    label.setIcon(ApplicationImages.Icons.Kafka.icon)
                } else {
                    if (label.text.isNullOrEmpty()) {
                        label.setText("<empty>")
                    }
                }
                return label
            }
        }
        preferredSize = Dimension(180, 0)
        model = DefaultTreeModel(
            DefaultMutableTreeNode("Kafka Brokers").apply {
                viewModel.brokerNames.forEach { name -> add(DefaultMutableTreeNode(name, false)) }
            }
        )
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        selectionModel.addTreeSelectionListener(::treeSelectionListener)
    }

    private val brokerNameTextField = jtextfield { it.bind(viewModel, BrokersManagerViewModel::brokerName) }
    private val connectionStatusLabel = JStatusLabel(false)

    private lateinit var saslSettingsPanel: JPanel
    private lateinit var saslSslSettingsPanel: JPanel
    private lateinit var sslSettingsPanel: JPanel

    init {
        title = ApplicationMessages["brokersManagerDialog.title"]
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isModal = true
        minimumSize = Dimension(820, 530)
        preferredSize = minimumSize

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                onCancelActionPerformed(ActionEvent(event.source, event.id, "windowClosing"))
            }
        })

        contentPane = jpanel(MigLayout("insets 3 10 10 10, gap 10")) {
            val okButton = jbutton(ApplicationMessages.ok, ::onOkActionPerformed)
            val cancelButton = jbutton(ApplicationMessages.cancel, ::onCancelActionPerformed)

            +(mainPanel() to "push, grow, wrap")
            +(okButton to "split 2, alignx right")
            +(cancelButton to "alignx right")
        }
        rootPane.registerKeyboardAction(
            ::onCancelActionPerformed,
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
        glassPane = JBusyPanel()

        viewModel.addPropertyChangeListener(::propertyChangeListener)
        allowAddingNewBrokerConnection()

        if (selectedIndex >= 0) {
            EventQueue.invokeLater { kafkaBrokersTree.setSelectionRow(selectedIndex + 1) }
        }
    }

    private fun mainPanel() = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        val kafkaBrokersListPanel = jpanel(MigLayout("insets 0, gap 0")) {
            val kafkaBrokersListToolBar = jtoolbar {
                +jbutton(addNewAction)
                +jbutton(removeAction)
                +jbutton(duplicateAction)
                +jbutton(importAction)
            }

            +(kafkaBrokersListToolBar to "wrap")
            +(JScrollPane(kafkaBrokersTree) to "push, grow")
        }
        add(kafkaBrokersListPanel)

        kafkaBrokerDetailsCard.add(noBrokerPanel(), NO_BROKER_PANEL)
        kafkaBrokerDetailsCard.add(brokerConfigPanel(), BROKER_CONFIG_PANEL)
        add(kafkaBrokerDetailsCard)
    }

    private fun noBrokerPanel() = jpanel(GridBagLayout()) {
        val constraints = GridBagConstraints().apply { insets = Insets(10, 0, 0, 0) }

        val addNewButton = jbutton(addNewAction) {
            it.text = it.toolTipText
            it.toolTipText = null
            it.isFocusable = false
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        }
        +(addNewButton to constraints.apply { gridy++ })

        val importButton = jbutton(importAction) {
            it.text = it.toolTipText
            it.toolTipText = null
            it.isFocusable = false
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        }
        +(importButton to constraints.apply { gridy++ })
    }

    private fun brokerConfigPanel() = jpanel(BorderLayout()) {
        val panelNorth = jpanel(MigLayout("insets 15, gap 10")) {
            +jlabel(ApplicationMessages["brokersManagerDialog.name"])
            +(brokerNameTextField to "pushx, growx, wrap")

            +jlabel(ApplicationMessages["brokersManagerDialog.source"])
            val customConfigRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.source.custom"], ViewType.CUSTOM.name)
            val propertiesConfigRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.source.props"], ViewType.PROPERTIES.name)
            buttonGroup {
                +customConfigRadioButton
                +propertiesConfigRadioButton
                it.bind(viewModel, BrokersManagerViewModel::viewType)
            }
            +(customConfigRadioButton to "split")
            +propertiesConfigRadioButton
        }

        configurationSourceCard.add(customConfigPanel(), ViewType.CUSTOM.name)
        configurationSourceCard.add(propertiesConfigPanel(), ViewType.PROPERTIES.name)

        val panelSouth = jpanel(FlowLayout(FlowLayout.LEFT, 15, 15)) {
            +jbutton(testConnectionAction) {
                it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
                it.isFocusable = false
            }
            +connectionStatusLabel
        }

        +(panelNorth to BorderLayout.NORTH)
        +(configurationSourceCard to BorderLayout.CENTER)
        +(panelSouth to BorderLayout.SOUTH)
    }

    private fun customConfigPanel() = jpanel(MigLayout("insets 0 30 0 15, gap 10")) {
        +(jlabel(ApplicationMessages["brokersManagerDialog.custom.servers"]))
        +(jtextfield { it.bind(viewModel, BrokersManagerViewModel::propBootstrapServers) } to "pushx, growx, wrap")

        +(jlabel(ApplicationMessages["brokersManagerDialog.custom.auth"]))
        val authenticationNoneRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.custom.auth.none"], ConfigBrokerView.AuthenticationType.NONE.name)
        val authenticationSASLRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.custom.auth.sasl"], ConfigBrokerView.AuthenticationType.SASL.name)
        val authenticationSSLRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.custom.auth.ssl"], ConfigBrokerView.AuthenticationType.SSL.name)
        buttonGroup {
            +authenticationNoneRadioButton
            +authenticationSASLRadioButton
            +authenticationSSLRadioButton
            it.bind(viewModel, BrokersManagerViewModel::viewAuthentication)
        }
        +(authenticationNoneRadioButton to "split")
        +(authenticationSASLRadioButton to "split")
        +(authenticationSSLRadioButton to "wrap")

        saslSettingsPanel = jpanel(MigLayout("insets 0 15 0 0, gap 10")) {
            +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.sasl.mechanism"])
            +(jsaslmechanismcombobox { it.bind(viewModel, BrokersManagerViewModel::propSASLMechanism) } to "split")
            +(jcheckbox(ApplicationMessages["brokersManagerDialog.custom.auth.sasl.ssl"]) { it.bind(viewModel, BrokersManagerViewModel::viewSASLEnableSSL) } to "wrap")

            +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.sasl.username"])
            +(jtextfield { it.bind(viewModel, BrokersManagerViewModel::propSASLUsername) } to "pushx, growx, wrap")

            +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.sasl.password"])
            +(jpasswordfield { it.bind(viewModel, BrokersManagerViewModel::propSASLPassword) } to "pushx, growx, wrap")
        }

        saslSslSettingsPanel = jpanel(MigLayout("insets 0 15 0 0, gap 10")) {
            +(jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.sasl.sslSettings"]) to "split, span")
            +(JSeparator() to "pushx, growx, wrap")
        }

        sslSettingsPanel = jpanel(MigLayout("insets 0 15 0 0, gap 10")) {
            +(jcheckbox(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.validate"]) { it.bind(viewModel, BrokersManagerViewModel::viewSSLValidateHostName) } to "wrap")

            val truststoreRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.truststore"], ConfigBrokerView.SSLType.TRUSTSTORE.name)
            val certificateRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.certif"], ConfigBrokerView.SSLType.CERTIFICATE.name)
            buttonGroup {
                +truststoreRadioButton
                +certificateRadioButton
                it.bind(viewModel, BrokersManagerViewModel::viewSSLType)
            }
            +(truststoreRadioButton to "split")
            +(certificateRadioButton to "wrap")

            val truststorePanel = jpanel(MigLayout("insets 0 15 0 0, gap 10")) {
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.truststore.location"])
                +(JFileField.ofSecureStoreFiles().apply { bind(viewModel, BrokersManagerViewModel::propTrustStoreLocation) } to "pushx, growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.truststore.password"])
                +(jpasswordfield().apply { bind(viewModel, BrokersManagerViewModel::propTrustStorePassword) } to "pushx, growx, wrap")
                +(jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.keystore"]) to "split, span")
                +(JSeparator() to "growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.keystore.location"])
                +(JFileField.ofSecureStoreFiles().apply { bind(viewModel, BrokersManagerViewModel::propKeyStoreLocation) } to "pushx, growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.keystore.password"])
                +(jpasswordfield().apply { bind(viewModel, BrokersManagerViewModel::propKeyStorePassword) } to "pushx, growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.keystore.key.password"])
                +(jpasswordfield().apply { bind(viewModel, BrokersManagerViewModel::propKeyPassword) } to "pushx, growx, wrap")
            }
            val certificatePanel = jpanel(MigLayout("insets 0 15 0 0, gap 10")) {
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.certif.accessKey"])
                +(JFileField.ofAnyFiles().apply { bind(viewModel, BrokersManagerViewModel::propCertifKey) } to "pushx, growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.certif.accessCertif"])
                +(JFileField.ofAnyFiles().apply { bind(viewModel, BrokersManagerViewModel::propCertifLocation) } to "pushx, growx, wrap")
                +jlabel(ApplicationMessages["brokersManagerDialog.custom.auth.ssl.certif.caCertif"])
                +(JFileField.ofAnyFiles().apply { bind(viewModel, BrokersManagerViewModel::propCertifCALocation) } to "pushx, growx, wrap")
            }
            sslTypeCard.add(truststorePanel, ConfigBrokerView.SSLType.TRUSTSTORE.name)
            sslTypeCard.add(certificatePanel, ConfigBrokerView.SSLType.CERTIFICATE.name)
            +(sslTypeCard to "span, split, push, grow")
        }

        val settingsPanel = jpanel(MigLayout("insets 0, gap 0, wrap, hidemode 3")) {
            +(saslSettingsPanel to "pushx, grow")
            +(saslSslSettingsPanel to "pushx, grow")
            +(sslSettingsPanel to "pushx, grow")
        }

        +(jscrollpane(settingsPanel) to "span, pushy, grow")
    }

    private fun propertiesConfigPanel() = jpanel(MigLayout("insets 0 30 0 15, gap 10")) {
        +jlabel(ApplicationMessages["brokersManagerDialog.props.source"])
        val propsSourceImplicitRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.props.source.implicit"], ConfigBrokerView.SourceType.IMPLICIT.name)
        val propsSourceFileRadioButton = jradiobutton(ApplicationMessages["brokersManagerDialog.props.source.file"], ConfigBrokerView.SourceType.FILE.name)
        buttonGroup {
            +propsSourceImplicitRadioButton
            +propsSourceFileRadioButton
            it.bind(viewModel, BrokersManagerViewModel::viewPropertiesSource)
        }
        +(propsSourceImplicitRadioButton to "split")
        +(propsSourceFileRadioButton to "pushx, growx, wrap")

        val implicitPropsPanel = jpanel(MigLayout("insets 0, gap 10")) {
            val textArea = jtextarea {
                it.addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(event: KeyEvent) {
                        if (event.isControlDown && event.keyCode == KeyEvent.VK_SPACE) {
                            val textArea = event.source as JTextArea
                            val popupMenu = JPopupMenu()
                            val list = jkafkapropertieslist().also {
                                it.selectedIndex = 0
                                it.addKeyListener(object : KeyAdapter() {
                                    override fun keyPressed(event: KeyEvent) {
                                        if (event.keyCode == KeyEvent.VK_ENTER || event.keyCode == KeyEvent.VK_TAB) {
                                            @Suppress("UNCHECKED_CAST")
                                            (event.source as JList<ConfigDef.ConfigKey>).selectedValue?.let { value ->
                                                val caretPosition = textArea.caretPosition
                                                val lineStartOffset = textArea.getLineStartOffset(textArea.getLineOfOffset(caretPosition))
                                                textArea.replaceRange("${value.name}=", lineStartOffset, caretPosition)
                                                popupMenu.isVisible = false
                                            }
                                        }
                                    }
                                })
                            }
                            popupMenu.add(jscrollpane(list))
                            popupMenu.show(textArea, 0, textArea.caret.magicCaretPosition.y + textArea.getBaseline(0, 0))
                        }
                    }
                })
                it.addFocusListener(object : FocusAdapter() {
                    override fun focusLost(event: FocusEvent) {
                        viewModel.updateKafkaPropertiesFromRaw((event.source as JTextArea).text)
                    }
                })
                it.bindModel(viewModel, BrokersManagerViewModel::propRawProperties)
            }
            +(textArea to "span, split, pushx, grow")
        }
        val filePropsPanel = jpanel(MigLayout("insets 0, gap 10")) {
            +(jlabel(ApplicationMessages["brokersManagerDialog.props.source.file.path"]))
            +(JFileField.ofPropertiesFiles().apply { bind(viewModel, BrokersManagerViewModel::viewPropertiesPath) } to "pushx, growx, wrap")
        }

        propsSourceCard.add(jscrollpane(implicitPropsPanel), ConfigBrokerView.SourceType.IMPLICIT.name)
        propsSourceCard.add(filePropsPanel, ConfigBrokerView.SourceType.FILE.name)
        +(propsSourceCard to "span, split, pushy, grow")
    }

    private fun treeSelectionListener(event: TreeSelectionEvent) {
        val selectedBrokerIndex = (event.source as TreeSelectionModel).leadSelectionRow - 1
        val showBrokerPanel = selectedBrokerIndex >= 0
        kafkaBrokerDetailsCard.showCard(if (showBrokerPanel) BROKER_CONFIG_PANEL else NO_BROKER_PANEL)

        removeAction.isEnabled = showBrokerPanel
        duplicateAction.isEnabled = showBrokerPanel

        viewModel.selectedBrokerIndex = selectedBrokerIndex
    }

    private fun propertyChangeListener(event: PropertyChangeEvent) {
        when (event.propertyName) {
            BrokersManagerViewModel::brokerName.name -> {
                val brokerIndex = viewModel.selectedBrokerIndex
                if (brokerIndex >= 0) {
                    (kafkaBrokersTree.model as DefaultTreeModel).let { treeModel ->
                        val childNode = (treeModel.root as DefaultMutableTreeNode).getChildAt(brokerIndex) as DefaultMutableTreeNode
                        childNode.setUserObject(event.newValue)
                        treeModel.reload(childNode)
                    }
                    brokerNameTextField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR.takeIf { event.newValue.toString().isEmpty() })
                }
            }
            BrokersManagerViewModel::viewType.name -> configurationSourceCard.showCard(event.newValue.toString())
            BrokersManagerViewModel::viewSSLType.name -> sslTypeCard.showCard(event.newValue.toString())
            BrokersManagerViewModel::viewPropertiesSource.name -> propsSourceCard.showCard(event.newValue.toString())
            BrokersManagerViewModel::viewAuthentication.name -> {
                saslSettingsPanel.isVisible = event.newValue == ConfigBrokerView.AuthenticationType.SASL.name
                saslSslSettingsPanel.isVisible = event.newValue == ConfigBrokerView.AuthenticationType.SASL.name && viewModel.viewSASLEnableSSL
                sslSettingsPanel.isVisible = event.newValue == ConfigBrokerView.AuthenticationType.SSL.name || (event.newValue == ConfigBrokerView.AuthenticationType.SASL.name && viewModel.viewSASLEnableSSL)
            }
            BrokersManagerViewModel::viewSASLEnableSSL.name -> {
                if (viewModel.viewAuthentication == ConfigBrokerView.AuthenticationType.SASL.name) {
                    saslSslSettingsPanel.isVisible = event.newValue as Boolean
                    sslSettingsPanel.isVisible = event.newValue as Boolean
                }
            }
            BrokersManagerViewModel::connectionStatus.name -> {
                connectionStatusLabel.clean()
                when (event.newValue as BrokersManagerViewModel.ConnectionStatus) {
                    BrokersManagerViewModel.ConnectionStatus.Undefined -> { }
                    BrokersManagerViewModel.ConnectionStatus.Process -> connectionStatusLabel.text = ApplicationMessages["brokersManagerDialog.test.process"]
                    BrokersManagerViewModel.ConnectionStatus.Connected -> connectionStatusLabel.success(ApplicationMessages["brokersManagerDialog.test.success"])
                    BrokersManagerViewModel.ConnectionStatus.Failure -> viewModel.connectionException.let { e ->
                        if (e != null) {
                            connectionStatusLabel.failure(ApplicationMessages["brokersManagerDialog.test.failure"], e)
                        } else {
                            connectionStatusLabel.failure(ApplicationMessages["brokersManagerDialog.test.failure"])
                        }
                    }
                }
            }
        }
    }

    //region Actions

    private fun onAddNewActionPerformed(event: ActionEvent) {
        val (index, name) = viewModel.addNewConnection()
        (kafkaBrokersTree.model as DefaultTreeModel).let { treeModel ->
            val rootNode = treeModel.root as DefaultMutableTreeNode
            treeModel.insertNodeInto(DefaultMutableTreeNode(name, false), rootNode, rootNode.childCount)
        }
        if (kafkaBrokersTree.isCollapsed(0)) {
            kafkaBrokersTree.expandRow(0)
        }
        kafkaBrokersTree.setSelectionRow(index + 1)
        allowAddingNewBrokerConnection()
    }

    private fun onRemoveActionPerformed(event: ActionEvent) {
        val selectedIndex = viewModel.selectedBrokerIndex
        if (selectedIndex >= 0) {
            kafkaBrokersTree.setSelectionRow(selectedIndex)
            (kafkaBrokersTree.model as DefaultTreeModel).let { treeModel ->
                val childNode = (treeModel.root as DefaultMutableTreeNode).getChildAt(selectedIndex) as DefaultMutableTreeNode
                treeModel.removeNodeFromParent(childNode)
            }
            viewModel.removeConnection(selectedIndex)
            allowAddingNewBrokerConnection()
        }
    }

    private fun onDuplicateActionPerformed(event: ActionEvent) {
        val (index, name) = viewModel.duplicateConnection()
        (kafkaBrokersTree.model as DefaultTreeModel).let { treeModel ->
            val rootNode = treeModel.root as DefaultMutableTreeNode
            treeModel.insertNodeInto(DefaultMutableTreeNode(name, false), rootNode, rootNode.childCount)
        }
        kafkaBrokersTree.setSelectionRow(index + 1)
        allowAddingNewBrokerConnection()
    }

    private fun onImportActionPerformed(event: ActionEvent) {
        val fileChooser = UIHelper.createPropertiesFileChooser()

        val state = fileChooser.showOpenDialog(SwingUtilities.windowForComponent(this))
        if (state == JFileChooser.APPROVE_OPTION) {
            runCatching {
                val selectedFilePath = fileChooser.selectedFile.toPath()
                if (Files.notExists(selectedFilePath)) {
                    JOptionPane.showMessageDialog(SwingUtilities.windowForComponent(this), "Selected file '$selectedFilePath' not found", "Error", JOptionPane.ERROR_MESSAGE)
                } else {
                    val (index, name) = viewModel.importFromFile(selectedFilePath)
                    (kafkaBrokersTree.model as DefaultTreeModel).let { treeModel ->
                        val rootNode = treeModel.root as DefaultMutableTreeNode
                        treeModel.insertNodeInto(DefaultMutableTreeNode(name, false), rootNode, rootNode.childCount)
                    }
                    kafkaBrokersTree.setSelectionRow(index + 1)
                    allowAddingNewBrokerConnection()
                }
            }
        }
    }

    private fun onTestConnectionActionPerformed(event: ActionEvent) {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
            panel.progressText = ApplicationMessages["brokersManagerDialog.test.process"]
            panel.start()
        }

        viewModel.testConnection {
            busyPanel.stop()
            defaultCloseOperation = DISPOSE_ON_CLOSE
        }
    }

    private fun onOkActionPerformed(event: ActionEvent) {
        viewModel.saveBrokers { result ->
            result.getOrThrow()
            EventService.Default.fire(ConfigBrokersUpdatedEvent())
            dispose()
        }
    }

    private fun onCancelActionPerformed(event: ActionEvent) {
        if (!JBusyPanel.findGlassPaneForComponent(this).isRunning) {
            dispose()
        }
    }

    //endregion

    private fun allowAddingNewBrokerConnection() {
        addNewAction.isEnabled = viewModel.brokerNames.size < ApplicationPrefs.brokersMaxCount
    }

    private fun jsaslmechanismcombobox(block: (JComboBox<String>) -> Unit = {}) = JComboBox<String>()
        .apply {
            model = DefaultComboBoxModel<String>().apply {
                addElement("PLAIN")
                addElement("SCRAM-SHA-256")
                addElement("SCRAM-SHA-512")
            }
        }
        .apply(block)

    private fun jkafkapropertieslist() = JList<ConfigDef.ConfigKey>().apply {
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val item = (value as? ConfigDef.ConfigKey)?.let { configKey ->
                    val color = if (isSelected) list.selectionForeground else Color.GRAY
                    "<html>${configKey.name} <font color=${"#" + Integer.toHexString(color.rgb and 0xffffff)}>= ${configKey.defaultValue}</font></html>"
                }
                return super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
            }
        }
        model = DefaultComboBoxModel<ConfigDef.ConfigKey>().apply {
            addAll(KafkaService.allConfigKeys.values)
        }
    }

}