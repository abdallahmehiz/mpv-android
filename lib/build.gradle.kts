import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.vanniktech.maven.publish")
}

version = "0.1.12"
group = "io.github.abdallahmehiz"

// libmpv's public headers, from the mpv checkout of buildscripts
val mpvHeaders = rootProject.file("buildscripts/deps/mpv/include")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
    doFirst {
        check(mpvHeaders.resolve("mpv/client.h").isFile) {
            "libmpv headers not found in $mpvHeaders, run buildscripts/download.sh first"
        }
    }
}

// ffmpeg's TLS (mbedtls) can't use the system trust store, so libmpv needs a CA bundle. Android ships
// it as an asset, but Apple apps don't get resources from a klib: embed the same file as a string.
val generateAppleCACert by tasks.registering {
    val input = file("src/androidMain/assets/cacert.pem")
    val outputDir = layout.buildDirectory.dir("generated/cacert/appleMain/kotlin")
    inputs.file(input)
    outputs.dir(outputDir)
    doLast {
        val quotes = "\"\"\""
        // split up, string constants are limited to 64K
        val chunks = input.readText().chunked(16_384).joinToString(",\n") { chunk ->
            quotes + chunk.replace("$", "\${'$'}") + quotes
        }
        val file = outputDir.get().file("is/xyz/mpv/CACert.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("// Generated from ${input.name} by the generateAppleCACert task, do not edit.")
            appendLine("package `is`.xyz.mpv")
            appendLine()
            appendLine("internal val CA_CERT: String = arrayOf(")
            appendLine(chunks)
            appendLine(").joinToString(\"\")")
        })
    }
}

kotlin {
    android {
        namespace = "is.xyz.mpv"
        compileSdk = 36
        minSdk = 24

        // ships cacert.pem and subfont.ttf from src/androidMain/assets
        androidResources { enable = true }

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.compilations.getByName("main").cinterops.create("mpv") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/mpv.def"))
            includeDirs(mpvHeaders)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
        }
        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("androidx.appcompat:appcompat:1.7.1")
        }
        appleMain {
            kotlin.srcDir(generateAppleCACert)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // only Maven Central needs signatures (CI passes the key), local publishing works without one
    if (providers.gradleProperty("signing.keyId").isPresent) signAllPublications()
    coordinates(
        group.toString(),
        "mpv-android-lib",
        version.toString()
    )
    pom {
        name.set("mpv Android library")
        description.set("The mpv library used by mpvKt.")
        inceptionYear.set("2024")
        url.set("https://github.com/abdallahmehiz/mpv-android/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit/")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("abdallahmehiz")
                name.set("Abdallah Mehiz")
                url.set("https://github.com/abdallahmehiz/")
            }
        }
        scm {
            url.set("https://github.com/abdallahmehiz/mpv-android/")
            connection.set("scm:git:git://github.com/abdallahmehiz/mpv-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/abdallahmehiz/mpv-android.git")
        }
    }
}
