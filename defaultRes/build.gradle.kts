plugins {
  id("com.android.library")
}

android {
  compileSdkVersion(Config.compileSdk)

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      res.setSrcDirs(listOf("res"))
    }
  }

  libraryVariants.all {
    generateBuildConfigProvider?.configure {
      enabled = false
    }
  }
}
