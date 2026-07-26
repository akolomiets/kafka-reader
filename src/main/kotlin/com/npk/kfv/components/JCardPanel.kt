package com.npk.kfv.components

import java.awt.CardLayout
import javax.swing.JPanel

class JCardPanel : JPanel(CardLayout()) {

    fun showCard(name: String) {
        (layout as CardLayout).show(this, name)
    }

}