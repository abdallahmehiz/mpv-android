package `is`.xyz.mpv

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.reflect.KClass

/**
 * Platform independent state of an [MPV] instance: property flows, observers and the
 * dispatching of events coming from the native event thread.
 */
internal class MPVSession(private val mpv: MPV) {
    private class PropertyState(val type: KClass<*>, val default: Any?) {
        val flow = MutableStateFlow(default)
    }

    // the lists are replaced, never modified, so they can be read after the lock is released
    private val statesLock = SynchronizedObject()
    private val states = HashMap<String, List<PropertyState>>()

    private fun statesOf(name: String): List<PropertyState> =
        synchronized(statesLock) { states[name] } ?: emptyList()

    // properties whose last read failed. mpv only re-checks a property on the events tied to it,
    // so one that comes back without such an event (e.g. hwdec-current after a decoder reinit
    // that didn't change the output format) is never notified again. these get re-read on every event.
    private val unavailableLock = SynchronizedObject()
    private val unavailable = HashSet<String>()

    private val observersLock = SynchronizedObject()
    private val observers: MutableList<MPV.EventObserver> = ArrayList()
    private val logObserversLock = SynchronizedObject()
    private val logObservers: MutableList<MPV.LogObserver> = ArrayList()

    private fun <T> eventBuffer(capacity: Int) = MutableSharedFlow<T>(
        extraBufferCapacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _logFlow = eventBuffer<Triple<String, Int, String>>(256)
    val logFlow = _logFlow.asSharedFlow()
    // not dropping here, a lost file-loaded would leave its collector waiting forever
    private val _eventFlow = MutableSharedFlow<Pair<Int, MPVNode>>(extraBufferCapacity = Int.MAX_VALUE)
    val eventFlow = _eventFlow.asSharedFlow()
    private val _eventPropertyFlow = eventBuffer<String>(64)
    val eventPropertyFlow = _eventPropertyFlow.asSharedFlow()

    fun initSession() {
        val formats = synchronized(statesLock) {
            states.flatMap { (name, list) -> list.map { name to formatOf(it.type) } }.distinct()
        }
        for ((name, format) in formats) mpv.observeProperty(name, format)
    }

    fun destroySession() {
        val all = synchronized(statesLock) { states.values.flatten() }
        for (state in all) state.flow.value = state.default
        synchronized(unavailableLock) { unavailable.clear() }
    }

    fun stateFlow(name: String, type: KClass<*>, default: Any?): StateFlow<Any?> {
        val format = formatOf(type)
        var created = false
        var observe = false
        val state = synchronized(statesLock) {
            val existing = states[name].orEmpty()
            existing.firstOrNull { it.type == type && it.default == default }
                ?: PropertyState(type, default).also {
                    created = true
                    // int/long and float/double are fed by the same observation
                    observe = existing.none { other -> formatOf(other.type) == format }
                    states[name] = existing + it
                }
        }
        if (created && mpv.isInitialized) {
            if (observe) mpv.observeProperty(name, format)
            state.flow.compareAndSet(default, mpv.getTypedProperty(name, type) ?: default)
        }
        return state.flow
    }

    private fun update(name: String, type: KClass<*>, value: Any) {
        synchronized(unavailableLock) { unavailable.remove(name) }
        for (state in statesOf(name)) if (state.type == type) state.flow.value = value
    }

    private fun refresh(property: String) {
        var missing = false
        for (state in statesOf(property)) {
            val value = mpv.getTypedProperty(property, state.type)
            if (value == null) missing = true
            state.flow.value = value ?: state.default
        }
        synchronized(unavailableLock) {
            if (missing) unavailable.add(property) else unavailable.remove(property)
        }
    }

    private fun refreshUnavailable() {
        val pending = synchronized(unavailableLock) { unavailable.toList() }
        for (property in pending) refresh(property)
    }

    fun addObserver(o: MPV.EventObserver) {
        synchronized(observersLock) { observers.add(o) }
    }

    fun removeObserver(o: MPV.EventObserver) {
        synchronized(observersLock) { observers.remove(o) }
    }

    fun addLogObserver(o: MPV.LogObserver) {
        synchronized(logObserversLock) { logObservers.add(o) }
    }

    fun removeLogObserver(o: MPV.LogObserver) {
        synchronized(logObserversLock) { logObservers.remove(o) }
    }

    private inline fun forEachObserver(block: (MPV.EventObserver) -> Unit) {
        synchronized(observersLock) {
            for (o in observers) block(o)
        }
    }

    fun eventProperty(property: String, value: Long) {
        forEachObserver { it.eventProperty(property, value) }
        update(property, Long::class, value)
        update(property, Int::class, value.toInt())
    }

    fun eventProperty(property: String, value: Boolean) {
        forEachObserver { it.eventProperty(property, value) }
        update(property, Boolean::class, value)
    }

    fun eventProperty(property: String, value: Double) {
        forEachObserver { it.eventProperty(property, value) }
        update(property, Double::class, value)
        update(property, Float::class, value.toFloat())
    }

    fun eventProperty(property: String, value: String) {
        forEachObserver { it.eventProperty(property, value) }
        update(property, String::class, value)
    }

    fun eventProperty(property: String, value: MPVNode) {
        forEachObserver { it.eventProperty(property, value) }
        update(property, MPVNode::class, value)
    }

    fun eventProperty(property: String) {
        forEachObserver { it.eventProperty(property) }
        // no value means it became unavailable, so read it again
        refresh(property)
        _eventPropertyFlow.tryEmit(property)
    }

    fun event(eventId: Int, data: MPVNode) {
        refreshUnavailable()
        forEachObserver { it.event(eventId, data) }
        _eventFlow.tryEmit(eventId to data)
    }

    fun logMessage(prefix: String, level: Int, text: String) {
        synchronized(logObserversLock) {
            for (o in logObservers) o.logMessage(prefix, level, text)
        }
        _logFlow.tryEmit(Triple(prefix, level, text))
    }
}
