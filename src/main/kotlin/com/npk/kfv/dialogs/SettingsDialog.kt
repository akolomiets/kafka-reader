package com.npk.kfv.dialogs

import com.formdev.flatlaf.*
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.FontUtils
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.UIHelper
import com.npk.swing.*
import com.npk.swing.BindingHelper.bind
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.*
import javax.swing.plaf.basic.BasicComboBoxRenderer

class SettingsDialog(owner: Window) : JDialog(owner), View<SettingsViewModel> {

    override val viewModel: SettingsViewModel = SettingsViewModel()

    private val fontFamilyLabel = jlabel(ApplicationMessages["settingsDialog.appearance.font.name"])
    private val fontFamilyComboBox = jfontfamilycombobox()
    private val fontSizeLabel = jlabel(ApplicationMessages["settingsDialog.appearance.font.size"])
    private val fontSizeComboBox = jfontsizecombobox()

    init {
        title = ApplicationMessages["settingsDialog.title"]
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = false
        isModal = true

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                if (viewModel.needRestart) {
                    JOptionPane.showMessageDialog(
                        SwingUtilities.windowForComponent(this@SettingsDialog),
                        ApplicationMessages["settingsDialog.warning.message"],
                        ApplicationMessages["messageDialog.warning.title"],
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        })

        val okButton = jbutton(ApplicationMessages.ok, { dispose() })

        with(contentPane) {
            layout = MigLayout("insets 10, gap 10")

            add(appearancePanel(), "push, grow, wrap")
            add(okButton, "alignx right")
        }
        with(rootPane) {
            defaultButton = okButton
            registerKeyboardAction(
                { dispose() },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            )
        }

        updateFontControls()
    }

    private fun appearancePanel() = jpanel(MigLayout("insets 20, gap 10")) {
        it.border = BorderFactory.createTitledBorder(" ${ApplicationMessages["settingsDialog.appearance.title"]} ")

        +jlabel(ApplicationMessages["settingsDialog.appearance.locale"])
        +(jlocalecombobox().also { it.bind(viewModel, SettingsViewModel::locale) } to "wrap")

        val lookAndFeelComboBox = jlookandfeelcombobox().also {
            it.bind(viewModel, SettingsViewModel::lookAndFeel)
            it.addItemListener(::onLookAndFeelChanged)
        }
        +jlabel(ApplicationMessages["settingsDialog.appearance.laf"])
        +(lookAndFeelComboBox to "wrap")

        +jlabel(ApplicationMessages["settingsDialog.appearance.scale"])
        val zoomComboBox = jcombobox(listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2f)) {
            it.renderer = object : BasicComboBoxRenderer() {
                override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component =
                    super.getListCellRendererComponent(list, "${((value as Float) * 100).toInt()}%", index, isSelected, cellHasFocus)
            }
            it.bind(viewModel, SettingsViewModel::uiScale)
        }
        +(zoomComboBox to "split 2")
        +(jlabel(ApplicationMessages["settingsDialog.appearance.scale.hint"]) to "wrap")

        +fontFamilyLabel
        +fontFamilyComboBox.also { it.addItemListener(::onFontFamilyChanged) }

        +fontSizeLabel
        +fontSizeComboBox.also { it.addItemListener(::onFontSizeChanged) }
    }

    private fun onLookAndFeelChanged(event: ItemEvent) {
        if (event.stateChange == ItemEvent.SELECTED) {
            EventQueue.invokeLater {
                try {
                    FlatAnimatedLafChange.showSnapshot()
                    UIManager.setLookAndFeel(viewModel.lookAndFeel.className)
                    if (UIHelper.isFlatLaf()) {
                        UIHelper.tryUpdateDefaultFont(viewModel.fontFamily, viewModel.fontSize)
                    } else {
                        UIManager.put("defaultFont", null)
                    }
                    updateFontControls()
                    FlatLaf.updateUI()
                    this.pack()
                } finally {
                    FlatAnimatedLafChange.hideSnapshotWithAnimation()
                }
            }
        }
    }

    private fun onFontFamilyChanged(event: ItemEvent) {
        if (event.stateChange == ItemEvent.SELECTED && (event.source as Component).isFocusOwner) {
            EventQueue.invokeLater {
                try {
                    FlatAnimatedLafChange.showSnapshot()
                    viewModel.fontFamily = event.item.toString()
                    UIHelper.tryUpdateDefaultFont(viewModel.fontFamily, null)
                    FlatLaf.updateUI()
                    this.pack()
                } finally {
                    FlatAnimatedLafChange.hideSnapshotWithAnimation()
                }
            }
        }
    }

    private fun onFontSizeChanged(event: ItemEvent) {
        if (event.stateChange == ItemEvent.SELECTED && (event.source as Component).isFocusOwner) {
            EventQueue.invokeLater {
                try {
                    FlatAnimatedLafChange.showSnapshot()
                    viewModel.fontSize = event.item as Int
                    UIHelper.tryUpdateDefaultFont(null, viewModel.fontSize)
                    FlatLaf.updateUI()
                    this.pack()
                } finally {
                    FlatAnimatedLafChange.hideSnapshotWithAnimation()
                }
            }
        }
    }

    private fun updateFontControls() {
        val currentFont = UIHelper.getCurrentFont()
        fontFamilyComboBox.selectedItem = currentFont.family
        fontSizeComboBox.selectedItem = currentFont.size

        val enableControls = UIHelper.isFlatLaf()
        fontFamilyLabel.isEnabled = enableControls
        fontFamilyComboBox.isEnabled = enableControls
        fontSizeLabel.isEnabled = enableControls
        fontSizeComboBox.isEnabled = enableControls
    }

    private fun jlocalecombobox() = JComboBox<Locale>().apply {
        renderer = object : BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val displayValue = (value as Locale).getDisplayLanguage(value)
                return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus)
            }
        }
        model = DefaultComboBoxModel(Vector(ApplicationMessages.availableLocales))
    }

    private fun jlookandfeelcombobox() = JComboBox<SettingsViewModel.LookAndFeelInfo>().apply {
        renderer = object : BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val displayValue = (value as SettingsViewModel.LookAndFeelInfo).name
                return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus)
            }
        }
        model = DefaultComboBoxModel<SettingsViewModel.LookAndFeelInfo>().apply {
            val lafInfos = buildList {
                add(SettingsViewModel.LookAndFeelInfo(FlatLightLaf.NAME, FlatLightLaf::class.java.name))
                add(SettingsViewModel.LookAndFeelInfo(FlatDarkLaf.NAME, FlatDarkLaf::class.java.name))
                add(SettingsViewModel.LookAndFeelInfo(FlatIntelliJLaf.NAME, FlatIntelliJLaf::class.java.name))
                add(SettingsViewModel.LookAndFeelInfo(FlatDarculaLaf.NAME, FlatDarculaLaf::class.java.name))
                add(SettingsViewModel.LookAndFeelInfo(FlatMacLightLaf.NAME, FlatMacLightLaf::class.java.name))
                add(SettingsViewModel.LookAndFeelInfo(FlatMacDarkLaf.NAME, FlatMacDarkLaf::class.java.name))

                UIManager.getInstalledLookAndFeels().forEach { installedLookAndFeel ->
                    val className = installedLookAndFeel.className
                    if (className != "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel" && className != "com.sun.java.swing.plaf.motif.MotifLookAndFeel") {
                        add(SettingsViewModel.LookAndFeelInfo(installedLookAndFeel.name, installedLookAndFeel.className))
                    }
                }
            }
            addAll(lafInfos)
        }
    }

    private fun jfontfamilycombobox() = JComboBox<String>().apply {
        model = DefaultComboBoxModel(FontUtils.getAvailableFontFamilyNames())
    }

    private fun jfontsizecombobox() = JComboBox<Int>().apply {
        model = DefaultComboBoxModel(arrayOf(10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28))
    }

}
