/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */


plugins {
  id("com.android.library")
  kotlin("android")
}

android {
namespace = "tachiyomix.defaultres"
  compileSdk = Config.compileSdk

  defaultConfig {
    minSdk = Config.minSdk
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      res.setSrcDirs(listOf("res"))
    }
  }
}

dependencies {
  compileOnly(libs.stdlib)
}
