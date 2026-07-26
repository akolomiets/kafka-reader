package com.npk.kfv.service

import java.lang.ref.SoftReference
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class LocalStorage<K : Any, V : Any>(timeToLive: Duration? = null) {

    private class LocalStorageValue<V>(val value: V, val expireTime: Long) {
        fun isAlive(): Boolean = expireTime < 0 || System.currentTimeMillis() < expireTime
    }

    private val timeToLive = timeToLive?.toMillis() ?: -1L
    private val localStorage = ConcurrentHashMap<K, SoftReference<LocalStorageValue<V>>>()

    operator fun contains(key: K): Boolean = get(key) != null

    operator fun get(key: K): V? =
        localStorage[key]?.let { ref ->
            ref.get()?.let { value ->
                if (!value.isAlive()) {
                    localStorage.remove(key)
                    null
                } else {
                    value.value
                }
            }
        }

    operator fun set(key: K, value: V) {
        localStorage[key] = SoftReference(LocalStorageValue(value, System.currentTimeMillis() + timeToLive))
    }

    fun remove(key: K): V? =
        localStorage.remove(key)?.let { ref ->
            ref.get()?.let { value ->
                value.value.takeIf { value.isAlive() }
            }
        }

    fun clear() {
        localStorage.clear()
    }

}
