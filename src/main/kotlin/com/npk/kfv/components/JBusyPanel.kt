package com.npk.kfv.components

import com.formdev.flatlaf.ui.FlatLineBorder
import com.npk.kfv.StopwatchTimer
import com.npk.kfv.synchronized
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class JBusyPanel : JPanel(GridBagLayout()) {

    companion object {
        private const val STOPWATCH_LABEL = "%02d : %02d"

        fun findGlassPaneForComponent(component: JComponent): JBusyPanel =
            (SwingUtilities.windowForComponent(component) as JFrame).glassPane as JBusyPanel

        fun findGlassPaneForComponent(dialog: JDialog): JBusyPanel =
            dialog.glassPane as JBusyPanel
    }

    private val progressBar = JProgressBar().also {
        it.isStringPainted = true
        it.isIndeterminate = true
        it.string = ""
        it.preferredSize = Dimension(it.preferredSize.width, it.preferredSize.height + 8)
    }
    private val progressLabel = JLabel()

    private val stopwatchTimer = StopwatchTimer { seconds ->
        progressBar.string = STOPWATCH_LABEL.format((seconds % 3600) / 60, seconds % 60)
    }

    init {
        isOpaque = false
        val panel = JPanel(GridBagLayout()).also { panel ->
            val constraint = GridBagConstraints().apply { insets = Insets(15, 25, 0, 25) }
            panel.border = FlatLineBorder(Insets(5, 5, 5, 5), Color.LIGHT_GRAY, 1.0f, 16)
            panel.add(progressBar, constraint.apply { gridy++ })
            panel.add(progressLabel, constraint.apply {
                gridy++
                insets.bottom = 15
            })
        }
        add(panel)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                event.consume()
            }
        })
    }

    var progressText: String
        get() = progressLabel.text
        set(value) { progressLabel.text = value }

    var isRunning: Boolean = false
        private set

    fun start() = synchronized {
        if (!isRunning) {
            isVisible = true
            isRunning = true

            progressBar.string = STOPWATCH_LABEL.format(0, 0)
            stopwatchTimer.reset()
            stopwatchTimer.start()
        }
    }

    fun stop() = synchronized {
        if (isRunning) {
            isRunning = false
            isVisible = false

            stopwatchTimer.stop()
            progressBar.string = ""
        }
    }

    override fun paintComponent(g: Graphics) {
        (g as Graphics2D).let { g2d ->
            g2d.color = background.let { Color(it.red, it.green, it.blue, 100) }
            g2d.fillRect(0, 0, width, height)
            super.paintComponent(g2d)
        }
    }

}