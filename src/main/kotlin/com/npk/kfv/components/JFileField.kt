package com.npk.kfv.components

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.ApplicationImages
import com.npk.kfv.UIHelper
import java.io.File
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JTextField
import javax.swing.SwingUtilities

class JFileField private constructor(private val fileChooser: JFileChooser, initialFile: File? = null) : JTextField() {

    companion object {

        fun ofAnyFiles(file: File? = null, block: (JFileField) -> Unit = {}) =
            JFileField(UIHelper.createAnyOpenFileChooser(), file).apply(block)

        fun ofPropertiesFiles(file: File? = null, block: (JFileField) -> Unit = {}) =
            JFileField(UIHelper.createPropertiesFileChooser(), file).apply(block)

        fun ofSecureStoreFiles(file: File? = null, block: (JFileField) -> Unit = {}) =
            JFileField(UIHelper.createSecureStoreFileChooser(), file).apply(block)

    }

    private val openFileChooserButton = JButton(ApplicationImages.Icons.Open.icon)

    var dialogTitle: String
        get() = fileChooser.dialogTitle
        set(value) { fileChooser.dialogTitle = value }

    var selectedFile: File?
        get() = fileChooser.selectedFile
        set(value) { fileChooser.selectedFile = value }

    init {
        openFileChooserButton.addActionListener {
            fileChooser.selectedFile = text?.let { File(it) }
            val state = fileChooser.showOpenDialog(SwingUtilities.windowForComponent(this))
            if (state == JFileChooser.APPROVE_OPTION) {
                text = fileChooser.selectedFile.toString()
            }
        }
        putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, openFileChooserButton)
        text = initialFile?.toString()
    }

    override fun setEnabled(enabled: Boolean) {
        openFileChooserButton.isEnabled = enabled
        super.setEnabled(enabled)
    }

}