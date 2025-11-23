

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

    compileOnly(libs.findLibrary("ireader-core").get()) { isChanging = true}

    compileOnly(libs.findLibrary("stdlib").get())
    compileOnly(libs.findLibrary("okhttp").get())
    compileOnly(libs.findLibrary("jsoup").get())
    compileOnly(libs.findLibrary("ktor-core").get())
    compileOnly(libs.findLibrary("ktor-cio").get())
    compileOnly(libs.findLibrary("ktor-android").get())
    compileOnly(libs.findLibrary("ktor-okhttp").get())
    compileOnly(libs.findLibrary("ktor-contentNegotiation").get())
    compileOnly(libs.findLibrary("ktor-serialization").get())
    compileOnly(libs.findLibrary("ktor-gson").get())
    compileOnly(libs.findLibrary("ktor-jackson").get())

    compileOnly(project(":annotations"))
    compileOnly(project(":common"))
}
