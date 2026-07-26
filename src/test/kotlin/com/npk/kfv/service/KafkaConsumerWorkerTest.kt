package com.npk.kfv.service

import com.npk.embedded.kafka.EmbeddedKafkaBroker
import com.npk.embedded.kafka.EmbeddedKafkaExtension
import com.npk.embedded.kafka.producerRecord
import com.npk.kfv.APPLICATION_ARTIFACT_ID
import com.npk.kfv.APPLICATION_VERSION
import com.npk.kfv.IntellijGuarded
import com.npk.kfv.broker.KafkaConsumerRecordsTableModel
import com.npk.swing.jbutton
import com.npk.swing.jpanel
import org.apache.kafka.common.serialization.Serializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.JOptionPane
import javax.swing.SwingWorker.StateValue
import javax.swing.UIManager
import kotlin.properties.Delegates

@IntellijGuarded
@ExtendWith(EmbeddedKafkaExtension::class)
internal class KafkaConsumerWorkerTest {

    private val topic = "test.consumer"
    private val consumerConfig = KafkaConsumerConfig(
        topic = topic,
        keyType = SerializationType.Uuid,
        valueType = SerializationType.Integer,
        groupId = "",
        autoCommit = false,
        startFrom = OffsetStrategy.Earliest,
        partitions = emptySet()
    )

    @Test
    fun `run kafka consumer worker`(embeddedKafkaBroker: EmbeddedKafkaBroker) {
        createTestTopicWithMessages(embeddedKafkaBroker)

        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf")
        JOptionPane.showMessageDialog(
            null,
            jpanel {
                var worker: KafkaConsumerWorker by Delegates.notNull()

                val tableModel = KafkaConsumerRecordsTableModel()
                tableModel.addTableModelListener { event ->
                    println("Table Model updated: $event")
                }

                +jbutton("Start Consuming", {
                    worker = KafkaConsumerWorker(embeddedKafkaBroker.connectionProperties, consumerConfig, tableModel)
                    worker.addPropertyChangeListener { event ->
                        if (event.propertyName == "state" && event.newValue == StateValue.STARTED) {
                            println("[*] Start Consuming")
                        } else {
                            println("[ ] Stop Consuming")
                        }
                    }
                    worker.execute()

                    println("  >>> ${worker.state()}")
                })
                +jbutton("Stop Consuming", { worker.stop() })
                +jbutton("Cancel Consumer", { worker.cancel(true) })
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createTestTopicWithMessages(embeddedKafkaBroker: EmbeddedKafkaBroker) {
        val keySerializer = consumerConfig.keyType.serializer.java as Class<out Serializer<Any>>
        val valueSerializer = consumerConfig.valueType.serializer.java as Class<out Serializer<Any?>>

        embeddedKafkaBroker.getKafkaProducer(keySerializer, valueSerializer).use { producer ->
            val futures = (1 .. 25)
                .map {
                    println("[$topic] Push test message $it")
                    val future = producer.send(
                        producerRecord(topic) {
                            key(generateRandomValueFor(consumerConfig.keyType))
                            value(generateRandomValueFor(consumerConfig.valueType))
                            headers(mapOf(
                                "app.name" to APPLICATION_ARTIFACT_ID,
                                "app.version" to APPLICATION_VERSION,
                                "env" to this::class.java.name
                            ))
                        }
                    )
                    CompletableFuture.supplyAsync { future.get() }
                }

            CompletableFuture.allOf(*futures.toTypedArray()).join()
        }
    }

    private fun generateRandomValueFor(type: SerializationType): Any? =
        when (type) {
            SerializationType.String -> random { name() }
            SerializationType.Uuid -> UUID.randomUUID()
            SerializationType.ObjectId -> random { objectId() }
            SerializationType.Short -> random { int().toShort() }
            SerializationType.Integer -> random { int() }
            SerializationType.Long -> random { long() }
            SerializationType.Float -> random { float() }
            SerializationType.Double -> random { double() }
            SerializationType.Boolean -> random { boolean() }
            SerializationType.ByteArray -> random { string(10..20).toByteArray() }
            SerializationType.Json -> random { "{ 'name': '${name()}' }" }
            SerializationType.None -> null
        }

}