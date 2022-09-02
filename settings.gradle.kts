/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */


enableFeaturePreview("VERSION_CATALOGS")
include(":annotations")
include(":compiler")
include(":deeplink")
include(":defaultRes")
// include(":sources")
//File(rootDir, "extensions").listFiles().forEach { dir ->
//  if (File(dir, "build.gradle.kts").exists()) {
//    val name = ":ext-${dir.name}"
//    include(name)
//    project(name).projectDir = File("extensions/${dir.name}")
//  }
//}
//
//
//File(rootDir, "sources").eachDir { dir ->
//  dir.eachDir { subdir ->
//    val name = ":extensions:individual:${dir.name}:${subdir.name}"
//    include(name)
//    project(name).projectDir = File("sources/${dir.name}/${subdir.name}")
//  }
//}

File(rootDir, "sources").eachDir { dir ->
    dir.eachDir { subDir ->
        subDir.listFiles()?.forEach { file ->
            if (File(subDir, "build.gradle.kts").exists()) {
                val name = ":extensions:individual:${dir.name}:${subDir.name}"
                include(name)
                project(name).projectDir = File("sources/${dir.name}/${subDir.name}")
            }
        }


    }

}


pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    }
    versionCatalogs {
        create("kotlinLibs") {
            from(files("./gradle/kotlin.versions.toml"))
        }
        create("test") {
            from(files("./gradle/testing.versions.toml"))
        }
    }
}

//include(":sample-single")
//project(":sample-single").projectDir = file("samples/single-site")
//
//include(":sample-single-multilang")
//project(":sample-single-multilang").projectDir = file("samples/single-site-multilang")
//
//include(":sample-multi")
//project(":sample-multi").projectDir = file("samples/multi-site")
//
//include(":sample-multi-srcs")
//project(":sample-multi-srcs").projectDir = file("samples/multi-site-srcs")

inline fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}


include(":test-extensions")
include(":multisrc")
