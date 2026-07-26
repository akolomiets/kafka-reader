package com.npk.kfv.broker

import com.npk.kfv.ApplicationPrefs
import com.npk.kfv.service.*
import com.npk.swing.ViewModel
import org.apache.kafka.common.acl.AccessControlEntry
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingWorker

class TopicACLViewModel(private val configBroker: ConfigBroker) : ViewModel() {

    private val logger = Logger.getLogger(this.javaClass.getName())
    private val loggerMarker = configBroker.loggerMarker

    private val localStorage = LocalStorage<String, List<AccessControlEntry>>(ApplicationPrefs.localStorageTtl)

    val tableModel = TopicACLTableModel()

    fun loadTopicACL(topicName: String) {
        if (topicName.isNotEmpty()) {
            val value = localStorage[topicName]
            if (value.isNullOrEmpty()) {
                localStorage.remove(topicName)
                tableModel.tableData = emptyList()
                firePropertyChange(ACTION_RELOAD_TOPIC_ACL_PROPERTY, null, topicName)
            } else {
                tableModel.tableData = value
            }
        } else {
            tableModel.tableData = emptyList()
        }
    }

    fun cleanTopicACL() {
        tableModel.tableData = emptyList()
        localStorage.clear()
    }

    fun listTopicACL(topicName: String, callback: (Result<Unit>) -> Unit) {
        localStorage.remove(topicName)
        tableModel.tableData = emptyList()

        (object : SwingWorker<List<AccessControlEntry>, Unit>() {
            override fun doInBackground(): List<AccessControlEntry> {
                logger.log(Level.INFO, "[$loggerMarker] List topic '$topicName' ACL")
                return KafkaService.listTopicACL(configBroker.connectionProperties, topicName)
            }
            override fun done() {
                try {
                    val topicACL = get()
                    localStorage[topicName] = topicACL
                    tableModel.tableData = topicACL
                    logger.log(Level.FINE, "[$loggerMarker] Topic '$topicName' ALC loaded")
                    callback(Result.success(Unit))
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "[$loggerMarker] Error while listing topic '$topicName' ACL", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }).execute()
    }

}