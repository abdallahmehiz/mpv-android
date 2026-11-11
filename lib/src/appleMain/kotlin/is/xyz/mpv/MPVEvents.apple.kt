@file:OptIn(ExperimentalForeignApi::class)

package `is`.xyz.mpv

import `is`.xyz.mpv.libmpv.MPV_EVENT_LOG_MESSAGE
import `is`.xyz.mpv.libmpv.MPV_EVENT_NONE
import `is`.xyz.mpv.libmpv.MPV_EVENT_PROPERTY_CHANGE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_FLAG
import `is`.xyz.mpv.libmpv.MPV_FORMAT_INT64
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NODE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NONE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_OSD_STRING
import `is`.xyz.mpv.libmpv.MPV_FORMAT_STRING
import `is`.xyz.mpv.libmpv.mpv_event
import `is`.xyz.mpv.libmpv.mpv_event_log_message
import `is`.xyz.mpv.libmpv.mpv_event_property
import `is`.xyz.mpv.libmpv.mpv_event_to_node
import `is`.xyz.mpv.libmpv.mpv_free_node_contents
import `is`.xyz.mpv.libmpv.mpv_node
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value

internal fun MPVSession.dispatch(event: mpv_event) {
    when (event.event_id) {
        MPV_EVENT_NONE -> Unit
        MPV_EVENT_LOG_MESSAGE -> {
            val msg = event.data?.reinterpret<mpv_event_log_message>()?.pointed ?: return
            logMessage(
                msg.prefix?.toKString().orEmpty(),
                msg.log_level.toInt(),
                msg.text?.toKString().orEmpty()
            )
        }
        MPV_EVENT_PROPERTY_CHANGE -> {
            val property = event.data?.reinterpret<mpv_event_property>()?.pointed ?: return
            dispatchPropertyChange(property)
        }
        else -> memScoped {
            val node = alloc<mpv_node>()
            mpv_event_to_node(node.ptr, event.ptr)
            node.toMPVNode()?.let { event(event.event_id.toInt(), it) }
            mpv_free_node_contents(node.ptr)
        }
    }
}

private fun MPVSession.dispatchPropertyChange(property: mpv_event_property) {
    val name = property.name?.toKString() ?: return
    if (property.format == MPV_FORMAT_NONE) return eventProperty(name)

    val data = property.data ?: return
    when (property.format) {
        MPV_FORMAT_FLAG -> eventProperty(name, data.reinterpret<IntVar>().pointed.value != 0)
        MPV_FORMAT_INT64 -> eventProperty(name, data.reinterpret<LongVar>().pointed.value)
        MPV_FORMAT_DOUBLE -> eventProperty(name, data.reinterpret<DoubleVar>().pointed.value)
        MPV_FORMAT_STRING, MPV_FORMAT_OSD_STRING ->
            data.reinterpret<CPointerVar<ByteVar>>().pointed.value?.toKString()
                ?.let { eventProperty(name, it) }
        MPV_FORMAT_NODE ->
            data.reinterpret<mpv_node>().pointed.toMPVNode()?.let { eventProperty(name, it) }
    }
}
