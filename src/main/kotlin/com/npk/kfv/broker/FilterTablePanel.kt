package com.npk.kfv.broker

import com.formdev.flatlaf.icons.FlatSearchIcon
import com.npk.kfv.ApplicationImages
import com.npk.kfv.ApplicationMessages
import com.npk.swing.*
import com.npk.swing.BindingHelper.bind
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableModel

internal class FilterTablePanel<T>(override val viewModel: FilterTablePanelViewModel<T>) : JPanel(BorderLayout()), View<FilterTablePanelViewModel<T>> where T : TableModel, T : HasTableColumnWidth {

    private val popupMenu = JPopupMenu().apply {
        add(jaction(ApplicationMessages["table.menu.copy"], ApplicationImages.Icons.Copy.icon, ::onCopyName))
        add(jaction(ApplicationMessages["table.menu.addFavorite"], ::onAddToFavorite))
        add(jaction(ApplicationMessages["table.menu.delFavorite"], ::onRemoveFromFavorite))
        add(jaction(ApplicationMessages["table.menu.details"], ::onViewDetails))
    }

    private val favoriteToggleButton = jtogglebutton(ApplicationImages.Icons.Star.icon) {
        it.toolTipText = ApplicationMessages["table.favorite"]
        it.bind(viewModel, FilterTablePanelViewModel<*>::favoriteFilter)
    }

    private val searchTextField = jtextfield {
        placeholderText = ApplicationMessages["table.search"]
        leadingIcon = FlatSearchIcon()
        showClearButton = true
        clearButtonCallback = { textComponent ->
            textComponent.text = ""
            textComponent.postActionEvent()
        }
        it.columns = 16
        it.maximumSize = it.preferredSize
        it.addActionListener { event -> viewModel.searchText = (event.source as JTextField).text }
    }

    private val searchComboBox = jcombobox(FilterTablePanelViewModel.SEARCH_LIMIT_ITEMS) {
        it.maximumSize = it.preferredSize
        it.bind(viewModel, FilterTablePanelViewModel<*>::searchLimit)
    }

    private val table = jtable(viewModel.tableModel) { table ->
        table.columnModel.getColumn(0).let {
            it.minWidth = viewModel.tableModel.getColumnWidth(0)
            it.maxWidth = viewModel.tableModel.getColumnWidth(0)
            it.resizable = false
            it.cellRenderer = object : DefaultTableCellRenderer() {
                init { horizontalAlignment = CENTER }
                override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    return (super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as DefaultTableCellRenderer).also { component ->
                        if (value as? Boolean == true) {
                            component.icon = ApplicationImages.Icons.Star.icon
                        } else {
                            component.icon = null
                        }
                    }
                }
            }
        }
        table.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    if (table.columnAtPoint(event.point) == 0) {
                        toggleFavorite()
                    } else {
                        onViewDetails(ActionEvent(event.source, event.id, "MouseEvent"))
                    }
                }
            }
            override fun mousePressed(event: MouseEvent) {
                showPopupMenu(event)
            }
            override fun mouseReleased(event: MouseEvent) {
                showPopupMenu(event)
            }
        })
    }

    init {
        val toolbar = jtoolbar {
            +favoriteToggleButton
            +searchTextField
            it.add(Box.createHorizontalStrut(10))
            +jlabel(ApplicationMessages["table.limit"])
            it.add(Box.createHorizontalStrut(5))
            +searchComboBox
        }
        add(toolbar, BorderLayout.NORTH)
        add(jscrollpane(table), BorderLayout.CENTER)
    }

    private fun showPopupMenu(event: MouseEvent) {
        if (popupMenu.isPopupTrigger(event)) {
            val row = table.rowAtPoint(event.point)
            if (!table.isRowSelected(row)) {
                table.setRowSelectionInterval(row, row)
            }
            popupMenu.show(table, event.x, event.y)
        }
    }

    private fun onCopyName(event: ActionEvent) {
        val modelRow = table.convertRowIndexToModel(table.selectedRow)
        val value = table.model.getValueAt(modelRow, 1)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value.toString()), null)
    }

    private fun onAddToFavorite(event: ActionEvent) {
        val row = table.selectedRow
        val modelRow = table.convertRowIndexToModel(row)
        val isFavorite = table.model.getValueAt(modelRow, 0) as Boolean
        if (!isFavorite) {
            table.model.setValueAt(true, modelRow, 0)
            table.repaint(table.getCellRect(row, 0, true))
        }
    }

    private fun onRemoveFromFavorite(event: ActionEvent) {
        val row = table.selectedRow
        val modelRow = table.convertRowIndexToModel(row)
        val isFavorite = table.model.getValueAt(modelRow, 0) as Boolean
        if (isFavorite) {
            table.model.setValueAt(false, modelRow, 0)
            table.repaint(table.getCellRect(row, 0, true))
        }
    }

    private fun onViewDetails(event: ActionEvent) {
        val modelRow = table.convertRowIndexToModel(table.selectedRow)
        val value = table.model.getValueAt(modelRow, 1)
        viewModel.viewDetails(value)
    }

    private fun toggleFavorite() {
        val row = table.selectedRow
        val modelRow = table.convertRowIndexToModel(row)
        val isFavorite = table.model.getValueAt(modelRow, 0) as Boolean
        table.model.setValueAt(!isFavorite, modelRow, 0)
        table.repaint(table.getCellRect(row, 0, true))
    }

}
