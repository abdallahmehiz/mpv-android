buildscript {
    val kotlinVersion by extra("2.3.0")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    id("com.vanniktech.maven.publish") version "0.32.0"
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

