package `is`.xyz.mpv

import kotlinx.coroutines.flow.SharedFlow

@Suppress("unused")
expect class MPV {
    val isInitialized: Boolean

    fun nativeInit()
    fun nativeDestroy()

    fun detachSurface()

    fun command(vararg cmd: String): Int
    fun commandNode(vararg cmd: String): MPVNode?

    fun setOptionString(name: String, value: String): Int

    fun getPropertyInt(property: String): Int?
    fun setPropertyInt(property: String, value: Int): Int
    fun getPropertyDouble(property: String): Double?
    fun setPropertyDouble(property: String, value: Double): Int
    fun getPropertyBoolean(property: String): Boolean?
    fun setPropertyBoolean(property: String, value: Boolean): Int
    fun getPropertyString(property: String): String?
    fun setPropertyString(property: String, value: String): Int
    fun getPropertyNode(property: String): MPVNode?
    fun setPropertyNode(property: String, node: MPVNode): Int

    fun getPropertyFloat(property: String): Float?
    fun setPropertyFloat(property: String, value: Float): Int

    fun getPropertyLong(property: String): Long?
    fun setPropertyLong(property: String, value: Long): Int

    fun observeProperty(property: String, format: Int): Int

    fun errorString(error: Int): String

    fun initSession()
    fun destroySession()

    internal val session: MPVSession

    val logFlow: SharedFlow<Triple<String, Int, String>>
    val eventFlow: SharedFlow<Pair<Int, MPVNode>>
    val eventPropertyFlow: SharedFlow<String>

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
