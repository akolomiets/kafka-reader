package com.npk.kfv.dialogs

import com.formdev.flatlaf.FlatClientProperties
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.swing.*
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import kotlin.math.floor

class SystemInformationDialog(owner: Window) : JDialog(owner), View<SystemInformationViewModel> {

    override val viewModel: SystemInformationViewModel = SystemInformationViewModel()

    private val threadsCountTextField = jtextfield {
        it.isEditable = false
    }

    private val cpuLoadProgressBar = JProgressBar(1, 100).apply {
        foreground = Color.decode("#FF7F50")
        isStringPainted = true
    }

    private val memoryUsageProgressBar = JProgressBar(1, 100).apply {
        foreground = Color.decode("#4682B4")
        isStringPainted = true
    }

    init {
        title = ApplicationMessages["systemInfoDialog.title"]
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isModal = true

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                viewModel.stopMonitoringSystemMetrics()
            }
        })

        val okButton = jbutton(ApplicationMessages.ok, { dispose() })

        with(contentPane) {
            layout = MigLayout("insets 0 10 10 10, gap 10")
            add(systemInformationPanel(), "push, grow, wrap")
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

        viewModel.addPropertyChangeListener(SystemInformationViewModel::systemMetrics.name) { event ->
            (event.newValue as SystemInformationViewModel.SystemMetrics).let { systemMetrics ->
                threadsCountTextField.text = "${systemMetrics.threadCount} / ${systemMetrics.daemonThreadCount}"
                if (systemMetrics.cpuLoad >= 0) {
                    cpuLoadProgressBar.value = systemMetrics.cpuLoad
                }
                val totalMem = systemMetrics.totalMemory / 1024 / 1024
                val usedMem = systemMetrics.freeMemory / 1024 / 1024
                memoryUsageProgressBar.value = floor(usedMem * 100.0 / totalMem).toInt()
                memoryUsageProgressBar.string = "$usedMem MB of $totalMem MB"
            }
        }

        viewModel.startMonitoringSystemMetrics()
    }

    private fun systemInformationPanel() = JTabbedPane().also { panel ->
        panel.addTab(ApplicationMessages["systemInfoDialog.basic"], jscrollpane(basicInformationPanel()))
        panel.addTab(ApplicationMessages["systemInfoDialog.prop"], jscrollpane(jtable(viewModel.systemPropertiesTableModel)))
        panel.addTab(ApplicationMessages["systemInfoDialog.env"], jscrollpane(jtable(viewModel.environmentVariablesTableModel)))
    }

    private fun basicInformationPanel() = jpanel(MigLayout("insets 10, gap 10")) {
        +jlabel(ApplicationMessages["systemInfoDialog.basic.hostname"])
        +(jtextfield {
            it.isEditable = false
            it.text = SystemInformationViewModel.HOST_NAME
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.username"])
        +(jtextfield {
            it.isEditable = false
            it.text = SystemInformationViewModel.USER_NAME
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.os"])
        +(jtextfield {
            it.isEditable = false
            it.text = SystemInformationViewModel.OS_INFO
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.locale"])
        +(jtextfield {
            it.isEditable = false
            it.text = viewModel.defaultLocale
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.rt"])
        +(jtextfield {
            it.isEditable = false
            it.text = viewModel.runtimeInfo
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.rt-path"])
        +(jtextfield {
            it.isEditable = false
            it.text = SystemInformationViewModel.RUNTIME_LOCATION
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.threads"])
        +(threadsCountTextField to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.cpu"])
        +(jtextfield {
            it.isEditable = false
            it.text = SystemInformationViewModel.CPU_INFO
        } to "pushx, growx, wrap")

        +jlabel(ApplicationMessages["systemInfoDialog.basic.cpu-load"])
        +(jlabel(ApplicationImages.Icons.Test.icon) to "split 2, gapleft 3")
        +(cpuLoadProgressBar to "pushx, growx, wrap")

        +(jlabel(ApplicationMessages["systemInfoDialog.basic.mem"]))
        +(jbutton(ApplicationImages.Icons.Clear.icon, { Runtime.getRuntime().gc() }) {
            it.isFocusable = false
            it.toolTipText = ApplicationMessages["systemInfoDialog.basic.mem.clean"]
            it.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS)
        } to "split 2, gapright 3")
        +(memoryUsageProgressBar to "pushx, growx")
    }

}
