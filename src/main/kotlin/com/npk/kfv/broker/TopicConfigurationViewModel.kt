package com.npk.kfv.broker

import com.npk.kfv.ApplicationPrefs
import com.npk.kfv.service.*
import com.npk.swing.ViewModel
import org.apache.kafka.clients.admin.ConfigEntry
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingWorker

class TopicConfigurationViewModel(private val configBroker: ConfigBroker) : ViewModel() {

    private val logger = Logger.getLogger(this.javaClass.getName())
    private val loggerMarker = configBroker.loggerMarker

    private val localStorage = LocalStorage<String, List<ConfigEntry>>(ApplicationPrefs.localStorageTtl)

    val tableModel = TopicConfigurationsTableModel()

    var documentation: String by observableProperty("")

    fun loadTopicConfiguration(topicName: String) {
        if (topicName.isNotEmpty()) {
            val value = localStorage[topicName]
            if (value.isNullOrEmpty()) {
                localStorage.remove(topicName)
                tableModel.tableData = emptyList()
                firePropertyChange(ACTION_RELOAD_TOPIC_CONFIG_PROPERTY, null, topicName)
            } else {
                tableModel.tableData = value
            }
        } else {
            tableModel.tableData = emptyList()
        }
    }

    fun cleanTopicConfiguration() {
        tableModel.tableData = emptyList()
        localStorage.clear()
    }

    fun selectRow(selectedRow: Int) {
        if (selectedRow >= 0) {
            documentation = tableModel.tableData[selectedRow].documentation()
                ?.let { "<html>$it</html>" }
                ?: "No documentation"
        } else {
            documentation = ""
        }
    }

    fun listTopicConfiguration(topicName: String, callback: (Result<Unit>) -> Unit) {
        (object : SwingWorker<List<ConfigEntry>, Unit>() {
            override fun doInBackground(): List<ConfigEntry> {
                logger.log(Level.INFO, "[$loggerMarker] List topic '$topicName' configuration")
                return KafkaService.listTopicConfiguration(configBroker.connectionProperties, topicName)
            }
            override fun done() {
                try {
                    val topicConfiguration = get()
                    localStorage[topicName] = topicConfiguration
                    tableModel.tableData = topicConfiguration
                    logger.log(Level.FINE, "[$loggerMarker] Topic '$topicName' configuration loaded")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while listing topic '$topicName' configuration", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

}