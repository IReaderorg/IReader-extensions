plugins {
  `kotlin-dsl`
}

repositories {
  google()
  jcenter()
}

dependencies {
  // Upgrading this version any further breaks our gradle task reorder to run kapt before
  // processing the manifest file (no known fix yet)
  implementation("com.android.tools.build:gradle:3.5.3")
}
