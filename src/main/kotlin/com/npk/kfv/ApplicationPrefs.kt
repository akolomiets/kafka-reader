package com.npk.kfv

import com.formdev.flatlaf.FlatLightLaf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URI
import java.security.SecureRandom
import java.time.Duration
import java.util.*
import java.util.prefs.Preferences
import javax.swing.JFrame
import kotlin.math.abs
import kotlin.properties.Delegates

const val APPLICATION_NAME =        "Kafka Reader"
const val APPLICATION_VERSION =     "2026.08"
const val APPLICATION_ARTIFACT_ID = "kafka-reader"
val APPLICATION_GITHUB_HYPERLINK =  URI("https://github.com/akolomiets/kafka-reader")

object ApplicationPrefs {

    private const val APP_KEY =             "id"
    private const val APP_DATA =            "data"
    private const val LOCALE_KEY =          "locale"
    private const val LAF_CLASS_NAME_KEY =  "laf.className"
    private const val UI_SCALE_KEY =        "sun.java2d.uiScale"
    private const val FONT_FAMILY_KEY =     "font.family"
    private const val FONT_SIZE_KEY =       "font.size"
    private const val WINDOW_STATE_KEY =    "window.state"
    private const val WINDOW_BOUNDS_KEY =   "window.bounds"
    private const val BROKERS_MAX_COUNT =   "brokers.max.count"
    private const val BROKERS_SELECTED =    "brokers.selected"
    private const val LOCAL_STORAGE_TTL =   "local.storage.ttl.sec"
    private const val REQUEST_TIMEOUT_MS =  "request.timeout.ms"

    private val appRootPrefs = Preferences.userRoot().node(APPLICATION_ARTIFACT_ID)
    private val preferences = appRootPrefs.node(APPLICATION_VERSION)

    val appKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var appKey = appRootPrefs.get(APP_KEY, null)
        if (appKey.isNullOrEmpty()) {
            appKey = UUID.randomUUID().toString()
            appRootPrefs.put(APP_KEY, appKey)
        }
        appKey
    }

    val appData: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var appData = appRootPrefs.getByteArray(APP_DATA, null)
        if (appData == null) {
            appData = ByteArray(32)
            SecureRandom().nextBytes(appData)
            appRootPrefs.putByteArray(APP_DATA, appData)
        }
        appData.joinToString("") { abs(it.toInt()).toString(Character.MAX_RADIX).padStart(2, '0') }
    }

    var locale: Locale by Delegates.observable(preferences.getLocale()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.put(LOCALE_KEY, newValue.toString())
        }
    }

    var lookAndFeelClassName: String by Delegates.observable(preferences.get(LAF_CLASS_NAME_KEY, FlatLightLaf::class.java.getName())) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.put(LAF_CLASS_NAME_KEY, newValue)
        }
    }

    var uiScale: Float by Delegates.observable(preferences.getFloat(UI_SCALE_KEY, 1.0f)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.putFloat(UI_SCALE_KEY, newValue)
        }
    }

    var fontFamily: String? by Delegates.observable(preferences.get(FONT_FAMILY_KEY, null)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.put(FONT_FAMILY_KEY, fontFamily)
        }
    }

    var fontSize: Int? by Delegates.observable(preferences.getInt(FONT_SIZE_KEY, 0).takeIf { it > 0 }) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            if (newValue != null) {
                preferences.putInt(FONT_SIZE_KEY, newValue)
            } else {
                preferences.remove(FONT_SIZE_KEY)
            }
        }
    }

    var windowState: Int by Delegates.observable(preferences.getInt(WINDOW_STATE_KEY, JFrame.NORMAL)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.putInt(WINDOW_STATE_KEY, newValue)
        }
    }

    var windowBounds: java.awt.Rectangle? by Delegates.observable(preferences.getWindowBounds()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.setWindowBounds(newValue)
        }
    }

    val brokersMaxCount: Int = preferences.getInt(BROKERS_MAX_COUNT, 0)
        .let { maxCount ->
            if (maxCount <= 0) {
                8.also { preferences.putInt(BROKERS_MAX_COUNT, it) }
            } else {
                maxCount
            }
        }

    var brokersSelected: Int by Delegates.observable(preferences.getInt(BROKERS_SELECTED, 0)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.putInt(BROKERS_SELECTED, newValue)
        }
    }

    val localStorageTtl: Duration? = preferences.getInt(LOCAL_STORAGE_TTL, Int.MIN_VALUE)
        .let { minutes ->
            if (minutes == Int.MIN_VALUE) {
                Duration.ofSeconds(300.also { preferences.putInt(LOCAL_STORAGE_TTL, it) }.toLong())
            } else {
                minutes
                    .takeIf { it in 1..86400 }
                    ?.let { Duration.ofSeconds(it.toLong()) }
            }
        }

    val requestTimeoutMs: Int = preferences.getInt(REQUEST_TIMEOUT_MS, Int.MIN_VALUE)
        .let { millis ->
            if (millis == Int.MIN_VALUE) {
                5000.also { preferences.putInt(REQUEST_TIMEOUT_MS, it) }
            } else {
                millis
            }
        }

    private fun Preferences.getLocale() =
        runCatching { Locale.forLanguageTag(preferences.get(LOCALE_KEY, null)) }
            .getOrDefault(Locale.ENGLISH)

    private fun Preferences.getWindowBounds(): java.awt.Rectangle? {
        val windowBoundsBytes = preferences.getByteArray(WINDOW_BOUNDS_KEY, null)
        return if (windowBoundsBytes != null) {
            runCatching {
                ObjectInputStream(ByteArrayInputStream(windowBoundsBytes)).use { ois -> ois.readObject() as java.awt.Rectangle }
            }.getOrNull()
        } else {
            null
        }
    }

    private fun Preferences.setWindowBounds(windowBounds: java.awt.Rectangle?) {
        if (windowBounds != null) {
            runCatching {
                val bos = ByteArrayOutputStream()
                ObjectOutputStream(bos).use { oos -> oos.writeObject(windowBounds) }
                preferences.putByteArray(WINDOW_BOUNDS_KEY, bos.toByteArray())
            }
        }
    }

}