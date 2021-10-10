plugins {
  `kotlin-dsl`
  kotlin("plugin.serialization") version Deps.kotlin.version
}

repositories {
  google()
  mavenCentral()
}

dependencies {
  implementation(gradleApi())
  implementation(Deps.kotlin.gradle)
  implementation(Deps.kotlin.compilerEmbeddable)
  implementation(Deps.kotlin.serialization.gradle)
  implementation(Deps.kotlin.serialization.json)
  implementation(Deps.android.gradle)
  implementation(Deps.android.sdklib)
}

kotlin {
  // Add Deps to compilation, so it will become available in main project
  sourceSets.getByName("main").kotlin.srcDir("buildSrc/src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
  kotlinOptions {
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.RequiresOptIn",
    )
  }
}
