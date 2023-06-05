import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.builder.model.ProductFlavor

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

val extensionList: List<Extension> by extra

android {
    namespace = "ireader.extension"
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
                if (!extension.icon.isAssetType()) {
                    res.srcDirs("${sourceDir}/res")
                }
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
                manifestPlaceholders.putAll(
                    mapOf(
                        "appName" to "IReader: ${extension.name} (${extension.lang})",
                        "sourceId" to ":${extension.sourceId}",
                        "sourceName" to extension.name,
                        "sourceLang" to extension.lang,
                        "sourceDescription" to extension.description,
                        "sourceNsfw" to if (extension.nsfw) 1 else 0,
                        "sourceIcon" to if (extension.icon == DEFAULT_ICON)
                            createExtensionIconLink(extension) else extension.icon,
                        "sourceDir" to extension.sourceDir,
                        "assetsDir" to extension.assetsDir
                    )
                )
            }
        }
    }
    lint {
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    applicationVariants.all {
        this as ApplicationVariantImpl
        val extension = currentExtension() ?: return@all
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

tasks.register("deploy",DeployTask::class.java)



dependencies {
    implementation(project(Proj.defaultRes))

    // Version Catalog not available here, and that is why we manually import them here
    val libs = project.extensions.getByType<VersionCatalogsExtension>()
        .named("libs")

    compileOnly(libs.findLibrary("ireader-core").get())

    compileOnly(libs.findLibrary("stdlib").get())
    compileOnly(libs.findLibrary("okhttp").get())
    compileOnly(libs.findLibrary("jsoup").get())
    compileOnly(libs.findLibrary("ktor-core").get())
    compileOnly(libs.findLibrary("ktor-contentNegotiation").get())
    compileOnly(libs.findLibrary("ktor-serialization").get())
    compileOnly(libs.findLibrary("ktor-gson").get())
    compileOnly(libs.findLibrary("ktor-jackson").get())
    compileOnly(libs.findLibrary("ktor-cio").get())
    compileOnly(libs.findLibrary("ktor-android").get())
    compileOnly(libs.findLibrary("ktor-okhttp").get())

    compileOnly(project(Proj.annotation))
    compileOnly(project(Proj.multisrc))
    ksp(project(Proj.compiler))

    extensionList.forEach { extension ->
        if (extension.deepLinks.isNotEmpty()) {
            add("${extension.flavor}Implementation", project(Proj.deeplink))
        }
        extension.projectDependencies.forEach { dependency ->
            add("${extension.flavor}Implementation", project(dependency))
        }
        extension.remoteDependencies.forEach { dependency ->
            add("${extension.flavor}Implementation", dependency)
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

fun BaseVariantImpl.currentExtension(): Extension? {
    val flavor = (productFlavors as List<ProductFlavor>).first()
    return extensionList.firstOrNull { it.flavor == flavor.name }
}
