package com.npk.kfv.broker

import com.npk.kfv.Tuples
import com.npk.kfv.service.*
import com.npk.swing.ViewModel
import org.apache.kafka.clients.admin.Config
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingWorker

class BrokerPanelViewModel(val configBroker: ConfigBroker) : ViewModel() {

    private val logger = Logger.getLogger(this.javaClass.getName())
    private val loggerMarker = configBroker.loggerMarker

    val name: String inline get() = configBroker.name

    val consumerViewModel: ConsumerViewModel = ConsumerViewModel(configBroker)
    val producerViewModel: ProducerViewModel = ProducerViewModel(configBroker)

    val topicsFilterTableViewModel = FilterTablePanelViewModel(TopicsTableModel(this))
    val topicPartitionsTableModel = TopicPartitionsTableModel()
    val topicConfigurationViewModel = TopicConfigurationViewModel(configBroker)
    val topicACLViewModel = TopicACLViewModel(configBroker)

    val groupsFilterTableViewModel = FilterTablePanelViewModel(ConsumerGroupsTableModel(this))
    val groupDetailsTableModel = ConsumerGroupDetailsTableModel()

    var topic: String by observableProperty("")
    var group: String by observableProperty("")

    var selectedTopicDataCard: String by observableProperty("")

    init {
        topicsFilterTableViewModel.favoriteFilter = configBroker.view.filterFavoriteTopics
        groupsFilterTableViewModel.favoriteFilter = configBroker.view.filterFavoriteGroups

        addPropertyChangeListener { event ->
            when (event.propertyName) {
                ::topic.name -> {
                    consumerViewModel.topic = event.newValue.toString()
                    producerViewModel.topic = event.newValue.toString()
                    topicPartitionsTableModel.selectedTopic = event.newValue.toString()
                    when (selectedTopicDataCard) {
                        "TopicConfigPanel" -> topicConfigurationViewModel.loadTopicConfiguration(topic)
                        "TopicACLPanel" -> topicACLViewModel.loadTopicACL(topic)
                    }
                }
                ::group.name -> {
                    groupDetailsTableModel.selectedGroup = event.newValue.toString()
                }
                ::selectedTopicDataCard.name -> {
                    when (event.newValue) {
                        "TopicConfigPanel" -> topicConfigurationViewModel.loadTopicConfiguration(topic)
                        "TopicACLPanel" -> topicACLViewModel.loadTopicACL(topic)
                    }
                }
            }
        }

        consumerViewModel.addPropertyChangeListener { event ->
            when (event.propertyName) {
                ACTION_START_CONSUMING,
                ACTION_STOP_CONSUMING -> firePropertyChange(event.propertyName, event.oldValue, event.newValue)
            }
        }
        topicsFilterTableViewModel.addPropertyChangeListener { event ->
            when (event.propertyName) {
                FilterTablePanelViewModel<*>::favoriteFilter.name -> {
                    configBroker.view.filterFavoriteTopics = event.newValue as Boolean
                    saveConfigBroker(configBroker)
                    firePropertyChange(ACTION_RELOAD_TOPICS_PROPERTY, false, true)
                }
                FilterTablePanelViewModel<*>::searchText.name,
                FilterTablePanelViewModel<*>::searchLimit.name -> firePropertyChange(ACTION_RELOAD_TOPICS_PROPERTY, false, true)
                ACTION_VIEW_DETAILS_PROPERTY -> firePropertyChange(ACTION_VIEW_DETAILS_TOPIC_PROPERTY, event.oldValue, event.newValue)
            }
        }
        groupsFilterTableViewModel.addPropertyChangeListener { event ->
            when (event.propertyName) {
                FilterTablePanelViewModel<*>::favoriteFilter.name -> {
                    configBroker.view.filterFavoriteGroups = event.newValue as Boolean
                    saveConfigBroker(configBroker)
                    firePropertyChange(ACTION_RELOAD_GROUPS_PROPERTY, false, true)
                }
                FilterTablePanelViewModel<*>::searchText.name,
                FilterTablePanelViewModel<*>::searchLimit.name -> firePropertyChange(ACTION_RELOAD_GROUPS_PROPERTY, false, true)
                ACTION_VIEW_DETAILS_PROPERTY -> firePropertyChange(ACTION_VIEW_DETAILS_GROUP_PROPERTY, event.oldValue, event.newValue)
            }
        }
    }

    fun cleanTopicData() {
        topic = ""
        topicsFilterTableViewModel.tableModel.tableData = emptyList()
        topicPartitionsTableModel.tableData = emptyList()
        topicConfigurationViewModel.cleanTopicConfiguration()
        topicACLViewModel.cleanTopicACL()
    }

    fun cleanGroupData() {
        group = ""
        groupsFilterTableViewModel.tableModel.tableData = emptyList()
        groupDetailsTableModel.tableData = emptyList()
    }

    fun addTopicToFavorite(topicName: String) {
        configBroker.view.favoriteTopics += topicName
        saveConfigBroker(configBroker)
        firePropertyChange(ACTION_REPAINT_TOPICS_PROPERTY, false, true)
    }

    fun removeTopicFromFavorite(topicName: String) {
        configBroker.view.favoriteTopics -= topicName
        saveConfigBroker(configBroker)
        firePropertyChange(ACTION_REPAINT_TOPICS_PROPERTY, false, true)
    }

    fun isTopicFavorite(topicName: String): Boolean {
        return topicName in configBroker.view.favoriteTopics
    }

    fun addGroupToFavorite(groupName: String) {
        configBroker.view.favoriteGroups += groupName
        saveConfigBroker(configBroker)
        firePropertyChange(ACTION_REPAINT_GROUPS_PROPERTY, false, true)
    }

    fun removeGroupFromFavorite(groupName: String) {
        configBroker.view.favoriteGroups -= groupName
        saveConfigBroker(configBroker)
        firePropertyChange(ACTION_REPAINT_GROUPS_PROPERTY, false, true)
    }

    fun isGroupFavorite(groupName: String): Boolean {
        return groupName in configBroker.view.favoriteGroups
    }

    fun listTopicsInfo(callback: (Result<List<Tuples.Tuple2<String, List<TopicPartitionDesc>>>>) -> Unit) {
        (object : SwingWorker<List<Tuples.Tuple2<String, List<TopicPartitionDesc>>>, Unit>() {
            override fun doInBackground(): List<Tuples.Tuple2<String, List<TopicPartitionDesc>>> {
                val favoriteTopics = if (configBroker.view.filterFavoriteTopics) configBroker.view.favoriteTopics else emptySet()
                val topicFilter = topicsFilterTableViewModel.searchText
                val limit = topicsFilterTableViewModel.searchLimit.toIntOrNull() ?: 0

                logger.log(Level.INFO, "[$loggerMarker] List topics info")
                return KafkaService.listTopicsInfo(configBroker.connectionProperties, favoriteTopics, topicFilter, limit)
            }
            override fun done() {
                try {
                    val value = get()
                    logger.log(Level.FINE, "[$loggerMarker] Topics info loaded, size: ${value.size}")
                    callback(Result.success(value))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while listing topics", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun listConsumerGroupsInfo(callback: (Result<List<GroupDesc>>) -> Unit) {
        (object : SwingWorker<List<GroupDesc>, Unit>() {
            override fun doInBackground(): List<GroupDesc> {
                val favoriteGroups = if (configBroker.view.filterFavoriteGroups) configBroker.view.favoriteGroups else emptySet()
                val groupFilter = groupsFilterTableViewModel.searchText
                val limit = groupsFilterTableViewModel.searchLimit.toIntOrNull() ?: 0

                logger.log(Level.INFO, "[$loggerMarker] List consumer groups info")
                return KafkaService.listGroupsInfo(configBroker.connectionProperties, favoriteGroups, groupFilter, limit)
            }
            override fun done() {
                try {
                    val value = get()
                    logger.log(Level.FINE, "[$loggerMarker] Consumer groups info loaded, size: ${value.size}")
                    callback(Result.success(value))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while listing consumer groups", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun createTopic(newTopic: String, partitionsNum: Int?, replicationFactor: Short?, callback: (Result<Config>) -> Unit) {
        (object : SwingWorker<Config, Unit>() {
            override fun doInBackground(): Config {
                logger.log(Level.INFO, "[$loggerMarker] Create new topic: $newTopic, partitionsNum: $partitionsNum, replicationFactor: $replicationFactor")
                return KafkaService.createTopic(configBroker.connectionProperties, newTopic, partitionsNum, replicationFactor)
            }
            override fun done() {
                try {
                    val value = get()
                    logger.log(Level.FINE, "[$loggerMarker] Topic $newTopic created")
                    callback(Result.success(value))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while deleting topic $newTopic", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun deleteTopic(topicName: String, callback: (Result<Unit>) -> Unit) {
        (object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                logger.log(Level.INFO, "[$loggerMarker] Delete topic $topicName")
                KafkaService.deleteTopic(configBroker.connectionProperties, topicName)
            }
            override fun done() {
                try {
                    get()
                    logger.log(Level.FINE, "[$loggerMarker] Topic $topicName deleted")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while deleting topic $topicName", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun clearTopic(topicName: String, callback: (Result<Unit>) -> Unit) {
        (object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                logger.log(Level.INFO, "[$loggerMarker] Clear topic $topicName")
                KafkaService.clearTopic(configBroker.connectionProperties, topicName)
            }
            override fun done() {
                try {
                    get()
                    logger.log(Level.FINE, "[$loggerMarker] Topic $topicName cleared")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while clearing topic $topicName", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun resetOffsetGroup(consumerGroup: String, topicName: String, offsetStrategy: OffsetStrategy, callback: (Result<Unit>) -> Unit) {
        (object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                logger.log(Level.INFO, "[$loggerMarker] Reset offset for group: $consumerGroup, topic: $topicName, offset: $offsetStrategy")
                KafkaService.resetOffsetGroup(configBroker.connectionProperties, consumerGroup, topicName, offsetStrategy)
            }
            override fun done() {
                try {
                    get()
                    logger.log(Level.FINE, "[$loggerMarker] The offset for group: $consumerGroup, topic: $topicName completed")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while resetting offset for group: $consumerGroup, topic: $topicName", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    fun deleteConsumerGroup(consumerGroup: String, callback: (Result<Unit>) -> Unit) {
        (object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                logger.log(Level.INFO, "[$loggerMarker] Delete consumer group $consumerGroup")
                KafkaService.deleteConsumerGroup(configBroker.connectionProperties, consumerGroup)
            }
            override fun done() {
                try {
                    get()
                    logger.log(Level.FINE, "[$loggerMarker] Consumer group $consumerGroup deleted")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while deleting consumer group $consumerGroup", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

    private fun saveConfigBroker(configBroker: ConfigBroker) {
        (object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                try {
                    ConfigService.saveConfigBroker(configBroker)
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while saving config broker", e)
                }
            }
        }).execute()
    }

}
