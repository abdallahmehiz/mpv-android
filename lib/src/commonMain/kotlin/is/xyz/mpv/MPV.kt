package `is`.xyz.mpv

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty

/**
 * A single libmpv instance.
 *
 * Constructing one is platform specific (Android needs a `Context`), as is attaching it to
 * something to render into. Everything else is shared between platforms.
 */
@Suppress("unused")
expect class MPV {
    val isInitialized: Boolean

    fun nativeInit()
    fun nativeDestroy()

    fun detachSurface()

    fun command(vararg cmd: String)
    fun commandNode(vararg cmd: String): MPVNode?

    fun setOptionString(name: String, value: String): Int

    fun getPropertyInt(property: String): Int?
    fun setPropertyInt(property: String, value: Int)
    fun getPropertyDouble(property: String): Double?
    fun setPropertyDouble(property: String, value: Double)
    fun getPropertyBoolean(property: String): Boolean?
    fun setPropertyBoolean(property: String, value: Boolean)
    fun getPropertyString(property: String): String?
    fun setPropertyString(property: String, value: String)
    fun getPropertyNode(property: String): MPVNode?
    fun setPropertyNode(property: String, node: MPVNode)

    fun getPropertyFloat(property: String): Float?
    fun setPropertyFloat(property: String, value: Float)

    fun getPropertyLong(property: String): Long?
    fun setPropertyLong(property: String, value: Long)

    fun observeProperty(property: String, format: Int)

    fun initSession()
    fun destroySession()

    val prop: PropertyAccessor
    val propFlow: PropertyFlowAccessor

    inner class PropertyAccessor {
        inline operator fun <reified T> get(name: String): T?
        inline operator fun <reified T> set(name: String, value: T)
    }

    inner class PropertyDelegate<T>(
        name: String,
        getter: (String) -> T?,
        setter: (String, T) -> Unit
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T?
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?)
    }

    inline fun <reified T> prop(name: String): PropertyDelegate<T>
    inline fun <reified T> prop(name: String, value: T)

    inline fun <reified T> propFlow(name: String): StateFlow<T?>

    inner class PropertyFlowAccessor {
        inline operator fun <reified T> get(name: String): StateFlow<T?>

        fun getIntFlow(name: String): StateFlow<Int?>
        fun getLongFlow(name: String): StateFlow<Long?>
        fun getFloatFlow(name: String): StateFlow<Float?>
        fun getDoubleFlow(name: String): StateFlow<Double?>
        fun getBooleanFlow(name: String): StateFlow<Boolean?>
        fun getStringFlow(name: String): StateFlow<String?>
        fun getNodeFlow(name: String): StateFlow<MPVNode?>
    }

    val logFlow: MutableSharedFlow<Triple<String, Int, String>>

    fun addObserver(o: EventObserver)
    fun removeObserver(o: EventObserver)
    fun addLogObserver(o: LogObserver)
    fun removeLogObserver(o: LogObserver)

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: MPVNode)
        fun event(eventId: Int, data: MPVNode)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    object mpvFormat {
        val MPV_FORMAT_NONE: Int
        val MPV_FORMAT_STRING: Int
        val MPV_FORMAT_OSD_STRING: Int
        val MPV_FORMAT_FLAG: Int
        val MPV_FORMAT_INT64: Int
        val MPV_FORMAT_DOUBLE: Int
        val MPV_FORMAT_NODE: Int
        val MPV_FORMAT_NODE_ARRAY: Int
        val MPV_FORMAT_NODE_MAP: Int
        val MPV_FORMAT_BYTE_ARRAY: Int
    }

    object mpvEvent {
        val MPV_EVENT_NONE: Int
        val MPV_EVENT_SHUTDOWN: Int
        val MPV_EVENT_LOG_MESSAGE: Int
        val MPV_EVENT_GET_PROPERTY_REPLY: Int
        val MPV_EVENT_SET_PROPERTY_REPLY: Int
        val MPV_EVENT_COMMAND_REPLY: Int
        val MPV_EVENT_START_FILE: Int
        val MPV_EVENT_END_FILE: Int
        val MPV_EVENT_FILE_LOADED: Int

        @Deprecated("")
        val MPV_EVENT_IDLE: Int

        @Deprecated("")
        val MPV_EVENT_TICK: Int
        val MPV_EVENT_CLIENT_MESSAGE: Int
        val MPV_EVENT_VIDEO_RECONFIG: Int
        val MPV_EVENT_AUDIO_RECONFIG: Int
        val MPV_EVENT_SEEK: Int
        val MPV_EVENT_PLAYBACK_RESTART: Int
        val MPV_EVENT_PROPERTY_CHANGE: Int
        val MPV_EVENT_QUEUE_OVERFLOW: Int
        val MPV_EVENT_HOOK: Int
    }

    object mpvLogLevel {
        val MPV_LOG_LEVEL_NONE: Int
        val MPV_LOG_LEVEL_FATAL: Int
        val MPV_LOG_LEVEL_ERROR: Int
        val MPV_LOG_LEVEL_WARN: Int
        val MPV_LOG_LEVEL_INFO: Int
        val MPV_LOG_LEVEL_V: Int
        val MPV_LOG_LEVEL_DEBUG: Int
        val MPV_LOG_LEVEL_TRACE: Int
    }

    fun close()

    fun setCacheDir(cacheDir: String)
    fun setConfigDir(configDir: String)
}
