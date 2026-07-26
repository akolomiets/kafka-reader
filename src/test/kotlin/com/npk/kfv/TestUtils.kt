package com.npk.kfv

import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "sun.java.command", matches = "^com.intellij")
annotation class IntellijGuarded

inline fun intellijGuarded(block: () -> Unit) {
    if (System.getProperty("sun.java.command", "").startsWith("com.intellij")) block()
}
