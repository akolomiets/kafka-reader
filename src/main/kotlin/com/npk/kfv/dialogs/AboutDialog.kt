package com.npk.kfv.dialogs

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.util.*
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.swing.*

object AboutDialog {

    fun showDialog(owner: Window) {
        JOptionPane.showMessageDialog(
            owner,
            AboutPanel(),
            ApplicationMessages["aboutDialog.title", APPLICATION_NAME],
            JOptionPane.INFORMATION_MESSAGE,
            ImageIcon(ApplicationImages.logo)
        )
    }

}

private class AboutPanel : JPanel(GridBagLayout()) {

    init {
        val constraints = GridBagConstraints()

        val applicationInfo = JLabel("$APPLICATION_NAME $APPLICATION_VERSION").apply {
            val font = getFont()
            setFont(font.deriveFont(font.style, font.size * 1.8f))
            putClientProperty(FlatClientProperties.STYLE_CLASS, "h2")
        }

        constraints.anchor = GridBagConstraints.FIRST_LINE_START
        add(applicationInfo, constraints)

        constraints.insets = Insets(0, 0, 12, 0)
        add(JLabel(getBuildInformation()), constraints.apply { gridy = 1 })
        add(JLabel(getJVMInformation()), constraints.apply { gridy++ })
        add(JLabel("Copyright \u00A9 2026 Andrey Kolomiets"), constraints.apply { gridy++ })

        val hyperlinkLabel = JLabel(APPLICATION_GITHUB_HYPERLINK.toString()).apply {
            icon = ApplicationImages.Icons.GitHub.icon
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(APPLICATION_GITHUB_HYPERLINK)
                    }
                }
                override fun mouseEntered(e: MouseEvent) {
                    foreground = UIManager.get("Component.linkColor") as? Color
                }
                override fun mouseExited(enter: MouseEvent) {
                    foreground = null
                }
            })
        }
        constraints.insets = Insets(0, 0, 0, 0)
        add(hyperlinkLabel, constraints.apply { gridy++ })
    }

    private fun getBuildInformation(): String =
        try {
            javaClass.getResourceAsStream("/" + JarFile.MANIFEST_NAME).use { inputStream ->
                val mainAttributes = Manifest(inputStream).mainAttributes
                val builtOn = Objects.toString(mainAttributes.getValue("Implementation-Build-Time"), Instant.now().toString())
                "Built on $builtOn"
            }
        } catch (_: Exception) {
            "#Undefined"
        }

    private fun getJVMInformation(): String =
        """
        <html>
        Java version "${System.getProperty("java.version")}" ${System.getProperty("java.version.date")}, vendor "${System.getProperty("java.vendor")}"<br>
        ${System.getProperty("java.runtime.name")} (build ${System.getProperty("java.vm.version")})<br>
        </html>
        """.trimIndent()

}