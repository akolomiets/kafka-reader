package com.npk.kfv.service

import com.npk.kfv.APPLICATION_ARTIFACT_ID
import com.npk.kfv.broker.KafkaConsumerRecordsTableModel
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.Deserializer
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingWorker

class KafkaConsumerWorker(
    properties: Map<String, Any?>,
    private val consumerConfig: KafkaConsumerConfig,
    private val tableModel: KafkaConsumerRecordsTableModel
) : SwingWorker<Boolean, KafkaConsumerRecord>() {

    private val consumer: KafkaConsumer<ByteArray, ByteArray> = properties.toMutableMap().let { props ->
        if (consumerConfig.groupId.isNotBlank()) {
            props[CommonClientConfigs.GROUP_ID_CONFIG] = consumerConfig.groupId
        } else {
            if (CommonClientConfigs.GROUP_ID_CONFIG !in props) {
                props[CommonClientConfigs.GROUP_ID_CONFIG] = "$APPLICATION_ARTIFACT_ID-${UUID.randomUUID()}"
            }
        }
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name

        KafkaConsumer(props)
    }

    private val keyDeserializer: Deserializer<*> = consumerConfig.keyType.deserializer.java
        .getDeclaredConstructor()
        .newInstance()
        .also { it.configure(properties, true) }
    @Suppress("UNCHECKED_CAST")
    private val keyValueConverter: ValueConverter<Any?> = consumerConfig.keyType.valueConverter as ValueConverter<Any?>

    private val valueDeserializer: Deserializer<*> = consumerConfig.valueType.deserializer.java
        .getDeclaredConstructor()
        .newInstance()
        .also { it.configure(properties, false) }
    @Suppress("UNCHECKED_CAST")
    private val valueValueConverter: ValueConverter<Any?> = consumerConfig.valueType.valueConverter as ValueConverter<Any?>

    private val running = AtomicBoolean(false)
    private val timeout = Duration.ofMillis(500)

    override fun doInBackground(): Boolean {
        val partitions = consumer
            .partitionsFor(consumerConfig.topic)
            .filter { consumerConfig.partitions.isEmpty() || it.partition() in consumerConfig.partitions }

        check(partitions.isNotEmpty()) { "No partitions found for topic: ${consumerConfig.topic}" }
        val topicPartitions = partitions.map { TopicPartition(consumerConfig.topic, it.partition()) }

        consumer.assign(topicPartitions)

        when (consumerConfig.startFrom) {
            OffsetStrategy.Latests -> consumer.seekToEnd(topicPartitions)
            OffsetStrategy.Earliest -> consumer.seekToBeginning(topicPartitions)
            else -> {
                val startTime = when (consumerConfig.startFrom) {
                    OffsetStrategy.LastHour -> (Instant.now() - Duration.ofHours(1))
                    OffsetStrategy.Today -> Instant.now().truncatedTo(ChronoUnit.DAYS)
                    OffsetStrategy.Yesterday -> (Instant.now() - Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS)
                }.toEpochMilli()

                val offsets = consumer.offsetsForTimes(topicPartitions.associateWith { startTime })
                topicPartitions.forEach { topicPartition ->
                    val offsetTimestamp = offsets[topicPartition]
                    if (offsetTimestamp != null) {
                        consumer.seek(topicPartition, offsetTimestamp.offset())
                    } else {
                        consumer.seekToEnd(listOf(topicPartition))
                    }
                }
            }
        }

        running.set(true)
        while (running.get() && !isCancelled) {
            val records = consumer.poll(timeout)
            if (!records.isEmpty) {
                records
                    .asSequence()
                    .map { it.toKafkaConsumerRecord() }
                    .chunked(10)
                    .forEach { chunk -> publish(*chunk.toTypedArray()) }

                if (consumerConfig.autoCommit) {
                    val offsets = records.associate { record -> TopicPartition(record.topic(), record.partition()) to OffsetAndMetadata(record.offset() + 1) }
                    consumer.commitSync(offsets)
                }
            }
        }

        return true
    }

    override fun process(chunks: List<KafkaConsumerRecord>) {
        tableModel.addChunks(chunks)
    }

    override fun done() {
        try {
            get()
        } catch (_: CancellationException) {
            running.set(false)
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause != null && cause !is WakeupException) {
                val record = KafkaConsumerRecord(
                    topic = consumerConfig.topic,
                    partition = -1,
                    offset = -1,
                    timestamp = Instant.now().toString(),
                    headers = emptyList(),
                    keyType = consumerConfig.keyType,
                    valueType = consumerConfig.valueType,
                    serializedKeySize = -1,
                    serializedValueSize = -1,
                    key = cause::class.java.simpleName,
                    value = cause.message ?: cause.toString(),
                    rawKey = null,
                    rawValue = null
                )
                tableModel.addChunks(listOf(record))
            }
        } finally {
            consumer.close()
        }
    }

    fun stop() {
        running.set(false)
        consumer.wakeup()
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.toKafkaConsumerRecord() = KafkaConsumerRecord(
        topic = topic(),
        partition = partition(),
        offset = offset(),
        timestamp = Instant.ofEpochMilli(timestamp()).toString(),
        headers = headers().map { header -> header.key() to header.value()?.let { String(it) }.orEmpty() },
        keyType = consumerConfig.keyType,
        valueType = consumerConfig.valueType,
        serializedKeySize = serializedKeySize(),
        serializedValueSize = serializedValueSize(),
        key = runCatching { keyValueConverter.convertToString(keyDeserializer.deserialize(topic(), key())) }.getOrElse { e -> e.message ?: e.toString() },
        value = runCatching { valueValueConverter.convertToString(valueDeserializer.deserialize(topic(), value())) }.getOrElse { e -> e.message ?: e.toString() },
        rawKey = key(),
        rawValue = value()
    )

}
