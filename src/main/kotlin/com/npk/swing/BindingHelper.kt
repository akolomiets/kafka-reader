package com.npk.swing

import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.ChangeListener
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

fun interface BindRegistration {
    fun unbind()
}

object BindingHelper {

    //region View <--> Model

    fun <T : ViewModel> JTextComponent.bind(viewModel: T, property: KMutableProperty1<T, String>): BindRegistration = let { textComponent ->
        val propertyName = property.name
        val documentListener = DocumentListenerFunction { property.set(viewModel, textComponent.text) }
        val propertyListener = PropertyChangeListener { event ->
            val oldValue = textComponent.text
            if (oldValue != event.newValue?.toString()) {
                textComponent.text = event.newValue?.toString()
            }
        }

        textComponent.text = property.get(viewModel)
        textComponent.document.addDocumentListener(documentListener)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration {
            textComponent.document.removeDocumentListener(documentListener)
            viewModel.removePropertyChangeListener(propertyName, propertyListener)
        }
    }

    fun <T : ViewModel, E> JComboBox<E>.bind(viewModel: T, property: KMutableProperty1<T, E>): BindRegistration = let { comboBox ->
        val propertyName = property.name
        val itemListener = ItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                @Suppress("UNCHECKED_CAST")
                property.set(viewModel, event.item as E)
            }
        }
        val propertyListener = PropertyChangeListener { event ->
            val oldValue = comboBox.selectedItem
            if (oldValue != event.newValue) {
                comboBox.selectedItem = event.newValue
            }
        }

        comboBox.selectedItem = property.get(viewModel)
        comboBox.addItemListener(itemListener)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration {
            comboBox.removeItemListener(itemListener)
            viewModel.removePropertyChangeListener(propertyName, propertyListener)
        }
    }

    fun <T : ViewModel> JToggleButton.bind(viewModel: T, property: KMutableProperty1<T, Boolean>): BindRegistration = let { toggleButton ->
        val propertyName = property.name
        val itemListener = ItemListener { property.set(viewModel, it.stateChange == ItemEvent.SELECTED) }
        val propertyListener = PropertyChangeListener { toggleButton.isSelected = (it.newValue as Boolean? == true) }

        toggleButton.isSelected = property.get(viewModel)
        toggleButton.addItemListener(itemListener)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration {
            toggleButton.removeItemListener(itemListener)
            viewModel.removePropertyChangeListener(propertyName, propertyListener)
        }
    }

    fun <T : ViewModel, V : Number> JSpinner.bind(viewModel: T, property: KMutableProperty1<T, V>): BindRegistration = let { spinner ->
        val propertyName = property.name
        val changeListener = ChangeListener { event ->
            @Suppress("UNCHECKED_CAST")
            property.set(viewModel, (event.source as JSpinner).value as V)
        }
        val propertyListener = PropertyChangeListener { event ->
            val oldValue = spinner.value
            if (oldValue != event.newValue) {
                spinner.value = event.newValue
            }
        }

        spinner.value = property.get(viewModel)
        spinner.addChangeListener(changeListener)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration {
            spinner.removeChangeListener(changeListener)
            viewModel.removePropertyChangeListener(propertyName, propertyListener)
        }
    }

    fun <T : ViewModel> ButtonGroup.bind(viewModel: T, property: KMutableProperty1<T, String>): BindRegistration = let { buttonGroup ->
        val propertyName = property.name
        val propertyValue = property.get(viewModel)
        val itemListener = ItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                property.set(viewModel, (it.item as AbstractButton).actionCommand)
            }
        }
        val propertyListener = PropertyChangeListener { event ->
            val newActionCommand = event.newValue?.toString()
            for (button in buttonGroup.elements.asIterator()) {
                if (button.actionCommand == newActionCommand) {
                    buttonGroup.setSelected(button.model, true)
                    break
                }
            }
        }

        buttonGroup.elements.asIterator().forEach { button ->
            if (button.actionCommand == propertyValue) {
                buttonGroup.setSelected(button.model, true)
            }
            button.addItemListener(itemListener)
        }
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration {
            buttonGroup.elements.asIterator().forEach { button -> button.removeItemListener(itemListener) }
            viewModel.removePropertyChangeListener(propertyName, propertyListener)
        }
    }

    //endregion

    //region View --> Model

    fun <T : ViewModel> JTextComponent.bindView(viewModel: T, property: KMutableProperty1<T, String>): BindRegistration = let { textComponent ->
        val documentListener = DocumentListenerFunction { property.set(viewModel, textComponent.text) }
        textComponent.document.addDocumentListener(documentListener)
        BindRegistration { textComponent.document.removeDocumentListener(documentListener) }
    }

    fun <T : ViewModel, E> JComboBox<E>.bindView(viewModel: T, property: KMutableProperty1<T, E>): BindRegistration = let { comboBox ->
        val itemListener = ItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                @Suppress("UNCHECKED_CAST")
                property.set(viewModel, event.item as E)
            }
        }
        comboBox.addItemListener(itemListener)
        BindRegistration { comboBox.removeItemListener(itemListener) }
    }

    //endregion

    //region View <-- Model

    fun <T : ViewModel> JLabel.bindModel(viewModel: T, property: KProperty1<T, String>): BindRegistration = let { label ->
        val propertyName = property.name
        val propertyListener = PropertyChangeListener { event ->
            if (label.text != event.newValue?.toString()) {
                label.text = event.newValue?.toString()
            }
        }

        label.text = property.get(viewModel)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration { viewModel.removePropertyChangeListener(propertyName, propertyListener) }
    }

    fun <T : ViewModel> JTextComponent.bindModel(viewModel: T, property: KProperty1<T, String>): BindRegistration = let { textComponent ->
        val propertyName = property.name
        val propertyListener = PropertyChangeListener { event ->
            val oldValue = textComponent.text
            if (oldValue != event.newValue?.toString()) {
                textComponent.text = event.newValue?.toString()
            }
        }

        textComponent.text = property.get(viewModel)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration { viewModel.removePropertyChangeListener(propertyName, propertyListener) }
    }

    fun <T : ViewModel, E> JComboBox<E>.bindModel(viewModel: T, property: KProperty1<T, E>): BindRegistration = let { comboBox ->
        val propertyName = property.name
        val propertyListener = PropertyChangeListener { event ->
            val oldValue = comboBox.selectedItem
            if (oldValue != event.newValue) {
                comboBox.selectedItem = event.newValue
            }
        }

        comboBox.selectedItem = property.get(viewModel)
        viewModel.addPropertyChangeListener(propertyName, propertyListener)

        BindRegistration { viewModel.removePropertyChangeListener(propertyName, propertyListener) }
    }

    //endregion

}
