buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(Deps.android.gradle)
    classpath(Deps.kotlin.gradle)
    classpath(Deps.kotlin.serialization.gradle)
      classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
  }
}

allprojects {
  repositories {
    mavenCentral()
    google()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
  }
}

tasks.register<Delete>("clean") {
  delete(rootProject.buildDir)
}

tasks.create<RepoTask>("repo")
