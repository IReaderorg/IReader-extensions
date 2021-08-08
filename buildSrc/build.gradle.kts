plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation(gradleApi())
  implementation("com.android.tools.build:gradle:7.0.0")
  implementation("com.android.tools:sdklib:30.0.0")
  implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.5.21")
  implementation("com.google.code.gson:gson:2.8.7")
}
