plugins {
  id("kotlin")
}

dependencies {
  implementation(Deps.kotlinpoet)
  implementation(Deps.ksp.api)

  implementation(project(Proj.annotation))
}
