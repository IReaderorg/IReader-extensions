object Deps {

  object kotlin {
    const val version = "1.5.31"
    const val serialization = "org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0"
    const val compilerEmbeddable = "org.jetbrains.kotlin:kotlin-compiler-embeddable:$version"
  }

  object android {
    const val sdklib = "com.android.tools:sdklib:30.0.0"
    const val gradle = "com.android.tools.build:gradle:7.0.2"
  }

  const val autoservice = "com.google.auto.service:auto-service:1.0-rc4"
  const val kotlinpoet = "com.squareup:kotlinpoet:1.0.0"

  const val tachiyomiCore = "org.tachiyomi:core-jvm:1.2-SNAPSHOT"
  const val tachiyomiSourceApi = "org.tachiyomi:source-api-jvm:1.2-SNAPSHOT"

  const val okhttp = "com.squareup.okhttp3:okhttp:4.10.0-RC1"
  const val jsoup = "org.jsoup:jsoup:1.13.1"

}
