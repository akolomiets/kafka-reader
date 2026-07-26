package com.npk.kfv.broker

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.Tuples
import com.npk.kfv.service.GroupDesc
import com.npk.kfv.service.KafkaConsumerRecord
import com.npk.kfv.service.TopicPartitionDesc
import com.npk.kfv.synchronized
import com.npk.swing.ViewModel
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.acl.AccessControlEntry
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel

class FilterTablePanelViewModel<T : TableModel>(val tableModel: T) : ViewModel() {

    companion object {
        val SEARCH_LIMIT_ITEMS = listOf("10", "50", "100", ApplicationMessages["table.all"])
    }

    var favoriteFilter: Boolean by observableProperty(false)
    var searchText: String by observableProperty("")
    var searchLimit: String by observableProperty(SEARCH_LIMIT_ITEMS.first())

    fun viewDetails(value: Any) {
        firePropertyChange(ACTION_VIEW_DETAILS_PROPERTY, null, value)
    }

}

interface HasTableColumnWidth {

    fun getColumnWidth(column: Int): Int

}

class TopicsTableModel(private val parentModel: BrokerPanelViewModel) : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            "",
            ApplicationMessages["table.topics[1]"],
            ApplicationMessages["table.topics[2]"],
            ApplicationMessages["table.topics[3]"],
            ApplicationMessages["table.topics[4]"],
            ApplicationMessages["table.topics[5]"]
        )
        private val COLUMN_TYPES = arrayOf(Any::class.java, String::class.java, Long::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, String::class.java)
        private val COLUMN_WIDTH = arrayOf(36, 240, 110, 80, 120, 110)
    }

    var tableData: List<Tuples.Tuple2<String, List<TopicPartitionDesc>>> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = tableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
        when (columnIndex) {
            0 -> parentModel.isTopicFavorite(tableData[rowIndex].t1)
            1 -> tableData[rowIndex].t1
            2 -> tableData[rowIndex].t2.sumOf { it.messageCount }
            3 -> tableData[rowIndex].t2.size
            4 -> tableData[rowIndex].t2.maxOfOrNull { it.replicas.size } ?: "0"
            5 -> tableData[rowIndex].t2.sumOf { it.isr.size }
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun setValueAt(value: Any, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0) {
            tableData[rowIndex].t1.let { topicName ->
                if (parentModel.isTopicFavorite(topicName)) {
                    parentModel.removeTopicFromFavorite(topicName)
                } else {
                    parentModel.addTopicToFavorite(topicName)
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }

    override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

    override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_TYPES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class KafkaConsumerRecordsTableModel : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            ApplicationMessages["table.topic.records[1]"],
            ApplicationMessages["table.topic.records[2]"],
            ApplicationMessages["table.topic.records[3]"],
            ApplicationMessages["table.topic.records[4]"],
            ApplicationMessages["table.topic.records[5]"],
            ApplicationMessages["table.topic.records[6]"]
        )
        private val COLUMN_TYPES = arrayOf(String::class.java, Int::class.javaObjectType, Long::class.javaObjectType, String::class.java, String::class.java, String::class.java)
        private val COLUMN_WIDTH = arrayOf(100, 100, 100, 100, 100, 100)
    }

    private val tableData: MutableList<KafkaConsumerRecord> = mutableListOf()

    fun addChunks(chunks: List<KafkaConsumerRecord>) {
        tableData.synchronized {
            val startRowIndex = it.size
            it.addAll(chunks)
            fireTableRowsInserted(startRowIndex, it.size - 1)
        }
    }

    fun clear() {
        tableData.synchronized {
            it.clear()
            fireTableDataChanged()
        }
    }

    fun getRowAt(rowIndex: Int): KafkaConsumerRecord = tableData[rowIndex]

    override fun getRowCount(): Int = tableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        when (columnIndex) {
            0 -> tableData[rowIndex].topic
            1 -> tableData[rowIndex].partition
            2 -> tableData[rowIndex].offset
            3 -> tableData[rowIndex].timestamp
            4 -> tableData[rowIndex].key
            5 -> tableData[rowIndex].value
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

    override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_TYPES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class TopicPartitionsTableModel : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            ApplicationMessages["table.topic.partitions[1]"],
            ApplicationMessages["table.topic.partitions[2]"],
            ApplicationMessages["table.topic.partitions[3]"],
            ApplicationMessages["table.topic.partitions[4]"],
            ApplicationMessages["table.topic.partitions[5]"],
            ApplicationMessages["table.topic.partitions[6]"]
        )
        private val COLUMN_TYPES = arrayOf(Int::class.javaObjectType, Long::class.javaObjectType, Long::class.javaObjectType, Long::class.javaObjectType, Int::class.javaObjectType, String::class.java)
        private val COLUMN_WIDTH = arrayOf(100, 130, 100, 100, 80, 130)
    }

    private var selectedTopicTableData: List<TopicPartitionDesc> = emptyList()

    var tableData: List<Tuples.Tuple2<String, List<TopicPartitionDesc>>> = emptyList()
        set(value) {
            field = value
            selectedTopic = ""
        }

    var selectedTopic: String = ""
        set(value) {
            field = value
            selectedTopicTableData = tableData
                .find { it.t1 == field }
                ?.t2
                .orEmpty()
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = selectedTopicTableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
        when (columnIndex) {
            0 -> selectedTopicTableData[rowIndex].partition
            1 -> selectedTopicTableData[rowIndex].messageCount
            2 -> selectedTopicTableData[rowIndex].startOffset
            3 -> selectedTopicTableData[rowIndex].endOffset
            4 -> selectedTopicTableData[rowIndex].leader
            5 -> selectedTopicTableData[rowIndex].replicas
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

    override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_TYPES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class TopicConfigurationsTableModel : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            ApplicationMessages["table.topic.configurations[1]"],
            ApplicationMessages["table.topic.configurations[2]"],
            ApplicationMessages["table.topic.configurations[3]"]
        )
        private val COLUMN_WIDTH = arrayOf(260, 140, 200)
    }

    var tableData: List<ConfigEntry> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = tableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        when (columnIndex) {
            0 -> tableData[rowIndex].name()
            1 -> tableData[rowIndex].type()
            2 -> tableData[rowIndex].value()
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class TopicACLTableModel : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            ApplicationMessages["table.topic.acl[1]"],
            ApplicationMessages["table.topic.acl[2]"],
            ApplicationMessages["table.topic.acl[3]"],
            ApplicationMessages["table.topic.acl[4]"]
        )
        private val COLUMN_WIDTH = arrayOf(340, 160, 100, 100)
    }

    var tableData: List<AccessControlEntry> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = tableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        when (columnIndex) {
            0 -> tableData[rowIndex].principal()
            1 -> tableData[rowIndex].host()
            2 -> tableData[rowIndex].operation()
            3 -> tableData[rowIndex].permissionType()
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class ConsumerGroupsTableModel(private val parentModel: BrokerPanelViewModel) : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            "",
            ApplicationMessages["table.groups[1]"],
            ApplicationMessages["table.groups[2]"],
            ApplicationMessages["table.groups[3]"]
        )
        private val COLUMN_WIDTH = arrayOf(36, 280, 160, 90)
    }

    var tableData: List<GroupDesc> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount(): Int = tableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
        when (columnIndex) {
            0 -> parentModel.isGroupFavorite(tableData[rowIndex].name)
            1 -> tableData[rowIndex].name
            2 -> tableData[rowIndex].state
            3 -> tableData[rowIndex].type
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun setValueAt(value: Any, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0) {
            tableData[rowIndex].name.let { groupName ->
                if (parentModel.isGroupFavorite(groupName)) {
                    parentModel.removeGroupFromFavorite(groupName)
                } else {
                    parentModel.addGroupToFavorite(groupName)
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }

    override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}

class ConsumerGroupDetailsTableModel : AbstractTableModel(), HasTableColumnWidth {

    companion object {
        private val COLUMN_NAMES = arrayOf(
            ApplicationMessages["table.group.details[1]"],
            ApplicationMessages["table.group.details[2]"],
            ApplicationMessages["table.group.details[3]"]
        )
        private val COLUMN_TYPES = arrayOf(String::class.java, Int::class.javaObjectType, Long::class.javaObjectType)
        private val COLUMN_WIDTH = arrayOf(320, 80, 140)
    }

    private var selectedGroupTableData: List<Tuples.Tuple2<TopicPartition, OffsetAndMetadata>> = emptyList()

    var tableData: List<GroupDesc> = emptyList()
        set(value) {
            field = value
            selectedGroup = ""
        }

    var selectedGroup: String = ""
        set(value) {
            field = value
            selectedGroupTableData = tableData
                .find { it.name == field }
                ?.topics
                .orEmpty()
            fireTableDataChanged()
        }

    val topicNames: Set<String>
        get() = selectedGroupTableData.mapTo(mutableSetOf()) { (topic) -> topic.topic() }

    override fun getRowCount(): Int = selectedGroupTableData.size

    override fun getColumnCount(): Int = COLUMN_NAMES.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        when (columnIndex) {
            0 -> selectedGroupTableData[rowIndex].t1.topic()
            1 -> selectedGroupTableData[rowIndex].t1.partition()
            2 -> selectedGroupTableData[rowIndex].t2.offset()
            else -> throw IndexOutOfBoundsException("Column index is out of bounds")
        }

    override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_TYPES[columnIndex]

    override fun getColumnWidth(column: Int): Int = COLUMN_WIDTH[column]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

}
