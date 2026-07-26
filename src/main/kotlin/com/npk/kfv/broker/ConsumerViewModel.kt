package com.npk.kfv.broker

import com.npk.kfv.service.*
import com.npk.swing.HasDisplayText
import com.npk.swing.ViewModel
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingWorker
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class ConsumerViewModel(configBroker: ConfigBroker) : ViewModel() {

    enum class FormatStrategy(override val displayText: String) : HasDisplayText {
        None("None"),
        Json("JSON"),
        Xml("XML")
    }

    private val logger = Logger.getLogger(this.javaClass.getName())
    private val loggerMarker = configBroker.loggerMarker

    private val indentSize = 2

    private val xmlTransformer: Transformer by lazy {
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "$indentSize")
        }
    }

    private val connectionProperties = configBroker.connectionProperties

    init {
        addPropertyChangeListener { event ->
            when (event.propertyName) {
                ::recordValueFormatStrategy.name -> recordValue = formatValueAs(event.newValue as FormatStrategy, recordUnformattedValue).orEmpty()
                ::recordKeyFormatStrategy.name -> recordKey = formatValueAs(event.newValue as FormatStrategy, recordUnformattedKey).orEmpty()
            }
        }
    }

    var topic: String = ""
        set(value) {
            if (!isConsuming) {
                field.let { oldValue ->
                    field = value
                    firePropertyChange(::topic.name, oldValue, value)
                }
            }
        }
    var group: String = connectionProperties[ConsumerConfig.GROUP_ID_CONFIG].orEmpty()
        set(value) {
            if (!isConsuming) {
                field.let { oldValue ->
                    field = value
                    firePropertyChange(::group.name, oldValue, value)
                }
            }
        }
    var keyType: SerializationType by observableProperty(connectionProperties.keyDeserializationTypeOrDefault())
    var valueType: SerializationType by observableProperty(connectionProperties.valueDeserializationTypeOrDefault())
    var autoCommit: Boolean by observableProperty(connectionProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] == "true")
    var startFrom: OffsetStrategy by observableProperty(OffsetStrategy.Latests)
    var partitions: String by observableProperty("")

    var recordValueFormatStrategy: FormatStrategy by observableProperty(FormatStrategy.None)
    var recordKeyFormatStrategy: FormatStrategy by observableProperty(FormatStrategy.None)

    private var recordUnformattedKey: String? = null
    private var recordUnformattedValue: String? = null
    private var recordRawKey: ByteArray? = null
    private var recordRawValue: ByteArray? = null

    var recordValue: String by observableProperty("")
    var recordKey: String by observableProperty("")
    var recordHeaders: String by observableProperty("")
    var recordTopic: String by observableProperty("")
    var recordPartition: String by observableProperty("")
    var recordOffset: String by observableProperty("")
    var recordTimestamp: String by observableProperty("")
    var recordKeySize: String by observableProperty("")
    var recordValueSize: String by observableProperty("")
    var recordKeyType: String by observableProperty("")
    var recordValueType: String by observableProperty("")

    val topicsComboBoxModel = DefaultComboBoxModel<String>()
    val groupsComboBoxModel = DefaultComboBoxModel<String>()
    val recordsTableModel = KafkaConsumerRecordsTableModel()

    private var consumerWorker: KafkaConsumerWorker? = null

    val isConsuming: Boolean get() = consumerWorker?.state == SwingWorker.StateValue.STARTED

    fun startConsuming() {
        consumerWorker?.cancel(true)

        val consumerConfig = KafkaConsumerConfig(
            topic = topic,
            keyType = keyType,
            valueType = valueType,
            groupId = group,
            autoCommit = autoCommit && group.isNotBlank(),
            startFrom = startFrom,
            partitions = partitions
                .split(',', ';')
                .filter { it.isNotBlank() }
                .mapTo(mutableSetOf()) { it.toInt() }
        )

        consumerWorker = KafkaConsumerWorker(connectionProperties, consumerConfig, recordsTableModel)
            .also { worker ->
                worker.addPropertyChangeListener { event ->
                    if (event.propertyName == "state" && event.newValue == SwingWorker.StateValue.STARTED) {
                        firePropertyChange(ACTION_START_CONSUMING, false, isConsuming)
                    }
                }
                worker.execute()
            }
    }

    fun stopConsuming() {
        logger.log(Level.INFO, "[$loggerMarker] Stopping consumer")
        firePropertyChange(ACTION_STOP_CONSUMING, isConsuming, false)
        consumerWorker?.stop()
    }

    fun clearTableData() {
        recordsTableModel.clear()
    }

    fun selectRecordsTableRow(selectedRow: Int) {
        if (selectedRow >= 0) {
            recordsTableModel.getRowAt(selectedRow).let { record ->
                recordUnformattedKey = record.key
                recordUnformattedValue = record.value
                recordRawKey = record.rawKey
                recordRawValue = record.rawValue

                recordValue = formatValueAs(recordValueFormatStrategy, recordUnformattedValue).orEmpty()
                recordKey = formatValueAs(recordKeyFormatStrategy, recordUnformattedKey).orEmpty()
                recordHeaders = record.headers.joinToString("\n") { (key, value) -> "$key=$value" }
                recordTopic = record.topic
                recordPartition = record.partition.toString()
                recordOffset = record.offset.toString()
                recordTimestamp = record.timestamp
                recordKeySize = record.serializedKeySize.toString()
                recordValueSize = record.serializedValueSize.toString()
                recordKeyType = record.keyType.displayText
                recordValueType = record.valueType.displayText
            }
        } else {
            recordUnformattedKey = null
            recordUnformattedValue = null
            recordRawKey = null
            recordRawValue = null

            recordValue = ""
            recordKey = ""
            recordHeaders = ""
            recordTopic = ""
            recordPartition = ""
            recordOffset = ""
            recordTimestamp = ""
            recordKeySize = ""
            recordValueSize = ""
            recordKeyType = ""
            recordValueType = ""
        }
    }

    fun saveValueToFile(selectedFile: File, callback: (Result<Unit>) -> Unit) {
        try {
            logger.log(Level.FINE, "[$loggerMarker] Save consumed value to file: $selectedFile")
            Files.write(selectedFile.toPath(), requireNotNull(recordRawValue) { "Consumed value is null" }, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$loggerMarker] Cannot save consumed value to file: $selectedFile", e)
            callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
        }
    }

    fun saveKeyToFile(selectedFile: File, callback: (Result<Unit>) -> Unit) {
        try {
            logger.log(Level.FINE, "[$loggerMarker] Save consumed key to file: $selectedFile")
            Files.write(selectedFile.toPath(), requireNotNull(recordRawKey) { "Consumed key is null" }, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            callback(Result.success(Unit))
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$loggerMarker] Cannot save consumed key to file: $selectedFile", e)
            callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
        }
    }


    fun updateTopics(topics: List<String>) {
        if (!isConsuming) {
            topic.let { selectedTopic ->
                topicsComboBoxModel.removeAllElements()
                topicsComboBoxModel.addAll(topics)
                topicsComboBoxModel.selectedItem = selectedTopic
            }
        }
    }

    fun updateGroups(groups: List<String>) {
        if (!isConsuming) {
            group.let { selectedGroup ->
                groupsComboBoxModel.removeAllElements()
                groupsComboBoxModel.addAll(groups)
                groupsComboBoxModel.selectedItem = selectedGroup
            }
        }
    }


    private fun formatValueAs(formatStrategy: FormatStrategy, value: String?): String? =
        value?.let {
            when (formatStrategy) {
                FormatStrategy.None -> it
                FormatStrategy.Json -> formatAsJson(it)
                FormatStrategy.Xml -> formatAsXml(it)
            }
        }

    private fun formatAsJson(value: String): String =
        buildString {
            var indent = 0
            var inString = false
            var escapeNext = false

            for (i in value.indices) {
                val char = value[i]
                when {
                    escapeNext -> {
                        append(char)
                        escapeNext = false
                    }
                    char == '\\' -> {
                        append(char)
                        escapeNext = true
                    }
                    char == '"' -> {
                        append(char)
                        inString = !inString
                    }
                    inString -> append(char)
                    char == '{' || char == '[' -> {
                        append(char)
                        append('\n')
                        indent++
                        append(" ".repeat(indent * indentSize))
                    }
                    char == '}' || char == ']' -> {
                        append('\n')
                        indent--
                        append(" ".repeat(indent * indentSize))
                        append(char)
                    }
                    char == ',' -> {
                        append(char)
                        append('\n')
                        append(" ".repeat(indent * indentSize))
                    }
                    char == ':' -> {
                        append(char)
                        append(' ')
                    }
                    !char.isWhitespace() -> append(char)
                }
            }
        }

    private fun formatAsXml(value: String): String =
        try {
            StringWriter()
                .use { writer ->
                    xmlTransformer.transform(StreamSource(StringReader(value.trim())), StreamResult(writer))
                    writer.flush()
                    writer.toString()
                }
                .lineSequence()
                .filter { line -> line.isNotBlank() }
                .joinToString("\n") { line ->
                    if (line.startsWith("<?xml")) {
                        if (!line.endsWith("?>")) {
                            line.replace("?>", "?>\n")
                        } else {
                            line
                        }
                    } else {
                        line
                    }
                }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$loggerMarker] Error occurred while formating xml", e)
            value
        }

    private fun Map<String, String>.keyDeserializationTypeOrDefault(): SerializationType =
        runCatching {
            get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)?.let { SerializationType.findByDeserializerClassName(it) }
        }.getOrDefault(null) ?: SerializationType.String

    private fun Map<String, String>.valueDeserializationTypeOrDefault(): SerializationType =
        runCatching {
            get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)?.let { SerializationType.findByDeserializerClassName(it) }
        }.getOrDefault(null) ?: SerializationType.String

}
