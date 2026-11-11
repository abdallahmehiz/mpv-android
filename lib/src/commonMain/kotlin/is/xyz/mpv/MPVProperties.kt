package `is`.xyz.mpv

import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

// Shared implementations behind the reified property accessors of MPV.
// Every platform has to declare those accessors itself, so they just call into these.

@PublishedApi
internal fun MPV.getTypedProperty(name: String, type: KClass<*>): Any? = when (type) {
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
internal fun MPV.setTypedProperty(name: String, type: KClass<*>, value: Any?) {
    when (type) {
        Int::class -> setPropertyInt(name, value as Int)
        Long::class -> setPropertyLong(name, value as Long)
        Float::class -> setPropertyFloat(name, value as Float)
        Double::class -> setPropertyDouble(name, value as Double)
        Boolean::class -> setPropertyBoolean(name, value as Boolean)
        String::class -> setPropertyString(name, value as String)
        MPVNode::class -> setPropertyNode(name, value as MPVNode)
        else -> unsupportedType(type)
    }
}

@PublishedApi
internal fun MPV.typedPropertyDelegate(name: String, type: KClass<*>): MPV.PropertyDelegate<*> =
    when (type) {
        Int::class -> PropertyDelegate(name, this::getPropertyInt, this::setPropertyInt)
        Long::class -> PropertyDelegate(name, this::getPropertyLong, this::setPropertyLong)
        Float::class -> PropertyDelegate(name, this::getPropertyFloat, this::setPropertyFloat)
        Double::class -> PropertyDelegate(name, this::getPropertyDouble, this::setPropertyDouble)
        Boolean::class -> PropertyDelegate(name, this::getPropertyBoolean, this::setPropertyBoolean)
        String::class -> PropertyDelegate(name, this::getPropertyString, this::setPropertyString)
        MPVNode::class -> PropertyDelegate(name, this::getPropertyNode, this::setPropertyNode)
        else -> unsupportedType(type)
    }

@PublishedApi
internal fun MPV.PropertyFlowAccessor.typedFlow(name: String, type: KClass<*>): StateFlow<*> =
    when (type) {
        Int::class -> getIntFlow(name)
        Long::class -> getLongFlow(name)
        Float::class -> getFloatFlow(name)
        Double::class -> getDoubleFlow(name)
        Boolean::class -> getBooleanFlow(name)
        String::class -> getStringFlow(name)
        MPVNode::class -> getNodeFlow(name)
        else -> unsupportedType(type)
    }

private fun unsupportedType(type: KClass<*>): Nothing =
    throw IllegalArgumentException("Unsupported property type: $type")
