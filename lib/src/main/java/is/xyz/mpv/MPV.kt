package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
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
import kotlin.reflect.KProperty

@Suppress("unused")
class MPV(
    context: Context,
    configDir: String = context.filesDir.resolve("mpv").toString(),
    cacheDir: String = context.cacheDir.resolve("mpv").toString(),
) {
    @Suppress("unused")
    private var nativeHandle: Long = 0

    val isInitialized: Boolean get() = nativeHandle != 0L

    external fun nativeCreate(appctx: Context)
    external fun nativeInit()
    external fun nativeDestroy()

    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(vararg cmd: String)
    external fun commandNode(vararg cmd: String): MPVNode?

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

    fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
    fun setPropertyFloat(property: String, value: Float) =
        setPropertyDouble(property, value.toDouble())

    fun getPropertyLong(property: String) = getPropertyInt(property)?.toLong()
    fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

    external fun observeProperty(property: String, format: Int)

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

    fun initSession() {
        observedProperties.asIterable().forEach { (property, format) ->
            observeProperty(property, format)
        }
    }

    fun destroySession() {
        sessionScope.cancel()
        stateCache.clear()
        observedProperties.clear()
    }

    private fun <T> getOrCreateState(
        key: String,
        format: Int,
        factory: () -> StateFlow<T?>
    ): StateFlow<T?> {
        return stateCache.getOrPut(key) {
            observedProperties[key] = format
            if (isInitialized) observeProperty(key, format)
            factory()
        } as StateFlow<T?>
    }

    val prop = PropertyAccessor()
    val propFlow = PropertyFlowAccessor()

    inner class PropertyAccessor {
        inline operator fun <reified T> get(name: String): T? {
            if (!isInitialized) return null
            return when (T::class) {
                Int::class -> getPropertyInt(name)
                Long::class -> getPropertyLong(name)
                Float::class -> getPropertyFloat(name)
                Double::class -> getPropertyDouble(name)
                Boolean::class -> getPropertyBoolean(name)
                String::class -> getPropertyString(name)
                MPVNode::class -> getPropertyNode(name)
                else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
            } as T?
        }

        inline operator fun <reified T> set(name: String, value: T) {
            if (!isInitialized) return
            when (T::class) {
                Int::class -> setPropertyInt(name, value as Int)
                Long::class -> setPropertyLong(name, value as Long)
                Float::class -> setPropertyFloat(name, value as Float)
                Double::class -> setPropertyDouble(name, value as Double)
                Boolean::class -> setPropertyBoolean(name, value as Boolean)
                String::class -> setPropertyString(name, value as String)
                MPVNode::class -> setPropertyNode(name, value as MPVNode)
                else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
            }
        }
    }

    inner class PropertyDelegate<T>(
        private val name: String,
        private val getter: (String) -> T?,
        private val setter: (String, T) -> Unit
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
            if (isInitialized) getter(name) else null

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            if (isInitialized && value != null) setter(name, value)
        }
    }

    inline fun <reified T> prop(name: String): PropertyDelegate<T> {
        return when (T::class) {
            Int::class -> PropertyDelegate(name, ::getPropertyInt, ::setPropertyInt)
            Long::class -> PropertyDelegate(name, ::getPropertyLong, ::setPropertyLong)
            Float::class -> PropertyDelegate(name, ::getPropertyFloat, ::setPropertyFloat)
            Double::class -> PropertyDelegate(name, ::getPropertyDouble, ::setPropertyDouble)
            Boolean::class -> PropertyDelegate(name, ::getPropertyBoolean, ::setPropertyBoolean)
            String::class -> PropertyDelegate(name, ::getPropertyString, ::setPropertyString)
            MPVNode::class -> PropertyDelegate(name, ::getPropertyNode, ::setPropertyNode)
            else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
        } as PropertyDelegate<T>
    }

    inline fun <reified T> prop(name: String, value: T) {
        when (T::class) {
            Int::class -> setPropertyInt(name, value as Int)
            Long::class -> setPropertyLong(name, value as Long)
            Float::class -> setPropertyFloat(name, value as Float)
            Double::class -> setPropertyDouble(name, value as Double)
            Boolean::class -> setPropertyBoolean(name, value as Boolean)
            String::class -> setPropertyString(name, value as String)
            MPVNode::class -> setPropertyNode(name, value as MPVNode)
            else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
        }
    }

    inline fun <reified T> propFlow(name: String): StateFlow<T?> = propFlow[name]

    inner class PropertyFlowAccessor {
        inline operator fun <reified T> get(name: String): StateFlow<T?> {
            return when (T::class) {
                Int::class -> getIntFlow(name)
                Long::class -> getLongFlow(name)
                Float::class -> getFloatFlow(name)
                Double::class -> getDoubleFlow(name)
                Boolean::class -> getBooleanFlow(name)
                String::class -> getStringFlow(name)
                MPVNode::class -> getNodeFlow(name)
                else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
            } as StateFlow<T?>
        }

        fun getIntFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_INT64) {
            intFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyInt(name) else null
                )
        }

        fun getLongFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_INT64) {
            longFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Eagerly,
                    initialValue = if (isInitialized) getPropertyLong(name) else null
                )
        }

        fun getFloatFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_DOUBLE) {
            floatFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyFloat(name) else null
                )
        }

        fun getDoubleFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_DOUBLE) {
            doubleFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyDouble(name) else null
                )
        }

        fun getBooleanFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_FLAG) {
            booleanFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyBoolean(name) else null
                )
        }

        fun getStringFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_STRING) {
            stringFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyString(name) else null
                )
        }

        fun getNodeFlow(name: String) = getOrCreateState(name, mpvFormat.MPV_FORMAT_NODE) {
            nodeFlow
                .filter { it.first == name }
                .map { it.second }
                .stateIn(
                    scope = sessionScope,
                    started = SharingStarted.Lazily,
                    initialValue = if (isInitialized) getPropertyNode(name) else null
                )
        }
    }

    private val observers: MutableList<EventObserver> = ArrayList()
    private val log_observers: MutableList<LogObserver> = ArrayList()
    val logFlow = MutableSharedFlow<Triple<String, Int, String>>()

    fun addObserver(o: EventObserver) {
        synchronized(observers) { observers.add(o) }
    }

    fun removeObserver(o: EventObserver) {
        synchronized(observers) { observers.remove(o) }
    }

    fun addLogObserver(o: LogObserver) {
        synchronized(log_observers) { log_observers.add(o) }
    }

    fun removeLogObserver(o: LogObserver) {
        synchronized(log_observers) { log_observers.remove(o) }
    }

    @Suppress("unused")
    private fun eventProperty(property: String, value: Long) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        sessionScope.launch {
            longFlow.emit(property to value)
            intFlow.emit(property to value.toInt())
        }
    }

    @Suppress("unused")
    private fun eventProperty(property: String, value: Boolean) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        sessionScope.launch { booleanFlow.emit(property to value) }
    }

    @Suppress("unused")
    private fun eventProperty(property: String, value: Double) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        sessionScope.launch {
            doubleFlow.emit(property to value)
            floatFlow.emit(property to value.toFloat())
        }
    }

    @Suppress("unused")
    private fun eventProperty(property: String, value: String) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        sessionScope.launch { stringFlow.emit(property to value) }
    }

    @Suppress("unused")
    private fun eventProperty(property: String, value: MPVNode) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property, value)
        }
        sessionScope.launch { nodeFlow.emit(property to value) }
    }

    @Suppress("unused")
    private fun eventProperty(property: String) {
        synchronized(observers) {
            for (o in observers) o.eventProperty(property)
        }
        sessionScope.launch { eventPropertyFlow.emit(property) }
    }

    @Suppress("unused")
    private fun event(eventId: Int, data: MPVNode) {
        synchronized(observers) {
            for (o in observers) o.event(eventId, data)
        }
        sessionScope.launch { eventFlow.emit(eventId) }
    }

    @Suppress("unused")
    private fun logMessage(prefix: String, level: Int, text: String) {
        synchronized(log_observers) {
            for (o in log_observers) o.logMessage(prefix, level, text)
        }
        sessionScope.launch { logFlow.emit(Triple(prefix, level, text)) }
    }

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

    object mpvEvent {
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

    fun close() {
        destroySession()
        nativeDestroy()
    }

    fun setCacheDir(cacheDir: String) {
        setOptionString("gpu-shader-cache-dir", cacheDir)
        setOptionString("icc-cache-dir", cacheDir)
    }

    fun setConfigDir(configDir: String) {
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