@Suppress("ClassName", "MemberVisibilityCanBePrivate")
object Deps {

  object kotlin {
    const val version = "1.6.10"
    const val gradle = "org.jetbrains.kotlin:kotlin-gradle-plugin:$version"
    const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$version"

    object serialization {
      private const val version = "1.3.2"
      const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:$version"
      const val gradle = "org.jetbrains.kotlin:kotlin-serialization:${kotlin.version}"
    }
  }

  object android {
    const val sdklib = "com.android.tools:sdklib:30.0.4"
    const val gradle = "com.android.tools.build:gradle:7.0.4"
  }

  object ksp {
    private const val version = "1.6.10-1.0.2"
    const val api = "com.google.devtools.ksp:symbol-processing-api:$version"
    const val gradle = "com.google.devtools.ksp:symbol-processing-gradle-plugin:$version"
  }

  const val kotlinpoet = "com.squareup:kotlinpoet-ksp:1.10.2"

  object tachiyomi {
    private const val version = "1.2-SNAPSHOT"
    //const val core = "org.tachiyomi:core-jvm:$version"
    const val core = "io.github.kazemcodes:core-androidRelease:1.0.1-SNAPSHOT"
   // const val api = "org.tachiyomi:source-api-jvm:$version"
  }

  const val okhttp = "com.squareup.okhttp3:okhttp:4.10.0-RC1"
  const val jsoup = "org.jsoup:jsoup:1.14.3"

}
