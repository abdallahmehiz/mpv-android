@file:OptIn(ExperimentalForeignApi::class)

package `is`.xyz.mpv

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIView

// Contains only the essential code needed to get a picture on the screen

class BaseMPVView(frame: CValue<CGRect>) : UIView(frame) {

    constructor() : this(CGRectMake(0.0, 0.0, 0.0, 0.0))

    val metalLayer = CAMetalLayer()

    /** Created on first use, so a view that never plays starts no player. */
    val mpv by lazy { MPV() }

    private var voInUse: String = "gpu"
    private var attached = false

    init {
        layer.addSublayer(metalLayer)
    }

    /**
     * Sets the VO to use
     * it is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        mpv.setOptionString("vo", vo)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val scale = contentScaleFactor
        metalLayer.frame = bounds
        metalLayer.contentsScale = scale
        bounds.useContents {
            metalLayer.drawableSize = CGSizeMake(size.width * scale, size.height * scale)
        }
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        if (window != null) attach() else detach()
    }

    private fun attach() {
        if (attached || window == null || !mpv.isInitialized) return
        mpv.attachSurface(metalLayer)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", voInUse)
        attached = true
    }

    private fun detach() {
        if (!attached) return
        attached = false
        if (!mpv.isInitialized) return
        mpv.setPropertyString("vo", "null")
        mpv.setPropertyString("force-window", "no")
        mpv.detachSurface()
    }
}
