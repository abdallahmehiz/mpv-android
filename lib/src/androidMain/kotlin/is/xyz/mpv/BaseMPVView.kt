package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

// Contains only the essential code needed to get a picture on the screen

open class BaseMPVView(
    context: Context, attrs: AttributeSet?
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    var mpv: MPV? = null
    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        mpv?.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mpv?.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val mpv = mpv
        if (mpv?.isInitialized != true) return
        Log.w(TAG, "attaching surface")
        mpv.attachSurface(holder.surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", voInUse)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val mpv = mpv
        if (mpv?.isInitialized != true) return
        Log.w(TAG, "detaching surface")
        mpv.setPropertyString("vo", "null")
        mpv.setPropertyString("force-window", "no")
        mpv.detachSurface()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        holder.addCallback(this)
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TAG = "mpv"
    }
}
