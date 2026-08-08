package com.npk.kfv

import com.npk.embedded.kafka.EmbeddedKafkaBroker
import com.npk.embedded.kafka.EmbeddedKafkaExtension
import com.npk.embedded.kafka.findSerializerByType
import com.npk.embedded.kafka.producerRecord
import com.npk.kfv.service.RandomDataGenerator
import com.npk.kfv.service.random
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

@IntellijGuarded
@ExtendWith(EmbeddedKafkaExtension::class)
internal class KafkaReaderTest {

    private val testValueGenerator: Map<Class<out Any>, Pair<String, RandomDataGenerator.() -> Any?>> = mapOf(
        Short::class.java to ("test.topic.short" to { int().toShort() }),
        Int::class.java to ("test.topic.int" to { int() }),
        Long::class.java to ("test.topic.long" to { long() }),
        Float::class.java to ("test.topic.float" to { float() }),
        Double::class.java to ("test.topic.double" to { double() }),
        Boolean::class.java to ("test.topic.boolen" to { boolean() }),
        String::class.java to ("test.topic.string" to { string(10 .. 50) }),
        ByteArray::class.java to ("test.topic.byte.array" to { string(10..20).toByteArray()}),
        UUID::class.java to ("test.topic.uuid" to { UUID.randomUUID() }),
        Void::class.java to ("test.topic.void" to { null })
    )

    @Test
    fun `launch with embedded kafka`(embeddedKafkaBroker: EmbeddedKafkaBroker) {
        startSendingRandomTestRecords(embeddedKafkaBroker)

        val edtRef = AtomicReference<Thread>()
        SwingUtilities.invokeLater {
            Launcher().launch()
            edtRef.set(Thread.currentThread())
        }

        Thread.sleep(Duration.ofSeconds(1))
        requireNotNull(edtRef.get()).join()
    }

    private fun startSendingRandomTestRecords(embeddedKafkaBroker: EmbeddedKafkaBroker) {
        thread(isDaemon = true) {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(Duration.ofSeconds(1))

                    val type = random { from(testValueGenerator.keys) }
                    val (topic, valueGenerator) = requireNotNull(testValueGenerator[type])
                    @Suppress("UNCHECKED_CAST")
                    sendTestRecord(embeddedKafkaBroker, type as Class<Any>, topic, random(valueGenerator)).get()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    private fun sendTestRecord(embeddedKafkaBroker: EmbeddedKafkaBroker, type: Class<Any>, topic: String, value: Any?): Future<RecordMetadata> =
        embeddedKafkaBroker.getKafkaProducer(findSerializerByType(UUID::class.java), findSerializerByType(type)).use { producer ->
            producer
                .send(
                    producerRecord(topic) {
                        key(UUID.randomUUID())
                        value(value)
                        headers(mapOf(
                            "app.name" to APPLICATION_ARTIFACT_ID,
                            "app.version" to APPLICATION_VERSION,
                            "env" to this::class.java.simpleName
                        ))
                    }
                )
        }

}
