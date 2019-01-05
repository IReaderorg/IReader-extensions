plugins {
  id("kotlin")
  id("kotlin-kapt")
}

dependencies {
  implementation(Deps.kotlinpoet)
  implementation(Deps.autoservice)
  kapt(Deps.autoservice)

  implementation(Deps.kotlin.stdlib)
  implementation(project(Proj.annotation))
}
