package com.npk.kfv.service

import com.npk.embedded.kafka.EmbeddedKafkaBroker
import com.npk.embedded.kafka.EmbeddedKafkaExtension
import com.npk.embedded.kafka.produceMessage
import com.npk.embedded.kafka.producerRecord
import com.npk.kfv.intellijGuarded
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.ConfigResource.Type
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.UUIDSerializer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.SwingWorker
import kotlin.concurrent.thread

@ExtendWith(EmbeddedKafkaExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KafkaServiceTest {

    companion object {
        private const val TEST_TOPIC =  "test.topic"
        private const val NEW_TOPIC =   "new.topic"
    }

    private lateinit var connectionProperties: Map<String, String>

    @BeforeAll
    fun setUp(embeddedKafkaBroker: EmbeddedKafkaBroker) {
        @Suppress("UNCHECKED_CAST")
        connectionProperties = embeddedKafkaBroker.connectionProperties as Map<String, String>

        embeddedKafkaBroker.getKafkaProducer(StringSerializer::class.java, StringSerializer::class.java).use { producer ->
            repeat(3) {
                producer
                    .send(
                        producerRecord(TEST_TOPIC) {
                            key(random { uuid() })
                            value(random { string() })
                        }
                    )
                    .get()
            }
        }

        KafkaService.resetOffsetGroup(connectionProperties, "test-group", TEST_TOPIC, OffsetStrategy.Earliest)
    }

    @Test
    fun `service public variables - allConfigKeys`() {
        assertTrue(KafkaService.allConfigKeys.isNotEmpty())
        intellijGuarded {
            KafkaService.allConfigKeys.forEach { (key, value) ->
                println(key)
                println("  displayName: ${value.displayName}, alternativeString: ${value.alternativeString}")
                println("  type: ${value.type}, defaultValue: ${value.defaultValue}")
                println("  dependents: ${value.dependents}")
                println("  documentation: ${value.documentation}")
            }
        }
    }

    @Test
    fun `service public variables - allPropertyNames`() {
        assertTrue(KafkaService.allPropertyNames.isNotEmpty())
        intellijGuarded { println(KafkaService.allPropertyNames.joinToString("\n")) }
    }

    @Test
    fun `service public variables - allSensitivePropertyNames`() {
        assertTrue(KafkaService.allSensitivePropertyNames.isNotEmpty())
        intellijGuarded { println(KafkaService.allSensitivePropertyNames.joinToString("\n")) }
    }

    @Test
    fun `check connection`() {
        val result = KafkaService.checkConnection(connectionProperties)
        assertTrue(result.isNotEmpty())
        intellijGuarded { println("clusterId: $result") }
    }

    @Test
    fun `list topics info`() {
        val result = KafkaService.listTopicsInfo(connectionProperties, emptySet(), "", -1)
        intellijGuarded { println(result) }
    }

    @Test
    fun `list topic configuration`() {
        val result = KafkaService.listTopicConfiguration(connectionProperties, TEST_TOPIC)
        intellijGuarded { println(result) }
    }

    @Test
    fun `topic acl`() {
        runCatching { KafkaService.listTopicACL(connectionProperties, TEST_TOPIC) }
            .onSuccess { result ->
                intellijGuarded { println(result) }
            }
            .onFailure { e ->
                println(e)
            }
    }

    @Test
    fun `list groups info`() {
        val result = KafkaService.listGroupsInfo(connectionProperties, emptySet(), "", -1)
        intellijGuarded { println(result) }
    }

    @Test
    fun `reset group' offsets`() {
        KafkaService.resetOffsetGroup(connectionProperties, "test-group", TEST_TOPIC, OffsetStrategy.Latests)
    }

    @Test
    fun `clear topic`() {
        KafkaService.clearTopic(connectionProperties, TEST_TOPIC)
    }

    @Test
    fun `delete consumer group`() {
        KafkaService.deleteConsumerGroup(connectionProperties, "test-group")
    }

    @Test
    fun `create new topic and produce message`() {
        KafkaService.createTopic(connectionProperties, NEW_TOPIC, null, null).let { result ->
            intellijGuarded { println(result) }
        }

        KafkaService.produceMessage(
            connectionProperties.toMutableMap().apply {
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            },
            NEW_TOPIC,
            null,
            UUID.randomUUID(),
            random { string() },
            ""
        ).let { result ->
            intellijGuarded { println(result) }
        }
    }

    @Disabled // Delete topic crashes on Win
    @Test
    fun `create new topic and delete topic`() {
        KafkaService.createTopic(connectionProperties, NEW_TOPIC, 1, 1)
        KafkaService.deleteTopic(connectionProperties, NEW_TOPIC)
    }

}


