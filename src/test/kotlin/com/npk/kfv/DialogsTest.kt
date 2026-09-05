package com.npk.kfv

import com.npk.kfv.dialogs.*
import com.npk.kfv.service.ConfigService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JFrame
import javax.swing.UIManager

@IntellijGuarded
internal class DialogsTest {

    @BeforeEach
    fun setUp() {
        UIManager.setLookAndFeel(ApplicationPrefs.lookAndFeelClassName)
        UIHelper.tryUpdateDefaultFont(ApplicationPrefs.fontFamily, ApplicationPrefs.fontSize)

        // FlatInspector.install( "ctrl shift alt X" );
        // FlatUIDefaultsInspector.install( "ctrl shift alt Y" );
    }

    @Test
    fun `About dialog`() {
        AboutDialog.showDialog(JFrame())
    }

    @Test
    fun `BrokersManager dialog`() {
        ConfigService.init()
        val configBrokers = ConfigService.loadConfigBrokers()

        BrokersManagerDialog(JFrame(), BrokersManagerViewModel(configBrokers)).run {
            pack()
            isVisible = true
        }
    }

    @Test
    fun `License dialog`() {
        LicenseDialog.showDialog(JFrame())
    }

    @Test
    fun `Settings dialog`() {
        SettingsDialog(JFrame()).run {
            pack()
            isVisible = true
        }
    }

    @Test
    fun `System Information dialog`() {
        SystemInformationDialog(JFrame()).run {
            pack()
            isVisible = true
        }
    }

}