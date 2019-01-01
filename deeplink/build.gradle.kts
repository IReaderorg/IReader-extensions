plugins {
  id("com.android.library")
}

android {
  compileSdkVersion(Config.compileSdk)

  libraryVariants.all {
    generateBuildConfigProvider?.configure {
      enabled = false
    }
  }
}
