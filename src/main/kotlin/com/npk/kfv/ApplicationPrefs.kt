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
const val APPLICATION_VERSION =     "2026.09"
const val APPLICATION_ARTIFACT_ID = "kafka-reader"
val APPLICATION_GITHUB_HYPERLINK =  URI("https://github.com/akolomiets/kafka-reader")

object ApplicationPrefs {

    private const val APP_KEY =                     "id"
    private const val APP_DATA =                    "data"
    private const val LOCALE_KEY =                  "locale"
    private const val LAF_CLASS_NAME_KEY =          "laf.className"
    private const val UI_SCALE_KEY =                "sun.java2d.uiScale"
    private const val FONT_FAMILY_KEY =             "font.family"
    private const val FONT_SIZE_KEY =               "font.size"
    private const val MAIN_WINDOW_STATE_KEY =       "main.window.state"
    private const val MAIN_WINDOW_BOUNDS_KEY =      "main.window.bounds"
    private const val BROKERS_WINDOW_BOUNDS_KEY =   "brokers.window.bounds"
    private const val BROKERS_MAX_COUNT =           "brokers.max.count"
    private const val BROKERS_SELECTED =            "brokers.selected"
    private const val LOCAL_STORAGE_TTL =           "local.storage.ttl.duration"
    private const val REQUEST_TIMEOUT =             "request.timeout.duration"

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

    var windowState: Int by Delegates.observable(preferences.getInt(MAIN_WINDOW_STATE_KEY, JFrame.NORMAL)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.putInt(MAIN_WINDOW_STATE_KEY, newValue)
        }
    }

    var windowBounds: java.awt.Rectangle? by Delegates.observable(preferences.getBounds(MAIN_WINDOW_BOUNDS_KEY)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.setBounds(MAIN_WINDOW_BOUNDS_KEY, newValue)
        }
    }

    var brokersWindowBounds: java.awt.Rectangle? by Delegates.observable(preferences.getBounds(BROKERS_WINDOW_BOUNDS_KEY)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.setBounds(BROKERS_WINDOW_BOUNDS_KEY, newValue)
        }
    }

    val brokersMaxCount: Int = preferences.getInt(BROKERS_MAX_COUNT, 0)
        .let { maxCount ->
            if (maxCount <= 0) {
                10.also { preferences.putInt(BROKERS_MAX_COUNT, it) }
            } else {
                maxCount
            }
        }

    var brokersSelected: Int by Delegates.observable(preferences.getInt(BROKERS_SELECTED, 0)) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            preferences.putInt(BROKERS_SELECTED, newValue)
        }
    }

    val localStorageTtl: Duration = preferences.getDuration(LOCAL_STORAGE_TTL)
        ?: Duration.ofMinutes(5).also { preferences.setDuration(LOCAL_STORAGE_TTL, it) }

    val requestTimeout: Duration = preferences.getDuration(REQUEST_TIMEOUT)
        ?: Duration.ofSeconds(15).also { preferences.setDuration(REQUEST_TIMEOUT, it) }


    private fun Preferences.getLocale() =
        runCatching { Locale.forLanguageTag(get(LOCALE_KEY, null)) }
            .getOrDefault(Locale.ENGLISH)

    private fun Preferences.getBounds(key: String): java.awt.Rectangle? {
        val boundsBytes = getByteArray(key, null)
        return if (boundsBytes != null) {
            runCatching {
                ObjectInputStream(ByteArrayInputStream(boundsBytes)).use { ois -> ois.readObject() as java.awt.Rectangle }
            }.getOrNull()
        } else {
            null
        }
    }

    private fun Preferences.setBounds(key: String, bounds: java.awt.Rectangle?) {
        if (bounds != null) {
            runCatching {
                val bos = ByteArrayOutputStream()
                ObjectOutputStream(bos).use { oos -> oos.writeObject(bounds) }
                putByteArray(key, bos.toByteArray())
            }
        }
    }

    private fun Preferences.getDuration(key: String): Duration? =
        runCatching {
            val value = get(key, "")
            if (value.isNotEmpty()) {
                Duration.parse(value)
            } else {
                null
            }
        }.getOrNull()

    private fun Preferences.setDuration(key: String, duration: Duration?) {
        if (duration != null) {
            put(key, duration.toString())
        }
    }

}