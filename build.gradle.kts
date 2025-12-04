/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(libs.android.gradle)
    classpath(libs.gradle)
    classpath(libs.serialization.gradle)
  }
}
tasks.register("delete", Delete::class) {
  delete(rootProject.buildDir)
}
tasks.create<RepoTask>("repo")

// Source ID management tasks
registerSourceIdTasks()

// JS build tasks for iOS support
// The js-sources module compiles all enabled sources to JS for iOS
tasks.register("buildJsSources") {
  group = "build"
  description = "Build JS bundles for iOS (uses js-sources module)"
  dependsOn(":js-sources:createSourceIndex")
}

// Make repo task also build JS sources
tasks.named<RepoTask>("repo") {
  dependsOn(":js-sources:createSourceIndex")
}
