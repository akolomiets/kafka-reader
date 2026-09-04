package com.npk.kfv

import com.formdev.flatlaf.FlatClientProperties.*
import com.npk.kfv.broker.BrokerPanel
import com.npk.kfv.broker.BrokerPanelViewModel
import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.dialogs.*
import com.npk.kfv.service.ConfigBroker
import com.npk.kfv.service.ConfigBrokersUpdatedEvent
import com.npk.kfv.service.ConfigService
import com.npk.kfv.service.EventService
import com.npk.swing.jaction
import com.npk.swing.jbutton
import com.npk.swing.jlabel
import com.npk.swing.jpanel
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.*

class ApplicationFrame : JFrame("$APPLICATION_NAME - $APPLICATION_VERSION") {

    private var configBrokers: List<ConfigBroker> = emptyList()

    private val addNewAction = jaction(::onAddNew) {
        name = ApplicationMessages["frame.addNew"]
        icon = ApplicationImages.Icons.Plus.icon
        tooltip = name
    }
    private val reloadAction = jaction(::onReloadData) {
        name = ApplicationMessages["frame.reload"]
        icon = ApplicationImages.Icons.Refresh.icon
        tooltip = "$name (F5)"
    }

    private val brokersTabbedPanel: JTabbedPane = JTabbedPane().apply {
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT)

        putClientProperty(TABBED_PANE_TAB_TYPE, TABBED_PANE_TAB_TYPE_UNDERLINED)
        putClientProperty(
            TABBED_PANE_LEADING_COMPONENT,
            JLabel(" Connections:  ", SwingConstants.LEADING).apply {
                putClientProperty(STYLE_CLASS, "small, semibold")
            }
        )
        putClientProperty(
            TABBED_PANE_TRAILING_COMPONENT,
            JToolBar().apply {
                isFocusable = false
                border = null

                add(jbutton(addNewAction) { it.text = null })
                add(Box.createHorizontalGlue())
                add(jbutton(reloadAction))
            }
        )

        addChangeListener { if (selectedIndex >= 0) ApplicationPrefs.brokersSelected = selectedIndex }

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), ::reloadAction.name)
        actionMap.put(::reloadAction.name, reloadAction)
    }

    private val bannerPanel: JPanel by lazy {
        jpanel(GridBagLayout()) {
            val constraints = GridBagConstraints()

            constraints.gridy = 1
            constraints.insets = Insets(0, 0, 20, 0)
            +(jlabel(ApplicationImages.bannerLogo) to constraints)

            constraints.gridy++
            constraints.insets = Insets(10, 0, 0, 0)
            +(jlabel(ApplicationMessages["frame.noBrokerPanel.text"]) to constraints)

            constraints.gridy++
            val addNewButton = jbutton(addNewAction) {
                it.toolTipText = null
                it.isFocusable = false
                it.putClientProperty(BUTTON_TYPE, BUTTON_TYPE_TOOLBAR_BUTTON)
            }
            +(addNewButton to constraints)

            constraints.gridy++
            constraints.insets = Insets(20, 0, 0, 0)
            +(jlabel(ApplicationMessages.randomQuote) to constraints)
        }
    }

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        iconImages = ApplicationImages.applicationIcons
        minimumSize = Dimension(800, 600)
        preferredSize = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .displayMode.let { Dimension(it.width - 80, it.height - 80) }

        jMenuBar = JMenuBar().also { menuBar ->
            menuBar.add(
                JMenu(ApplicationMessages["menu.file"]).also { menu ->
                    menu.add(jaction(ApplicationMessages["menu.file.brokers"], ApplicationImages.Icons.Kafka.icon, ::onBrokers))
                    menu.addSeparator()
                    menu.add(jaction(ApplicationMessages["menu.file.settings"], ApplicationImages.Icons.Settings.icon, ::onSettings))
                    menu.addSeparator()
                    menu.add(jaction(ApplicationMessages["menu.file.exit"], ApplicationImages.Icons.Exit.icon, ::onExit))
                }
            )
            menuBar.add(
                JMenu(ApplicationMessages["menu.help"]).also { menu ->
                    menu.add(jaction(ApplicationMessages["menu.help.license"], ApplicationImages.Icons.Scales.icon, ::onLicense))
                    menu.addSeparator()
                    menu.add(jaction(ApplicationMessages["menu.help.about"], ::onAbout))
                }
            )
        }

        contentPane = jpanel(BorderLayout()) {
            it.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }

        glassPane = JBusyPanel()

        addWindowStateListener { event ->
            event.getNewState().let { state ->
                if ((state and ICONIFIED) == 0) {
                    ApplicationPrefs.windowState = state
                }
            }
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                onExit(ActionEvent(event.source, event.id, "windowClosing"))
            }
        })

        EventService.Default.addEventListener(ConfigBrokersUpdatedEvent::class) {
            Logger.getLogger(this.javaClass.getName()).log(Level.INFO, "Reload broker configs")

            val busyPanel = (glassPane as JBusyPanel).also { panel ->
                panel.progressText = ApplicationMessages["frame.reload.process"]
                panel.start()
            }

            SwingUtilities.invokeLater {
                val selectedIndex = brokersTabbedPanel.selectedIndex

                removeConfigAndDestroyBrokerTabs()
                loadConfigAndConstructBrokerTabs()

                brokersTabbedPanel.selectedIndex = brokersTabbedPanel.tabCount.let { tabCount ->
                    if (tabCount > 0) {
                        if (tabCount > selectedIndex && selectedIndex != -1) {
                            selectedIndex
                        } else {
                            tabCount - 1
                        }
                    } else {
                        -1
                    }
                }

                busyPanel.stop()
                contentPane.run {
                    revalidate()
                    repaint()
                }
            }
        }

        val brokersSelected = ApplicationPrefs.brokersSelected
        loadConfigAndConstructBrokerTabs()
        if (configBrokers.size > brokersSelected) {
            brokersTabbedPanel.selectedIndex = brokersSelected
        }
    }

    //region Actions

    private fun onAddNew(event: ActionEvent) {
        val model = BrokersManagerViewModel(configBrokers)
        val (index) = model.addNewConnection()
        BrokersManagerDialog(this, model, index).run {
            setLocationRelativeTo(this@ApplicationFrame)
            pack()
            isVisible = true
        }
    }

    private fun onReloadData(event: ActionEvent) {
        (brokersTabbedPanel.selectedComponent as BrokerPanel).onReloadData(event)
    }

    private fun onBrokers(event: ActionEvent) {
        BrokersManagerDialog(this, BrokersManagerViewModel(configBrokers), brokersTabbedPanel.selectedIndex).run {
            setLocationRelativeTo(this@ApplicationFrame)
            pack()
            isVisible = true
        }
    }

    private fun onSettings(event: ActionEvent) {
        SettingsDialog(this).run {
            pack()
            setLocationRelativeTo(this@ApplicationFrame)
            isVisible = true
        }
    }

    private fun onExit(event: ActionEvent) {
        if (extendedState == NORMAL) {
            ApplicationPrefs.windowBounds = bounds
        }

        var componentCount = brokersTabbedPanel.tabCount
        while (componentCount-- > 0) {
            (brokersTabbedPanel.getComponentAt(componentCount) as BrokerPanel).dispose()
        }

        dispose()
    }

    private fun onLicense(event: ActionEvent) {
        LicenseDialog.showDialog(this)
    }

    private fun onAbout(event: ActionEvent) {
        AboutDialog.showDialog(this)
    }

    //endregion

    private fun loadConfigAndConstructBrokerTabs() {
        configBrokers = ConfigService.loadConfigBrokers()
        configBrokers.forEach { configBroker ->
            brokersTabbedPanel.addTab(configBroker.name, BrokerPanel(BrokerPanelViewModel(configBroker)))
        }

        if (configBrokers.isNotEmpty()) {
            contentPane.add(brokersTabbedPanel, BorderLayout.CENTER)
        } else {
            contentPane.add(bannerPanel, BorderLayout.CENTER)
        }

        addNewAction.isEnabled = configBrokers.size < ApplicationPrefs.brokersMaxCount
    }

    private fun removeConfigAndDestroyBrokerTabs() {
        configBrokers = emptyList()
        brokersTabbedPanel.selectedIndex = -1

        var componentCount = brokersTabbedPanel.tabCount
        while (componentCount-- > 0) {
            (brokersTabbedPanel.getComponentAt(componentCount) as BrokerPanel).dispose()
            brokersTabbedPanel.removeTabAt(componentCount)
        }
        componentCount = contentPane.componentCount
        while (componentCount-- > 0) {
            contentPane.remove(componentCount)
        }

        addNewAction.isEnabled = true
    }

}
