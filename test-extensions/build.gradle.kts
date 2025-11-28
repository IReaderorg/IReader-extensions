plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = Config.compileSdk
    namespace = "ireader.test.extensions"
    defaultConfig {
        minSdk = Config.minSdk
        targetSdk = Config.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

// Extension dependency is configured dynamically by test scripts
// To test manually:
// 1. Uncomment ONE of the lines below (or add your own)
// 2. Run: ./gradlew :test-extensions:test
//
// Example extensions:
// implementation(project(":extensions:v5:en:novelbuddy"))
// implementation(project(":extensions:individual:en:mylovenovel"))

dependencies {
    // Core dependencies - always included
    implementation(project(":multisrc"))
    implementation(libs.bundles.common)
    implementation(libs.bundles.commonTesting)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
    implementation(project(":annotations"))
    implementation(project(":compiler"))
    
    // Extension to test - ADD YOUR EXTENSION HERE
    // The test scripts will automatically configure this
    // For manual testing, uncomment and modify one of these:
    // implementation(project(":extensions:v5:en:novelbuddy"))
    // implementation(project(":extensions:individual:en:mylovenovel"))
}
