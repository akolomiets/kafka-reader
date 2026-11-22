package com.npk.kfv.dialogs

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.APPLICATION_NAME
import com.npk.kfv.APPLICATION_VERSION
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.kfv.components.JStatusLabel
import com.npk.swing.View
import com.npk.swing.jbutton
import com.npk.swing.jlabel
import com.npk.swing.jpanel
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

object UpdateDialog {

    fun showDialog(owner: Window) {
        JOptionPane.showMessageDialog(
            owner,
            UpdatePanel(),
            ApplicationMessages["updateDialog.title"],
            JOptionPane.INFORMATION_MESSAGE,
            ImageIcon(ApplicationImages.logoUpdate)
        )
    }

}

private class UpdatePanel : JPanel(GridBagLayout()), View<UpdateViewModel> {

    override val viewModel: UpdateViewModel = UpdateViewModel()

    private val checkButton = jbutton(ApplicationImages.Icons.Refresh.icon, ::onCheckActionPerformed) { btn ->
        btn.isFocusable = false
        btn.toolTipText = ApplicationMessages["updateDialog.title"]
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    }
    private val checkProgress = JProgressBar()
    private val checkStatus = JStatusLabel(false).apply { text = "\u00a0" }

    init {
        val constraints = GridBagConstraints()
        constraints.anchor = GridBagConstraints.FIRST_LINE_START
        constraints.insets = Insets(0, 0, 12, 0)

        val applicationInfo = jlabel("$APPLICATION_NAME $APPLICATION_VERSION") {
            val font = it.font
            it.font = font.deriveFont(font.style, font.size * 1.8f)
            it.putClientProperty(FlatClientProperties.STYLE_CLASS, "h2")
        }
        add(applicationInfo, constraints.apply { gridy = 1 })

        constraints.insets = Insets(0, 0, 0, 0)
        add(
            jpanel(FlowLayout(FlowLayout.LEFT, 0, 0)) { pnl ->
                pnl.preferredSize = Dimension(applicationInfo.preferredSize.width + 26, checkButton.preferredSize.height + 6)
                pnl.add(checkButton)
                pnl.add(checkProgress)
            },
            constraints.apply { gridy++ }
        )

        constraints.insets = Insets(0, 6, 0, 0)
        add(checkStatus, constraints.apply { gridy++ })

        SwingUtilities.invokeLater { onCheckActionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Checking")) }
    }

    private fun onCheckActionPerformed(event: ActionEvent) {
        checkButton.isEnabled = false
        checkProgress.isIndeterminate = true
        checkStatus.clean()
        checkStatus.text = ApplicationMessages["updateDialog.process"]

        viewModel.checkForUpdates { result ->
            checkButton.isEnabled = true
            checkProgress.isIndeterminate = false
            checkProgress.value = if (result.isSuccess) 100 else 0

            result
                .onSuccess { (code, tag, uri) ->
                    when (code) {
                        200 -> checkStatus.success(ApplicationMessages["updateDialog.success"])
                        201 if (uri != null) -> {
                            checkStatus.information(ApplicationMessages["updateDialog.updates", tag])
                            checkStatus.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            checkStatus.addMouseListener(object : MouseAdapter() {
                                override fun mouseClicked(event: MouseEvent) {
                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                        Desktop.getDesktop().browse(uri)
                                    }
                                }
                                override fun mouseEntered(e: MouseEvent) {
                                    checkStatus.foreground = UIManager.get("Component.linkColor") as? Color
                                }
                                override fun mouseExited(enter: MouseEvent) {
                                    checkStatus.foreground = null
                                }
                            })
                        }
                    }
                }
                .onFailure { e -> checkStatus.failure(ApplicationMessages["updateDialog.failure"], e) }
        }
    }

}
