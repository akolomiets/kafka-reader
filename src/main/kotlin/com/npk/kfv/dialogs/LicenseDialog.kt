package com.npk.kfv.dialogs

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.requireResource
import java.awt.Font
import java.awt.Window
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

object LicenseDialog {

    fun showDialog(owner: Window) {
        JOptionPane.showMessageDialog(
            owner,
            LicensePanel(),
            ApplicationMessages["licenseDialog.title"],
            JOptionPane.PLAIN_MESSAGE
        )
    }

}

private class LicensePanel : JPanel() {

    init {
        val textArea = JTextArea(25, 80).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
            isEditable = false
            loadLicenseText()
            caretPosition = 0
        }
        add(JScrollPane(textArea))
    }

    private fun JTextArea.loadLicenseText() {
        try {
            requireResource("/LICENSE").openStream().use { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).forEachLine { line ->
                    append("$line\n")
                }
            }
        } catch (e: Exception) {
            Logger.getLogger(this.javaClass.getName()).log(Level.SEVERE, "Cannot load resource: /LICENSE", e)
            append("Apache License Version 2.0 (http://www.apache.org/licenses)")
        }
    }

}
