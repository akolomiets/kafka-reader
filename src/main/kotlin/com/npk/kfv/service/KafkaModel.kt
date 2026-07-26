package com.npk.kfv.service

import com.npk.kfv.Tuples
import com.npk.swing.HasDisplayText
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.GroupState
import org.apache.kafka.common.GroupType
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.*
import java.util.*
import kotlin.reflect.KClass

class KafkaConsumerRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: String,
    val headers: List<Pair<String, String>>,
    val keyType: SerializationType,
    val valueType: SerializationType,
    val serializedKeySize: Int,
    val serializedValueSize: Int,
    val key: String?,
    val value: String?,
    val rawKey: ByteArray?,
    val rawValue: ByteArray?
)

class KafkaConsumerConfig(
    val topic: String,
    val keyType: SerializationType,
    val valueType: SerializationType,
    val groupId: String,
    val autoCommit: Boolean,
    val startFrom: OffsetStrategy,
    val partitions: Set<Int>
)

class TopicPartitionDesc(
    val partition: Int,
    val messageCount: Long,
    val startOffset: Long,
    val endOffset: Long,
    val leader: Int,
    val replicas: List<Int>,
    val isr: List<Int>
)

class GroupDesc(
    val name: String,
    val state: GroupState,
    val type: GroupType,
    val topics: List<Tuples.Tuple2<TopicPartition, OffsetAndMetadata>>
)

enum class OffsetStrategy(override val displayText: String) : HasDisplayText {
    Latests("Latests"),
    Earliest("From the beginning"),
    LastHour("Last hour"),
    Today("Today"),
    Yesterday("Yesterday")
}

enum class SerializationType(
    override val displayText: String,
    val valueConverter: ValueConverter<*>,
    val serializer: KClass<out Serializer<*>>,
    val deserializer: KClass<out Deserializer<*>>
) : HasDisplayText {

    String("String", ValueConverters.stringConverter, StringSerializer::class, StringDeserializer::class),
    Uuid("UUID", ValueConverters.uuidConverter, UUIDSerializer::class, UUIDDeserializer::class),
    ObjectId("ObjectId", ValueConverters.stringConverter, StringSerializer::class, StringDeserializer::class),
    Short("Short", ValueConverters.shortConverter, ShortSerializer::class, ShortDeserializer::class),
    Integer("Integer", ValueConverters.intConverter, IntegerSerializer::class, IntegerDeserializer::class),
    Long("Long", ValueConverters.longConverter, LongSerializer::class, LongDeserializer::class),
    Float("Float", ValueConverters.floatConverter, FloatSerializer::class, FloatDeserializer::class),
    Double("Double", ValueConverters.doubleConverter, DoubleSerializer::class, DoubleDeserializer::class),
    Boolean("Boolean", ValueConverters.booleanConverter, BooleanSerializer::class, BooleanDeserializer::class),
    ByteArray("Bytes", ValueConverters.byteArrayConverter, ByteArraySerializer::class, ByteArrayDeserializer::class),
    Json("JSON", ValueConverters.stringConverter, StringSerializer::class, StringDeserializer::class),
    None("None", ValueConverters.noneValueConverter, VoidSerializer::class, VoidDeserializer::class);

    companion object {

        fun findBySerializerClassName(className: String): SerializationType? =
            entries.find { it.serializer.java.name == className }

        fun findByDeserializerClassName(className: String): SerializationType? =
            entries.find { it.deserializer.java.name == className }

    }

}

interface ValueConverter<T> {

    fun convertToString(value: T): String

    fun convertToObject(value: String): T

}

object ValueConverters {

    val stringConverter: ValueConverter<String?> = object : ValueConverter<String?> {
        override fun convertToString(value: String?): String = value.orEmpty()
        override fun convertToObject(value: String): String = value
    }

    val uuidConverter: ValueConverter<UUID?> = object : ValueConverter<UUID?> {
        override fun convertToString(value: UUID?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): UUID = UUID.fromString(value)
    }

    val shortConverter: ValueConverter<Short?> = object : ValueConverter<Short?> {
        override fun convertToString(value: Short?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Short? = value.takeIf { it.isNotBlank() }?.toShort()
    }

    val intConverter: ValueConverter<Int?> = object : ValueConverter<Int?> {
        override fun convertToString(value: Int?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Int? = value.takeIf { it.isNotBlank() }?.toInt()
    }

    val longConverter: ValueConverter<Long?> = object : ValueConverter<Long?> {
        override fun convertToString(value: Long?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Long? = value.takeIf { it.isNotBlank() }?.toLong()
    }

    val floatConverter: ValueConverter<Float?> = object : ValueConverter<Float?> {
        override fun convertToString(value: Float?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Float? = value.takeIf { it.isNotBlank() }?.toFloat()
    }

    val doubleConverter: ValueConverter<Double?> = object : ValueConverter<Double?> {
        override fun convertToString(value: Double?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Double? = value.takeIf { it.isNotBlank() }?.toDouble()
    }

    val booleanConverter: ValueConverter<Boolean?> = object : ValueConverter<Boolean?> {
        override fun convertToString(value: Boolean?): String = value?.toString().orEmpty()
        override fun convertToObject(value: String): Boolean? = value.takeIf { it.isNotBlank() }?.toBoolean()
    }

    val byteArrayConverter: ValueConverter<ByteArray?> = object : ValueConverter<ByteArray?> {
        override fun convertToString(value: ByteArray?): String = value?.let { String(it) }.orEmpty()
        override fun convertToObject(value: String): ByteArray? =
            value
                .takeIf { it.isNotEmpty() }
                ?.let {
                    if (it.startsWith("base64:")) {
                        try {
                            Base64.getDecoder().decode(it.substring(7))
                        } catch (_: Exception) {
                            it.toByteArray()
                        }
                    } else {
                        it.toByteArray()
                    }
                }
    }

    val noneValueConverter = object : ValueConverter<Any?> {
        override fun convertToString(value: Any?): String = ""
        override fun convertToObject(value: String): Any? = null
    }

}