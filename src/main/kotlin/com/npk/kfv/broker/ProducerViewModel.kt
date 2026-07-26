package com.npk.kfv.broker

import com.npk.kfv.APPLICATION_ARTIFACT_ID
import com.npk.kfv.FlexPropertiesSupport.printHeader
import com.npk.kfv.FlexPropertiesSupport.printSection
import com.npk.kfv.FlexPropertiesSupport.readBySection
import com.npk.kfv.service.*
import com.npk.kfv.service.RandomDataGenerator.CountryType
import com.npk.swing.ViewModel
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.record.internal.CompressionType
import org.apache.kafka.common.serialization.StringSerializer
import java.awt.event.ActionEvent
import java.io.File
import java.io.PrintWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingWorker
import kotlin.math.round

class ProducerViewModel(private val configBroker: ConfigBroker) : ViewModel() {

    companion object {
        private const val DEFAULT_PARTITION =       ""
        private val DEFAULT_COMPRESSION =           CompressionType.NONE
        private const val DEFAULT_HEADERS =         "app.name=$APPLICATION_ARTIFACT_ID"
        private val DEFAULT_KEY_TYPE =              SerializationType.String
        private val DEFAULT_VALUE_TYPE =            SerializationType.String

        private val PRODUCER_PARTITION_KEY =        "producer:partition"
        private val PRODUCER_COMPRESSION_KEY =      "producer:compression"
        private val PRODUCER_HEADERS_KEY =          "producer:headers"
        private val PRODUCER_KEY_TYPE_KEY =         "producer:key.type"
        private val PRODUCER_KEY_DATA_KEY =         "producer:key.data"
        private val PRODUCER_VALUE_TYPE_KEY =       "producer:value.type"
        private val PRODUCER_VALUE_DATA_KEY =       "producer:value.data"
        private val PRODUCER_VALUE_FROMFILE_KEY =   "producer:value.fromFile"
        private val PRODUCER_VALUE_FILE_KEY =       "producer:value.file"
    }

    private val logger = Logger.getLogger(this.javaClass.getName())
    private val loggerMarker = configBroker.loggerMarker

    private val localStorage = mutableMapOf<String, MutableMap<String, Any?>>()
    private lateinit var preferences: MutableMap<String, Any?>

    init {
        addPropertyChangeListener { event ->
            when (event.propertyName) {
                ::keySerializerHint.name,
                ::valueSerializerHint.name -> return@addPropertyChangeListener
            }
            if (event.propertyName == ::topic.name) {
                preferences = localStorage.computeIfAbsent(event.newValue.toString()) { mutableMapOf() }

                partition = preferences[::partition.name] as String? ?: DEFAULT_PARTITION
                compression = preferences[::compression.name] as CompressionType? ?: DEFAULT_COMPRESSION
                headers = preferences[::headers.name] as String? ?: DEFAULT_HEADERS
                keyType = preferences[::keyType.name] as SerializationType? ?: DEFAULT_KEY_TYPE
                keyData = preferences[::keyData.name] as String? ?: ""
                valueType = preferences[::valueType.name] as SerializationType? ?: DEFAULT_KEY_TYPE
                valueData = preferences[::valueData.name] as String? ?: ""
                valueDataIsFromFile = preferences[::valueDataIsFromFile.name] as Boolean? ?: false
                valueDataFile = preferences[::valueDataFile.name] as String? ?: ""
            } else {
                when (event.propertyName) {
                    ::keyType.name -> {
                        keySerializerHint = (event.newValue as? SerializationType)?.serializer?.java?.name.orEmpty()
                    }
                    ::valueType.name -> {
                        valueDataIsFromFile = false
                        valueSerializerHint = (event.newValue as? SerializationType)?.serializer?.java?.name.orEmpty()
                    }
                }
                preferences[event.propertyName] = event.newValue
            }
        }
    }

    var topic: String by observableProperty("")
    var partition: String by observableProperty(DEFAULT_PARTITION)
    var compression: CompressionType by observableProperty(configBroker.compressionTypeOrDefault())
    var headers: String by observableProperty(DEFAULT_HEADERS)
    var keyType: SerializationType by observableProperty(configBroker.keySerializationTypeOrDefault())
    var keySerializerHint: String by observableProperty(keyType.serializer.java.name.orEmpty())
    var keyData: String by observableProperty("")
    var valueType: SerializationType by observableProperty(configBroker.valueSerializationTypeOrDefault())
    var valueSerializerHint: String by observableProperty(valueType.serializer.java.name.orEmpty())
    var valueData: String by observableProperty("")
    var valueDataIsFromFile: Boolean by observableProperty(false)
    var valueDataFile: String by observableProperty("")

    val topicsComboBoxModel = DefaultComboBoxModel<String>()

    fun updateTopics(topics: List<String>) {
        topicsComboBoxModel.removeAllElements()
        topicsComboBoxModel.addAll(topics)
        topicsComboBoxModel.selectedItem = topic
    }

    fun onGenerateKeyRandomDataActionPerformed(event: ActionEvent) {
        keyData = generateRandomData(keyType)
    }

    fun onGenerateValueRandomDataActionPerformed(event: ActionEvent) {
        valueData = generateRandomData(valueType)
    }

    fun produceMessage(callback: (Result<RecordMetadata>) -> Unit) {
        (object : SwingWorker<RecordMetadata, Unit>() {
            override fun doInBackground(): RecordMetadata {
                logger.log(Level.INFO, "[$loggerMarker] Produce message to topic $topic")

                val properties = configBroker.connectionProperties.toMutableMap()
                properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keyType.serializer.java.name
                properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueType.serializer.java.name
                properties[ProducerConfig.COMPRESSION_TYPE_CONFIG] = compression.name

                val valueData = if (valueDataIsFromFile) {
                    if (valueType.serializer is StringSerializer) {
                        logger.log(Level.FINE, "[$loggerMarker] Load string data from file $valueDataFile")
                        Files.readString(Path.of(valueDataFile))
                    } else {
                        logger.log(Level.FINE, "[$loggerMarker] Load byte array data from file $valueDataFile")
                        Files.readAllBytes(Path.of(valueDataFile))
                    }
                } else {
                    valueType.valueConverter.convertToObject(valueData)
                }

                return KafkaService.produceMessage(
                    properties,
                    topic,
                    partition.takeIf { it.isNotBlank() }?.toInt(),
                    keyType.valueConverter.convertToObject(keyData),
                    valueData,
                    headers
                )
            }
            override fun done() {
                try {
                    val value = get()
                    logger.log(Level.FINE, "[$loggerMarker] Message produced for topic '${value.topic()}', partition '${value.partition()}', offset '${value.offset()}'")
                    callback(Result.success(value))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while deleting topic", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun saveToFile(selectedFile: File, callback: (Result<File>) -> Unit) {
        logger.log(Level.FINE, "[$loggerMarker] Save producer data to file: $selectedFile")
        try {
            Files.newBufferedWriter(selectedFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
                PrintWriter(writer).apply {
                    printHeader {
                        println("broker.id=${configBroker.id}")
                        println("broker.name=${configBroker.name}")
                        println("topic=$topic")
                    }
                    printSection(PRODUCER_PARTITION_KEY, partition)
                    printSection(PRODUCER_COMPRESSION_KEY, compression)
                    printSection(PRODUCER_HEADERS_KEY, headers)
                    printSection(PRODUCER_KEY_TYPE_KEY, keyType)
                    printSection(PRODUCER_KEY_DATA_KEY, keyData)
                    printSection(PRODUCER_VALUE_TYPE_KEY, valueType)
                    printSection(PRODUCER_VALUE_DATA_KEY, valueData)
                    printSection(PRODUCER_VALUE_FROMFILE_KEY, valueDataIsFromFile)
                    printSection(PRODUCER_VALUE_FILE_KEY, valueDataFile)
                }
            }
            callback(Result.success(selectedFile))
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$loggerMarker] Cannot save producer data to file: $selectedFile", e)
            callback(Result.failure(e))
        }
    }

    fun loadFromFile(selectedFile: File, callback: (Result<File>) -> Unit) {
        logger.log(Level.FINE, "[$loggerMarker] Load producer data from file: $selectedFile")
        try {
            val selectedPath = selectedFile.toPath()
            require(Files.exists(selectedPath)) { "File not exists: $selectedFile" }

            Files.newBufferedReader(selectedFile.toPath()).use { reader ->
                partition = preferences[::partition.name] as String? ?: DEFAULT_PARTITION
                compression = preferences[::compression.name] as CompressionType? ?: DEFAULT_COMPRESSION
                headers = preferences[::headers.name] as String? ?: DEFAULT_HEADERS
                keyType = preferences[::keyType.name] as SerializationType? ?: DEFAULT_KEY_TYPE
                keyData = ""
                valueType = preferences[::valueType.name] as SerializationType? ?: DEFAULT_VALUE_TYPE
                valueData = ""
                valueDataIsFromFile = false
                valueDataFile = ""

                reader.readBySection { section, value ->
                    when (section) {
                        PRODUCER_PARTITION_KEY -> partition = value.toIntOrNull()?.toString().orEmpty()
                        PRODUCER_COMPRESSION_KEY -> compression = CompressionType.forName(value)
                        PRODUCER_HEADERS_KEY -> headers = value
                        PRODUCER_KEY_TYPE_KEY -> keyType = SerializationType.valueOf(value)
                        PRODUCER_KEY_DATA_KEY -> keyData = value
                        PRODUCER_VALUE_TYPE_KEY -> valueType = SerializationType.valueOf(value)
                        PRODUCER_VALUE_DATA_KEY -> valueData = value
                        PRODUCER_VALUE_FROMFILE_KEY -> valueDataIsFromFile = value.toBoolean()
                        PRODUCER_VALUE_FILE_KEY -> valueDataFile = value
                    }
                }

                if (valueDataIsFromFile) {
                    if (valueDataFile.isNotEmpty()) {
                        valueData = ""
                    } else {
                        valueDataIsFromFile = false
                    }
                } else {
                    valueDataFile = ""
                }
            }
            callback(Result.success(selectedFile))
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$loggerMarker] Cannot load data from file: $selectedFile", e)
            callback(Result.failure(e))
        }
    }

    private fun generateRandomData(type: SerializationType): String =
        when (type) {
            SerializationType.String -> random { string() }
            SerializationType.Uuid -> random { uuid() }
            SerializationType.ObjectId -> random { objectId() }
            SerializationType.Short -> random { int().toShort().toString() }
            SerializationType.Integer -> random { int().toString() }
            SerializationType.Long -> random { long().toString() }
            SerializationType.Float -> random { float().toString() }
            SerializationType.Double -> random { double().toString() }
            SerializationType.Boolean -> random { boolean().toString() }
            SerializationType.ByteArray -> random { "base64:" + Base64.getEncoder().encodeToString(random { string(32..64).toByteArray() }) }
            SerializationType.Json ->
                buildString {
                    appendLine("{")
                    randomDataObject().let { obj ->
                        obj.entries.forEachIndexed { index, (key, value) ->
                            append("    \"$key\" : ")
                            append(if (value is String) "\"$value\"" else value)
                            if (index < obj.size - 1) {
                                append(",")
                            }
                            append("\n")
                        }
                    }
                    append("}")
                }
            SerializationType.None -> ""
        }

    private fun randomDataObject(): Map<String, Any> = buildMap {
        fun putRandomValue(key: String, randomValueGenerator: RandomDataGenerator.() -> Any) {
            if (RandomDataGenerator.boolean()) {
                put(key, RandomDataGenerator.randomValueGenerator())
            }
        }

        putRandomValue("id", { "${long()}" })
        putRandomValue("isActive", { boolean() })
        putRandomValue("name", { name() })
        putRandomValue("firstName", { name() })
        putRandomValue("lastName", { name() })
        putRandomValue("age", { int(1..99)})
        putRandomValue("index", { int() })
        putRandomValue("guid", { uuid() })
        putRandomValue("email", { email() })
        putRandomValue("temperature", { round(double(-89.2, 56.7) * 10.0) / 10.0 })
        putRandomValue("color", { from("Black", "Gray", "Blue", "Green", "Cyan", "Red", "Magenta", "Yellow", "White") })
        putRandomValue("status", { from("OPEN", "IN_PROGRESS", "REOPENED", "RESOLVED", "CLOSED") })
        putRandomValue("country", { country(CountryType.Name) })
        putRandomValue("currency", { currency().currencyCode })
        putRandomValue("phone", { phoneNumber() })
        putRandomValue("latitude", { BigDecimal(double(-90.0, +90.0)).setScale(6, RoundingMode.HALF_EVEN) })
        putRandomValue("longitude", { BigDecimal(double(-180.0, +180.0)).setScale(6, RoundingMode.HALF_EVEN) })
    }

    private fun ConfigBroker.compressionTypeOrDefault(): CompressionType =
        runCatching {
            CompressionType.forName(connectionProperties[ProducerConfig.COMPRESSION_TYPE_CONFIG])
        }.getOrDefault(DEFAULT_COMPRESSION)

    private fun ConfigBroker.keySerializationTypeOrDefault(): SerializationType =
        runCatching {
            connectionProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG]?.let { SerializationType.findBySerializerClassName(it) }
        }.getOrDefault(DEFAULT_KEY_TYPE) ?: DEFAULT_KEY_TYPE

    private fun ConfigBroker.valueSerializationTypeOrDefault(): SerializationType =
        runCatching {
            connectionProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG]?.let { SerializationType.findBySerializerClassName(it) }
        }.getOrDefault(null) ?: DEFAULT_KEY_TYPE

}