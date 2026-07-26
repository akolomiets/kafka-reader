package com.npk.embedded.kafka

import org.junit.jupiter.api.extension.*

class EmbeddedKafkaExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    override fun beforeAll(context: ExtensionContext) {
        embeddedKafkaBroker = EmbeddedKafkaBrokerObjectPool.acquireOrCreate()
    }

    override fun afterAll(context: ExtensionContext) {
        EmbeddedKafkaBrokerObjectPool.release()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type.isAssignableFrom(EmbeddedKafkaBroker::class.java)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return embeddedKafkaBroker
    }

}