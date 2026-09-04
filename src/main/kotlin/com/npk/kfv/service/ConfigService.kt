package com.npk.kfv.service

import com.npk.kfv.APPLICATION_ARTIFACT_ID
import com.npk.kfv.ApplicationPrefs
import com.npk.kfv.FlexPropertiesSupport.HEADER_KAFKA_READER
import com.npk.kfv.FlexPropertiesSupport.parseStringToMap
import com.npk.kfv.FlexPropertiesSupport.printHeader
import com.npk.kfv.FlexPropertiesSupport.printSection
import com.npk.kfv.FlexPropertiesSupport.readBySection
import com.npk.kfv.synchronized
import java.io.PrintWriter
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.*
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.io.path.*

object ConfigService {

    private const val CONF_NAME_KEY =                               "conf:name"
    private const val CONF_VIEW_TYPE_KEY =                          "conf:view.type"
    private const val CONF_VIEW_CUSTOM_AUTHENTICATION_KEY =         "conf:view.custom.authentication"
    private const val CONF_VIEW_CUSTOM_SSL_KEY =                    "conf:view.custom.ssl"
    private const val CONF_VIEW_CUSTOM_SASLENABLESSL_KEY =          "conf:view.custom.saslEnableSSL"
    private const val CONF_VIEW_CUSTOM_SSLVALIDATEHOSTNAME_KEY =    "conf:view.custom.sslValidateHostName"
    private const val CONF_VIEW_PROPERTIES_SOURCE_KEY =             "conf:view.properties.source"
    private const val CONF_VIEW_PROPERTIES_PATH_KEY =               "conf:view.properties.path"
    private const val CONF_VIEW_FILTERFAVORITETOPICS_KEY =          "conf:view.filterFavoriteTopics"
    private const val CONF_VIEW_FAVORITETOPICS_KEY =                "conf:view.favoriteTopics"
    private const val CONF_VIEW_FILTERFAVORITEGROUPS_KEY =          "conf:view.filterFavoriteGroups"
    private const val CONF_VIEW_FAVORITEGROUPS_KEY =                "conf:view.favoriteGroups"
    private const val CONF_VIEW_TOPICSLIMIT_KEY =                   "conf:view.topicsLimit"
    private const val CONF_VIEW_GROUPSLIMIT_KEY =                   "conf:view.groupsLimit"

    private const val CONF_PROPERTIES_KEY =                         "conf:properties"

    private class WrongIdException : RuntimeException()

    private lateinit var logger: Logger

    private val rootConfigPath = Path.of(System.getProperty("user.home"), ".$APPLICATION_ARTIFACT_ID")
    private val loggingConfigPath = rootConfigPath.resolve("app.logging.properties")

    fun init() {
        if (!rootConfigPath.exists() || !rootConfigPath.isDirectory()) {
            rootConfigPath.createDirectory()
        }
        if (loggingConfigPath.notExists()) {
            Files.writeString(loggingConfigPath, defaultLoggingProperties())
        }
        try {
            LogManager.getLogManager().readConfiguration(loggingConfigPath.inputStream())
        } catch (e: Exception) {
            println("[ERROR] Cannot load $loggingConfigPath - ${e.message}")
        }
        logger = Logger.getLogger(this.javaClass.getName())
    }

    fun loadConfigBrokers(): List<ConfigBroker> = synchronized {
        buildList {
            listAllConfigFiles().forEach { (path, id) ->
                if (size > ApplicationPrefs.brokersMaxCount) {
                    logger.log(Level.WARNING, "The number of brokers exceeds the maximum. Take the first ${ApplicationPrefs.brokersMaxCount}")
                    return@forEach
                }
                try {
                    add(loadConfigBroker(path, id))
                } catch (_: WrongIdException) {
                    logger.log(Level.WARNING, "Wrong Id, skip config file '$path'")
                    Files.move(path, path.toAbsolutePath().parent.resolve("skip.$id.txt"), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error loading config file '$path'", e)
                    Files.move(path, path.toAbsolutePath().parent.resolve("fail.$id.txt"), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun loadConfigBroker(configFile: Path, id: String): ConfigBroker {
        logger.log(Level.INFO, "Load broker config from file: $configFile")

        val broker = ConfigBroker(
            id,
            configFile.fileName.toString(),
            ConfigBrokerView(
                type = ConfigBrokerView.ViewType.PROPERTIES,
                properties = ConfigBrokerView.PropertiesView(ConfigBrokerView.SourceType.IMPLICIT)
            ),
            emptyMap()
        )

        Files.newBufferedReader(configFile).use { reader ->
            reader.readBySection { section, value ->
                when (section) {
                    HEADER_KAFKA_READER -> {
                        val header = Properties().also { it.load(StringReader(value)) }
                        if (ApplicationPrefs.appKey != header["id"]) {
                            throw WrongIdException()
                        }
                    }
                    CONF_NAME_KEY -> if (value.isNotEmpty()) { broker.name = value }
                    CONF_VIEW_TYPE_KEY -> ConfigBrokerView.ViewType.valueOf(value).let {
                        broker.view.type = it
                        broker.view.custom = null
                        broker.view.properties = null
                        when (it) {
                            ConfigBrokerView.ViewType.CUSTOM -> broker.view.custom = ConfigBrokerView.CustomView()
                            ConfigBrokerView.ViewType.PROPERTIES -> broker.view.properties = ConfigBrokerView.PropertiesView()
                        }
                    }
                    CONF_VIEW_CUSTOM_AUTHENTICATION_KEY -> requireNotNull(broker.view.custom).authentication = ConfigBrokerView.AuthenticationType.valueOf(value)
                    CONF_VIEW_CUSTOM_SSL_KEY -> requireNotNull(broker.view.custom).ssl = ConfigBrokerView.SSLType.valueOf(value)
                    CONF_VIEW_CUSTOM_SASLENABLESSL_KEY -> requireNotNull(broker.view.custom).saslEnableSSL = value.toBoolean()
                    CONF_VIEW_CUSTOM_SSLVALIDATEHOSTNAME_KEY -> requireNotNull(broker.view.custom).sslValidateHostName = value.toBoolean()
                    CONF_VIEW_PROPERTIES_SOURCE_KEY -> requireNotNull(broker.view.properties).source = ConfigBrokerView.SourceType.valueOf(value)
                    CONF_VIEW_PROPERTIES_PATH_KEY -> requireNotNull(broker.view.properties).path = Path.of(value)
                    CONF_VIEW_FILTERFAVORITETOPICS_KEY -> broker.view.filterFavoriteTopics = value.toBoolean()
                    CONF_VIEW_FAVORITETOPICS_KEY -> broker.view.favoriteTopics = buildSet {
                        StringReader(value).forEachLine { add(it.trim()) }
                    }
                    CONF_VIEW_FILTERFAVORITEGROUPS_KEY -> broker.view.filterFavoriteGroups = value.toBoolean()
                    CONF_VIEW_FAVORITEGROUPS_KEY -> broker.view.favoriteGroups = buildSet {
                        StringReader(value).forEachLine { add(it.trim()) }
                    }
                    CONF_PROPERTIES_KEY -> broker.properties =
                        parseStringToMap(value) { _, value -> if (CipherService.shouldDecrypt(value)) CipherService.decrypt(value) else value }
                    CONF_VIEW_TOPICSLIMIT_KEY -> broker.view.topicsLimit = value
                    CONF_VIEW_GROUPSLIMIT_KEY -> broker.view.groupsLimit = value
                }
            }
        }

        return broker.also {
            if (it.view.type == ConfigBrokerView.ViewType.PROPERTIES && it.view.properties?.source == ConfigBrokerView.SourceType.FILE) {
                it.properties = emptyMap()
            }
        }
    }

    fun saveConfigBrokers(brokers: List<ConfigBroker>): Unit = synchronized {
        listAllConfigFiles().forEach { (path, id) ->
            if (brokers.none { it.id == id }) {
                logger.log(Level.INFO, "Drop config file '$path'")
                Files.move(path, path.toAbsolutePath().parent.resolve("drop.$id.txt"), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        brokers.forEach { broker -> saveConfigBroker(broker) }
    }

    fun saveConfigBroker(broker: ConfigBroker): Unit = synchronized {
        val dataFile = rootConfigPath.resolve("conf.${broker.id}.txt")
        Files.newBufferedWriter(dataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { writer ->
            PrintWriter(writer).apply {
                printHeader()

                printSection(CONF_NAME_KEY, broker.name)
                printSection(CONF_VIEW_TYPE_KEY, broker.view.type)

                when (broker.view.type) {
                    ConfigBrokerView.ViewType.CUSTOM -> {
                        printSection(CONF_VIEW_CUSTOM_AUTHENTICATION_KEY, broker.view.custom?.authentication)
                        printSection(CONF_VIEW_CUSTOM_SSL_KEY, broker.view.custom?.ssl)
                        printSection(CONF_VIEW_CUSTOM_SASLENABLESSL_KEY, broker.view.custom?.saslEnableSSL)
                        printSection(CONF_VIEW_CUSTOM_SSLVALIDATEHOSTNAME_KEY, broker.view.custom?.sslValidateHostName)
                    }
                    ConfigBrokerView.ViewType.PROPERTIES -> {
                        printSection(CONF_VIEW_PROPERTIES_SOURCE_KEY, broker.view.properties?.source)
                        printSection(CONF_VIEW_PROPERTIES_PATH_KEY, broker.view.properties?.path)
                    }
                }

                printSection(CONF_VIEW_FILTERFAVORITETOPICS_KEY, broker.view.filterFavoriteTopics)
                if (broker.view.favoriteTopics.isNotEmpty()) {
                    printSection(CONF_VIEW_FAVORITETOPICS_KEY) {
                        broker.view.favoriteTopics.forEach { println(it) }
                    }
                }
                printSection(CONF_VIEW_FILTERFAVORITEGROUPS_KEY, broker.view.filterFavoriteGroups)
                if (broker.view.favoriteGroups.isNotEmpty()) {
                    printSection(CONF_VIEW_FAVORITEGROUPS_KEY) {
                        broker.view.favoriteGroups.forEach { println(it) }
                    }
                }

                if (broker.view.topicsLimit.isNotEmpty()) {
                    printSection(CONF_VIEW_TOPICSLIMIT_KEY, broker.view.topicsLimit)
                }
                if (broker.view.groupsLimit.isNotEmpty()) {
                    printSection(CONF_VIEW_GROUPSLIMIT_KEY, broker.view.groupsLimit)
                }

                if (!(broker.view.type == ConfigBrokerView.ViewType.PROPERTIES && broker.view.properties?.source == ConfigBrokerView.SourceType.FILE) && broker.properties.isNotEmpty()) {
                    printSection(CONF_PROPERTIES_KEY) {
                        val sensitiveProperties = KafkaService.allSensitivePropertyNames
                        broker.properties.forEach { (key, value) ->
                            print(key)
                            print("=")
                            println(if (key in sensitiveProperties) CipherService.encrypt(value) else value)
                        }
                    }
                }
            }
        }
    }


    private fun listAllConfigFiles(): List<Pair<Path, String>> =
        Files.list(rootConfigPath)
            .filter { path -> Files.isRegularFile(path) }
            .filter { path -> path.fileName.toString().let { it.startsWith("conf.", true) && it.endsWith(".txt", true) } }
            .sorted()
            .map { path ->
                path to path.fileName.toString().let { fileName -> fileName.substring(5, fileName.length - 4) }
            }
            .toList()

    private fun defaultLoggingProperties(): String =
        $$"""
            # Default Log Configuration $${Instant.now()}
            com.npk.kfv.level=INFO
            com.npk.kfv.handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler
            
            java.util.logging.SimpleFormatter.format=%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS [%4$-7s] %2$s - %5$s%6$s%n
                        
            # File Logging
            java.util.logging.FileHandler.level=ALL
            java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
            java.util.logging.FileHandler.encoding=UTF-8
            java.util.logging.FileHandler.pattern=%t/$$APPLICATION_ARTIFACT_ID.%g.log
            java.util.logging.FileHandler.append=true
            java.util.logging.FileHandler.limit=1048576
            java.util.logging.FileHandler.count=3
            
            # Console Logging
            java.util.logging.ConsoleHandler.level=INFO
            java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
            java.util.logging.ConsoleHandler.encoding=UTF-8
        """.trimIndent()

}