package com.npk.kfv.components

import com.npk.kfv.ApplicationImages
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.*

class JStacktracePanel private constructor(val component: JComponent): JOptionPane(component, ERROR_MESSAGE, DEFAULT_OPTION) {

    companion object {

        fun showMessageDialog(parent: Component?, title: String, message: String?, e: Throwable, expanded: Boolean = true) {
            val pane = JStacktracePanel(createContentPanel(message, e, expanded))
            pane.componentOrientation = (parent ?: getRootFrame()).componentOrientation

            val dialog = pane.createDialog(parent, title)
            pane.selectInitialValue()
            pane.component.addPropertyChangeListener("toggleExpansion") {
                dialog.pack()
            }

            dialog.minimumSize = Dimension(380, 0)
            dialog.isVisible = true
            dialog.dispose()

            parent?.let {
                it.revalidate()
                it.repaint()
            }
        }

        private fun createContentPanel(message: String?, e: Throwable, expanded: Boolean) = JPanel(BorderLayout(0, 10)).also { panel ->
            if (!message.isNullOrEmpty()) {
                panel.add(JLabel(message), BorderLayout.NORTH)
            }
            panel.add(StacktraceComponent(panel, e, expanded), BorderLayout.CENTER)
        }

    }

    private class StacktraceComponent(owner: JComponent, e: Throwable, expanded: Boolean) : JPanel(GridBagLayout()) {
        init {
            val content = JScrollPane(
                JTextArea(18, 0).apply {
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    isEditable = false
                    text = StringWriter().use { writer ->
                        e.printStackTrace(PrintWriter(writer))
                        writer.toString()
                    }
                    caretPosition = 0
                }
            )

            val header = JLabel("The exception stacktrace was").apply {
                foreground = Color.GRAY
                icon = ApplicationImages.Icons.Stacktrace.icon
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        val isShowing = content.isShowing()
                        content.isVisible = !isShowing
                        owner.firePropertyChange("toggleExpansion", isShowing, !isShowing)
                    }
                    override fun mouseEntered(event: MouseEvent) {
                        foreground = UIManager.get("Component.linkColor") as? Color
                    }
                    override fun mouseExited(enter: MouseEvent) {
                        foreground = Color.GRAY
                    }
                })
            }

            val constraints = GridBagConstraints().apply {
                insets = Insets(0, 0, 3, 0)
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                gridwidth = GridBagConstraints.REMAINDER
            }
            add(header, constraints)
            add(content, constraints)
            content.isVisible = expanded
        }
    }

}
