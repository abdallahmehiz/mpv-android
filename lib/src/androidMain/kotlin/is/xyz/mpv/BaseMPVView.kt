package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File

// Contains only the essential code needed to get a picture on the screen

open class BaseMPVView(
    context: Context, attrs: AttributeSet?
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var hasSurface = false

    /** Created on first use, so a view that never plays starts no player. */
    val mpv by lazy { MPV(context) }

    private var voInUse: String = "gpu"
    private var filePath: String? = null

    /**
     * Applies the config dirs and the hooks below to [mpv].
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        mpv.setConfigDir(configDir)
        mpv.setCacheDir(cacheDir)
        // mpv is initialized on creation, so config-dir alone no longer loads these
        File(configDir, "mpv.conf").takeIf { it.exists() }?.let { mpv.command("load-config-file", it.path) }
        File(configDir, "input.conf").takeIf { it.exists() }?.let { mpv.command("load-input-conf", it.path) }
        initOptions()
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        mpv.setOptionString("force-window", "no")
        observeProperties()
    }

    /**
     * Closes [mpv].
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        holder.removeCallback(this)
        mpv.close()
    }

    protected open fun initOptions() {}
    protected open fun postInitOptions() {}
    protected open fun observeProperties() {}

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) {
        if (hasSurface) mpv.command("loadfile", filePath) else this.filePath = filePath
    }

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
        mpv.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        hasSurface = true
        if (!mpv.isInitialized) return
        Log.w(TAG, "attaching surface")
        mpv.attachSurface(holder.surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", voInUse)
        filePath?.let { mpv.command("loadfile", it) }
        filePath = null
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
        if (!mpv.isInitialized) return
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
