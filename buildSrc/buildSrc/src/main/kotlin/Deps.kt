@Suppress("ClassName", "MemberVisibilityCanBePrivate")
object Deps {

  object kotlin {
    const val version = "1.5.31"
    const val compilerEmbeddable = "org.jetbrains.kotlin:kotlin-compiler-embeddable:$version"
    const val gradle = "org.jetbrains.kotlin:kotlin-gradle-plugin:$version"
    const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$version"

    object serialization {
      private const val version = "1.3.0"
      const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:$version"
      const val gradle = "org.jetbrains.kotlin:kotlin-serialization:${kotlin.version}"
    }
  }

  object android {
    const val sdklib = "com.android.tools:sdklib:30.0.0"
    const val gradle = "com.android.tools.build:gradle:7.0.2"
  }

  object ksp {
    const val api = "com.google.devtools.ksp:symbol-processing-api:1.5.31-1.0.0"
    const val gradle = "com.google.devtools.ksp:symbol-processing-gradle-plugin:1.5.31-1.0.0"
  }

  const val kotlinpoet = "com.squareup:kotlinpoet-ksp:1.10.1"

  const val tachiyomiCore = "org.tachiyomi:core-jvm:1.2-SNAPSHOT"
  const val tachiyomiSourceApi = "org.tachiyomi:source-api-jvm:1.2-SNAPSHOT"

  const val okhttp = "com.squareup.okhttp3:okhttp:4.10.0-RC1"
  const val jsoup = "org.jsoup:jsoup:1.13.1"

}
