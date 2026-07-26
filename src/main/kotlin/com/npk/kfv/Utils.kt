package com.npk.kfv

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.FontUtils
import com.npk.kfv.service.KafkaService
import java.awt.Font
import java.io.File
import java.io.PrintWriter
import java.io.Reader
import java.io.StringReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import javax.swing.JFileChooser
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

inline fun <T : Any, R> T.synchronized(block: (T) -> R): R = synchronized(this, { block(this) })

inline fun requireResource(name: String, lazyMessage: () -> String = { "Required resource '$name' not found." }): URL =
    requireNotNull(ApplicationFrame::class.java.getResource(name), lazyMessage)

class StopwatchTimer(listener: (Long) -> Unit) : Timer(1000, null) {

    private var seconds = 0L

    init {
        addActionListener { listener(++seconds) }
    }

    fun reset() {
        seconds = 0L
    }

}

object UIHelper {

    fun isFlatLaf(): Boolean = UIManager.getLookAndFeel() is FlatLaf

    fun getCurrentFont(): Font = UIManager.getFont("defaultFont") ?: UIManager.getFont("Label.font")

    fun tryUpdateDefaultFont(fontFamily: String?, fontSize: Int?) {
        val font = UIManager.getFont("defaultFont")
        if (font != null && ((fontFamily != null && font.family != fontFamily) || (fontSize != null && font.size != fontSize))) {
            val newFont = FontUtils.getCompositeFont(fontFamily ?: font.family, font.style, fontSize ?: font.size)
            UIManager.put("defaultFont", newFont)
        }
    }

    fun createAnyOpenFileChooser(): JFileChooser = JFileChooser(System.getProperty("user.home")).apply {
        dialogType = JFileChooser.OPEN_DIALOG
        fileSelectionMode = JFileChooser.FILES_ONLY
        isFileHidingEnabled = false
    }

    fun createAnySaveFileChooser(defaultFileName: String = ""): JFileChooser = JFileChooser(System.getProperty("user.home")).apply {
        dialogType = JFileChooser.SAVE_DIALOG
        fileSelectionMode = JFileChooser.FILES_ONLY
        isFileHidingEnabled = false
        defaultFileName
            .takeIf { it.isNotBlank() }
            ?.let { fileName -> selectedFile = File(fileName) }
    }

    fun createPropertiesFileChooser(): JFileChooser = JFileChooser(System.getProperty("user.home")).apply {
        dialogType = JFileChooser.OPEN_DIALOG
        fileSelectionMode = JFileChooser.FILES_ONLY
        isFileHidingEnabled = false
        fileFilter = FileNameExtensionFilter("Properties Files (*.properties)", "properties")
    }

    fun createSecureStoreFileChooser(): JFileChooser = JFileChooser(System.getProperty("user.home")).apply {
        dialogType = JFileChooser.OPEN_DIALOG
        fileSelectionMode = JFileChooser.FILES_ONLY
        isFileHidingEnabled = false
        fileFilter = FileNameExtensionFilter("Java KeyStore (*.jks)", "jks")
        addChoosableFileFilter(FileNameExtensionFilter("Personal Information Exchange (*.p12, *.pfx)", "p12", "pfx"))
        addChoosableFileFilter(FileNameExtensionFilter("Privacy-Enhanced Mail (*.pem)", "pem"))
    }

}

object Tuples {

    fun <T1, T2> of(t1: T1, t2: T2) = Tuple2<T1, T2>(arrayOf(t1, t2))

    fun <T1, T2, T3> of(t1: T1, t2: T2, t3: T3) = Tuple3<T1, T2, T3>(arrayOf(t1, t2, t3))

    fun <T1, T2, T3, T4> of(t1: T1, t2: T2, t3: T3, t4: T4) = Tuple4<T1, T2, T3, T4>(arrayOf(t1, t2, t3, t4))

    @JvmInline
    @Suppress("UNCHECKED_CAST")
    value class Tuple2<T1, T2>(private val values: Array<Any?>) {
        val t1: T1 get() = values[0] as T1
        val t2: T2 get() = values[1] as T2

        operator fun get(index: Int): Any? = values[index]

        operator fun component1(): T1 = t1
        operator fun component2(): T2 = t2
    }

    @JvmInline
    @Suppress("UNCHECKED_CAST")
    value class Tuple3<T1, T2, T3>(private val values: Array<Any?>) {
        val t1: T1 get() = values[0] as T1
        val t2: T2 get() = values[1] as T2
        val t3: T3 get() = values[2] as T3

        operator fun get(index: Int): Any? = values[index]

        operator fun component1(): T1 = t1
        operator fun component2(): T2 = t2
        operator fun component3(): T3 = t3
    }

    @JvmInline
    @Suppress("UNCHECKED_CAST")
    value class Tuple4<T1, T2, T3, T4>(private val values: Array<Any?>) {
        val t1: T1 get() = values[0] as T1
        val t2: T2 get() = values[1] as T2
        val t3: T3 get() = values[2] as T3
        val t4: T4 get() = values[3] as T4

        operator fun get(index: Int): Any? = values[index]

        operator fun component1(): T1 = t1
        operator fun component2(): T2 = t2
        operator fun component3(): T3 = t3
        operator fun component4(): T4 = t4
    }

}

object FlexPropertiesSupport {

    fun loadConnectionPropertiesFromFile(path: Path): Map<String, String> =
        Files.newBufferedReader(path).use { reader ->
            Properties().let { properties ->
                properties.load(reader)
                properties.asSequence()
                    .map { it.key.toString() to it.value.toString() }
                    .filter { (key) -> key in KafkaService.allPropertyNames }
                    .toMap()
            }
        }

    fun parseStringToMap(input: String, transformValue: (key: String, value: String) -> String = { _, value -> value }): Map<String, String> =
        buildMap {
            StringReader(input).forEachLine {
                val line = it.trim()
                if (!line.startsWith("#")) {
                    line.indexOf("=").let { index ->
                        if (index >= 0) {
                            val key = line.substring(0, index).trimEnd()
                            val value = line.substring(index + 1).trimStart()
                            put(key, transformValue(key, value))
                        } else {
                            put(line, transformValue(line, ""))
                        }
                    }
                }
            }
        }

    const val HEADER_KAFKA_READER = "header:$APPLICATION_ARTIFACT_ID"

    fun PrintWriter.printSection(name: String, block: PrintWriter.() -> Unit) {
        println("[$name]")
        block()
        println()
    }

    fun PrintWriter.printSection(name: String, value: Any?) {
        if (value != null && value.toString().isNotBlank()) printSection(name) { println(value) }
    }

    fun PrintWriter.printHeader(block: PrintWriter.() -> Unit = {}) = printSection(HEADER_KAFKA_READER) {
        println("id=${ApplicationPrefs.appKey}")
        println("version=$APPLICATION_VERSION")
        println("timestamp=${Instant.now()}")
        block(this)
    }

    fun Reader.readBySection(block: (String, String) -> Unit) {
        var currentSection = ""
        val currentValueBuilder = StringBuilder()
        forEachLine { line ->
            when {
                line.isBlank() || line.startsWith('#') -> {}
                line.startsWith("[") && line.endsWith("]") -> {
                    if (currentSection.isNotEmpty()) {
                        block(currentSection, currentValueBuilder.toString())
                    }
                    currentSection = line.substring(1, line.length - 1)
                    currentValueBuilder.clear()
                }
                currentSection.isNotEmpty() -> {
                    if (currentValueBuilder.isNotEmpty()) {
                        currentValueBuilder.appendLine()
                    }
                    currentValueBuilder.append(line)
                }
            }
        }
        if (currentSection.isNotEmpty()) {
            block(currentSection, currentValueBuilder.toString())
        }
    }

}
