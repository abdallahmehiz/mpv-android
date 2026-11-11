package `is`.xyz.mpv

import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

// MPV_ERROR_UNINITIALIZED from client.h
private const val MPV_ERROR_UNINITIALIZED = -3

val MPV.prop: PropertyAccessor get() = PropertyAccessor(this)

class PropertyAccessor @PublishedApi internal constructor(@PublishedApi internal val mpv: MPV) {
    inline operator fun <reified T : Any> get(name: String): T? =
        mpv.getTypedProperty(name, T::class) as T?

    inline operator fun <reified T : Any> set(name: String, value: T) {
        mpv.setTypedProperty(name, T::class, value)
    }
}

class PropertyDelegate<T : Any> @PublishedApi internal constructor(
    private val mpv: MPV,
    private val name: String,
    private val type: KClass<T>,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        mpv.getTypedProperty(name, type) as T?

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        if (value != null) mpv.setTypedProperty(name, type, value)
    }
}

inline fun <reified T : Any> MPV.prop(name: String): PropertyDelegate<T> =
    PropertyDelegate(this, name, T::class)

inline fun <reified T : Any> MPV.prop(name: String, value: T): Int =
    setTypedProperty(name, T::class, value)

// the value is set from mpv's event thread
inline fun <reified T : Any> MPV.propFlow(name: String): StateFlow<T?> = typedFlow(name, T::class)

inline fun <reified T : Any> MPV.propFlow(name: String, default: T): StateFlow<T> =
    typedFlow(name, T::class, default)

@PublishedApi
internal fun MPV.getTypedProperty(name: String, type: KClass<*>): Any? =
    if (!isInitialized) null else when (type) {
        Int::class -> getPropertyInt(name)
        Long::class -> getPropertyLong(name)
        Float::class -> getPropertyFloat(name)
        Double::class -> getPropertyDouble(name)
        Boolean::class -> getPropertyBoolean(name)
        String::class -> getPropertyString(name)
        MPVNode::class -> getPropertyNode(name)
        else -> unsupportedType(type)
    }

@PublishedApi
internal fun MPV.setTypedProperty(name: String, type: KClass<*>, value: Any): Int =
    if (!isInitialized) MPV_ERROR_UNINITIALIZED else when (type) {
        Int::class -> setPropertyInt(name, value as Int)
        Long::class -> setPropertyLong(name, value as Long)
        Float::class -> setPropertyFloat(name, value as Float)
        Double::class -> setPropertyDouble(name, value as Double)
        Boolean::class -> setPropertyBoolean(name, value as Boolean)
        String::class -> setPropertyString(name, value as String)
        MPVNode::class -> setPropertyNode(name, value as MPVNode)
        else -> unsupportedType(type)
    }

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> MPV.typedFlow(name: String, type: KClass<T>): StateFlow<T?> =
    session.stateFlow(name, type, null) as StateFlow<T?>

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> MPV.typedFlow(name: String, type: KClass<T>, default: T): StateFlow<T> =
    session.stateFlow(name, type, default) as StateFlow<T>

internal fun formatOf(type: KClass<*>): Int = when (type) {
    Int::class, Long::class -> MPV.mpvFormat.MPV_FORMAT_INT64
    Float::class, Double::class -> MPV.mpvFormat.MPV_FORMAT_DOUBLE
    Boolean::class -> MPV.mpvFormat.MPV_FORMAT_FLAG
    String::class -> MPV.mpvFormat.MPV_FORMAT_STRING
    MPVNode::class -> MPV.mpvFormat.MPV_FORMAT_NODE
    else -> unsupportedType(type)
}

private fun unsupportedType(type: KClass<*>): Nothing =
    throw IllegalArgumentException("Unsupported property type: $type")
