

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = Config.compileSdk
    namespace = "ireader.test_extensions"
    defaultConfig {
        minSdk = Config.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

dependencies {
    val libs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("libs")

    // Core dependencies (KMP-compatible)
    compileOnly(libs.findLibrary("ireader-core").get()) { isChanging = true }
    compileOnly(libs.findLibrary("stdlib").get())
    
    // HTML parsing (KMP)
    compileOnly(libs.findLibrary("ksoup").get())
    
    // Date/Time (KMP)
    compileOnly(libs.findLibrary("kotlinx-datetime").get())
    
    // HTTP client (KMP)
    compileOnly(libs.findLibrary("ktor-core").get())
    compileOnly(libs.findLibrary("ktor-contentNegotiation").get())
    compileOnly(libs.findLibrary("ktor-serialization").get())
    
    // Android-specific Ktor engines
    compileOnly(libs.findLibrary("ktor-cio").get())
    compileOnly(libs.findLibrary("ktor-android").get())
    compileOnly(libs.findLibrary("ktor-okhttp").get())

    compileOnly(project(":annotations"))
    compileOnly(project(":common"))
}
