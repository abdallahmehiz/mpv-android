package `is`.xyz.mpv.test

import android.app.Application
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.test.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    class MainViewModel(app: Application) : AndroidViewModel(app) {
        val mpv = MPV(app.applicationContext).also {
            it.command(
                "loadfile",
                "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v"
            )
        }
    }

    val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.mpv = viewModel.mpv
    }

    override fun onPause() {
        super.onPause()
        viewModel.mpv.setPropertyBoolean("pause", true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.mpv.setPropertyBoolean("pause", false)
    }
}