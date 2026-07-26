package com.npk.swing

import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

fun interface DocumentListenerFunction : DocumentListener {

    override fun insertUpdate(event: DocumentEvent) = changedUpdate(event)

    override fun removeUpdate(event: DocumentEvent) = changedUpdate(event)

}