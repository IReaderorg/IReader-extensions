/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
plugins {
  id("com.diffplug.spotless") version "6.6.1"
}
spotless {
  kotlin {
    ktlint(libs.versions.ktlint.get())
      .userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    target("**/*.kt")
    targetExclude("$buildDir/**/*.kt")
    targetExclude("bin/**/*.kt")
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint()
  }
}

buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(libs.android.gradle)
    classpath(kotlinLibs.gradle)
    classpath(kotlinLibs.serialization.gradle)
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
  }
}
tasks.register("delete", Delete::class) {
  delete(rootProject.buildDir)
}

//tasks.register<Delete>("clean") {
//  delete(rootProject.buildDir)
//}

tasks.create<RepoTask>("repo")
