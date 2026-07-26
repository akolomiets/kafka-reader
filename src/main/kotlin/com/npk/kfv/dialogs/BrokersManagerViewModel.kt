package com.npk.kfv.dialogs

import com.npk.kfv.FlexPropertiesSupport.loadConnectionPropertiesFromFile
import com.npk.kfv.FlexPropertiesSupport.parseStringToMap
import com.npk.kfv.service.ConfigBroker
import com.npk.kfv.service.ConfigBrokerView
import com.npk.kfv.service.ConfigService
import com.npk.kfv.service.KafkaService
import com.npk.swing.ViewModel
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingWorker
import kotlin.io.path.name

class BrokersManagerViewModel(configBrokers: List<ConfigBroker>) : ViewModel() {

    enum class ConnectionStatus {
        Undefined,
        Process,
        Connected,
        Failure
    }

    enum class SecurityProtocol {
        PLAIN,
        SSL,
        SASL_PLAINTEXT,
        SASL_SSL
    }

    companion object {
        private const val DEFAULT_SECURITY_PROTOCOL_CONFIG = "PLAINTEXT"
    }

    private class BrokerModel(
        val id: String = System.nanoTime().toString(),
        var name: String,
        var view: ConfigBrokerView = ConfigBrokerView(
            type = ConfigBrokerView.ViewType.CUSTOM,
            custom = ConfigBrokerView.CustomView(authentication = ConfigBrokerView.AuthenticationType.NONE)
        ),
        var properties: Map<String, String> = emptyMap(),
        var status: ConnectionStatus = ConnectionStatus.Undefined,
        var exception: Exception? = null
    )

    private val logger = Logger.getLogger(this.javaClass.getName())

    private val brokers = configBrokers
        .mapTo(mutableListOf()) { broker -> BrokerModel(broker.id, broker.name, broker.view, broker.properties) }

    private var kafkaProperties: MutableMap<String, String> = mutableMapOf()

    val brokerNames: List<String> get() = brokers.map { it.name }

    var selectedBrokerIndex: Int = -1
        set(newIndex) {
            val oldIndex = field
            field = newIndex
            setSelectedBrokerIndex(oldIndex, newIndex)
        }

    var brokerName: String by observableProperty("")

    var viewType: String by observableProperty("")
    var viewAuthentication: String by observableProperty("")
    var viewSASLEnableSSL: Boolean by observableProperty(false)
    var viewSSLType: String by observableProperty("")
    var viewSSLValidateHostName: Boolean by observableProperty(false)

    var viewPropertiesSource: String by observableProperty("")
    var viewPropertiesPath: String by observableProperty("")

    var propBootstrapServers: String by observableProperty("")

    var propSASLMechanism: String by observableProperty("")
    var propSASLUsername: String by observableProperty("")
    var propSASLPassword: String by observableProperty("")

    var propTrustStoreLocation: String by observableProperty("")
    var propTrustStorePassword: String by observableProperty("")
    var propKeyStoreLocation: String by observableProperty("")
    var propKeyStorePassword: String by observableProperty("")
    var propKeyPassword: String by observableProperty("")

    var propCertifKey: String by observableProperty("")
    var propCertifLocation: String by observableProperty("")
    var propCertifCALocation: String by observableProperty("")

    var propRawProperties: String by observableProperty("")

    var connectionException: Exception? = null
    var connectionStatus: ConnectionStatus by observableProperty(ConnectionStatus.Undefined)

    init {
        addPropertyChangeListener { event ->
            when (event.propertyName) {
                ::brokerName.name,
                ::propRawProperties.name -> { /* nop */ }
                else -> {
                    toModel(kafkaProperties)
                    updateRawProperties()
                }
            }
        }
    }

    fun addNewConnection(): Pair<Int, String> {
        val name = "Kafka (${brokers.size + 1})"
        brokers += BrokerModel(
            name = name,
            properties = mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to "127.0.0.1:9092"
            )
        )
        return (brokers.size - 1) to name
    }

    fun removeConnection(index: Int) {
        if (index >= 0 && index < brokers.size) {
            brokers.removeAt(index)
        }
    }

    fun duplicateConnection(): Pair<Int, String> {
        val broker = if (selectedBrokerIndex >= 0) {
            brokers[selectedBrokerIndex].let {
                toModel(it)
                val newName = it.name.replace(Regex("\\s+\\(Clone\\s+\\d+\\)$", RegexOption.IGNORE_CASE), "")
                BrokerModel(
                    name = "$newName (Clone ${brokers.size + 1})",
                    view = it.view,
                    properties = it.properties
                )
            }
        } else {
            BrokerModel(name = "Kafka (Clone ${brokers.size + 1})")
        }

        brokers += broker
        return (brokers.size - 1) to broker.name
    }

    fun importFromFile(propertiesFile: Path): Pair<Int, String> {
        val name = propertiesFile.name.substringBeforeLast(".")
        val properties = loadConnectionPropertiesFromFile(propertiesFile)

        brokers += BrokerModel(
            name = name,
            view = ConfigBrokerView(
                type = ConfigBrokerView.ViewType.PROPERTIES,
                properties = ConfigBrokerView.PropertiesView(source = ConfigBrokerView.SourceType.IMPLICIT)
            ),
            properties = properties
        )
        return (brokers.size - 1) to name
    }

    fun updateKafkaPropertiesFromRaw(text: String) {
        propRawProperties = text
        kafkaProperties = parseStringToMap(propRawProperties).toMutableMap()
        toView(kafkaProperties)
    }

    fun testConnection(callback: (Result<Unit>) -> Unit) {
        logger.log(Level.FINE, "Check connection. Broker index: $selectedBrokerIndex")
        if (selectedBrokerIndex >= 0) {
            connectionException = null
            connectionStatus = ConnectionStatus.Process

            val broker = brokers[selectedBrokerIndex]
            toModel(broker)

            (object : SwingWorker<String, Unit>() {
                override fun doInBackground(): String {
                    logger.log(Level.FINE, "Checking connection for broker id: ${broker.id}, name: ${broker.name}")
                    val connectionProperties = if (broker.view.type == ConfigBrokerView.ViewType.PROPERTIES && broker.view.properties?.source == ConfigBrokerView.SourceType.FILE) {
                        loadConnectionPropertiesFromFile(requireNotNull(broker.view.properties?.path) { "Properties File is required" })
                    } else {
                        broker.properties
                    }
                    return KafkaService.checkConnection(connectionProperties)
                }
                override fun done() {
                    try {
                        val clusterInfo = get()
                        logger.log(Level.FINE, "Connection success (clusterInfo: $clusterInfo)")
                        connectionStatus = ConnectionStatus.Connected
                        callback(Result.success(Unit))
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Connection failure", e)
                        connectionException = e
                        connectionStatus = ConnectionStatus.Failure
                        callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                    }
                }
            }).execute()
        }
    }

    fun saveBrokers(callback: (Result<List<ConfigBroker>>) -> Unit) {
        if (selectedBrokerIndex >= 0) {
            toModel(brokers[selectedBrokerIndex])
        }
        val configBrokers = brokers.map { broker ->
            ConfigBroker(
                id = broker.id,
                name = broker.name,
                view = broker.view,
                broker.properties
            )
        }
        callback(
            runCatching {
                ConfigService.saveConfigBrokers(configBrokers)
                configBrokers
            }
        )
    }

    private fun setSelectedBrokerIndex(oldIndex: Int, newIndex: Int) {
        if (oldIndex >= 0 && oldIndex < brokers.size) {
            val broker = brokers[oldIndex]
            toModel(broker)
        }
        if (newIndex >= 0 && newIndex < brokers.size) {
            val broker = brokers[newIndex]
            toView(broker)
        }
    }

    private fun toModel(broker: BrokerModel) {
        broker.name = brokerName
        broker.view = ConfigBrokerView(
            type = ConfigBrokerView.ViewType.valueOf(viewType),
            custom = if (viewType == ConfigBrokerView.ViewType.CUSTOM.name) {
                ConfigBrokerView.CustomView(
                    authentication = ConfigBrokerView.AuthenticationType.valueOf(viewAuthentication),
                    ssl = viewSSLType.takeIf { viewAuthentication != ConfigBrokerView.AuthenticationType.NONE.name }?.let { ConfigBrokerView.SSLType.valueOf(it) },
                    saslEnableSSL = viewSASLEnableSSL,
                    sslValidateHostName = viewSSLValidateHostName
                )
            } else {
                null
            },
            properties = if (viewType == ConfigBrokerView.ViewType.PROPERTIES.name) {
                ConfigBrokerView.PropertiesView(
                    source = ConfigBrokerView.SourceType.valueOf(viewPropertiesSource),
                    path = viewPropertiesPath.takeIf { it.isNotEmpty() && viewPropertiesSource == ConfigBrokerView.SourceType.FILE.name }?.let { Path.of(it) }
                )
            } else {
                null
            }
        )
        broker.properties = if (broker.view.type == ConfigBrokerView.ViewType.PROPERTIES && broker.view.properties?.source == ConfigBrokerView.SourceType.FILE) {
            emptyMap()
        } else {
            toModel(kafkaProperties)
            kafkaProperties.toMap()
        }
        broker.status = connectionStatus
        broker.exception = connectionException
    }

    private fun toModel(properties: MutableMap<String, String>) = with(properties) {
        if (viewType == ConfigBrokerView.ViewType.CUSTOM.name) {
            remove(AdminClientConfig.SECURITY_PROTOCOL_CONFIG)
            remove(SaslConfigs.SASL_MECHANISM)
            remove(SaslConfigs.SASL_JAAS_CONFIG)
            remove(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)
            remove(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG)
            remove(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)
            remove(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG)
            remove(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)
            remove(SslConfigs.SSL_KEY_PASSWORD_CONFIG)
            remove("ssl.key.location")
            remove("ssl.certificate.location")
            remove("ssl.ca.location")

            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, propBootstrapServers)
            if (viewAuthentication == ConfigBrokerView.AuthenticationType.NONE.name) {
                put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, DEFAULT_SECURITY_PROTOCOL_CONFIG)
            }
            if (viewAuthentication == ConfigBrokerView.AuthenticationType.SASL.name) {
                put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, if (viewSASLEnableSSL) SecurityProtocol.SASL_SSL.name else SecurityProtocol.SASL_PLAINTEXT.name)
                put(SaslConfigs.SASL_MECHANISM, propSASLMechanism)
                put(SaslConfigs.SASL_JAAS_CONFIG, if (propSASLMechanism == SecurityProtocol.PLAIN.name) {
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username='$propSASLUsername' password='$propSASLPassword';"
                } else {
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username='$propSASLUsername' password='$propSASLPassword';"
                })
            }
            if (viewAuthentication == ConfigBrokerView.AuthenticationType.SSL.name || viewSASLEnableSSL) {
                if (viewAuthentication == ConfigBrokerView.AuthenticationType.SSL.name) {
                    put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
                }
                if (viewSSLValidateHostName) {
                    remove(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG)
                } else {
                    put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
                }
                when (viewSSLType) {
                    ConfigBrokerView.SSLType.TRUSTSTORE.name -> {
                        putOrRemove(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, propTrustStoreLocation)
                        putOrRemove(
                            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                            when {
                                propTrustStoreLocation.endsWith(".jks", true) -> "JKS"
                                propTrustStoreLocation.endsWith(".p12", true) -> "PKCS12"
                                propTrustStoreLocation.endsWith(".pfx", true) -> "PKCS12"
                                propTrustStoreLocation.endsWith(".pem", true) -> "PEM"
                                else -> null
                            }.takeIf { it != SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE }.orEmpty()
                        )
                        putOrRemove(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, propTrustStorePassword)
                        putOrRemove(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, propKeyStoreLocation)
                        putOrRemove(
                            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,
                            when {
                                propKeyStoreLocation.endsWith(".jks", true) -> "JKS"
                                propKeyStoreLocation.endsWith(".p12", true) -> "PKCS12"
                                propKeyStoreLocation.endsWith(".pfx", true) -> "PKCS12"
                                propKeyStoreLocation.endsWith(".pem", true) -> "PEM"
                                else -> ""
                            }.takeIf { it != SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE }.orEmpty()
                        )
                        putOrRemove(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, propKeyStorePassword)
                        putOrRemove(SslConfigs.SSL_KEY_PASSWORD_CONFIG, propKeyPassword)
                    }
                    ConfigBrokerView.SSLType.CERTIFICATE.name -> {
                        putOrRemove("ssl.key.location", propCertifKey)
                        putOrRemove("ssl.certificate.location", propCertifLocation)
                        putOrRemove("ssl.ca.location", propCertifCALocation)
                    }
                }
            } else {
                remove(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG)
                remove(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG)
            }
        }
    }

    private fun toView(broker: BrokerModel) {
        brokerName = broker.name

        viewType = broker.view.type.name
        viewAuthentication = broker.view.custom?.authentication?.name ?: ConfigBrokerView.AuthenticationType.NONE.name
        viewSASLEnableSSL = broker.view.custom?.saslEnableSSL == true
        viewSSLType = broker.view.custom?.ssl?.name ?: ConfigBrokerView.SSLType.TRUSTSTORE.name
        viewSSLValidateHostName = broker.view.custom?.sslValidateHostName == true
        viewPropertiesSource = broker.view.properties?.source?.name ?: ConfigBrokerView.SourceType.IMPLICIT.name
        viewPropertiesPath = broker.view.properties?.path?.toString() ?: ""

        toView(broker.properties)
        kafkaProperties = broker.properties.toMutableMap()
        connectionStatus = broker.status
        connectionException = broker.exception
        updateRawProperties()
    }

    private fun toView(properties: Map<String, String>) {
        propBootstrapServers = properties.getOrDefault(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "")
        propSASLMechanism = properties.getOrDefault(SaslConfigs.SASL_MECHANISM, SecurityProtocol.PLAIN.name)

        propBootstrapServers = properties.getOrDefault(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "")

        propSASLMechanism = properties.getOrDefault(SaslConfigs.SASL_MECHANISM, SecurityProtocol.PLAIN.name)
        val (username, password) = "username\\s*?=\\s*?'(?<username>.*?)'\\s*?password\\s*?=\\s*?'(?<password>.*?)'"
            .toRegex()
            .find(properties.getOrDefault(SaslConfigs.SASL_JAAS_CONFIG, ""))
            ?.groups
            ?.let { groups -> groups["username"]?.value.orEmpty() to groups["password"]?.value.orEmpty() }
            ?: ("" to "")
        propSASLUsername = username
        propSASLPassword = password

        propTrustStoreLocation = properties.getOrDefault(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "")
        propTrustStorePassword = properties.getOrDefault(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "")
        propKeyStoreLocation = properties.getOrDefault(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, "")
        propKeyStorePassword = properties.getOrDefault(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "")
        propKeyPassword = properties.getOrDefault(SslConfigs.SSL_KEY_PASSWORD_CONFIG, "")

        propCertifKey = properties.getOrDefault("ssl.key.location", "")
        propCertifLocation = properties.getOrDefault("ssl.certificate.location", "")
        propCertifCALocation = properties.getOrDefault("ssl.ca.location", "")
    }

    private fun updateRawProperties() {
        propRawProperties = kafkaProperties.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }

    private fun MutableMap<String, String>.putOrRemove(key: String, value: String) =
        if (value.isNotEmpty()) put(key, value) else remove(key)

}