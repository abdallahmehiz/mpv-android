@file:OptIn(ExperimentalForeignApi::class)

package `is`.xyz.mpv

import cnames.structs.mpv_handle
import `is`.xyz.mpv.libmpv.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_FLAG
import `is`.xyz.mpv.libmpv.MPV_FORMAT_INT64
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NODE
import `is`.xyz.mpv.libmpv.MPV_FORMAT_STRING
import `is`.xyz.mpv.libmpv.mpv_command
import `is`.xyz.mpv.libmpv.mpv_command_node
import `is`.xyz.mpv.libmpv.mpv_format
import `is`.xyz.mpv.libmpv.mpv_free
import `is`.xyz.mpv.libmpv.mpv_free_node_contents
import `is`.xyz.mpv.libmpv.mpv_get_property
import `is`.xyz.mpv.libmpv.mpv_node
import `is`.xyz.mpv.libmpv.mpv_set_option
import `is`.xyz.mpv.libmpv.mpv_set_property
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value

// The libmpv calls used by MPV, taking care of the native memory.

internal typealias MpvHandle = CPointer<mpv_handle>

internal fun MpvHandle.command(args: Array<out String>): Int = memScoped {
    val argv = allocArray<CPointerVar<ByteVar>>(args.size + 1)
    args.forEachIndexed { i, arg -> argv[i] = arg.cstr.ptr }
    argv[args.size] = null
    mpv_command(this@command, argv)
}

/** Runs a command and passes its result to [block], or returns null if the command failed. */
internal inline fun <R> MpvHandle.commandNode(vararg args: String, block: (mpv_node) -> R): R? =
    memScoped {
        val argv = alloc<mpv_node>()
        MPVNode.ArrayNode(Array(args.size) { MPVNode.StringNode(args[it]) }).writeTo(argv, this)
        val result = alloc<mpv_node>()
        if (mpv_command_node(this@commandNode, argv.ptr, result.ptr) < 0) return null
        try {
            block(result)
        } finally {
            mpv_free_node_contents(result.ptr)
        }
    }

internal fun MpvHandle.setWid(wid: Long) {
    memScoped {
        val value = alloc<LongVar>()
        value.value = wid
        mpv_set_option(this@setWid, "wid", MPV_FORMAT_INT64, value.ptr)
    }
}

internal fun MpvHandle.getLong(name: String): Long? =
    getProperty<LongVar, Long>(name, MPV_FORMAT_INT64) { it.value }

internal fun MpvHandle.setLong(name: String, value: Long) =
    setProperty<LongVar>(name, MPV_FORMAT_INT64) { it.value = value }

internal fun MpvHandle.getDouble(name: String): Double? =
    getProperty<DoubleVar, Double>(name, MPV_FORMAT_DOUBLE) { it.value }

internal fun MpvHandle.setDouble(name: String, value: Double) =
    setProperty<DoubleVar>(name, MPV_FORMAT_DOUBLE) { it.value = value }

internal fun MpvHandle.getFlag(name: String): Boolean? =
    getProperty<IntVar, Boolean>(name, MPV_FORMAT_FLAG) { it.value != 0 }

internal fun MpvHandle.setFlag(name: String, value: Boolean) =
    setProperty<IntVar>(name, MPV_FORMAT_FLAG) { it.value = if (value) 1 else 0 }

internal fun MpvHandle.getString(name: String): String? =
    getProperty<CPointerVar<ByteVar>, String>(name, MPV_FORMAT_STRING) { string ->
        string.value?.toKString().also { mpv_free(string.value) }
    }

internal fun MpvHandle.setString(name: String, value: String) =
    setProperty<CPointerVar<ByteVar>>(name, MPV_FORMAT_STRING) { it.value = value.cstr.ptr }

internal fun MpvHandle.getNode(name: String): MPVNode? =
    getProperty<mpv_node, MPVNode>(name, MPV_FORMAT_NODE) { node ->
        node.toMPVNode().also { mpv_free_node_contents(node.ptr) }
    }

internal fun MpvHandle.setNode(name: String, node: MPVNode) =
    setProperty<mpv_node>(name, MPV_FORMAT_NODE) { node.writeTo(it, this) }

private inline fun <reified T : CVariable, R : Any> MpvHandle.getProperty(
    name: String,
    format: mpv_format,
    read: (T) -> R?,
): R? = memScoped {
    val data = alloc<T>()
    if (mpv_get_property(this@getProperty, name, format, data.ptr) < 0) null else read(data)
}

private inline fun <reified T : CVariable> MpvHandle.setProperty(
    name: String,
    format: mpv_format,
    write: MemScope.(T) -> Unit,
): Int = memScoped {
    val data = alloc<T>()
    write(data)
    mpv_set_property(this@setProperty, name, format, data.ptr)
}
