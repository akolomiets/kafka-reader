package com.npk.kfv.service

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KClass


class ConfigBrokersUpdatedEvent : EventService.Event


class EventService {

    interface Event

    fun interface EventListener<E : Event> {
        operator fun invoke(event: E)
    }

    fun interface ListenerRegistration {
        fun removeListener()
    }

    companion object {

        val Default = EventService()

    }

    private val lock = ReentrantReadWriteLock()
    private val eventListeners: MutableMap<KClass<out Event>, MutableList<EventListener<in Event>>> = mutableMapOf()

    fun <E : Event> fire(event: E) =
        lock.read {
            eventListeners[event::class]?.forEach { listener -> listener(event) }
        }

    fun <E : Event> addEventListener(eventType: KClass<E>, eventListener: EventListener<Event>): ListenerRegistration =
        lock.write {
            val listeners = eventListeners.computeIfAbsent(eventType) { mutableListOf() }
            listeners.add(eventListener)
            ListenerRegistration { removeEventListener(eventType, eventListener) }
        }

    fun <E : Event> removeEventListener(eventType: KClass<E>, eventListener: EventListener<Event>) =
        lock.write {
            eventListeners[eventType]?.remove(eventListener)
        }

}
