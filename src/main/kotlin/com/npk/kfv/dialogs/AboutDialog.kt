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
import kotlin.math.floor

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
        add(createMemoryPanel(), constraints.apply { gridy++ })
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

    private fun createMemoryPanel(): JPanel {
        val progressBar = JProgressBar(1, 100).apply {
            isStringPainted = true
            preferredSize = Dimension(preferredSize.width, preferredSize.height + 8)
            updateMemoryUsage()
        }

        val clearButton = JButton(ApplicationImages.Icons.Clear.icon).apply {
            isFocusable = false
            toolTipText = "Clean up the memory"
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
            addActionListener {
                Runtime.getRuntime().gc()
                progressBar.updateMemoryUsage()
            }
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JLabel("Memory:"))
            add(Box.createRigidArea(Dimension(10, 0)))
            add(progressBar)
            add(Box.createRigidArea(Dimension(3, 0)))
            add(clearButton)
        }
    }

    private fun JProgressBar.updateMemoryUsage() {
        val totalMem = Runtime.getRuntime().totalMemory() / 1024 / 1024
        val usedMem = totalMem - Runtime.getRuntime().freeMemory() / 1024 / 1024
        setValue(floor(usedMem * 100.0 / totalMem).toInt())
        setString(usedMem.toString() + "M of " + totalMem + "M")
    }

    private fun getJVMInformation(): String =
        """
        <html>
        Java version "${System.getProperty("java.version")}" ${System.getProperty("java.version.date")}, vendor "${System.getProperty("java.vendor")}"<br>
        ${System.getProperty("java.runtime.name")} (build ${System.getProperty("java.vm.version")})<br>
        </html>
        """.trimIndent()

}