package com.npk.swing

import java.awt.Point
import javax.swing.JComboBox
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicComboPopup
import kotlin.math.max
import kotlin.math.min

class BoundsPopupMenuListener(private val maximumWidth: Int = -1) : PopupMenuListener {

    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
        val combobox = e.getSource() as JComboBox<*>
        if (combobox.itemCount > 0) {
            val child = combobox.accessibleContext.getAccessibleChild(0)
            if (child is BasicComboPopup) {
                SwingUtilities.invokeLater { customizePopup(combobox, child) }
            }
        }
    }

    override fun popupMenuCanceled(e: PopupMenuEvent) { }

    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) { }

    private fun customizePopup(combobox: JComboBox<*>, popup: BasicComboPopup) {
        val list = popup.list
        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, list) as? JScrollPane?

        if (scrollPane != null) {
            val scrollPaneSize = scrollPane.preferredSize
            val scrollBarWidth = if (combobox.itemCount > combobox.maximumRowCount) {
                scrollPane.verticalScrollBar.preferredSize.width
            } else {
                0
            }

            var popupWidth = list.preferredSize.width + scrollBarWidth + 5
            popupWidth = if (maximumWidth != -1) {
                min(popupWidth, maximumWidth)
            } else {
                max(popupWidth, scrollPaneSize.width)
            }

            scrollPaneSize.width = popupWidth
            scrollPane.preferredSize = scrollPaneSize
            scrollPane.maximumSize = scrollPaneSize
        }

        val location: Point = combobox.locationOnScreen
        val height = combobox.preferredSize.height
        popup.setLocation(location.x, location.y + height - 1)
        popup.setLocation(location.x, location.y + height)
    }

}