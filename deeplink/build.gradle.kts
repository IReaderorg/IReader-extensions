plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  compileSdkVersion(Config.compileSdk)

  defaultConfig {
    minSdkVersion(Config.minSdk)
  }

  libraryVariants.all {
    generateBuildConfigProvider?.configure {
      enabled = false
    }
  }
}
