package com.npk.kfv.service

import com.npk.kfv.ApplicationPrefs
import com.npk.kfv.Tuples
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.GroupState
import org.apache.kafka.common.GroupType
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.acl.*
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.ConfigResource.Type
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.resource.ResourcePatternFilter
import org.apache.kafka.common.resource.ResourceType
import org.apache.kafka.common.utils.Utils
import java.io.StringReader
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.jvm.optionals.getOrDefault

object KafkaService {

    val allConfigKeys: Map<String, ConfigDef.ConfigKey> = buildMap {
        putAll(ConsumerConfig.configDef().configKeys())
        ProducerConfig.configDef().configKeys().forEach { (key, value) -> putIfAbsent(key, value) }
        AdminClientConfig.configDef().configKeys().forEach { (key, value) -> putIfAbsent(key, value) }
    }

    val allPropertyNames: Set<String> inline get() = allConfigKeys.keys

    val allSensitivePropertyNames: Set<String> =
        allConfigKeys
            .filter { entry -> !entry.value.internalConfig && entry.value.type.isSensitive }
            .mapTo(mutableSetOf()) { entry -> entry.key }

    fun checkConnection(properties: Map<String, String>): String =
        AdminClient.create(properties).use { client ->
            val options = DescribeClusterOptions()
            if (AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG !in properties) {
                options.timeoutMs(ApplicationPrefs.requestTimeoutMs)
            }
            client.describeCluster(options).clusterId().get()
        }

    fun listTopicsInfo(properties: Map<String, String>, favoriteTopics: Set<String>, topicFilter: String, limit: Int): List<Tuples.Tuple2<String, List<TopicPartitionDesc>>> =
        AdminClient.create(properties).use { client ->
            val filter: (String) -> Boolean = if (favoriteTopics.isEmpty() && topicFilter.isEmpty())
                { _ -> true }
            else
                { topicName ->
                    (favoriteTopics.isEmpty() || topicName in favoriteTopics)
                            && (topicName.isEmpty() || topicName.contains(topicFilter, true))
                }

            val options = ListTopicsOptions().listInternal(false)
            val topicsNames = client.listTopics(options).names().get().asSequence()
                .filter(filter)
                .let { if (limit > 0) it.take(limit) else it }
                .toList()

            val topicDescriptions = client.describeTopics(topicsNames).allTopicNames().get()

            val earliestSpec = OffsetSpec.earliest()
            val latestSpec = OffsetSpec.latest()

            val (startPartitionOffsets, endPartitionOffsets) = topicDescriptions.asSequence()
                .flatMap { (topicName, topicDescription) ->
                    topicDescription.partitions().map { TopicPartition(topicName, it.partition()) }
                }
                .fold(Tuples.of(mutableMapOf<TopicPartition, OffsetSpec>(), mutableMapOf<TopicPartition, OffsetSpec>())) { acc, topicPartition ->
                    acc.also { (earliestOffsets, latestOffsets) ->
                        earliestOffsets[topicPartition] = earliestSpec
                        latestOffsets[topicPartition] = latestSpec
                    }
                }

            val startPartitionOffsetsFuture = client.listOffsets(startPartitionOffsets).all()
            val endPartitionOffsetsFuture = client.listOffsets(endPartitionOffsets).all()
            KafkaFuture.allOf(startPartitionOffsetsFuture, endPartitionOffsetsFuture).get()

            val startOffsets = startPartitionOffsetsFuture.get()
            val endOffsets = endPartitionOffsetsFuture.get()

            topicDescriptions.map { (topicName, topicDescription) ->
                val topicPartitionDesc = topicDescription.partitions().map {
                    val topicPartition = TopicPartition(topicName, it.partition())

                    val startOffset = startOffsets[topicPartition]?.offset() ?: 0
                    val endOffset = endOffsets[topicPartition]?.offset() ?: 0

                    TopicPartitionDesc(
                        partition = it.partition(),
                        messageCount = endOffset - startOffset,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        leader = it.leader().id(),
                        replicas = it.replicas().orEmpty().map { it.id() },
                        isr = it.isr().orEmpty().map { it.id() }
                    )
                }
                Tuples.of(topicName, topicPartitionDesc)
            }
        }

    fun listTopicConfiguration(properties: Map<String, String>, topic: String): List<ConfigEntry> =
        AdminClient.create(properties).use { client ->
            val configResource = ConfigResource(Type.TOPIC, topic)
            val describeConfigsResult = client.describeConfigs(listOf(configResource), DescribeConfigsOptions().includeDocumentation(true))
            describeConfigsResult.all().get()[configResource]?.entries()?.toList().orEmpty()
        }

    fun listTopicACL(properties: Map<String, String>, topic: String): List<AccessControlEntry> =
        AdminClient.create(properties).use { client ->
            val filter = AclBindingFilter(
                ResourcePatternFilter(ResourceType.TOPIC, topic, PatternType.LITERAL),
                AccessControlEntryFilter(null, null, AclOperation.ANY, AclPermissionType.ANY)
            )
            client.describeAcls(filter).values().get().map { it.entry() }
        }

    fun listGroupsInfo(properties: Map<String, String>, favoriteGroups: Set<String>, groupFilter: String, limit: Int): List<GroupDesc> =
        AdminClient.create(properties).use { client ->
            val filter: (GroupListing) -> Boolean = if (favoriteGroups.isEmpty() && groupFilter.isEmpty())
                { _ -> true }
            else
                { groupListing ->
                    (favoriteGroups.isEmpty() || groupListing.groupId() in favoriteGroups)
                            && (groupFilter.isEmpty() || groupListing.groupId().contains(groupFilter, true))
                }

            val groups = client.listGroups().all().get().asSequence()
                .filter(filter)
                .let { if (limit > 0) it.take(limit) else it }
                .toList()

            val offsetSpec = ListConsumerGroupOffsetsSpec()
            val groupSpec = groups.associate { it.groupId() to offsetSpec }

            client.listConsumerGroupOffsets(groupSpec).all().get()
                .map { (groupId, consumerGroupOffsets) ->
                    val groupListing = groups.first { it.groupId() == groupId }
                    GroupDesc(
                        name = groupId,
                        state = groupListing.groupState().getOrDefault(GroupState.UNKNOWN),
                        type = groupListing.type().getOrDefault(GroupType.UNKNOWN),
                        topics = consumerGroupOffsets.entries.map { Tuples.of(it.key, it.value) }
                    )
                }
        }

    fun produceMessage(properties: Map<String, String>, topic: String, partition: Int?, keyData: Any?, valueData: Any?, headers: String): RecordMetadata =
        KafkaProducer<Any, Any>(properties).use { producer ->
            val headers = Properties()
                .also { it.load(StringReader(headers)) }
                .filter { (key) -> key.toString().isNotEmpty() }
                .map { (key, value) -> RecordHeader(key.toString(), Utils.utf8(value.toString())) }
                .takeIf { it.isNotEmpty() }

            producer.send(ProducerRecord(topic, partition, keyData, valueData, headers)).get()
        }

    fun createTopic(properties: Map<String, String>, topic: String, partitionsNum: Int?, replicationFactor: Short?): Config =
        AdminClient.create(properties).use { client ->
            val newTopic = NewTopic(topic, Optional.ofNullable(partitionsNum), Optional.ofNullable(replicationFactor))
            return client.createTopics(listOf(newTopic)).config(topic).get()
        }

    fun deleteTopic(properties: Map<String, String>, topic: String) {
        AdminClient.create(properties).use { client ->
            client.deleteTopics(listOf(topic)).all().get()
        }
    }

    fun clearTopic(properties: Map<String, String>, topic: String) {
        AdminClient.create(properties).use { client ->
            val latestSpec = OffsetSpec.latest()

            val topicPartitionOffsets = client.describeTopics(listOf(topic)).allTopicNames().get()
                .flatMap { (topicName, topicDescription) ->
                    topicDescription.partitions().map { TopicPartition(topicName, it.partition()) }
                }
                .associateWith { latestSpec }

            val topicRecordsToDelete = client.listOffsets(topicPartitionOffsets).all().get()
                .mapValues { (_, topicPartitionOffsets) -> RecordsToDelete.beforeOffset(topicPartitionOffsets.offset()) }

            client.deleteRecords(topicRecordsToDelete).all().get()
        }
    }

    fun resetOffsetGroup(properties: Map<String, String>, group: String, topic: String, offsetStrategy: OffsetStrategy) {
        val offsetSpec = when (offsetStrategy) {
            OffsetStrategy.Latests -> OffsetSpec.latest()
            OffsetStrategy.Earliest -> OffsetSpec.earliest()
            OffsetStrategy.LastHour -> OffsetSpec.forTimestamp((Instant.now() - Duration.ofHours(1)).toEpochMilli())
            OffsetStrategy.Today -> OffsetSpec.forTimestamp(Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli())
            OffsetStrategy.Yesterday -> OffsetSpec.forTimestamp((Instant.now() - Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS).toEpochMilli())
        }

        AdminClient.create(properties).use { client ->
            val topicPartitionOffsets = client.describeTopics(listOf(topic)).allTopicNames().get()
                .flatMap { (topicName, topicDescription) ->
                    topicDescription.partitions().map { TopicPartition(topicName, it.partition()) }
                }
                .associateWith { offsetSpec }

            val offsets = client.listOffsets(topicPartitionOffsets).all().get()
                .mapValues { (_, topicPartitionOffsets) -> OffsetAndMetadata(topicPartitionOffsets.offset()) }

            client.alterConsumerGroupOffsets(group, offsets).all().get()
        }
    }

    fun deleteConsumerGroup(properties: Map<String, String>, group: String) {
        AdminClient.create(properties).use { client ->
            client.deleteConsumerGroups(listOf(group)).all().get()
        }
    }

}