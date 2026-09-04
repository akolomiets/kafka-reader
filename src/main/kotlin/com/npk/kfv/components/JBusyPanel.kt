package com.npk.kfv.components

import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.renderer.awt.AwtComponentPlatformSupport
import com.github.weisj.jsvg.renderer.output.Output
import com.github.weisj.jsvg.ui.AnimationPlayer
import com.github.weisj.jsvg.view.ViewBox
import com.npk.kfv.StopwatchTimer
import com.npk.kfv.requireResource
import com.npk.kfv.synchronized
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class JBusyPanel : JPanel(GridBagLayout()) {

    private class SpinnerComponent : JComponent() {

        private val document = requireNotNull(SVGLoader().load(requireResource("/ico/spinner.svg")))
        private val player = AnimationPlayer({ parent?.repaint() }).also { it.setAnimation(document.animation()) }

        override fun getWidth(): Int = 64

        override fun getHeight(): Int = 64

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            (g as Graphics2D).let { g2d ->
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

                document.renderWithPlatform(
                    AwtComponentPlatformSupport(this),
                    Output.createForGraphics(g2d),
                    ViewBox(0f, 0f, getWidth().toFloat(), getHeight().toFloat()),
                    player.animationState()
                )
            }
        }

        fun startAnimation() {
            player.start()
        }

        fun stopAnimation() {
            player.stop()
        }

    }

    companion object {
        private const val STOPWATCH_LABEL = "%02d : %02d"

        fun findGlassPaneForComponent(component: JComponent): JBusyPanel =
            (SwingUtilities.windowForComponent(component) as JFrame).glassPane as JBusyPanel

        fun findGlassPaneForComponent(dialog: JDialog): JBusyPanel =
            dialog.glassPane as JBusyPanel
    }

    private val spinnerComponent = SpinnerComponent()
    private val stopwatchLabel = JLabel()
    private val progressLabel = JLabel()

    private val stopwatchTimer = StopwatchTimer { seconds ->
        stopwatchLabel.text = STOPWATCH_LABEL.format((seconds % 3600) / 60, seconds % 60)
    }

    init {
        isOpaque = false

        val constraints = GridBagConstraints()

        constraints.apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            anchor = GridBagConstraints.CENTER
            insets = Insets(0, -spinnerComponent.width, spinnerComponent.height, 0)
        }
        add(spinnerComponent, constraints)

        constraints.apply {
            gridx = 0
            gridy = 1
            weightx = 1.0
            weighty = 0.0
            anchor = GridBagConstraints.PAGE_END
            insets = Insets(0, 0, 5, 0)
        }
        add(stopwatchLabel, constraints)

        constraints.apply {
            gridx = 0
            gridy = 2
            weightx = 1.0
            weighty = 0.0
            anchor = GridBagConstraints.PAGE_END
            insets = Insets(0, 0, 20, 0)
        }
        add(progressLabel, constraints)

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

            spinnerComponent.startAnimation()
            stopwatchLabel.text = STOPWATCH_LABEL.format(0, 0)
            stopwatchTimer.reset()
            stopwatchTimer.start()
        }
    }

    fun stop() = synchronized {
        if (isRunning) {
            isRunning = false
            isVisible = false

            stopwatchTimer.stop()
            stopwatchLabel.text = ""
            spinnerComponent.stopAnimation()
        }
    }

    override fun paintComponent(g: Graphics) {
        (g as Graphics2D).let { g2d ->
            val oldPainter = g2d.paint

            g2d.color = background.let { Color(it.red, it.green, it.blue, 100) }
            g2d.fillRect(0, 0, width, height)

            g2d.paint = GradientPaint(
                0f, (height - 260).toFloat(), background.let { Color(it.red, it.green, it.blue, 0) },
                0f, height.toFloat(), background.let { Color(it.red, it.green, it.blue, 255) }
            )
            g2d.fillRect(0, height - 260, width, 260)

            g2d.paint = GradientPaint(
                0f, (height - 140).toFloat(), background.let { Color(it.red, it.green, it.blue, 0) },
                0f, height.toFloat(), background.let { Color(it.red, it.green, it.blue, 255) }
            )
            g2d.fillRect(0, height - 140, width, 140)

            g2d.paint = oldPainter
        }
    }

}
