

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

dependencies {
    // Extension to test - configured automatically by test scripts
    // Uncomment and change for manual testing:
    implementation(project(":extensions:v5:en:chrysanthemumgarden"))
    
    implementation(project(":multisrc"))
    implementation(libs.bundles.common)
    implementation(libs.bundles.commonTesting)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
    implementation(project(":annotations"))
    implementation(project(":compiler"))
}
