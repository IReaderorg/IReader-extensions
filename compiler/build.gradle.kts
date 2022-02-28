

plugins {
  id("kotlin")
}

dependencies {
  implementation(Deps.kotlinpoet)
  implementation(Deps.ksp.api)
  implementation(Deps.okhttp)
  implementation(project(Proj.annotation))
}
