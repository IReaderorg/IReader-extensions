

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
    //change this name to your extension name
    //implementation(project(":extensions:individual:en:mylovenovel"))
    implementation(project(":multisrc"))
    implementation(libs.bundles.common)
    implementation(libs.bundles.commonTesting)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
    implementation(project(Proj.annotation))
    implementation(project(Proj.compiler))
}
