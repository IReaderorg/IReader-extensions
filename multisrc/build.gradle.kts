
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = Config.compileSdk
    namespace = "org.ireader.test_extensions"
    defaultConfig {
        minSdk = Config.minSdk
        targetSdk = Config.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        tasks.withType<KotlinJvmCompile> {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}

dependencies {
    val kotlinLibs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("kotlinLibs")
    val libs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("libs")

    compileOnly(libs.findLibrary("ireader-core").get()) { isChanging = true}

    compileOnly(kotlinLibs.findLibrary("stdlib").get())
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

    compileOnly(project(Proj.annotation))
    compileOnly(project(Proj.compiler))
}
