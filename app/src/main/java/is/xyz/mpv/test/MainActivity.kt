package `is`.xyz.mpv.test

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val players = mutableMapOf<Int, MPV>()

    fun getOrCreatePlayer(index: Int): MPV {
        return players.getOrPut(index) {
            println("Creating MPV instance for item $index")
            MPV(getApplication<Application>().applicationContext).apply {
                command(
                    "loadfile",
                    "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v"
                )
                setPropertyBoolean("pause", false)
            }
        }
    }

    fun releasePlayer(index: Int) {
        println("Releasing MPV instance for item $index")
        players.remove(index)?.close()
    }

    override fun onCleared() {
        println("Clearing ViewModel and closing all ${players.size} mpv instances")
        players.values.forEach { it.close() }
        players.clear()
    }
}

@Composable
fun App() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(100) { index ->
            VideoItem(index)
        }
    }
}

@Composable
fun VideoItem(index: Int) {
    val viewModel = viewModel<MainViewModel>()
    val mpv = viewModel.getOrCreatePlayer(index)

    DisposableEffect(index) {
        onDispose {
            viewModel.releasePlayer(index)
        }
    }

    AndroidView(
        factory = { context ->
            BaseMPVView(context, null).also {
                it.mpv = mpv
            }
        },
        modifier = Modifier.fillMaxWidth().height(256.dp)
    )
}