package com.npk.swing

import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class ViewModel {

    private val lock: Any = Any()
    private val propertyChangeSupport: PropertyChangeSupport by lazy(lock) { PropertyChangeSupport(this) }

    fun addPropertyChangeListener(listener: PropertyChangeListener) = synchronized(lock) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }
    fun removePropertyChangeListener(listener: PropertyChangeListener) = synchronized(lock) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    fun addPropertyChangeListener(propertyName: String, listener: PropertyChangeListener) = synchronized(lock) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener)
    }
    fun removePropertyChangeListener(propertyName: String, listener: PropertyChangeListener) = synchronized(lock) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener)
    }

    protected fun firePropertyChange(propertyName: String, oldValue: Any?, newValue: Any?) =
        propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue)

    protected fun <V> observableProperty(initValue: V): ReadWriteProperty<ViewModel, V> = ObservableProperty(initValue)

    private class ObservableProperty<V>(private var value: V) : ReadWriteProperty<ViewModel, V> {

        override fun getValue(thisRef: ViewModel, property: KProperty<*>): V = value

        override fun setValue(thisRef: ViewModel, property: KProperty<*>, value: V) {
            val oldValue = this.value
            this.value = value
            thisRef.firePropertyChange(property.name, oldValue, value)
        }

    }

}