plugins {
    `kotlin-dsl`
    // Must match the KGP version added below (libs.gradle). Precompiled script plugins
    // cannot declare their own plugin versions, so every plugin they apply has to be on
    // this build's classpath - see the kotlin("plugin.serialization") calls in
    // extension-setup.gradle.kts / extension-js-setup.gradle.kts.
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(gradleApi())

    // KGP has to be on the classpath twice over: the precompiled scripts apply
    // `org.jetbrains.kotlin.multiplatform`, and its presence here also decides which
    // Kotlin compiler kotlin-dsl compiles buildSrc with.
    implementation(libs.gradle)
    implementation(libs.serialization.gradle)

    // RepoTask.kt uses @Serializable / Json.
    implementation(libs.serialization.json)

    implementation(libs.android.gradle)
    implementation(libs.android.sdklib)
    implementation(libs.ksp.gradle)
    implementation(libs.dex2jar.translator)
    implementation(libs.dex2jar.tools)
    implementation(libs.asm)
}

kotlin {
    // Add Deps to compilation, so it will become available in main project
    sourceSets.getByName("main").kotlin.srcDir("buildSrc/src/main/kotlin")
}

// kotlin-dsl compiles buildSrc's Kotlin sources, and the script bodies it extracts from
// the precompiled script plugins, with a default language/api level that predates what
// the Kotlin compiler on the classpath above still supports; KGP 2.3 rejects it outright
// with "Language version 1.8 is no longer supported". Pin both to a level inside the
// supported range (2.0-2.3), applied to every KotlinCompile task kotlin-dsl creates.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        // compilePluginsBlocks compiles the extracted .gradle.kts script bodies. Scripts
        // are not supported under K2's LightTree analysis, and the failure is silent:
        // the compiler warns and emits no class, so the generated ExtensionSetupPlugin
        // adapter later dies with "Failed to apply plugin 'extension-setup'" /
        // NoClassDefFoundError for Extension_setup_gradle. KGP 2.3 defaults LightTree on.
        freeCompilerArgs.add("-Xuse-fir-lt=false")
    }
}
