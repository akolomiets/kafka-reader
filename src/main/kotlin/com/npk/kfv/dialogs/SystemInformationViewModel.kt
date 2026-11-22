package com.npk.kfv.dialogs

import com.npk.kfv.ApplicationMessages
import com.npk.kfv.broker.HasTableColumnWidth
import com.npk.swing.ViewModel
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.security.MessageDigest
import java.util.*
import java.util.jar.JarFile
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

        val CPU_INFO: String = "${System.getenv("PROCESSOR_IDENTIFIER") ?: System.getProperty("os.arch")}, VM Cores ${Runtime.getRuntime().availableProcessors()}"

    }

    class SystemMetrics(
        val threadCount: Int,
        val daemonThreadCount: Int,
        val cpuLoad: Int,
        val totalMemory: Long,
        val freeMemory: Long
    )

    class JarInfo(
        val fileName: String,
        val fileSize: Long,
        val implTitle: String?,
        val implVersion: String?,
        val implVendor: String?,
        val hash: String?
    )

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

    class JarInfoTableModel(private val tableData: List<JarInfo>) : AbstractTableModel(), HasTableColumnWidth {

        companion object {
            private val COLUMN_NAMES = arrayOf(
                ApplicationMessages["systemInfoDialog.jar[1]"],
                ApplicationMessages["systemInfoDialog.jar[2]"],
                ApplicationMessages["systemInfoDialog.jar[3]"],
                ApplicationMessages["systemInfoDialog.jar[4]"],
                ApplicationMessages["systemInfoDialog.jar[5]"],
                ApplicationMessages["systemInfoDialog.jar[6]"]
            )
            private val COLUMN_TYPES = arrayOf(String::class.java, Long::class.javaObjectType, String::class.java, String::class.java, String::class.java, String::class.java)
        }

        override fun getRowCount(): Int = tableData.size

        override fun getColumnCount(): Int = COLUMN_NAMES.size

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
            when (columnIndex) {
                0 -> tableData[rowIndex].fileName
                1 -> tableData[rowIndex].fileSize
                2 -> tableData[rowIndex].implTitle
                3 -> tableData[rowIndex].implVersion
                4 -> tableData[rowIndex].implVendor
                5 -> tableData[rowIndex].hash
                else -> throw IndexOutOfBoundsException("Column index is out of bounds")
            }

        override fun getColumnName(columnIndex: Int): String = COLUMN_NAMES[columnIndex]

        override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_TYPES[columnIndex]

        override fun getColumnWidth(column: Int): Int = 100

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    }

    private val runtime = Runtime.getRuntime()
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val osBean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean::class.java)

    val defaultLocale: String = Locale.getDefault().let { locale -> "${locale.toLanguageTag()}, ${locale.getDisplayName(Locale.ROOT)}" }

    val runtimeInfo: String = ManagementFactory.getRuntimeMXBean().let { runtimeBean ->
        "pid: ${runtimeBean.pid}, ${runtimeBean.vmName} ${runtimeBean.vmVersion} (${runtimeBean.vmVendor})"
    }

    var systemMetrics: SystemMetrics by observableProperty(SystemMetrics(-1, -1, -1, -1, -1))

    val systemPropertiesTableModel = KeyValueTableModel(System.getProperties().mapKeys { it.key.toString() })
    val environmentVariablesTableModel = KeyValueTableModel(System.getenv())
    val jarInfoTableModel = JarInfoTableModel(getClassPathJarInfo(System.getProperty("java.class.path").split(File.pathSeparator)))

    private var monitoringSystemMetricsWorker: SwingWorker<Unit, SystemMetrics>? = null

    fun startMonitoringSystemMetrics() {
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

    fun cleanupMemory() {
        runtime.gc()
    }

    private fun getClassPathJarInfo(items: List<String>): List<JarInfo> =
        buildList {
            items.asSequence()
                .map { File(it) }
                .filter { it.isFile && it.name.endsWith(".jar", true) }
                .forEach { file ->
                    val jarAttr = JarFile(file).manifest?.mainAttributes
                    val jarInfo = JarInfo(
                        fileName = file.name,
                        fileSize = file.length(),
                        implTitle = jarAttr?.getValue("Implementation-Title"),
                        implVersion = jarAttr?.getValue("Implementation-Version"),
                        implVendor = jarAttr?.getValue("Implementation-Vendor"),
                        hash = calculateHash(file)
                    )

                    add(jarInfo)

                    val classPathItems = jarAttr?.getValue("Class-Path")?.split(" ")
                    if (!classPathItems.isNullOrEmpty()) {
                        val parent = file.parent
                        addAll(getClassPathJarInfo(classPathItems.map { "$parent${File.separator}$it" }))
                    }
                }
        }

    private fun calculateHash(file: File): String? =
        if (file.exists())
            runCatching {
                val digester = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)

                file.inputStream().buffered().use { input ->
                    var read = input.read(buffer)
                    while (read != -1) {
                        digester.update(buffer, 0, read)
                        read = input.read(buffer)
                    }
                }

                digester.digest().joinToString("") { "%02x".format(it)  }
            }.getOrNull()
        else
            null

}