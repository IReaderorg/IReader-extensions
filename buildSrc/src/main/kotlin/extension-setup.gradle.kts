import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.gradle.tasks.ProcessMultiApkApplicationManifest
import com.android.builder.model.ProductFlavor

plugins {
  id("com.android.application")
  id("kotlin-android")
  kotlin("plugin.serialization")
  id("kotlin-kapt")
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
          "appName" to "Tachiyomi: ${extension.name} (${extension.lang})",
          "sourceId" to ":${extension.id}",
          "sourceName" to extension.name,
          "sourceLang" to extension.lang,
          "sourceDescription" to extension.description,
          "sourceNsfw" to if (extension.nsfw) 1 else 0
        ))
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  applicationVariants.all {
    this as ApplicationVariantImpl
    val extension = currentExtension()
    outputs.all {
      this as BaseVariantOutputImpl
      val nameLower = extension.name.toLowerCase()
      outputFileName = "tachiyomi-${extension.lang}-${nameLower}-v${extension.versionName}.apk"
    }
  }
  buildFeatures {
    // Disable unused AGP features
    buildConfig = false
    aidl = false
    renderScript = false
    resValues = false
    shaders = false
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
  compileOnly(Deps.tachiyomiCore)
  compileOnly(Deps.tachiyomiSourceApi)

  compileOnly(Deps.okhttp)
  compileOnly(Deps.jsoup)

  compileOnly(project(Proj.annotation))
  kapt(project(Proj.compiler))

  extensionList.forEach { extension ->
    if (extension.deepLinks.isNotEmpty()) {
      add("${extension.flavor}Implementation", project(Proj.deeplink))
    }
  }
}

kapt {
  arguments {
    val variant = variant as ApplicationVariantImpl
    val extension = variant.currentExtension()

    arg("SOURCE_NAME", extension.name)
    arg("SOURCE_LANG", extension.lang)
    arg("SOURCE_ID", extension.id)
    arg("MANIFEST_HAS_DEEPLINKS", extension.deepLinks.isNotEmpty())
  }
}

afterEvaluate {
  android.applicationVariants.all {
    this as ApplicationVariantImpl
    val sources = sourceSets
    val extension = currentExtension()

    outputs.forEach { output ->
      output.processManifestProvider.configure {
        doLast {
          this as ProcessMultiApkApplicationManifest
          val outputDirectory = multiApkManifestOutputDirectory.get().asFile
          val manifestFile = File(outputDirectory, "AndroidManifest.xml").absolutePath

          ManifestGenerator.process(manifestFile, extension, sources)
        }
      }
    }
  }
}

fun BaseVariantImpl.currentExtension(): Extension {
  val flavor = (productFlavors as List<ProductFlavor>).first()
  return extensionList.first { it.flavor == flavor.name }
}
