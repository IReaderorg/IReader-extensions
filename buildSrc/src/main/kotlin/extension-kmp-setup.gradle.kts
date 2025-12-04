import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

/**
 * KMP Extension Setup
 * 
 * This plugin configures sources to build:
 * - APK (Android)
 * - JAR (JVM/Desktop)
 * - JS (Browser/iOS via JS interop)
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val kmpExtensionList: List<KmpExtension> by extra

android {
    namespace = "ireader.extension"
    compileSdk = Config.compileSdk
    
    defaultConfig {
        minSdk = Config.minSdk
    }
    
    sourceSets {
        named("main") {
            manifest.srcFile("$rootDir/extensions/AndroidManifest.xml")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    // Android target
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                }
            }
        }
    }
    
    // JVM target for Desktop
    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                }
            }
        }
    }
    
    // JS target for Browser/iOS interop
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "${project.name}.js"
            }
            distribution {
                outputDirectory = file("$buildDir/dist/js")
            }
        }
        nodejs()
        binaries.executable()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(project(":annotations"))
                
                // Version Catalog
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                
                // KMP dependencies
                implementation(libs.findLibrary("ksoup").get())
                implementation(libs.findLibrary("ktor-core").get())
                implementation(libs.findLibrary("kotlinx-datetime").get())
                implementation(libs.findLibrary("serialization-json").get())
            }
            kotlin.srcDirs("src/commonMain/kotlin")
        }
        
        val androidMain by getting {
            dependencies {
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                    
                implementation(libs.findLibrary("ktor-okhttp").get())
                implementation(libs.findLibrary("ktor-android").get())
            }
            kotlin.srcDirs("src/androidMain/kotlin")
        }
        
        val desktopMain by getting {
            dependencies {
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                    
                implementation(libs.findLibrary("ktor-cio").get())
            }
            kotlin.srcDirs("src/desktopMain/kotlin")
        }
        
        val jsMain by getting {
            dependencies {
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")
                    
                implementation(libs.findLibrary("ktor-client-js").get())
            }
            kotlin.srcDirs("src/jsMain/kotlin")
        }
    }
}

// KSP configuration
dependencies {
    add("kspCommonMainMetadata", project(":compiler"))
}

// Task to build all outputs
tasks.register("buildAllPlatforms") {
    group = "build"
    description = "Build APK, JAR, and JS outputs"
    
    dependsOn(
        "assembleRelease",           // Android APK
        "desktopJar",                // Desktop JAR
        "jsBrowserDistribution"      // JS bundle
    )
}

// Task to create JAR from JVM compilation
tasks.register<Jar>("desktopJar") {
    group = "build"
    description = "Create JAR for desktop/JVM"
    
    archiveBaseName.set("${project.name}-desktop")
    archiveVersion.set("1.0.0")
    
    from(kotlin.targets.getByName("desktop").compilations.getByName("main").output.allOutputs)
    
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to "1.0.0"
        )
    }
}
