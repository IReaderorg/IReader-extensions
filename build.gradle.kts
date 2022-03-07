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

tasks.create<RepoTask>("repo")
