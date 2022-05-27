buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(libs.android.gradle)
    classpath(kotlinLibs.gradle)
    classpath(kotlinLibs.serialization.gradle)
  }
}

tasks.register<Delete>("clean") {
  delete(rootProject.buildDir)
}

tasks.create<RepoTask>("repo")
