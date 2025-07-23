package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Wrapper for native library

@Suppress("unused")
object MPVLib {
    init {
        val libs = arrayOf("mpv", "player")
        for (lib in libs) {
            System.loadLibrary(lib)
        }
    }

    external fun create(appctx: Context?)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(cmd: Array<String>)
    external fun commandNode(cmd: Array<String>): MPVNode?

    external fun setOptionString(name: String, value: String): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)
    external fun getPropertyNode(property: String): MPVNode?
    external fun setPropertyNode(property: String, node: MPVNode)

    external fun observeProperty(property: String, format: Int)

    private val observers: MutableList<EventObserver> = ArrayList()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<Int>()
    private val eventPropertyFlow = MutableSharedFlow<String>()

    data class Property<T>(
        val type: Int,
        val getProperty: (String) -> T?,
        val flow: MutableSharedFlow<Pair<String, T>> = MutableSharedFlow(),
        val map: MutableMap<String, StateFlow<T?>> = mutableMapOf(),
    ) {
        operator fun get(property: String): StateFlow<T?> {
            return map.getOrPut(property) {
                observeProperty(property, type)
                flow.filter { it.first == property }
                    .map { it.second }
                    .stateIn(scope, SharingStarted.Lazily, getProperty(property))
            }
        }

        operator fun set(property: String, value: T) {
            when (type) {
                mpvFormat.MPV_FORMAT_INT64 -> setPropertyInt(property, value as Int)
                mpvFormat.MPV_FORMAT_FLAG -> setPropertyBoolean(property, value as Boolean)
                mpvFormat.MPV_FORMAT_STRING -> setPropertyString(property, value as String)
                mpvFormat.MPV_FORMAT_DOUBLE -> setPropertyDouble(property, value as Double)
                mpvFormat.MPV_FORMAT_NODE,
                mpvFormat.MPV_FORMAT_NODE_ARRAY,
                mpvFormat.MPV_FORMAT_NODE_MAP -> setPropertyNode(property, value as MPVNode)
                else -> throw IllegalArgumentException("Unsupported property type")
            }
        }

        fun emit(property: String, value: T) {
            scope.launch { flow.emit(Pair(property, value)) }
        }
    }

    val propInt = Property(mpvFormat.MPV_FORMAT_INT64, ::getPropertyInt)
    val propBoolean = Property(mpvFormat.MPV_FORMAT_FLAG, ::getPropertyBoolean)
    val propString = Property(mpvFormat.MPV_FORMAT_STRING, ::getPropertyString)
    val propDouble = Property(mpvFormat.MPV_FORMAT_DOUBLE, ::getPropertyDouble)
    val propNode = Property(mpvFormat.MPV_FORMAT_NODE, ::getPropertyNode)

    fun eventFlow(property: String): Flow<Unit> {
        observeProperty(property, mpvFormat.MPV_FORMAT_NONE)
        return eventPropertyFlow.filter { it == property }.map { it }
    }

    fun eventFlow(eventId: Int): Flow<Unit> {
        return eventFlow.filter { it == eventId }.map { }
    }

    @JvmStatic
    fun addObserver(o: EventObserver) {
        synchronized(observers) { observers.add(o) }
    }

    @JvmStatic
    fun removeObserver(o: EventObserver) {
        synchronized(observers) { observers.remove(o) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        propInt.emit(property, value.toInt())
    }

    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        propBoolean.emit(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: Double) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        propDouble.emit(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: String) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        propString.emit(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: MPVNode) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        propNode.emit(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property)
        }
        scope.launch { eventPropertyFlow.emit(property) }
    }

    @JvmStatic
    fun event(eventId: Int) {
        synchronized(observers) {
            for (o in observers) o.event(eventId)
        }
        scope.launch { eventFlow.emit(eventId) }
    }

    private val log_observers: MutableList<LogObserver> = ArrayList()
    val logFlow = MutableSharedFlow<Triple<String, Int, String>>()

    @JvmStatic
    fun addLogObserver(o: LogObserver) {
        synchronized(log_observers) { log_observers.add(o) }
    }

    @JvmStatic
    fun removeLogObserver(o: LogObserver) {
        synchronized(log_observers) { log_observers.remove(o) }
    }

    @JvmStatic
    fun logMessage(prefix: String, level: Int, text: String) {
        synchronized(log_observers) {
            for (o in log_observers) o.logMessage(prefix, level, text)
        }
        scope.launch { logFlow.emit(Triple(prefix, level, text)) }
    }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: MPVNode)
        fun event(eventId: Int)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    object mpvFormat {
        const val MPV_FORMAT_NONE: Int = 0
        const val MPV_FORMAT_STRING: Int = 1
        const val MPV_FORMAT_OSD_STRING: Int = 2
        const val MPV_FORMAT_FLAG: Int = 3
        const val MPV_FORMAT_INT64: Int = 4
        const val MPV_FORMAT_DOUBLE: Int = 5
        const val MPV_FORMAT_NODE: Int = 6
        const val MPV_FORMAT_NODE_ARRAY: Int = 7
        const val MPV_FORMAT_NODE_MAP: Int = 8
        const val MPV_FORMAT_BYTE_ARRAY: Int = 9
    }

    object mpvEventId {
        const val MPV_EVENT_NONE: Int = 0
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_LOG_MESSAGE: Int = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        const val MPV_EVENT_COMMAND_REPLY: Int = 5
        const val MPV_EVENT_START_FILE: Int = 6
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8

        @Deprecated("")
        const val MPV_EVENT_IDLE: Int = 11

        @Deprecated("")
        const val MPV_EVENT_TICK: Int = 14
        const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
        const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        const val MPV_EVENT_SEEK: Int = 20
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        const val MPV_EVENT_HOOK: Int = 25
    }

    object mpvLogLevel {
        const val MPV_LOG_LEVEL_NONE: Int = 0
        const val MPV_LOG_LEVEL_FATAL: Int = 10
        const val MPV_LOG_LEVEL_ERROR: Int = 20
        const val MPV_LOG_LEVEL_WARN: Int = 30
        const val MPV_LOG_LEVEL_INFO: Int = 40
        const val MPV_LOG_LEVEL_V: Int = 50
        const val MPV_LOG_LEVEL_DEBUG: Int = 60
        const val MPV_LOG_LEVEL_TRACE: Int = 70
    }
}