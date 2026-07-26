package com.npk.embedded.kafka

import kafka.server.KafkaConfig
import kafka.server.KafkaRaftServer
import kafka.server.Server
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.utils.Time
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

interface EmbeddedKafkaBroker {

    val connectionProperties: Map<String, Any?>

    fun <K, V> getKafkaProducer(keySerializer: Class<out Serializer<K>>, valueSerializer: Class<out Serializer<V>>): Producer<K, V>

    fun <K, V> getKafkaConsumer(keyDeserializer: Class<out Deserializer<K>>, valueDeserializer: Class<out Deserializer<V>>): Consumer<K, V>

}

internal class EmbeddedKafkaBrokerImpl(private val brokerPort: Int = DEFAULT_BROKER_PORT, private val controllerPort: Int = DEFAULT_CONTROLLER_PORT) : EmbeddedKafkaBroker, Closeable {

    companion object {
        const val DEFAULT_BROKER_PORT =     9092
        const val DEFAULT_CONTROLLER_PORT = 9093

        private const val CLIENT_ID =   "embedded-kafka"
        private const val GROUP_ID =    "embedded-kafka"
        private const val LOG_MARKER =  "[EMBEDDED KAFKA]"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var logFolder: Path
    private lateinit var server: Server

    @Volatile
    private var isRunning: Boolean = false

    override val connectionProperties: Map<String, Any?> by lazy {
        mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:$brokerPort",
            CommonClientConfigs.CLIENT_ID_CONFIG to CLIENT_ID,
            CommonClientConfigs.GROUP_ID_CONFIG to GROUP_ID
        )
    }

    override fun <K, V> getKafkaProducer(keySerializer: Class<out Serializer<K>>, valueSerializer: Class<out Serializer<V>>): Producer<K, V> {
        check(isRunning) { "EmbeddedKafkaBroker is not running" }
        return KafkaProducer(buildDefaultProducerConfig(keySerializer, valueSerializer))
    }

    override fun <K, V> getKafkaConsumer(keyDeserializer: Class<out Deserializer<K>>, valueDeserializer: Class<out Deserializer<V>>): Consumer<K, V> {
        check(isRunning) { "EmbeddedKafkaBroker is not running" }
        return KafkaConsumer(buildDefaultConsumerConfig(keyDeserializer, valueDeserializer))
    }

    fun start() {
        if (!isRunning) {
            synchronized(this) {
                if (!isRunning) {
                    startBroker()
                    isRunning = true
                }
            }
        }
    }

    override fun close() {
        if (isRunning) {
            synchronized(this) {
                if (isRunning) {
                    stopBroker()
                    isRunning = false
                }
            }
        }
    }

    private fun startBroker() {
        try {
            logFolder = Files.createTempDirectory("$CLIENT_ID-")

            logger.info("$LOG_MARKER Starting kafka broker on port $brokerPort")
            Files.writeString(
                logFolder.resolve("meta.properties"),
                """
                    version=0
                    broker.id=1
                    cluster.id=${UUID.randomUUID()}
                """.trimIndent()
            )

            server = KafkaRaftServer(KafkaConfig(buildDefaultBrokerConfig(), false), Time.SYSTEM)
            server.startup()
        } catch (e: Exception) {
            logger.error("$LOG_MARKER Error starting kafka broker", e)
        }
    }

    private fun stopBroker() {
        try {
            logger.info("$LOG_MARKER Shutting down kafka broker")
            server.shutdown()
            server.awaitShutdown()

            if (Files.exists(logFolder)) {
                logger.info("$LOG_MARKER Deleting log folder: $logFolder")
                Files.walk(logFolder).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
                }
            }
        } catch (e: Exception) {
            logger.error("$LOG_MARKER Error clean-upping ", e)
        }
    }

    private fun buildDefaultBrokerConfig() =
        mapOf(
            // Node configuration
            "node.id" to "1",
            "process.roles" to "broker,controller",
            "controller.quorum.voters" to "1@localhost:$controllerPort",
            // Listeners
            "listeners" to "PLAINTEXT://0.0.0.0:$brokerPort,CONTROLLER://0.0.0.0:$controllerPort",
            "advertised.listeners" to "PLAINTEXT://localhost:$brokerPort",
            "controller.listener.names" to "CONTROLLER",
            "listener.security.protocol.map" to "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
            "inter.broker.listener.name" to "PLAINTEXT",
            // Log directory
            "log.dirs" to logFolder.toAbsolutePath().toString(),
            "metadata.log.dir" to logFolder.toAbsolutePath().toString(),
            // Topic defaults
            "num.partitions" to "3",
            "offsets.topic.num.partitions" to "1",
            "default.replication.factor" to "1",
            "offsets.topic.replication.factor" to "1",
            "auto.leader.rebalance.enable" to "false",
            "leader.imbalance.check.interval.seconds" to "300",
            "unclean.leader.election.enable" to "false",
            "auto.create.topics.enable" to "true",
            "delete.topic.enable" to "true",
            // Performance tuning
            "group.min.session.timeout.ms" to "100",
            "transaction.state.log.replication.factor" to "1",
            "transaction.state.log.min.isr" to "1",
            "transaction.state.log.num.partitions" to "1",
            "transaction.timeout.ms" to "500",
            "num.io.threads" to "3",
            "num.network.threads" to "1",
            "queued.max.requests" to "100",
            "num.recovery.threads.per.data.dir" to "1",
            // "authorizer.class.name" to "org.apache.kafka.metadata.authorizer.StandardAuthorizer",
            // "allow.everyone.if.no.acl.found" to "true"
        )

    private fun <K, V> buildDefaultProducerConfig(keySerializer: Class<out Serializer<K>>, valueSerializer: Class<out Serializer<V>>) =
        mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:$brokerPort",
            CommonClientConfigs.CLIENT_ID_CONFIG to CLIENT_ID,
            CommonClientConfigs.GROUP_ID_CONFIG to GROUP_ID,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer,
            // ProducerConfig.TRANSACTIONAL_ID_CONFIG to "$CLIENT_ID-tx-1",
            // ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true"
        )

    private fun <K, V> buildDefaultConsumerConfig(keyDeserializer: Class<out Deserializer<K>>, valueDeserializer: Class<out Deserializer<V>>) =
        mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:$brokerPort",
            CommonClientConfigs.CLIENT_ID_CONFIG to CLIENT_ID,
            CommonClientConfigs.GROUP_ID_CONFIG to GROUP_ID,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "true",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer
        )

}