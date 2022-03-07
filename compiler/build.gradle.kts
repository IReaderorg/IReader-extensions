plugins {
  kotlin("jvm")
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(kotlinLibs.ksp.api)

  implementation(project(Proj.annotation))
}
