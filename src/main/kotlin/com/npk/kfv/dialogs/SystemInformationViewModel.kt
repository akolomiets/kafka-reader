package com.npk.kfv.dialogs

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.broker.HasTableColumnWidth
import com.npk.swing.ViewModel
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.*
import javax.swing.SwingWorker
import javax.swing.table.AbstractTableModel

class SystemInformationViewModel : ViewModel() {

    companion object {

        val HOST_NAME: String by lazy(LazyThreadSafetyMode.NONE) {
            runCatching {
                InetAddress.getLocalHost().let { host -> "${host.canonicalHostName} (${host.hostAddress})" }
            }.getOrDefault("#Undefined")
        }

        val USER_NAME: String = System.getProperty("user.name")

        val OS_INFO: String by lazy(LazyThreadSafetyMode.NONE) {
            ManagementFactory.getOperatingSystemMXBean().let { osBean -> "${osBean.name} (${osBean.arch}), version ${osBean.version}" }
        }

        val RUNTIME_LOCATION: String = System.getProperty("java.home")

        val CPU_INFO: String by lazy(LazyThreadSafetyMode.NONE) {
            val processor = if ("win" in System.getProperty("os.name").lowercase()) {
                runCatching {
                    Runtime.getRuntime().exec("powershell.exe \"Get-CimInstance -ClassName Win32_Processor | Select-Object Name\"")
                        .inputStream
                        .bufferedReader()
                        .readText()
                }.mapCatching { result ->
                    result.lineSequence()
                        .filterNot { line -> line.isEmpty() }
                        .filterNot { line -> line.startsWith("Name", true) }
                        .filterNot { line -> line.startsWith("---") }
                        .joinToString()
                }.getOrElse { System.getenv("PROCESSOR_IDENTIFIER") }
            } else {
                null
            }
            "${processor ?: System.getProperty("os.arch")}, VM Cores ${Runtime.getRuntime().availableProcessors()}"
        }

    }

    class KeyValueTableModel(data: Map<String, Any?>) : AbstractTableModel(), HasTableColumnWidth {

        private val tableData = data.entries.toList()

        override fun getRowCount(): Int = tableData.size

        override fun getColumnCount(): Int = 2

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
            when (columnIndex) {
                0 -> tableData[rowIndex].key
                1 -> tableData[rowIndex].value
                else -> throw IndexOutOfBoundsException("Column index is out of bounds")
            }

        override fun getColumnName(columnIndex: Int): String =
            when (columnIndex) {
                0 -> ApplicationMessages["systemInfoDialog.key"]
                1 -> ApplicationMessages["systemInfoDialog.value"]
                else -> throw IndexOutOfBoundsException("Column index is out of bounds")
            }

        override fun getColumnWidth(column: Int): Int = 100

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    }

    class SystemMetrics(
        val threadCount: Int,
        val daemonThreadCount: Int,
        val cpuLoad: Int,
        val totalMemory: Long,
        val freeMemory: Long
    )

    val defaultLocale: String = Locale.getDefault().let { locale -> "${locale.toLanguageTag()}, ${locale.getDisplayName(Locale.ROOT)}" }

    val runtimeInfo: String = ManagementFactory.getRuntimeMXBean().let { runtimeBean ->
        "pid: ${runtimeBean.pid}, ${runtimeBean.vmName} ${runtimeBean.vmVersion} (${runtimeBean.vmVendor})"
    }

    var systemMetrics: SystemMetrics by observableProperty(SystemMetrics(-1, -1, -1, -1, -1))

    val systemPropertiesTableModel = KeyValueTableModel(System.getProperties().mapKeys { it.key.toString() })
    val environmentVariablesTableModel = KeyValueTableModel(System.getenv())

    private var monitoringSystemMetricsWorker: SwingWorker<Unit, SystemMetrics>? = null

    fun startMonitoringSystemMetrics() {
        val runtime = Runtime.getRuntime()
        val threadBean = ManagementFactory.getThreadMXBean()
        val osBean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean::class.java)

        monitoringSystemMetricsWorker = object : SwingWorker<Unit, SystemMetrics>() {
            override fun doInBackground() {
                while (!isCancelled) {
                    try {
                        publish(
                            SystemMetrics(
                                threadCount = threadBean.threadCount,
                                daemonThreadCount = threadBean.daemonThreadCount,
                                cpuLoad = (osBean.cpuLoad * 100).toInt(),
                                totalMemory = runtime.totalMemory(),
                                freeMemory = runtime.freeMemory()
                            )
                        )
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            override fun process(chunks: List<SystemMetrics>) {
                chunks.lastOrNull()?.let { systemMetrics = it }
            }
        }.also { it.execute() }
    }

    fun stopMonitoringSystemMetrics() {
        monitoringSystemMetricsWorker?.cancel(true)
        monitoringSystemMetricsWorker = null
    }

}