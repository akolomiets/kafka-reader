package com.npk.embedded.kafka

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

object EmbeddedKafkaBrokerObjectPool {

    private const val LOG_MARKER = "[EMBEDDED KAFKA POOL]"

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var broker: EmbeddedKafkaBrokerImpl? = null
    private val usageCount = AtomicInteger(0)

    fun acquireOrCreate(): EmbeddedKafkaBroker =
        usageCount.incrementAndGet().let { count ->
            logger.info("$LOG_MARKER Acquire broker #$count")
            val currentBroker = lock.readLock().withLock { broker }
            if (currentBroker == null) {
                logger.info("$LOG_MARKER Try to create a broker")
                lock.writeLock().withLock {
                    broker ?: EmbeddedKafkaBrokerImpl().also {
                        it.start()
                        broker = it
                    }
                }
            } else {
                currentBroker
            }
        }

    fun release(): Unit =
        usageCount.decrementAndGet().let { count ->
            if (count >= 0) {
                logger.info("$LOG_MARKER Release broker. Left $usageCount usages")
                if (count == 0) {
                    logger.info("$LOG_MARKER Close and clean-up broker.")
                    lock.writeLock().withLock {
                        broker?.let {
                            it.close()
                            broker = null
                        }
                    }
                }
            }
        }

}