/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */



enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
include(":annotations")
include(":compiler")
include(":deeplink")
include(":defaultRes")
include(":common")
include(":js-sources")

if (System.getenv("CI") == null || System.getenv("CI_MODULE_GEN") == "true") {
    File(rootDir, "sources").eachDir { dir ->
        if (dir.name != "multisrc") {
            dir.eachDir { subDir ->
                if (File(subDir, "build.gradle.kts").exists()) {
                    val name = ":extensions:individual:${dir.name}:${subDir.name}"
                    include(name)
                    project(name).projectDir = File("sources/${dir.name}/${subDir.name}")
                }
            }
        }
    }

    File(rootDir, "sources/multisrc").eachDir { dir ->
        if (File(dir, "build.gradle.kts").exists()) {
            val dirName = ":extensions:multisrc:${dir.name}"
            include(dirName)
            project(dirName).projectDir =
                File("sources/multisrc/${dir.name}")
        }
    }

    // Include sources-v5-batch directory (converted plugins V5)
    File(rootDir, "sources-v5-batch").eachDir { dir ->
        if (dir.name != "multisrc") {
            dir.eachDir { subDir ->
                if (File(subDir, "build.gradle.kts").exists()) {
                    val name = ":extensions:v5:${dir.name}:${subDir.name}"
                    include(name)
                    project(name).projectDir = File("sources-v5-batch/${dir.name}/${subDir.name}")
                }
            }
        }
    }
} else {
    // Running in CI (GitHub Actions)
    val chunkSize = System.getenv("CI_CHUNK_SIZE").toInt()
    val chunk = System.getenv("CI_CHUNK_NUM").toInt()
    File(rootDir, "sources").getChunk(chunk,chunkSize) { dir ->
        if (dir.name != "multisrc") {
            if (File(dir, "build.gradle.kts").exists()) {
                val name = ":extensions:individual:${dir.parentFile.name}:${dir.name}"
                include(name)
                project(name).projectDir = dir
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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2") }
        maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
        // Node.js distribution for Kotlin/JS
        ivy("https://nodejs.org/dist") {
            name = "Node.js Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Yarn distribution for Kotlin/JS
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}
inline fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

fun File.getChunk(chunk: Int, chunkSize: Int,block: (File) -> Unit) {
    return listFiles()
        // Lang folder

        ?.filter { it.isDirectory }
        // Extension subfolders
        ?.mapNotNull { dir -> dir.listFiles()?.filter { it.isDirectory } }
        ?.flatten()
        ?.sortedBy { it.name }
        ?.filterNotNull()!!
        .chunked(chunkSize)[chunk]
        .forEach { block(it) }
}

// Test module - only include when explicitly testing
// To enable: set environment variable ENABLE_TEST_MODULE=true
// Or run: ./gradlew :test-extensions:test (will fail if not configured)
if (System.getenv("ENABLE_TEST_MODULE") == "true" || System.getProperty("enableTestModule") == "true") {
    include(":test-extensions")
}

// Source Test Server - always included for development
include(":source-test-server")

include(":multisrc")
