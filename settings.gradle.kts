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


pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        //  mavenLocal()
        mavenCentral()
        google()
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2") }
        maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    }
}
inline fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
include(":test-extensions")
include(":multisrc")
