buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(Deps.android.gradle)
    classpath(Deps.kotlin.gradle)
    classpath(Deps.kotlin.serialization.gradle)
  }
}

allprojects {
  repositories {
    mavenCentral()
    google()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
  }
}

tasks.create<RepoTask>("repo")
