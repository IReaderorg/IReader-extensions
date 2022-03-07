plugins {
  id("com.android.library")
  kotlin("android")
}

android {
  compileSdk = Config.compileSdk

  defaultConfig {
    minSdk = Config.minSdk
  }
}

dependencies {
  compileOnly(kotlinLibs.stdlib)
}
