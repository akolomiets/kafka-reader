package com.npk.kfv.components

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.npk.kfv.ApplicationImages
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.*
import javax.swing.JLabel
import javax.swing.SwingUtilities

class JStatusLabel(var styleBackground: Boolean = true) : JLabel() {

    companion object {
        private val LIGHT_SUCCESS_BACKGROUND_COLOR =        Color(Integer.decode("#f2fcf3"))
        private val LIGHT_FAILURE_BACKGROUND_COLOR =        Color(Integer.decode("#fff7f7"))
        private val LIGHT_INFORMATION_BACKGROUND_COLOR =    Color(Integer.decode("#e7effd"))
        private val DARK_SUCCESS_BACKGROUND_COLOR =         Color(Integer.decode("#254025"))
        private val DARK_FAILURE_BACKGROUND_COLOR =         Color(Integer.decode("#402929"))
        private val DARK_INFORMATION_BACKGROUND_COLOR =     Color(Integer.decode("#292940"))

        private const val LABEL_STYLE = "border: 5,8,5,8; arc: 16;"

        private val fadeOutTimer: Timer = Timer("FadeOutTimer-", true)
    }

    private var fadeOutTimerTask: TimerTask? = null

    fun success(message: String) {
        clean()
        icon = ApplicationImages.Icons.Success.icon
        text = message
        if (styleBackground) {
            background = if (FlatLaf.isLafDark()) DARK_SUCCESS_BACKGROUND_COLOR else LIGHT_SUCCESS_BACKGROUND_COLOR
            //putClientProperty(FlatClientProperties.STYLE, "border: 5,5,5,5, #bde0c5, 1.0f, 15; arc: 15;")
            putClientProperty(FlatClientProperties.STYLE, LABEL_STYLE)
        }
    }

    fun success(message: String, duration: Duration) {
        success(message)
        startFadeOutTimer(duration)
    }

    fun failure(message: String) {
        clean()
        icon = ApplicationImages.Icons.Failure.icon
        text = message
        if (styleBackground) {
            background = if (FlatLaf.isLafDark()) DARK_FAILURE_BACKGROUND_COLOR else LIGHT_FAILURE_BACKGROUND_COLOR
            //putClientProperty(FlatClientProperties.STYLE, "border: 5,5,5,5, #eababa, 1.0f, 15; arc: 15;")
            putClientProperty(FlatClientProperties.STYLE, LABEL_STYLE)
        }
    }

    fun failure(message: String, duration: Duration) {
        failure(message)
        startFadeOutTimer(duration)
    }

    fun failure(message: String, throwable: Throwable) {
        failure(message)
        toolTipText = throwable.toString()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                JStacktracePanel.showMessageDialog(
                    SwingUtilities.windowForComponent(this@JStatusLabel),
                    "Exception",
                    throwable.message,
                    throwable,
                    throwable.message.isNullOrEmpty()
                )
            }
        })
    }

    fun information(message: String) {
        clean()
        icon = ApplicationImages.Icons.Information.icon
        text = message
        if (styleBackground) {
            background = if (FlatLaf.isLafDark()) DARK_INFORMATION_BACKGROUND_COLOR else LIGHT_INFORMATION_BACKGROUND_COLOR
            //putClientProperty(FlatClientProperties.STYLE, "border: 5,5,5,5, #b3cbfc, 1.0f, 15; arc: 15;")
            putClientProperty(FlatClientProperties.STYLE, LABEL_STYLE)
        }
    }

    fun information(message: String, duration: Duration) {
        information(message)
        startFadeOutTimer(duration)
    }

    fun clean() {
        icon = null
        text = null
        toolTipText = null
        background = null
        putClientProperty(FlatClientProperties.STYLE, null)
        cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
        mouseListeners.forEach(::removeMouseListener)
        fadeOutTimerTask?.cancel()
        fadeOutTimer.purge()
    }

    private fun startFadeOutTimer(duration: Duration) {
        fadeOutTimerTask = object : TimerTask() {
            private val forceHide = !styleBackground
            override fun run() {
                if (forceHide) {
                    cancel()
                    clean()
                }
                if (background.alpha <= 10) {
                    cancel()
                    clean()
                } else {
                    background = Color(background.red, background.green, background.blue, background.alpha - 3)
                }
            }
        }
        fadeOutTimer.schedule(fadeOutTimerTask, duration.toMillis(), 80L)
    }

}
