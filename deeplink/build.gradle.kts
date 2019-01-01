plugins {
  id("com.android.library")
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
