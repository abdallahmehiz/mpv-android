@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package `is`.xyz.mpv

import `is`.xyz.mpv.libmpv.mpv_create
import `is`.xyz.mpv.libmpv.mpv_initialize
import `is`.xyz.mpv.libmpv.mpv_observe_property
import `is`.xyz.mpv.libmpv.mpv_request_log_messages
import `is`.xyz.mpv.libmpv.mpv_set_option_string
import `is`.xyz.mpv.libmpv.mpv_terminate_destroy
import `is`.xyz.mpv.libmpv.mpv_wait_event
import `is`.xyz.mpv.libmpv.mpv_wakeup
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toLong
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.isEqualToData
import platform.Foundation.writeToFile
import platform.Foundation.NSUserDomainMask
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIImage
import platform.posix.LC_NUMERIC
import platform.posix.pthread_create
import platform.posix.pthread_join
import platform.posix.pthread_setname_np
import platform.posix.pthread_t
import platform.posix.pthread_tVar
import platform.posix.setlocale
import kotlin.concurrent.Volatile
import kotlin.reflect.KProperty

/**
 * libmpv on iOS/tvOS.
 *
 * libmpv itself is not bundled: the application has to link it (and its dependencies), e.g. as
 * built by buildscripts/ios/build.sh. Video is rendered either with OpenGL ES through the render
 * API, see [MPVGLView], or through Vulkan (MoltenVK) into a [CAMetalLayer], see [attachSurface]
 * and [BaseMPVView].
 */
@Suppress("unused")
actual class MPV(
    configDir: String = defaultDirectory(NSApplicationSupportDirectory),
    cacheDir: String = defaultDirectory(NSCachesDirectory),
) {
    private var nativeHandle: MpvHandle? = null

    actual val isInitialized: Boolean get() = nativeHandle != null

    internal val handle: MpvHandle
        get() = checkNotNull(nativeHandle) { "libmpv is not initialized" }

    private var eventThread: pthread_t? = null

    @Volatile
    private var eventThreadRequestExit = false

    // keeps the layer alive while mpv renders into it
    private var surface: CAMetalLayer? = null

    private fun nativeCreate() {
        check(nativeHandle == null) { "mpv is already initialized for this instance" }
        setlocale(LC_NUMERIC, "C")

        val mpv = checkNotNull(mpv_create()) { "context init failed" }
        nativeHandle = mpv

        // use terminal log level but request verbose messages
        // this way --msg-level can be used to adjust later
        mpv_request_log_messages(mpv, "terminal-default")
        mpv_set_option_string(mpv, "msg-level", "all=v")
    }

    actual fun nativeInit() {
        check(mpv_initialize(handle) >= 0) { "mpv init failed" }

        eventThreadRequestExit = false
        val ref = StableRef.create(this)
        eventThread = memScoped {
            val thread = alloc<pthread_tVar>()
            if (pthread_create(thread.ptr, null, eventThreadMain, ref.asCPointer()) != 0) {
                ref.dispose()
                error("thread create failed")
            }
            thread.value
        }
    }

    actual fun nativeDestroy() {
        val mpv = nativeHandle ?: return

        // poke event thread and wait for it to exit
        eventThreadRequestExit = true
        mpv_wakeup(mpv)
        eventThread?.let { pthread_join(it, null) }
        eventThread = null

        mpv_terminate_destroy(mpv)
        nativeHandle = null
        surface = null
    }

    private fun eventLoop(mpv: MpvHandle) {
        while (true) {
            val event = mpv_wait_event(mpv, -1.0) ?: continue
            if (eventThreadRequestExit) break
            autoreleasepool { session.dispatch(event.pointed) }
        }
    }

    /** Makes mpv render into [layer]. Requires `vo=gpu`/`gpu-next` with `gpu-api=vulkan`. */
    fun attachSurface(layer: CAMetalLayer) {
        surface = layer
        handle.setWid(layer.objcPtr().toLong())
    }

    actual fun detachSurface() {
        handle.setWid(0)
        surface = null
    }

    actual fun command(vararg cmd: String) = handle.command(cmd)

    actual fun commandNode(vararg cmd: String): MPVNode? {
        require(cmd.isNotEmpty()) { "commandNode called with empty array" }
        return handle.commandNode(*cmd) { it.toMPVNode() }
    }

    actual fun setOptionString(name: String, value: String): Int =
        mpv_set_option_string(handle, name, value)

    /** Grabs the current video frame, cropped to a square and scaled to [dimension]. */
    fun grabThumbnail(dimension: Int): UIImage? = handle.grabThumbnail(dimension)

    actual fun getPropertyInt(property: String): Int? = handle.getLong(property)?.toInt()
    actual fun setPropertyInt(property: String, value: Int) = handle.setLong(property, value.toLong())
    actual fun getPropertyDouble(property: String): Double? = handle.getDouble(property)
    actual fun setPropertyDouble(property: String, value: Double) = handle.setDouble(property, value)
    actual fun getPropertyBoolean(property: String): Boolean? = handle.getFlag(property)
    actual fun setPropertyBoolean(property: String, value: Boolean) = handle.setFlag(property, value)
    actual fun getPropertyString(property: String): String? = handle.getString(property)
    actual fun setPropertyString(property: String, value: String) = handle.setString(property, value)
    actual fun getPropertyNode(property: String): MPVNode? = handle.getNode(property)
    actual fun setPropertyNode(property: String, node: MPVNode) = handle.setNode(property, node)

    actual fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
    actual fun setPropertyFloat(property: String, value: Float) =
        setPropertyDouble(property, value.toDouble())

    actual fun getPropertyLong(property: String) = getPropertyInt(property)?.toLong()
    actual fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

    actual fun observeProperty(property: String, format: Int) {
        mpv_observe_property(handle, 0u, property, format.toUInt())
    }

    private val session = MPVSession(this)

    actual fun initSession() = session.initSession()

    actual fun destroySession() = session.destroySession()

    actual val prop = PropertyAccessor()
    actual val propFlow = PropertyFlowAccessor()

    actual inner class PropertyAccessor {
        actual inline operator fun <reified T> get(name: String): T? =
            if (isInitialized) this@MPV.getTypedProperty(name, T::class) as T? else null

        actual inline operator fun <reified T> set(name: String, value: T) {
            if (isInitialized) this@MPV.setTypedProperty(name, T::class, value)
        }
    }

    actual inner class PropertyDelegate<T> actual constructor(
        private val name: String,
        private val getter: (String) -> T?,
        private val setter: (String, T) -> Unit
    ) {
        actual operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
            if (isInitialized) getter(name) else null

        actual operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            if (isInitialized && value != null) setter(name, value)
        }
    }

    actual inline fun <reified T> prop(name: String): PropertyDelegate<T> =
        typedPropertyDelegate(name, T::class) as PropertyDelegate<T>

    actual inline fun <reified T> prop(name: String, value: T) = setTypedProperty(name, T::class, value)

    actual inline fun <reified T> propFlow(name: String): StateFlow<T?> = propFlow[name]

    actual inner class PropertyFlowAccessor {
        actual inline operator fun <reified T> get(name: String): StateFlow<T?> =
            typedFlow(name, T::class) as StateFlow<T?>

        actual fun getIntFlow(name: String) = session.getIntFlow(name)
        actual fun getLongFlow(name: String) = session.getLongFlow(name)
        actual fun getFloatFlow(name: String) = session.getFloatFlow(name)
        actual fun getDoubleFlow(name: String) = session.getDoubleFlow(name)
        actual fun getBooleanFlow(name: String) = session.getBooleanFlow(name)
        actual fun getStringFlow(name: String) = session.getStringFlow(name)
        actual fun getNodeFlow(name: String) = session.getNodeFlow(name)
    }

    actual val logFlow: MutableSharedFlow<Triple<String, Int, String>> get() = session.logFlow

    actual fun addObserver(o: EventObserver) = session.addObserver(o)

    actual fun removeObserver(o: EventObserver) = session.removeObserver(o)

    actual fun addLogObserver(o: LogObserver) = session.addLogObserver(o)

    actual fun removeLogObserver(o: LogObserver) = session.removeLogObserver(o)

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
        destroySession()
        nativeDestroy()
    }

    actual fun setCacheDir(cacheDir: String) {
        setOptionString("gpu-shader-cache-dir", cacheDir)
        setOptionString("icc-cache-dir", cacheDir)
    }

    actual fun setConfigDir(configDir: String) {
        setOptionString("config", "yes")
        setOptionString("config-dir", configDir)
    }

    private companion object {
        val eventThreadMain = staticCFunction { arg: COpaquePointer? ->
            pthread_setname_np("event_thread")
            val ref = arg!!.asStableRef<MPV>()
            val mpv = ref.get()
            mpv.nativeHandle?.let { mpv.eventLoop(it) }
            ref.dispose()
            null as COpaquePointer?
        }
    }

    init {
        nativeCreate()
        nativeInit()
        initSession()
        setConfigDir(configDir.also(::createDirectory))
        setCacheDir(cacheDir.also(::createDirectory))
        // like the cacert.pem asset on Android, without it https fails certificate verification
        writeCACert("$configDir/cacert.pem")?.let { setOptionString("tls-ca-file", it) }
        setOptionString("idle", "once")
        setPropertyBoolean("pause", true)
    }
}

private fun defaultDirectory(directory: NSSearchPathDirectory): String {
    val base = NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first()
    return "$base/mpv"
}

private fun createDirectory(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
}

/** Writes the embedded CA bundle to [path] unless it is already there, returns the path or null on failure. */
private fun writeCACert(path: String): String? {
    val data = NSString.create(string = CA_CERT).dataUsingEncoding(NSUTF8StringEncoding) ?: return null
    if (NSFileManager.defaultManager.contentsAtPath(path)?.isEqualToData(data) == true) return path
    return path.takeIf { data.writeToFile(path, true) }
}
