package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import kotlinx.coroutines.flow.SharedFlow

@Suppress("unused")
actual class MPV(
    context: Context,
    configDir: String = context.filesDir.resolve("mpv").toString(),
    cacheDir: String = context.cacheDir.resolve("mpv").toString(),
) {
    @Suppress("unused")
    private var nativeHandle: Long = 0

    actual val isInitialized: Boolean get() = nativeHandle != 0L

    external fun nativeCreate(appctx: Context)
    actual external fun nativeInit()
    actual external fun nativeDestroy()

    external fun attachSurface(surface: Surface)
    actual external fun detachSurface()

    actual external fun command(vararg cmd: String): Int
    actual external fun commandNode(vararg cmd: String): MPVNode?

    actual external fun setOptionString(name: String, value: String): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    actual external fun getPropertyInt(property: String): Int?
    actual external fun setPropertyInt(property: String, value: Int): Int
    actual external fun getPropertyDouble(property: String): Double?
    actual external fun setPropertyDouble(property: String, value: Double): Int
    actual external fun getPropertyBoolean(property: String): Boolean?
    actual external fun setPropertyBoolean(property: String, value: Boolean): Int
    actual external fun getPropertyString(property: String): String?
    actual external fun setPropertyString(property: String, value: String): Int
    actual external fun getPropertyNode(property: String): MPVNode?
    actual external fun setPropertyNode(property: String, node: MPVNode): Int

    actual fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
    actual fun setPropertyFloat(property: String, value: Float) =
        setPropertyDouble(property, value.toDouble())

    actual external fun getPropertyLong(property: String): Long?
    actual external fun setPropertyLong(property: String, value: Long): Int

    actual external fun observeProperty(property: String, format: Int): Int

    actual external fun errorString(error: Int): String

    internal actual val session = MPVSession(this)

    actual fun initSession() = session.initSession()

    actual fun destroySession() = session.destroySession()

    actual val logFlow: SharedFlow<Triple<String, Int, String>> get() = session.logFlow
    actual val eventFlow: SharedFlow<Pair<Int, MPVNode>> get() = session.eventFlow
    actual val eventPropertyFlow: SharedFlow<String> get() = session.eventPropertyFlow

    actual fun addObserver(o: EventObserver) = session.addObserver(o)

    actual fun removeObserver(o: EventObserver) = session.removeObserver(o)

    actual fun addLogObserver(o: LogObserver) = session.addLogObserver(o)

    actual fun removeLogObserver(o: LogObserver) = session.removeLogObserver(o)

    @Suppress("unused")
    private fun eventProperty(property: String, value: Long) = session.eventProperty(property, value)

    @Suppress("unused")
    private fun eventProperty(property: String, value: Boolean) = session.eventProperty(property, value)

    @Suppress("unused")
    private fun eventProperty(property: String, value: Double) = session.eventProperty(property, value)

    @Suppress("unused")
    private fun eventProperty(property: String, value: String) = session.eventProperty(property, value)

    @Suppress("unused")
    private fun eventProperty(property: String, value: MPVNode) = session.eventProperty(property, value)

    @Suppress("unused")
    private fun eventProperty(property: String) = session.eventProperty(property)

    @Suppress("unused")
    private fun event(eventId: Int, data: MPVNode) = session.event(eventId, data)

    @Suppress("unused")
    private fun logMessage(prefix: String, level: Int, text: String) =
        session.logMessage(prefix, level, text)

    actual interface EventObserver {
        actual fun eventProperty(property: String)
        actual fun eventProperty(property: String, value: Long)
        actual fun eventProperty(property: String, value: Boolean)
        actual fun eventProperty(property: String, value: String)
        actual fun eventProperty(property: String, value: Double)
        actual fun eventProperty(property: String, value: MPVNode)
        actual fun event(eventId: Int, data: MPVNode)
    }

    actual interface LogObserver {
        actual fun logMessage(prefix: String, level: Int, text: String)
    }

    actual object mpvFormat {
        actual const val MPV_FORMAT_NONE: Int = 0
        actual const val MPV_FORMAT_STRING: Int = 1
        actual const val MPV_FORMAT_OSD_STRING: Int = 2
        actual const val MPV_FORMAT_FLAG: Int = 3
        actual const val MPV_FORMAT_INT64: Int = 4
        actual const val MPV_FORMAT_DOUBLE: Int = 5
        actual const val MPV_FORMAT_NODE: Int = 6
        actual const val MPV_FORMAT_NODE_ARRAY: Int = 7
        actual const val MPV_FORMAT_NODE_MAP: Int = 8
        actual const val MPV_FORMAT_BYTE_ARRAY: Int = 9
    }

    actual object mpvEvent {
        actual const val MPV_EVENT_NONE: Int = 0
        actual const val MPV_EVENT_SHUTDOWN: Int = 1
        actual const val MPV_EVENT_LOG_MESSAGE: Int = 2
        actual const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        actual const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        actual const val MPV_EVENT_COMMAND_REPLY: Int = 5
        actual const val MPV_EVENT_START_FILE: Int = 6
        actual const val MPV_EVENT_END_FILE: Int = 7
        actual const val MPV_EVENT_FILE_LOADED: Int = 8

        @Deprecated("")
        actual const val MPV_EVENT_IDLE: Int = 11

        @Deprecated("")
        actual const val MPV_EVENT_TICK: Int = 14
        actual const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
        actual const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        actual const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        actual const val MPV_EVENT_SEEK: Int = 20
        actual const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        actual const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        actual const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        actual const val MPV_EVENT_HOOK: Int = 25
    }

    actual object mpvLogLevel {
        actual const val MPV_LOG_LEVEL_NONE: Int = 0
        actual const val MPV_LOG_LEVEL_FATAL: Int = 10
        actual const val MPV_LOG_LEVEL_ERROR: Int = 20
        actual const val MPV_LOG_LEVEL_WARN: Int = 30
        actual const val MPV_LOG_LEVEL_INFO: Int = 40
        actual const val MPV_LOG_LEVEL_V: Int = 50
        actual const val MPV_LOG_LEVEL_DEBUG: Int = 60
        actual const val MPV_LOG_LEVEL_TRACE: Int = 70
    }

    actual fun close() {
        // event thread first, it could still be setting flows
        nativeDestroy()
        destroySession()
    }

    actual fun setCacheDir(cacheDir: String) {
        setOptionString("gpu-shader-cache-dir", cacheDir)
        setOptionString("icc-cache-dir", cacheDir)
    }

    actual fun setConfigDir(configDir: String) {
        setOptionString("config", "yes")
        setOptionString("config-dir", configDir)
    }

    companion object {
        var systemLibraryLoaded = false
    }

    init {
        if (!systemLibraryLoaded) {
            System.loadLibrary("mpv")
            System.loadLibrary("player")
            systemLibraryLoaded = true
        }
        nativeCreate(context)
        nativeInit()
        initSession()
        setConfigDir(configDir)
        setCacheDir(cacheDir)
        setOptionString("idle", "once")
        setPropertyBoolean("pause", true)
    }
}
