package `is`.xyz.mpv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

@Suppress("unused")
object Utils {
    private fun copyAssetFile(assetManager: AssetManager, filename: String, outFile: File): Boolean {
        var ins: InputStream? = null
        var out: OutputStream? = null
        try {
            ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
            // Note that .available() will return the full file size for asset streams, and it even works
            // for compressed assets. Though none of this is documented...
            val avail = ins.available().toLong()
            if (outFile.length() == avail) {
                Log.v(TAG, "Skipping copy of asset file (exists same size): $filename")
                return true
            }
            out = FileOutputStream(outFile)
            ins.copyTo(out)
            Log.w(TAG, "Copied asset file ($avail bytes): $filename")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset file: $filename", e)
            return false
        } finally {
            out?.close()
            ins?.close()
        }
        return true
    }

    fun copyAssets(context: Context) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        val configDir = context.filesDir.path

        for (name in files) {
            copyAssetFile(assetManager, name, File("$configDir/$name"))
        }
    }

    fun findRealPath(fd: Int): String? {
        var ins: InputStream? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Double check that we can read it
                ins = FileInputStream(path)
                ins.read()
                return path
            }
        } catch(e: Exception) { } finally { ins?.close() }
        return null
    }

    fun convertDp(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.resources.displayMetrics).toInt()
    }

    fun prettyTime(d: Int, sign: Boolean = false): String {
        if (sign)
            return (if (d >= 0) "+" else "-") + prettyTime(abs(d))

        val hours = d / 3600
        val minutes = d % 3600 / 60
        val seconds = d % 60
        if (hours == 0)
            return "%02d:%02d".format(minutes, seconds)
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun getScreenBrightness(activity: Activity): Float? {
        // check if window has brightness set
        val lp = activity.window.attributes
        if (lp.screenBrightness >= 0f)
            return lp.screenBrightness

        // read system pref: https://stackoverflow.com/questions/4544967/#answer-8114307
        // (doesn't work with auto-brightness mode)
        val resolver = activity.contentResolver
        return try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Settings.SettingNotFoundException) {
            null
        }
    }

    data class StoragePath(val path: File, val description: String)

    @SuppressLint("NewApi")
    fun getStorageVolumes(context: Context): List<StoragePath> {
        val list = mutableListOf<StoragePath>()
        assert(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val candidates = mutableListOf<String>()
        // check all media dirs, there's usually one on each storage volume
        context.getExternalFilesDirs(null).forEach {
            if (it != null)
                candidates.add(it.absolutePath)
        }
        // go on a journey to find other mounts Google doesn't want us to find
        File("/proc/mounts").forEachLine { line ->
            val path = line.split(' ')[1]
            if (path.startsWith("/proc") || path.startsWith("/sys") ||
                path.startsWith("/dev") || path.startsWith("/apex")
            )
                return@forEachLine
            candidates.add(path)
        }

        val wrapGetStorageVolume = { it: File ->
            try {
                storageManager.getStorageVolume(it)
            } catch (e: SecurityException) { null }
        }

        for (path in candidates) {
            var root = File(path)
            val vol = wrapGetStorageVolume(root) ?: continue
            if (vol.state != Environment.MEDIA_MOUNTED && vol.state != Environment.MEDIA_MOUNTED_READ_ONLY)
                continue

            // find the actual root path of that volume
            while (true) {
                val parent = root.parentFile
                if (parent == null || wrapGetStorageVolume(parent) != vol)
                    break
                root = parent
            }

            if (!list.any { it.path == root })
                list.add(StoragePath(root, vol.getDescription(context)))
        }
        return list
    }

    fun viewGroupMove(from: ViewGroup, id: Int, to: ViewGroup, toIndex: Int) {
        val view: View? = (0 until from.childCount)
                .map { from.getChildAt(it) }.firstOrNull { it.id == id }
        if (view == null)
            error("$from does not have child with id=$id")
        from.removeView(view)
        to.addView(view, if (toIndex >= 0) toIndex else (to.childCount + 1 + toIndex))
    }

    fun viewGroupReorder(group: ViewGroup, idOrder: Array<Int>) {
        val m = mutableMapOf<Int, View>()
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            m[c.id] = c
        }
        group.removeAllViews()
        // Re-add children in specified order and unhide
        for (id in idOrder) {
            val c = m.remove(id) ?: error("$group did not have child with id=$id")
            c.visibility = View.VISIBLE
            group.addView(c)
        }
        // Keep unspecified children but hide them
        for (c in m.values) {
            c.visibility = View.GONE
            group.addView(c)
        }
    }

    fun fileBasename(str: String): String {
        val isURL = str.indexOf("://") != -1
        val last = str.replaceBeforeLast('/', "").trimStart('/')
        return if (isURL)
            Uri.decode(last.replaceAfter('?', "").trimEnd('?'))
        else
            last
    }

    fun visibleChildren(view: View): Int {
        if (view is ViewGroup && view.isVisible) {
            return (0 until view.childCount).sumOf { visibleChildren(view.getChildAt(it)) }
        }
        return if (view.isVisible) 1 else 0
    }

    class AudioMetadata {
        var mediaTitle: String? = null
            private set
        var mediaArtist: String? = null
            private set
        var mediaAlbum: String? = null
            private set

        fun readAll(mpvInstance: MPV) {
            mediaTitle = mpvInstance.prop["media-title"]
            update("metadata", mpvInstance) // read artist & album
        }

        /** callback for properties of type <code>MPV_FORMAT_NONE</code> */
        fun update(property: String, mpvInstance: MPV): Boolean {
            // TODO?: maybe one day this could natively handle a MPV_FORMAT_NODE_MAP
            if (property == "metadata") {
                // If we observe individual keys libmpv won't notify us once they become
                // unavailable, so we observe "metadata" and read both keys on trigger.
                mediaArtist = mpvInstance.prop["metadata/by-key/Artist"]
                mediaAlbum = mpvInstance.prop["metadata/by-key/Album"]
                return true
            }
            return false
        }

        /** callback for properties of type <code>MPV_FORMAT_STRING</code> */
        fun update(property: String, value: String): Boolean {
            when (property) {
                "media-title" -> mediaTitle = value
                else -> return false
            }
            return true
        }

        fun formatTitle(): String? = if (!mediaTitle.isNullOrEmpty()) mediaTitle else null

        fun formatArtistAlbum(): String? {
            val artistEmpty = mediaArtist.isNullOrEmpty()
            val albumEmpty = mediaAlbum.isNullOrEmpty()
            return when {
                !artistEmpty && !albumEmpty -> "$mediaArtist / $mediaAlbum"
                !artistEmpty -> mediaAlbum
                !albumEmpty -> mediaArtist
                else -> null
            }
        }
    }

    inline fun <reified T: Parcelable> getParcelableArray(bundle: Bundle, key: String): Array<T> {
        val array = BundleCompat.getParcelableArray(bundle, key, T::class.java)
        return if (array == null)
            emptyArray()
        else // the result is not T[] nor castable because BundleCompat is stupid
            array.mapNotNull { it as? T }.toTypedArray()
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    fun isXLargeTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
    }

    /**
     * Sets the inset listener for the given view so that system bars are simply avoided by padding.
     * Note that this will modify the view's padding and probably leave ugly empty space at the top
     * (if using an action bar).
     */
    fun handleInsetsAsPadding(view: View) {
        val orig = listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                orig[0] + i.left,
                orig[1] + i.top,
                orig[2] + i.right,
                orig[3] + i.bottom
            )
            insets
        }
    }

    private const val TAG = "mpv"

    // This is used to filter files in the file picker, so it contains just about everything
    // FFmpeg/mpv could possibly read
    val MEDIA_EXTENSIONS = setOf(
            /* Playlist */
            "cue", "m3u", "m3u8", "pls", "vlc",

            /* Audio */
            "3ga", "3ga2", "a52", "aac", "ac3", "ac4", "adt", "adts", "aif", "aifc", "aiff", "alac",
            "amr", "ape", "au", "awb", "dsf", "dts", "dts-hd", "dtshd", "eac3", "f4a", "flac",
            "lc3", "lpcm", "m1a", "m2a", "m4a", "mka", "mlp", "mp+", "mp1", "mp2", "mp3",
            "mpa", "mpc", "mpga", "mpp", "oga", "ogg", "opus", "pcm", "qoa", "ra", "ram", "rax",
            "shn", "snd", "spx", "tak", "thd", "thd+ac3", "true-hd", "truehd", "tta", "wav", "weba",
            "wma", "wv", "wvp",

            /* Video / Container */
            "264", "265", "266", "3g2", "3ga", "3gp", "3gp2", "3gpp", "3gpp2", "amr", "asf",
            "asx", "av1", "avc", "avf", "avi", "bdm", "bdmv", "clpi", "cpi", "divx", "dv", "evo",
            "evob", "f4v", "flc", "fli", "flic", "flv", "gxf", "h264", "h265", "h266", "hdmov",
            "hdv", "hevc", "lrv", "m1u", "m1v", "m2t", "m2ts", "m2v", "m4u", "m4v", "mk3d", "mkv",
            "mj2", "mov", "mp2", "mp2v", "mp4", "mp4v", "mpe", "mpeg", "mpeg2", "mpeg4", "mpg",
            "mpg4", "mpl", "mpv", "mpv2", "mts", "mtv", "mxf", "mxu", "nsv", "nut", "ogg", "ogm",
            "ogv", "ogx", "qt", "qtvr", "rm", "rmj", "rmm", "rms", "rmvb", "rmx", "rv", "rvx",
            "sdp", "tod", "trp", "ts", "tsa", "tsv", "tts", "vc1", "vfw", "vob", "vro", "vvc",
            "webm", "wm", "wmv", "wmx", "x264", "x265", "xvid", "y4m", "yuv",

            /* Picture */
            "apng", "avif", "bmp", "exr", "gif", "heic", "heif", "j2c", "j2k", "jfif", "jp2", "jpc",
            "jpe", "jpeg", "jpg", "jpg2", "png", "qoi", "tga", "tif", "tiff", "webp",
    )

    val PROTOCOLS = setOf(
        "file", "content", "http", "https", "data", "ftp",
        "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
    )

    data class Versions(
        val mpv: String,
        val buildDate: String,
        val libPlacebo: String,
        val ffmpeg: String,
    )

    val VERSIONS = Versions(
        mpv = "%MPV_VERSION%",
        buildDate = "%DATE%",
        libPlacebo = "%LIBPLACEBO_VERSION%",
        ffmpeg = "%FFMPEG_VERSION%",
    )
}
