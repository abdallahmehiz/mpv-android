package `is`.xyz.mpv

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Platform independent state of an [MPV] instance: property flows, observers and the
 * dispatching of events coming from the native event thread.
 */
internal class MPVSession(private val mpv: MPV) {
    private var sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val intFlow = MutableSharedFlow<Pair<String, Int>>()
    private val booleanFlow = MutableSharedFlow<Pair<String, Boolean>>()
    private val stringFlow = MutableSharedFlow<Pair<String, String>>()
    private val doubleFlow = MutableSharedFlow<Pair<String, Double>>()
    private val nodeFlow = MutableSharedFlow<Pair<String, MPVNode>>()
    private val longFlow = MutableSharedFlow<Pair<String, Long>>()
    private val floatFlow = MutableSharedFlow<Pair<String, Float>>()
    private val eventPropertyFlow = MutableSharedFlow<String>()
    private val eventFlow = MutableSharedFlow<Int>()

    private val stateCache = mutableMapOf<String, StateFlow<*>>()
    private val observedProperties = mutableMapOf<String, Int>()

    private val observersLock = SynchronizedObject()
    private val observers: MutableList<MPV.EventObserver> = ArrayList()
    private val logObserversLock = SynchronizedObject()
    private val logObservers: MutableList<MPV.LogObserver> = ArrayList()
    val logFlow = MutableSharedFlow<Triple<String, Int, String>>()

    fun initSession() {
        observedProperties.asIterable().forEach { (property, format) ->
            mpv.observeProperty(property, format)
        }
    }

    fun destroySession() {
        sessionScope.cancel()
        stateCache.clear()
        observedProperties.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrCreateState(
        key: String,
        format: Int,
        factory: () -> StateFlow<T?>
    ): StateFlow<T?> {
        return stateCache.getOrPut(key) {
            observedProperties[key] = format
            if (mpv.isInitialized) mpv.observeProperty(key, format)
            factory()
        } as StateFlow<T?>
    }

    private fun <T> MutableSharedFlow<Pair<String, T>>.stateFor(
        name: String,
        started: SharingStarted,
        initialValue: () -> T?,
    ): StateFlow<T?> = filter { it.first == name }
        .map { it.second }
        .stateIn(
            scope = sessionScope,
            started = started,
            initialValue = if (mpv.isInitialized) initialValue() else null
        )

    fun getIntFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_INT64) {
        intFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyInt(name) }
    }

    fun getLongFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_INT64) {
        longFlow.stateFor(name, SharingStarted.Eagerly) { mpv.getPropertyLong(name) }
    }

    fun getFloatFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_DOUBLE) {
        floatFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyFloat(name) }
    }

    fun getDoubleFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_DOUBLE) {
        doubleFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyDouble(name) }
    }

    fun getBooleanFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_FLAG) {
        booleanFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyBoolean(name) }
    }

    fun getStringFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_STRING) {
        stringFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyString(name) }
    }

    fun getNodeFlow(name: String) = getOrCreateState(name, MPV.mpvFormat.MPV_FORMAT_NODE) {
        nodeFlow.stateFor(name, SharingStarted.Lazily) { mpv.getPropertyNode(name) }
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
        sessionScope.launch {
            longFlow.emit(property to value)
            intFlow.emit(property to value.toInt())
        }
    }

    fun eventProperty(property: String, value: Boolean) {
        forEachObserver { it.eventProperty(property, value) }
        sessionScope.launch { booleanFlow.emit(property to value) }
    }

    fun eventProperty(property: String, value: Double) {
        forEachObserver { it.eventProperty(property, value) }
        sessionScope.launch {
            doubleFlow.emit(property to value)
            floatFlow.emit(property to value.toFloat())
        }
    }

    fun eventProperty(property: String, value: String) {
        forEachObserver { it.eventProperty(property, value) }
        sessionScope.launch { stringFlow.emit(property to value) }
    }

    fun eventProperty(property: String, value: MPVNode) {
        forEachObserver { it.eventProperty(property, value) }
        sessionScope.launch { nodeFlow.emit(property to value) }
    }

    fun eventProperty(property: String) {
        forEachObserver { it.eventProperty(property) }
        sessionScope.launch { eventPropertyFlow.emit(property) }
    }

    fun event(eventId: Int, data: MPVNode) {
        forEachObserver { it.event(eventId, data) }
        sessionScope.launch { eventFlow.emit(eventId) }
    }

    fun logMessage(prefix: String, level: Int, text: String) {
        synchronized(logObserversLock) {
            for (o in logObservers) o.logMessage(prefix, level, text)
        }
        sessionScope.launch { logFlow.emit(Triple(prefix, level, text)) }
    }
}
