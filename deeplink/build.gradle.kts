plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  compileSdk = Config.compileSdk

  defaultConfig {
    minSdk = Config.minSdk
  }
}

dependencies {
  compileOnly(Deps.kotlin.stdlib)
}
