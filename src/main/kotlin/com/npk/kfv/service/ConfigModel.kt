package com.npk.kfv.service

import com.npk.kfv.FlexPropertiesSupport.loadConnectionPropertiesFromFile
import java.nio.file.Path

data class ConfigBroker(
    val id: String,
    var name: String = "",
    val view: ConfigBrokerView = ConfigBrokerView(type = ConfigBrokerView.ViewType.CUSTOM, custom = ConfigBrokerView.CustomView()),
    var properties: Map<String, String> = emptyMap()
)

val ConfigBroker.loggerMarker: String
    get() = "$id:$name"

val ConfigBroker.connectionProperties: Map<String, String>
    get() = if (view.type == ConfigBrokerView.ViewType.PROPERTIES && view.properties?.source == ConfigBrokerView.SourceType.FILE)
        loadConnectionPropertiesFromFile(
            requireNotNull(view.properties?.path) {
                "The 'view.properties.path' property is required when 'view.properties.source' == FILE"
            }
        )
    else
        properties


data class ConfigBrokerView(
    var type: ViewType,
    var custom: CustomView? = null,
    var properties: PropertiesView? = null,
    var filterFavoriteTopics: Boolean = false,
    var favoriteTopics: Set<String> = emptySet(),
    var filterFavoriteGroups: Boolean = false,
    var favoriteGroups: Set<String> = emptySet()
) {

    data class CustomView(
        var authentication: AuthenticationType = AuthenticationType.NONE,
        var ssl: SSLType? = null,
        var saslEnableSSL: Boolean = false,
        var sslValidateHostName: Boolean = false
    )

    data class PropertiesView(
        var source: SourceType = SourceType.IMPLICIT,
        var path: Path? = null
    )

    enum class ViewType {
        CUSTOM,
        PROPERTIES
    }

    enum class AuthenticationType {
        NONE,
        SASL,
        SSL
    }

    enum class SSLType {
        TRUSTSTORE,
        CERTIFICATE
    }

    enum class SourceType {
        IMPLICIT,
        FILE
    }

}
