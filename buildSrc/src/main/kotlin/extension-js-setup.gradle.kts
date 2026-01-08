
/**
 * Gradle plugin for building IReader extensions as JS bundles for iOS.
 * This complements the Android extension-setup.gradle.kts for KMP support.
 */

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val extensionList: List<Extension> by extra

kotlin {
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "${project.name}.js"
                output.libraryTarget = "commonjs2"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                // Version Catalog
                val libs = project.extensions.getByType<VersionCatalogsExtension>()
                    .named("libs")

                implementation(libs.findLibrary("ireader-core").get())
                implementation(libs.findLibrary("ksoup").get())
                implementation(libs.findLibrary("ktor-core").get())
                implementation(libs.findLibrary("ktor-client-js").get())
                implementation(libs.findLibrary("kotlinx-datetime").get())
                implementation(libs.findLibrary("ktor-contentNegotiation").get())
                implementation(libs.findLibrary("ktor-serialization").get())

                // Test dependencies for KSP-generated tests
                implementation(libs.findLibrary("kotlin-test").get())
                implementation(libs.findLibrary("kotlin-test-junit").get())
                implementation(libs.findLibrary("coroutines").get())

                implementation(project(":common"))
                implementation(libs.findLibrary("robolectric").get())
                implementation(libs.findLibrary("testRunner").get())
                implementation(libs.findLibrary("extJunit").get())
                implementation(libs.findLibrary("truth").get())
            }

            kotlin.srcDirs(
                "src/commonMain/kotlin",
                "src/jsMain/kotlin"
            )
        }
    }
}

// Task to build all JS extensions
tasks.register("buildJsBundle") {
    group = "build"
    description = "Build JS bundle for iOS"
    dependsOn("jsBrowserProductionWebpack")
}
