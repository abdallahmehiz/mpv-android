import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
val universalBase = 8000

version = "0.1.12"
group = "io.github.abdallahmehiz"

android {
    namespace = "is.xyz.mpv"
    compileSdk = 36
    defaultConfig { minSdk = 21 }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
}

tasks.register<Jar>("sourceJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
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

