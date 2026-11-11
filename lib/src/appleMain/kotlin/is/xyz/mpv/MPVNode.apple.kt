@file:OptIn(ExperimentalForeignApi::class)

package `is`.xyz.mpv

import `is`.xyz.mpv.libmpv.MPV_FORMAT_BYTE_ARRAY
import `is`.xyz.mpv.libmpv.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_FLAG
import `is`.xyz.mpv.libmpv.MPV_FORMAT_INT64
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NODE_ARRAY
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NODE_MAP
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NONE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_OSD_STRING
import `is`.xyz.mpv.libmpv.MPV_FORMAT_STRING
import `is`.xyz.mpv.libmpv.mpv_byte_array
import `is`.xyz.mpv.libmpv.mpv_node
import `is`.xyz.mpv.libmpv.mpv_node_list
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString

// Convert native->kotlin mpv node and vice versa, similar to the jni implementation

internal fun mpv_node.toMPVNode(): MPVNode? = when (format) {
    MPV_FORMAT_NONE -> MPVNode.None
    MPV_FORMAT_STRING, MPV_FORMAT_OSD_STRING -> MPVNode.StringNode(u.string?.toKString().orEmpty())
    MPV_FORMAT_FLAG -> MPVNode.BooleanNode(u.flag != 0)
    MPV_FORMAT_INT64 -> MPVNode.IntNode(u.int64)
    MPV_FORMAT_DOUBLE -> MPVNode.DoubleNode(u.double_)
    MPV_FORMAT_BYTE_ARRAY -> {
        val ba = u.ba?.pointed
        val size = ba?.size?.toInt() ?: 0
        MPVNode.ByteArrayNode(if (size > 0) ba!!.data!!.readBytes(size) else ByteArray(0))
    }
    MPV_FORMAT_NODE_ARRAY -> {
        val list = u.list?.pointed
        MPVNode.ArrayNode(Array(list?.num ?: 0) { list!!.values!![it].toMPVNode() ?: MPVNode.None })
    }
    MPV_FORMAT_NODE_MAP -> {
        val list = u.list?.pointed
        val map = LinkedHashMap<String, MPVNode>()
        for (i in 0 until (list?.num ?: 0)) {
            val key = list!!.keys!![i]?.toKString() ?: continue
            list.values!![i].toMPVNode()?.let { map[key] = it }
        }
        MPVNode.MapNode(map)
    }
    else -> null
}

internal fun MPVNode.writeTo(node: mpv_node, scope: AutofreeScope) {
    when (this) {
        is MPVNode.None -> node.format = MPV_FORMAT_NONE
        is MPVNode.StringNode -> {
            node.format = MPV_FORMAT_STRING
            node.u.string = value.cstr.getPointer(scope)
        }
        is MPVNode.BooleanNode -> {
            node.format = MPV_FORMAT_FLAG
            node.u.flag = if (value) 1 else 0
        }
        is MPVNode.IntNode -> {
            node.format = MPV_FORMAT_INT64
            node.u.int64 = value
        }
        is MPVNode.DoubleNode -> {
            node.format = MPV_FORMAT_DOUBLE
            node.u.double_ = value
        }
        is MPVNode.ByteArrayNode -> {
            val ba = scope.alloc<mpv_byte_array>()
            ba.data = if (value.isNotEmpty()) value.toCValues().getPointer(scope) else null
            ba.size = value.size.toULong()
            node.format = MPV_FORMAT_BYTE_ARRAY
            node.u.ba = ba.ptr
        }
        is MPVNode.ArrayNode -> {
            val list = scope.alloc<mpv_node_list>()
            list.num = value.size
            list.keys = null
            list.values = if (value.isNotEmpty()) scope.allocArray<mpv_node>(value.size) else null
            value.forEachIndexed { i, child -> child.writeTo(list.values!![i], scope) }
            node.format = MPV_FORMAT_NODE_ARRAY
            node.u.list = list.ptr
        }
        is MPVNode.MapNode -> {
            val list = scope.alloc<mpv_node_list>()
            list.num = value.size
            if (value.isNotEmpty()) {
                val keys = scope.allocArray<CPointerVar<ByteVar>>(value.size)
                val values = scope.allocArray<mpv_node>(value.size)
                value.entries.forEachIndexed { i, (key, child) ->
                    keys[i] = key.cstr.getPointer(scope)
                    child.writeTo(values[i], scope)
                }
                list.keys = keys
                list.values = values
            } else {
                list.keys = null
                list.values = null
            }
            node.format = MPV_FORMAT_NODE_MAP
            node.u.list = list.ptr
        }
    }
}
