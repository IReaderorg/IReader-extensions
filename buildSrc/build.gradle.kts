plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation("com.android.tools.build:gradle:4.1.1")
  implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.4.10")
}
