@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION") // OpenGL ES and GLKit are deprecated, but still the simplest way to render

package `is`.xyz.mpv

import cnames.structs.mpv_render_context
import `is`.xyz.mpv.libmpv.MPV_RENDER_API_TYPE_OPENGL
import `is`.xyz.mpv.libmpv.MPV_RENDER_PARAM_API_TYPE
import `is`.xyz.mpv.libmpv.MPV_RENDER_PARAM_FLIP_Y
import `is`.xyz.mpv.libmpv.MPV_RENDER_PARAM_INVALID
import `is`.xyz.mpv.libmpv.MPV_RENDER_PARAM_OPENGL_FBO
import `is`.xyz.mpv.libmpv.MPV_RENDER_PARAM_OPENGL_INIT_PARAMS
import `is`.xyz.mpv.libmpv.MPV_RENDER_UPDATE_FRAME
import `is`.xyz.mpv.libmpv.mpv_opengl_fbo
import `is`.xyz.mpv.libmpv.mpv_opengl_init_params
import `is`.xyz.mpv.libmpv.mpv_render_context_create
import `is`.xyz.mpv.libmpv.mpv_render_context_free
import `is`.xyz.mpv.libmpv.mpv_render_context_render
import `is`.xyz.mpv.libmpv.mpv_render_context_set_update_callback
import `is`.xyz.mpv.libmpv.mpv_render_context_update
import `is`.xyz.mpv.libmpv.mpv_render_param
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.EAGL.EAGLContext
import platform.EAGL.kEAGLRenderingAPIOpenGLES3
import platform.GLKit.GLKView
import platform.GLKit.GLKViewDrawableColorFormatRGBA8888
import platform.UIKit.UIViewContentMode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.gles3.GL_COLOR_BUFFER_BIT
import platform.gles3.GL_FRAMEBUFFER_BINDING
import platform.gles3.glClear
import platform.gles3.glClearColor
import platform.gles3.glGetIntegerv
import platform.posix.RTLD_LAZY
import platform.posix.dlopen
import platform.posix.dlsym

class MPVGLView(frame: CValue<CGRect>) :
    GLKView(frame, EAGLContext(kEAGLRenderingAPIOpenGLES3)) {

    constructor() : this(CGRectMake(0.0, 0.0, 0.0, 0.0))

    var mpv: MPV? = null
        set(value) {
            if (field === value) return
            detach()
            field = value
            attach()
        }

    private var renderContext: CPointer<mpv_render_context>? = null
    private var selfRef: StableRef<MPVGLView>? = null

    init {
        drawableColorFormat = GLKViewDrawableColorFormatRGBA8888
        enableSetNeedsDisplay = true
        contentMode = UIViewContentMode.UIViewContentModeRedraw
    }

    private fun attach() {
        val mpv = mpv
        if (renderContext != null || mpv?.isInitialized != true) return
        EAGLContext.setCurrentContext(context)

        renderContext = memScoped {
            val initParams = alloc<mpv_opengl_init_params>()
            initParams.get_proc_address = getProcAddress
            initParams.get_proc_address_ctx = null
            val params = renderParams(
                MPV_RENDER_PARAM_API_TYPE to MPV_RENDER_API_TYPE_OPENGL.cstr.ptr,
                MPV_RENDER_PARAM_OPENGL_INIT_PARAMS to initParams.ptr,
            )
            val result = alloc<CPointerVar<mpv_render_context>>()
            check(mpv_render_context_create(result.ptr, mpv.handle, params) >= 0) {
                "failed to create the mpv render context"
            }
            result.value
        }

        val ref = StableRef.create(this)
        selfRef = ref
        mpv_render_context_set_update_callback(renderContext, onUpdate, ref.asCPointer())
        mpv.setPropertyString("vo", "libmpv")
    }

    private fun detach() {
        val renderContext = renderContext ?: return
        mpv?.takeIf { it.isInitialized }?.setPropertyString("vo", "null")

        EAGLContext.setCurrentContext(context)
        // after this returns the update callback can't be running anymore
        mpv_render_context_set_update_callback(renderContext, null, null)
        mpv_render_context_free(renderContext)
        this.renderContext = null
        selfRef?.dispose()
        selfRef = null
        setNeedsDisplay()
    }

    internal fun update() {
        val renderContext = renderContext ?: return
        if (mpv_render_context_update(renderContext) and MPV_RENDER_UPDATE_FRAME.toULong() != 0uL)
            setNeedsDisplay()
    }

    override fun drawRect(rect: CValue<CGRect>) {
        val renderContext = renderContext
        if (renderContext == null) {
            glClearColor(0f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT.toUInt())
            return
        }
        memScoped {
            val binding = alloc<IntVar>()
            glGetIntegerv(GL_FRAMEBUFFER_BINDING.toUInt(), binding.ptr)
            val fbo = alloc<mpv_opengl_fbo>()
            fbo.fbo = binding.value
            fbo.w = drawableWidth.toInt()
            fbo.h = drawableHeight.toInt()
            fbo.internal_format = 0
            val flipY = alloc<IntVar>()
            flipY.value = 1
            mpv_render_context_render(
                renderContext,
                renderParams(MPV_RENDER_PARAM_OPENGL_FBO to fbo.ptr, MPV_RENDER_PARAM_FLIP_Y to flipY.ptr)
            )
        }
    }

}

private val openGLES = dlopen("/System/Library/Frameworks/OpenGLES.framework/OpenGLES", RTLD_LAZY)

private val getProcAddress = staticCFunction { _: COpaquePointer?, name: CPointer<ByteVar>? ->
    dlsym(openGLES, name?.toKString())
}

private val onUpdate = staticCFunction { arg: COpaquePointer? ->
    val view = arg!!.asStableRef<MPVGLView>().get()
    dispatch_async(dispatch_get_main_queue()) { view.update() }
}

private fun MemScope.renderParams(vararg params: Pair<UInt, COpaquePointer?>): CPointer<mpv_render_param> {
    val array = allocArray<mpv_render_param>(params.size + 1)
    params.forEachIndexed { i, (type, data) ->
        array[i].type = type
        array[i].data = data
    }
    array[params.size].type = MPV_RENDER_PARAM_INVALID
    array[params.size].data = null
    return array
}
