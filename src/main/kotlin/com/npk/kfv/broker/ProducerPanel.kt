package com.npk.kfv.broker

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.UIHelper
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JFileField
import com.npk.kfv.components.JStatusLabel
import com.npk.kfv.service.SerializationType
import com.npk.swing.*
import com.npk.swing.BindingHelper.bind
import com.npk.swing.BindingHelper.bindModel
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.beans.PropertyChangeEvent
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

internal class ProducerPanel(override val viewModel: ProducerViewModel) : JPanel(MigLayout("insets 0 0 10 0, gap 10")), View<ProducerViewModel> {

    private val keyDataTextArea = jtextarea { it.bind(viewModel, ProducerViewModel::keyData) }
    private val valueDataTextArea = jtextarea { it.bind(viewModel, ProducerViewModel::valueData) }
    private val valueDataFileCheckBox = jcheckbox(ApplicationMessages["broker.producer.value.fromFile"]) {
        it.bind(viewModel, ProducerViewModel::valueDataIsFromFile)
    }
    private val valueDataFileTextField = JFileField.ofAnyFiles {
        it.isEnabled = valueDataFileCheckBox.isSelected
        it.bind(viewModel, ProducerViewModel::valueDataFile)
    }

    private val generateKeyRandomDataButton = jbutton(ApplicationImages.Icons.Magic.icon, viewModel::onGenerateKeyRandomDataActionPerformed) {
        it.isFocusable = false
        it.toolTipText = ApplicationMessages["broker.producer.randomData"]
        it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    }
    private val generateValueRandomDataButton = jbutton(ApplicationImages.Icons.Magic.icon, viewModel::onGenerateValueRandomDataActionPerformed) {
        it.isFocusable = false
        it.toolTipText = ApplicationMessages["broker.producer.randomData"]
        it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    }

    private val statusLabel = JStatusLabel()

    init {
        val produceButton = jbutton(ApplicationMessages["broker.producer.produce"], ApplicationImages.Icons.Run.icon, ::onProduceActionPerformed) {
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            it.isFocusable = false
        }

        add(jscrollpane(mainPanel()), "push, grow, wrap")
        add(produceButton, "split, gapright 20")
        add(statusLabel, "growx, gapright 20")

        transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                return support.getDataFlavors().any { flavor -> flavor.isFlavorJavaFileListType() }
            }
            override fun importData(support: TransferSupport): Boolean {
                return if (canImport(support)) {
                    @Suppress("UNCHECKED_CAST")
                    val dataFile = (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>).first()
                    loadDataFromFile(dataFile)
                    true
                } else {
                    false
                }
            }
        }

        viewModel.addPropertyChangeListener(::propertyChangeListener)
    }

    private fun mainPanel() = jpanel(MigLayout("insets 10, gap 10")) {
        +jlabel(ApplicationMessages["broker.producer.topic"])
        +(JComboBox(viewModel.topicsComboBoxModel).also {
            it.bindModel(viewModel, ProducerViewModel::topic)
            AutoCompleteDecorator.decorate(it)
        } to "pushx, growx")
        +(jbutton(ApplicationImages.Icons.Export.icon, ::onSaveDataActionPerformed) {
            it.isFocusable = false
            it.toolTipText = ApplicationMessages["broker.producer.saveData"]
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        } to "split, alignx right, gapafter 0")
        +(jbutton(ApplicationImages.Icons.Import.icon, ::onLoadDataActionPerformed) {
            it.isFocusable = false
            it.toolTipText = ApplicationMessages["broker.producer.loadData"]
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        } to "alignx right, wrap")

        +jlabel(ApplicationMessages["broker.producer.partition"])
        +(jnumberfield {
            placeholderText = "Auto"
            showClearButton = true
            it.columns = 10
            it.bind(viewModel, ProducerViewModel::partition)
        } to "split")
        it.add(Box.createHorizontalStrut(10))
        +jlabel(ApplicationMessages["broker.producer.compression"])
        +(compressioncombobox { it.bind(viewModel, ProducerViewModel::compression)} to "wrap")

        +(jlabel(ApplicationMessages["broker.producer.headers"]))
        +(jtextarea { it.bind(viewModel, ProducerViewModel::headers) } to "span, grow, wrap")

        +(jlabel(ApplicationMessages["broker.producer.key"]) to "split, span")
        +(JSeparator() to "growx, wrap")
        +(jlabel(ApplicationMessages["broker.producer.key.type"]) to "gapleft 15")
        +(jcombobox(SerializationType.entries) { it.bind(viewModel, ProducerViewModel::keyType) } to "split 2")
        +jlabel {
            it.foreground = Color.GRAY
            it.bindModel(viewModel, ProducerViewModel::keySerializerHint)
        }
        +(generateKeyRandomDataButton to "alignx right, wrap")
        +(keyDataTextArea to "gapleft 15, span, grow, wrap")

        +(jlabel(ApplicationMessages["broker.producer.value"]) to "split, span")
        +(JSeparator() to "growx, wrap")
        +(jlabel(ApplicationMessages["broker.producer.value.type"]) to "gapleft 15")
        +(jcombobox(SerializationType.entries) { it.bind(viewModel, ProducerViewModel::valueType) } to "split 2")
        +jlabel {
            it.foreground = Color.GRAY
            it.bindModel(viewModel, ProducerViewModel::valueSerializerHint)
        }
        +(generateValueRandomDataButton to "alignx right, wrap")
        +(valueDataFileCheckBox to "gapleft 15")
        +(valueDataFileTextField to "span, grow")
        +(valueDataTextArea to "gapleft 15, span, grow")
    }

    private fun propertyChangeListener(event: PropertyChangeEvent) {
        when (event.propertyName) {
            ProducerViewModel::topic.name -> {
                statusLabel.clean()
            }
            ProducerViewModel::keyType.name -> {
                (event.newValue != SerializationType.None).let { isEnabled ->
                    keyDataTextArea.isEnabled = isEnabled
                    generateKeyRandomDataButton.isEnabled = isEnabled
                }
            }
            ProducerViewModel::valueType.name -> {
                (event.newValue != SerializationType.None).let { isEnabled ->
                    valueDataTextArea.isEnabled = isEnabled
                    generateValueRandomDataButton.isEnabled = isEnabled
                }
                (event.newValue == SerializationType.String || event.newValue == SerializationType.ByteArray || event.newValue == SerializationType.Json).let { isEnabled ->
                    valueDataFileCheckBox.isEnabled = isEnabled
                    valueDataFileTextField.isEnabled = isEnabled && valueDataFileCheckBox.isSelected
                }
            }
            ProducerViewModel::valueDataIsFromFile.name -> {
                (event.newValue == true).let { isEnabled ->
                    valueDataTextArea.isEnabled = !isEnabled
                    generateValueRandomDataButton.isEnabled = !isEnabled
                    valueDataFileTextField.isEnabled = isEnabled
                }
            }
        }
    }

    private fun onProduceActionPerformed(event: ActionEvent) {
        val topicName = viewModel.topic
        val busyPanel = JBusyPanel.findGlassPaneForComponent(this).also { panel ->
            panel.progressText = ApplicationMessages["broker.producer.process", topicName]
            panel.start()
        }

        statusLabel.clean()
        viewModel.produceMessage { result ->
            result
                .onSuccess { recordMeta ->
                    statusLabel.success(ApplicationMessages["broker.producer.success", recordMeta.partition(), recordMeta.offset()])
                    statusLabel.toolTipText = "key: ${viewModel.keyData}\npartition: ${recordMeta.partition()}\noffset: ${recordMeta.offset()}"
                }
                .onFailure { e ->
                    statusLabel.failure(ApplicationMessages["broker.producer.failure"], e)
                }
            busyPanel.stop()
        }
    }

    private fun onSaveDataActionPerformed(event: ActionEvent) {
        val defaultFileName = "${viewModel.topic} ${DateTimeFormatter.ofPattern("yyyy-MM-dd hhmm").format(LocalDateTime.now())}.txt"
        val fileChooser = UIHelper.createAnySaveFileChooser(defaultFileName)
        if (fileChooser.showSaveDialog(SwingUtilities.windowForComponent(this)) == JFileChooser.APPROVE_OPTION) {
            viewModel.saveToFile(fileChooser.selectedFile) { result ->
                result
                    .onSuccess { file ->
                        statusLabel.information(ApplicationMessages["broker.producer.saveData.success", file.name], Duration.ofSeconds(10))
                    }
                    .onFailure { e ->
                        statusLabel.failure(ApplicationMessages["broker.producer.saveData.failure"], e)
                    }
            }
        }
    }

    private fun onLoadDataActionPerformed(event: ActionEvent) {
        val fileChooser = UIHelper.createAnyOpenFileChooser()
        if (fileChooser.showOpenDialog(SwingUtilities.windowForComponent(this)) == JFileChooser.APPROVE_OPTION) {
            loadDataFromFile(fileChooser.selectedFile)
        }
    }

    private fun loadDataFromFile(selectedFile: File) {
        viewModel.loadFromFile(selectedFile) { result ->
            result
                .onSuccess { file ->
                    statusLabel.information(ApplicationMessages["broker.producer.loadData.success", file.name], Duration.ofSeconds(10))
                }
                .onFailure { e ->
                    statusLabel.failure(ApplicationMessages["broker.producer.loadData.failure"], e)
                }
        }
    }

}