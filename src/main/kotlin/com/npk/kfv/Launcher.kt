package com.npk.kfv

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.util.SystemInfo
import com.npk.kfv.components.JStacktracePanel
import com.npk.kfv.service.ConfigService
import java.awt.Dimension
import java.awt.EventQueue
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main(args: Array<String>) {
    Launcher().launch()
}

class Launcher {

    private val logger: Logger

    init {
        ConfigService.init()
        logger = Logger.getLogger(this.javaClass.getName())
    }

    fun launch() {
        logger.log(Level.INFO, "---")
        logger.log(Level.INFO, "Start $APPLICATION_NAME - $APPLICATION_VERSION")

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Logger.getLogger("com.npk.kfv").log(Level.SEVERE, "Uncaught Exception", e)
            EventQueue.invokeLater {
                JStacktracePanel.showMessageDialog(null, "Uncaught Exception", e.message, e)
                Runtime.getRuntime().exit(1)
            }
        }

        when {
            SystemInfo.isMacOS -> {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                System.setProperty("apple.awt.application.name", APPLICATION_NAME);
                System.setProperty("apple.awt.application.appearance", "system");
            }
            SystemInfo.isLinux -> {
                JFrame.setDefaultLookAndFeelDecorated(true)
                JDialog.setDefaultLookAndFeelDecorated(true)
            }
        }

        if (System.getProperty("sun.java2d.uiScale") == null) {
            val uiScale = String.format(Locale.ROOT, "%.1f", ApplicationPrefs.uiScale)
            if (uiScale != "1.0") {
                System.setProperty("sun.java2d.uiScale", uiScale)
                logger.log(Level.INFO, "Set 'sun.java2d.uiScale' to $uiScale")
            }
        }
        if (System.getProperty("swing.defaultlaf").isNullOrEmpty()) {
            try {
                FlatLaf.registerCustomDefaultsSource("com.npk.kfv.themes")
                UIManager.setLookAndFeel(ApplicationPrefs.lookAndFeelClassName)
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Error while setting the LAF '${ApplicationPrefs.lookAndFeelClassName}'", e)
                ApplicationPrefs.lookAndFeelClassName = FlatLightLaf::class.java.getName()
                FlatLightLaf.setup()
            }
        }
        if (UIHelper.isFlatLaf()) {
            UIHelper.tryUpdateDefaultFont(ApplicationPrefs.fontFamily, ApplicationPrefs.fontSize)
        }

        SwingUtilities.invokeLater {
            ApplicationFrame().run {
                ApplicationPrefs.windowBounds?.let {
                    bounds = it
                    preferredSize = Dimension(it.width, it.height)
                }
                extendedState = ApplicationPrefs.windowState

                pack()
                isVisible = true
            }
        }
    }

}