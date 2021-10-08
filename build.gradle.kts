buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:7.0.2")
    classpath(kotlin("gradle-plugin", version = Deps.kotlin.version))
    classpath(kotlin("serialization", version = Deps.kotlin.version))
  }
}

allprojects {
  repositories {
    mavenCentral()
    google()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
  }
}

tasks.register<RepoTask>("repo")
