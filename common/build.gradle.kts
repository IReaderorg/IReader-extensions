plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    // JS target disabled for now - enable when needed for iOS/web
    // js(IR) {
    //     browser {
    //         webpackTask {
    //             mainOutputFileName = "common.js"
    //         }
    //     }
    //     binaries.library()
    // }

    sourceSets {
        commonMain.dependencies {
            // Use api for KMP dependencies that need to be available to consumers
            api(libs.ksoup)
            api(libs.ireader.core)
            api(libs.ktor.core)
            api(libs.kotlinx.datetime)
        }

        jvmMain.dependencies {
            compileOnly(libs.ktor.cio)
        }

        // jsMain.dependencies {
        //     api(libs.ktor.client.js)
        // }
    }
}
