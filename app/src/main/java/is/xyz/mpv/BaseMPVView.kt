package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(
    context: Context, attrs: AttributeSet?
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    lateinit var mpv: MPV
    lateinit var configDir: String
    lateinit var cacheDir: String


    protected abstract fun initOptions()
    protected abstract fun postInitOptions()

    protected abstract fun observeProperties()

    private var filePath: String? = null

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        mpv.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        mpv.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        mpv.setOptionString("force-window", "yes")

        if (filePath != null) {
            mpv.command("loadfile", filePath as String)
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            mpv.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        mpv.setPropertyString("vo", "null")
        mpv.setPropertyString("force-window", "no")
        // Note that before calling detachSurface() we need to be sure that libmpv
        // is done using the surface.
        // FIXME: There could be a race condition here, because I don't think
        // setting a property will wait for VO deinit.
        mpv.detachSurface()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mpv.create(context)

        /* set normal options (user-supplied config can override) */
        mpv.setOptionString("config", "yes")
        mpv.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            mpv.setOptionString(opt, cacheDir)
        initOptions()

        /* set hardcoded options */
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        mpv.setOptionString("force-window", "no")
        // need to idle at least once for playFile() logic to work
        mpv.setOptionString("idle", "once")

        holder.addCallback(this)
        observeProperties()
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        mpv.destroy()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TAG = "mpv"
    }
}
