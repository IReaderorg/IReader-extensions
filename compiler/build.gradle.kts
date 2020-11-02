plugins {
  id("kotlin")
  id("kotlin-kapt")
}

dependencies {
  implementation(Deps.kotlinpoet)
  implementation(Deps.autoservice)
  kapt(Deps.autoservice)

  implementation(project(Proj.annotation))
}
