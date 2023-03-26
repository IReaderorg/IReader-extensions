plugins {
  `kotlin-dsl`
  alias(libs.plugins.serialization)
}

dependencies {
  implementation(gradleApi())
  implementation(libs.gradle)
  implementation(libs.serialization.gradle)
  implementation(libs.serialization.json)
  implementation(libs.android.gradle)
  implementation(libs.android.sdklib)
  implementation(libs.ksp.gradle)
  implementation(libs.dex2jar.translator)
  implementation(libs.dex2jar.tools)
}

kotlin {
  // Add Deps to compilation, so it will become available in main project
  sourceSets.getByName("main").kotlin.srcDir("buildSrc/src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
  kotlinOptions {
    freeCompilerArgs += listOf(
      "-opt-in=org.mylibrary.OptInAnnotation"
    )
  }
}
