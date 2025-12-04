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
tasks.register("buildAllJsSources") {
  group = "build"
  description = "Build all KMP-enabled sources as JS bundles for iOS"
  
  dependsOn(
    subprojects
      .filter { it.path.startsWith(":extensions:") }
      .filter { it.plugins.hasPlugin("extension-js-setup") }
      .map { "${it.path}:jsBrowserProductionWebpack" }
  )
}

tasks.register("packageJsSources") {
  group = "distribution"
  description = "Package all JS sources for distribution"
  
  dependsOn("buildAllJsSources")
  
  doLast {
    val outputDir = file("build/js-dist")
    outputDir.mkdirs()
    
    // Copy all JS bundles
    subprojects
      .filter { it.path.startsWith(":extensions:") }
      .forEach { project ->
        val jsDir = project.file("build/dist/js/productionExecutable")
        if (jsDir.exists()) {
          copy {
            from(jsDir)
            into(outputDir.resolve(project.name))
          }
        }
      }
    
    println("JS sources packaged to: ${outputDir.absolutePath}")
  }
}
