plugins {
  `kotlin-dsl`
  alias(kotlinLibs.plugins.serialization)
}

dependencies {
  implementation(gradleApi())
  implementation(kotlinLibs.gradle)
  implementation(kotlinLibs.serialization.gradle)
  implementation(kotlinLibs.serialization.json)
  implementation(libs.android.gradle)
  implementation(libs.android.sdklib)
  implementation(kotlinLibs.ksp.gradle)
}

kotlin {
  // Add Deps to compilation, so it will become available in main project
  sourceSets.getByName("main").kotlin.srcDir("buildSrc/src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
  kotlinOptions {
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.RequiresOptIn"
    )
  }
}
