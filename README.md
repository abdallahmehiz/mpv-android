# mpv-android-lib

[![Build Status](https://github.com/abdallahmehiz/mpv-android/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/abdallahmehiz/mpv-android/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.abdallahmehiz/mpv-android-lib.svg)](https://central.sonatype.com/artifact/io.github.abdallahmehiz/mpv-android-lib)

A library version of [mpv-android](https://github.com/mpv-android/mpv-android), providing [libmpv](https://github.com/mpv-player/mpv) for Android applications.
Initially made for [mpvKt](https://github.com/abdallahmehiz/mpvKt).

## "New" Features

* **Multiple MPV instances**
* **`mpv_node` support**
* **DASH support** 

## Installation

Add the dependency to your `build.gradle`:

```groovy
dependencies {
    implementation "io.github.abdallahmehiz:mpv-android-lib:<version>"
}
```

## Getting Started

### Using BaseMPVView

The simplest way to extend `BaseMPVView`:

```kotlin
class MyPlayerView(context: Context, attrs: AttributeSet?) : BaseMPVView(context, attrs) {

    override fun initOptions() {
        // Set options before mpv.init() is called
        mpv.setOptionString("hwdec", "auto")
    }

    override fun postInitOptions() {
        // Set options after mpv.init() is called
        mpv.setOptionString("sub-auto", "fuzzy")
    }
}

val playerView = MyPlayerView(context, null)
playerView.initialize(configDir = filesDir.path, cacheDir = cacheDir.path)
playerView.playFile("/path/to/video.mp4")
```

### Using MPV() Directly

You can also use `MPV()` then attach to fully control your mpv instance.

```kotlin
val mpv = MPV()

mpv.create(context)
mpv.setOptionString("config", "yes")
mpv.init()

// Attach to a view surface
mpv.attachSurface(surface)

// Load and play a file
mpv.command("loadfile", "/path/to/video.mp4")

// Access props
val paused: Boolean? = mpv.prop["pause"]
mpv.prop["pause"] = false

// access and set nodes
val node: MPVNode? = mpv.getPropertyNode("track-list")
mpv.setPropertyNode("chapter-list", myCustomChaptersList)

// observe as kotlin flows
val pauseState: StateFlow<Boolean?> = mpv.propFlow("pause")
val volume: StateFlow<Int> = mpv.propFlow("volume", default = 100)

// cleanup
mpv.detachSurface()
mpv.destroy()
```

### Multiple Instances

Each `MPV()` or `BaseMPVView` instance is independent:

```kotlin
val player1 = MPV()
val player2 = MPV()

player1.create(context)
player2.create(context)

// Each player can play different content simultaneously
```

## Building from source

Take a look at the [README](buildscripts/README.md) inside the `buildscripts` directory.

Some other documentation can be found at this [link](http://mpv-android.github.io/mpv-android/).
