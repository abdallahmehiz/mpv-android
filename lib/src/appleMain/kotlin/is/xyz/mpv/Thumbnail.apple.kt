@file:OptIn(ExperimentalForeignApi::class)

package `is`.xyz.mpv

import `is`.xyz.mpv.libmpv.MPV_FORMAT_BYTE_ARRAY
import `is`.xyz.mpv.libmpv.MPV_FORMAT_INT64
import `is`.xyz.mpv.libmpv.MPV_FORMAT_NODE_MAP
import `is`.xyz.mpv.libmpv.MPV_FORMAT_STRING
import `is`.xyz.mpv.libmpv.mpv_byte_array
import `is`.xyz.mpv.libmpv.mpv_node
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGInterpolationHigh
import platform.UIKit.UIImage

internal fun MpvHandle.grabThumbnail(dimension: Int): UIImage? =
    commandNode("screenshot-raw", "video") { thumbnailFromScreenshot(it, dimension) }

private fun thumbnailFromScreenshot(result: mpv_node, dimension: Int): UIImage? {
    if (result.format != MPV_FORMAT_NODE_MAP) return null
    val list = result.u.list?.pointed ?: return null

    var w = 0L
    var h = 0L
    var stride = 0L
    var formatOk = false
    var data: mpv_byte_array? = null
    for (i in 0 until list.num) {
        val value = list.values!![i]
        when (list.keys!![i]?.toKString()) {
            "w" -> if (value.format == MPV_FORMAT_INT64) w = value.u.int64
            "h" -> if (value.format == MPV_FORMAT_INT64) h = value.u.int64
            "stride" -> if (value.format == MPV_FORMAT_INT64) stride = value.u.int64
            "format" -> formatOk =
                value.format == MPV_FORMAT_STRING && value.u.string?.toKString() == "bgr0"
            "data" -> if (value.format == MPV_FORMAT_BYTE_ARRAY) data = value.u.ba?.pointed
        }
    }
    val pixels = data?.data?.reinterpret<ByteVar>()
    if (w == 0L || h == 0L || stride == 0L || !formatOk || pixels == null) return null

    // crop to square
    val side = minOf(w, h)
    val cropLeft = (w - side) / 2
    val cropTop = (h - side) / 2
    val cropped = pixels + (cropTop * stride + cropLeft * 4)

    // bgr0 is BGRX in memory, i.e. little endian XRGB
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or kCGBitmapByteOrder32Little
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val source = CGBitmapContextCreate(
        cropped, side.convert(), side.convert(), 8u, stride.convert(), colorSpace, bitmapInfo
    )
    val sourceImage = CGBitmapContextCreateImage(source)

    // scale to appropriate size
    val target = CGBitmapContextCreate(
        null, dimension.convert(), dimension.convert(), 8u, 0u, colorSpace, bitmapInfo
    )
    CGContextSetInterpolationQuality(target, kCGInterpolationHigh)
    CGContextDrawImage(
        target, CGRectMake(0.0, 0.0, dimension.toDouble(), dimension.toDouble()), sourceImage
    )
    val scaled = CGBitmapContextCreateImage(target)
    val image = scaled?.let { UIImage.imageWithCGImage(it) }

    CGImageRelease(scaled)
    CGImageRelease(sourceImage)
    CGContextRelease(target)
    CGContextRelease(source)
    CGColorSpaceRelease(colorSpace)
    return image
}
