package com.npk.swing

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.npk.kfv.UIHelper
import com.npk.kfv.broker.HasTableColumnWidth
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.*
import java.util.function.Consumer
import javax.swing.*
import javax.swing.plaf.basic.BasicComboBoxRenderer
import javax.swing.table.TableModel
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent

//region Action

@JvmInline
value class ActionScope(private val action: Action) {

    var name: String?
        get() = action.getValue(Action.NAME)?.toString()
        set(value) = action.putValue(Action.NAME, value)

    var icon: Icon?
        get() = action.getValue(Action.SMALL_ICON) as Icon?
        set(value) = action.putValue(Action.SMALL_ICON, value)

    var tooltip: String?
        get() = action.getValue(Action.SHORT_DESCRIPTION)?.toString()
        set(value) = action.putValue(Action.SHORT_DESCRIPTION, value)

    var isEnabled: Boolean
        get() = action.isEnabled
        set(value) { action.isEnabled = value }

}

fun jaction(name: String, listener: ActionListener): Action = object : AbstractAction(name) {
    override fun actionPerformed(event: ActionEvent) = listener.actionPerformed(event)
}

fun jaction(icon: Icon, listener: ActionListener): Action = object : AbstractAction(null, icon) {
    override fun actionPerformed(event: ActionEvent) = listener.actionPerformed(event)
}

fun jaction(name: String, icon: Icon, listener: ActionListener): Action = object : AbstractAction(name, icon) {
    override fun actionPerformed(event: ActionEvent) = listener.actionPerformed(event)
}

fun jaction(listener: ActionListener, block: ActionScope.(Action) -> Unit = {}): Action =
    object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) = listener.actionPerformed(e)
    }.also { ActionScope(it).block(it) }

//endregion

interface HasDisplayText {
    val displayText: String
}

interface JComponentScope {
    operator fun JComponent.unaryPlus()
    operator fun Pair<JComponent, Any>.unaryPlus()
}

fun interface ButtonGroupScope {
    operator fun AbstractButton.unaryPlus()
}

//region JLabel

fun jlabel(text: String = "", block: JComponentScope.(JLabel) -> Unit = {}): JLabel =
    JLabel(text).also { label -> JComponentScopeImpl(label).block(label) }

fun jlabel(icon: Icon, block: JComponentScope.(JLabel) -> Unit = {}): JLabel =
    JLabel(icon).also { label -> JComponentScopeImpl(label).block(label) }

//endregion

//region JButton

fun jbutton(text: String, actionListener: ActionListener, block: JComponentScope.(JButton) -> Unit = {}) =
    JButton(text).also { button ->
        button.addActionListener(actionListener)
        JComponentScopeImpl(button).block(button)
    }

fun jbutton(icon: Icon, actionListener: ActionListener, block: JComponentScope.(JButton) -> Unit = {}) =
    JButton(icon).also { button ->
        button.addActionListener(actionListener)
        JComponentScopeImpl(button).block(button)
    }

fun jbutton(text: String, icon: Icon, actionListener: ActionListener, block: JComponentScope.(JButton) -> Unit = {}) =
    JButton(text, icon).also { button ->
        button.addActionListener(actionListener)
        JComponentScopeImpl(button).block(button)
    }

fun jbutton(action: Action, block: JComponentScope.(JButton) -> Unit = {}) =
    JButton(action).also { button -> JComponentScopeImpl(button).block(button) }

fun jtogglebutton(icon: Icon, block: JComponentScope.(JToggleButton) -> Unit = {}) =
    JToggleButton(icon).also { button -> JComponentScopeImpl(button).block(button) }

fun jtogglebutton(icon: Icon, actionListener: ActionListener, block: JComponentScope.(JToggleButton) -> Unit = {}) =
    JToggleButton(icon).also { button ->
        button.addActionListener(actionListener)
        JComponentScopeImpl(button).block(button)
    }

fun jradiobutton(text: String, actionCommand: String, block: (JRadioButton) -> Unit = {}) =
    JRadioButton(text).also { radioButton ->
        radioButton.actionCommand = actionCommand
        radioButton.apply(block)
    }

fun jcheckbox(text: String, block: (JCheckBox) -> Unit = {}) =
    JCheckBox(text).also { chekBox -> chekBox.apply(block) }

//endregion

//region JTextComponent

@JvmInline
value class JTextComponentScope(private val component: JTextComponent) {

    var placeholderText: String?
        get() = component.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT)?.toString()
        set(text) { component.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text) }

    var leadingIcon: Icon?
        get() = component.getClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON) as Icon?
        set(value) { component.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, value) }

    var trailingIcon: Icon?
        get() = component.getClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON) as Icon?
        set(value) { component.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON, value) }

    var showClearButton: Boolean?
        get() = component.getClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON) as Boolean?
        set(value) { component.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, value) }

    var clearButtonCallback: Consumer<JTextField>?
        @Suppress("UNCHECKED_CAST")
        get() = component.getClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK) as Consumer<JTextField>?
        set(value) { component.putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, value) }

}

fun jtextfield(block: JTextComponentScope.(JTextField) -> Unit = {}) =
    JTextField().also { textField -> JTextComponentScope(textField).block(textField) }

fun jtextarea(block: (JTextArea) -> Unit = {}) =
    JTextArea().also { textArea ->
        if (UIHelper.isFlatLaf()) {
            textArea.border = FlatTextBorder()
        }
        textArea.apply(block)
    }

fun jpasswordfield(block: JTextComponentScope.(JPasswordField) -> Unit = {}) =
    JPasswordField().also { passwordField ->
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true")
        JTextComponentScope(passwordField).block(passwordField)
    }

fun jnumberfield(block: JTextComponentScope.(JTextField) -> Unit = {}) =
    JTextField().also { textField ->
        (textField.document as AbstractDocument).documentFilter = object : DocumentFilter() {
            override fun insertString(fb: FilterBypass?, offset: Int, text: String?, attr: AttributeSet?) {
                if (text.isNullOrEmpty() || text.toLongOrNull() != null) {
                    super.insertString(fb, offset, text, attr)
                }
            }
            override fun replace(fb: FilterBypass?, offset: Int, length: Int, text: String?, attr: AttributeSet?) {
                if (text.isNullOrEmpty() || text.toLongOrNull() != null) {
                    super.replace(fb, offset, length, text, attr)
                }
            }
        }
        JTextComponentScope(textField).block(textField)
    }

//endregion

//region JComboBox

fun <T> jcombobox(block: (JComboBox<T>) -> Unit = {}) =
    JComboBox<T>().also { comboBox -> comboBox.apply(block) }

fun <T> jcombobox(items: List<T>, block: (JComboBox<T>) -> Unit = {}) =
    JComboBox<T>(Vector(items)).also { comboBox -> comboBox.apply(block) }

@JvmName("jdisplaytextcombobox")
fun <T : HasDisplayText> jcombobox(items: List<T>, block: (JComboBox<T>) -> Unit = {}) =
    JComboBox<T>(Vector(items)).also { comboBox ->
        comboBox.renderer = object : BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                @Suppress("UNCHECKED_CAST")
                return super.getListCellRendererComponent(list, (value as T).displayText, index, isSelected, cellHasFocus)
            }
        }
        comboBox.apply(block)
    }

//endregion

//region JTable

fun <T> jtable(model: T, block: (JTable) -> Unit = {}): JTable where T : TableModel, T : HasTableColumnWidth =
    object : JTable(model) {
        init {
            showHorizontalLines = true
            autoCreateRowSorter = true
            // TODO: Probably need to disable auto resizing
            // autoResizeMode = JTable.AUTO_RESIZE_OFF
            tableHeader.reorderingAllowed = false
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            for (i in 0 ..< columnModel.columnCount) {
                columnModel.getColumn(i).preferredWidth = model.getColumnWidth(i)
            }
        }
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (rowCount == 0) {
                (g as Graphics2D).let { g2d ->
                    val message = "No Data Available"
                    val fontMetrics = g2d.fontMetrics
                    val messageWidth = fontMetrics.stringWidth(message)
                    g2d.setRenderingHints(Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as Map<*, *>)
                    g2d.color = Color.GRAY
                    g2d.drawString(message, (width - messageWidth) / 2, fontMetrics.height + 5)
                }
            }
        }
        override fun getScrollableTracksViewportHeight(): Boolean {
            return if (rowCount == 0) {
                preferredSize.height < parent.height
            } else {
                super.getScrollableTracksViewportHeight()
            }
        }
    }.apply(block)

//endregion

//region ButtonGroup

fun buttonGroup(block: ButtonGroupScope.(ButtonGroup) -> Unit) {
    ButtonGroup().also { buttonGroup ->
        ButtonGroupScope { buttonGroup.add(this) }.block(buttonGroup)
    }
}

//endregion

//region JPanel

fun jtoolbar(block: JComponentScope.(JToolBar) -> Unit): JToolBar =
    JToolBar().also { toolbar -> JComponentScopeImpl(toolbar).block(toolbar) }

fun jpanel(layout: LayoutManager = FlowLayout(), block: JComponentScope.(JPanel) -> Unit) =
    JPanel(layout).also { panel -> JComponentScopeImpl(panel).block(panel) }

fun jscrollpane(view: JComponent, block: (JScrollPane) -> Unit = {}) =
    JScrollPane(view).also { panel ->
        panel.border = null
        panel.apply(block)
    }

//endregion

@JvmInline
private value class JComponentScopeImpl<T : JComponent>(private val target: T) : JComponentScope {
    override fun JComponent.unaryPlus() {
        target.add(this)
    }
    override fun Pair<JComponent, Any>.unaryPlus() {
        target.add(this.first, this.second)
    }
}
