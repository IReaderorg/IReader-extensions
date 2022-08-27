import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.builder.model.ProductFlavor

plugins {
  id("com.android.application")
  id("kotlin-android")
  kotlin("plugin.serialization")
  id("com.google.devtools.ksp")
}

val extensionList: List<Extension> by extra

android {
  compileSdk = Config.compileSdk
  defaultConfig {
    minSdk = Config.minSdk
    targetSdk = Config.targetSdk
    multiDexEnabled = false
  }
  sourceSets {
    named("main") {
      manifest.srcFile("$rootDir/extensions/AndroidManifest.xml")
      java.srcDirs("main/src")
      res.srcDirs("main/res")
      resources.setSrcDirs(emptyList<Any>())
    }
    extensionList.forEach { extension ->
      val sourceDir = extension.sourceDir
      create(extension.flavor) {
        java.srcDirs("${sourceDir}/src")
        res.srcDirs("${sourceDir}/res")
      }
    }
  }
  flavorDimensions.add("default")
  productFlavors {
    extensionList.forEach { extension ->
      create(extension.flavor) {
        dimension = "default"
        versionCode = extension.versionCode
        versionName = extension.versionName
        manifestPlaceholders.putAll(mapOf(
          "appName" to "IReader: ${extension.name} (${extension.lang})",
          "sourceId" to ":${extension.sourceId}",
          "sourceName" to extension.name,
          "sourceLang" to extension.lang,
          "sourceDescription" to extension.description,
          "sourceNsfw" to if (extension.nsfw) 1 else 0
        ))
      }
    }
  }
  compileOptions {
    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
      kotlinOptions {
        jvmTarget = "11"
      }
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  applicationVariants.all {
    this as ApplicationVariantImpl
    val extension = currentExtension()
    outputs.all {
      this as BaseVariantOutputImpl
      val nameLower = extension.name.toLowerCase()
      outputFileName = "ireader-${extension.lang}-${nameLower}-v${extension.versionName}.apk"

      processManifestForExtension(extension)
    }
  }
  dependenciesInfo {
    includeInApk = false
  }

  if (System.getenv("STORE_PATH") != null) {
    signingConfigs {
      create("release") {
        storeFile = file(System.getenv("STORE_PATH"))
        keyAlias = System.getenv("STORE_ALIAS")
        storePassword = System.getenv("STORE_PASS")
        keyPassword = System.getenv("KEY_PASS")
      }
    }
    buildTypes.all {
      signingConfig = signingConfigs.getByName("release")
    }
  }
}

dependencies {
  implementation(project(Proj.defaultRes))

  // Version Catalog not available here, and that is why we manually import them here
  val kotlinLibs = project.extensions.getByType<VersionCatalogsExtension>()
    .named("kotlinLibs")
  val libs = project.extensions.getByType<VersionCatalogsExtension>()
    .named("libs")

  compileOnly(libs.findLibrary("ireader-core").get()) { isChanging = true}

  compileOnly(kotlinLibs.findLibrary("stdlib").get())
  compileOnly(libs.findLibrary("okhttp").get())
  compileOnly(libs.findLibrary("jsoup").get())
  compileOnly(libs.findLibrary("ktor-core").get())
  compileOnly(libs.findLibrary("ktor-contentNegotiation").get())
  compileOnly(libs.findLibrary("ktor-serialization").get())
  compileOnly(libs.findLibrary("ktor-gson").get())
  compileOnly(libs.findLibrary("ktor-jackson").get())
  implementation(libs.findLibrary("ktor-cio").get())
  implementation(libs.findLibrary("ktor-android").get())
  implementation(libs.findLibrary("ktor-okhttp").get())

  compileOnly(project(Proj.annotation))
  ksp(project(Proj.compiler))

  extensionList.forEach { extension ->
    if (extension.deepLinks.isNotEmpty()) {
      add("${extension.flavor}Implementation", project(Proj.deeplink))
    }
  }
}

ksp {
  extensionList.forEach { extension ->
    val prefix = extension.flavor
    arg("${prefix}_name", extension.name)
    arg("${prefix}_lang", extension.lang)
    arg("${prefix}_id", extension.sourceId.toString())
    arg("${prefix}_has_deeplinks", extension.deepLinks.isNotEmpty().toString())
  }
}

fun BaseVariantImpl.currentExtension(): Extension {
  val flavor = (productFlavors as List<ProductFlavor>).first()
  return extensionList.first { it.flavor == flavor.name }
}


configurations.all {
  resolutionStrategy {

    cacheChangingModulesFor(0, "seconds") //comment this line if you found needing to connect internet annoying
    force("org.jsoup:jsoup:1.14.3")
  }
}