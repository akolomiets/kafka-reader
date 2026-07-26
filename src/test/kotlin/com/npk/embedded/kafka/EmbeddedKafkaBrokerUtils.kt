package com.npk.embedded.kafka

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.*
import java.util.*

fun <K, V> producerRecord(topic: String, builder: ProducerRecordBuilder<K, V>.() -> Unit): ProducerRecord<K, V> =
    ProducerRecordBuilder<K, V>(topic)
        .apply(builder)
        .build()

class ProducerRecordBuilder<K, V>(private val topic: String) {

    private var key: K? = null
    private var value: V? = null
    private var partition: Int? = null
    private var headers: Map<String, String>? = null

    fun key(key: K): ProducerRecordBuilder<K, V> {
        this.key = key
        return this
    }

    fun value(value: V): ProducerRecordBuilder<K, V> {
        this.value = value
        return this
    }

    fun partition(partition: Int): ProducerRecordBuilder<K, V> {
        this.partition = partition
        return this
    }

    fun headers(headers: Map<String, String>): ProducerRecordBuilder<K, V> {
        this.headers = headers
        return this
    }

    fun build(): ProducerRecord<K, V> =
        ProducerRecord<K, V>(topic, partition, key, value, headers?.map { RecordHeader(it.key, it.value.toByteArray(Charsets.UTF_8)) })

}


inline fun <reified K : Any, reified V : Any> EmbeddedKafkaBroker.getKafkaProducer(): Producer<K, V> =
    getKafkaProducer(findSerializerByType(K::class.java), findSerializerByType(V::class.java))

@Suppress("UNCHECKED_CAST")
fun <K, V> EmbeddedKafkaBroker.produceMessage(record: ProducerRecord<K, V>): RecordMetadata {
    val keySerializer = findSerializerByType(record.key()::class.java) as Class<out Serializer<K>>
    val valueSerializer = findSerializerByType(record.value()::class.java) as Class<out Serializer<V>>
    return getKafkaProducer(keySerializer, valueSerializer).use { producer -> producer.send(record).get() }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> findSerializerByType(type: Class<T>): Class<out Serializer<T>> =
    when (type) {
        Short::class.java -> ShortSerializer::class.java
        Int::class.java -> IntegerSerializer::class.java
        Long::class.java -> LongSerializer::class.java
        Float::class.java -> FloatSerializer::class.java
        Double::class.java -> DoubleSerializer::class.java
        Boolean::class.java -> BooleanSerializer::class.java
        String::class.java -> StringSerializer::class.java
        ByteArray::class.java -> ByteArraySerializer::class.java
        UUID::class.java -> UUIDSerializer::class.java
        Void::class.java -> VoidSerializer::class.java
        else -> throw IllegalArgumentException("Serializer by type $type not found")
    } as Class<out Serializer<T>>

@Suppress("UNCHECKED_CAST")
fun <T : Any> findDeserializerByType(type: Class<T>): Class<out Deserializer<T>> =
    when (type) {
        Short::class.java -> ShortDeserializer::class.java
        Int::class.java -> IntegerDeserializer::class.java
        Long::class.java -> LongDeserializer::class.java
        Float::class.java -> FloatDeserializer::class.java
        Double::class.java -> DoubleDeserializer::class.java
        Boolean::class.java -> BooleanDeserializer::class.java
        String::class.java -> StringDeserializer::class.java
        ByteArray::class.java -> ByteArrayDeserializer::class.java
        UUID::class.java -> UUIDDeserializer::class.java
        Void::class.java -> VoidDeserializer::class.java
        else -> throw IllegalArgumentException("Deserializer by type $type not found")
    } as Class<out Deserializer<T>>
