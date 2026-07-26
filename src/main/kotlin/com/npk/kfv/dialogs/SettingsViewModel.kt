package com.npk.kfv.dialogs

import com.npk.kfv.ApplicationPrefs
import com.npk.swing.ViewModel
import java.util.*
import javax.swing.LookAndFeel
import javax.swing.UIManager

class SettingsViewModel : ViewModel() {

    data class LookAndFeelInfo(
        val name: String,
        val className: String
    )

    private val oldLocale = ApplicationPrefs.locale
    private val oldUiScale = ApplicationPrefs.uiScale

    val needRestart: Boolean get() = oldLocale != locale || oldUiScale != uiScale

    var locale: Locale by observableProperty(ApplicationPrefs.locale)
    var lookAndFeel: LookAndFeelInfo by observableProperty(UIManager.getLookAndFeel().toLookAndFeelInfo())
    var uiScale: Float by observableProperty(ApplicationPrefs.uiScale)
    var fontFamily: String? by observableProperty(ApplicationPrefs.fontFamily)
    var fontSize: Int? by observableProperty(ApplicationPrefs.fontSize)

    init {
        addPropertyChangeListener { event ->
            when (event.propertyName) {
                ::locale.name -> ApplicationPrefs.locale = locale
                ::lookAndFeel.name -> ApplicationPrefs.lookAndFeelClassName = lookAndFeel.className
                ::uiScale.name -> ApplicationPrefs.uiScale = uiScale
                ::fontFamily.name -> ApplicationPrefs.fontFamily = fontFamily
                ::fontSize.name -> ApplicationPrefs.fontSize = fontSize
            }
        }
    }

    private fun LookAndFeel.toLookAndFeelInfo() = LookAndFeelInfo(name, this::class.java.name)

}