package `is`.xyz.mpv.test

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.recyclerview.widget.RecyclerView
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.test.databinding.ActivityMainBinding
import `is`.xyz.mpv.test.databinding.ItemVideoBinding

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

class ViewsActivity : ComponentActivity() {
    val viewModel by viewModels<MainViewModel>()
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.adapter = PlayerAdapter(viewModel)
    }
}

class PlayerAdapter(
    val viewModel: MainViewModel
) : RecyclerView.Adapter<PlayerViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemVideoBinding.inflate(layoutInflater, parent, false)
        return PlayerViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PlayerViewHolder) {
        holder.release(holder.bindingAdapterPosition)
    }

    override fun getItemCount(): Int = 100
}

class PlayerViewHolder(
    val binding: ItemVideoBinding,
    val viewModel: MainViewModel
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(index: Int) {
        val player = viewModel.getOrCreatePlayer(index)
        binding.root.mpv = player
    }

    fun release(index: Int) {
        binding.root.mpv = null
        viewModel.releasePlayer(index)
    }
}


// Compose version of the same thing

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(100, key = { it }) { index ->
            VideoItem(index)
        }
    }
}

@Composable
fun VideoItem(index: Int) {
    val viewModel = viewModel<MainViewModel>()

    DisposableEffect(index) {
        onDispose {
            viewModel.releasePlayer(index)
        }
    }

    AndroidView(
        factory = { context ->
            BaseMPVView(context, null).also {
                val mpv = viewModel.getOrCreatePlayer(index)
                it.mpv = mpv
            }
        },
        modifier = Modifier.fillMaxWidth().height(256.dp)
    )
}