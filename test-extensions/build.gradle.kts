
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
    //change this name to your extension name
    implementation(project(":extensions:individual:ar:kolnovel"))

    val kotlinLibs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("kotlinLibs")
    val libs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("libs")


    implementation(test.bundles.common)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
    implementation(libs.findLibrary("ireader-core").get()) { isChanging = true}

    implementation(kotlinLibs.findLibrary("stdlib").get())
    implementation(libs.findLibrary("okhttp").get())
    implementation(libs.findLibrary("jsoup").get())
    implementation(libs.findLibrary("ktor-core").get())
    implementation(libs.findLibrary("ktor-cio").get())
    implementation(libs.findLibrary("ktor-android").get())
    implementation(libs.findLibrary("ktor-okhttp").get())
    implementation(libs.findLibrary("ktor-contentNegotiation").get())
    implementation(libs.findLibrary("ktor-serialization").get())
    implementation(libs.findLibrary("ktor-gson").get())
    implementation(libs.findLibrary("ktor-jackson").get())

    implementation(project(Proj.annotation))
    implementation(project(Proj.compiler))
}