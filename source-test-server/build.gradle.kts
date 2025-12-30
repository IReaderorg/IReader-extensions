plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("ireader.testserver.MainKt")
    // Disable bytecode verification for dex2jar converted classes
    applicationDefaultJvmArgs = listOf(
        "-XX:+EnableDynamicAgentLoading",
        "-Xverify:none"  // Skip bytecode verification for dex2jar classes
    )
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-server-cors:3.0.3")
    implementation("io.ktor:ktor-server-html-builder:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    
    // Ktor Client (for sources)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.contentNegotiation)
    implementation(libs.ktor.serialization)
    
    // HTML parsing
    implementation(libs.ksoup)
    
    // Serialization
    implementation(libs.serialization.json)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")
    
    // Reflection for source discovery
    implementation("org.reflections:reflections:0.10.2")
    
    // IReader core (for source interfaces)
    implementation(libs.ireader.core)
    
    // DateTime
    implementation(libs.kotlinx.datetime)
    
    // dex2jar - for converting APK/DEX to JAR
    implementation("com.github.pxb1988:dex2jar:v2.4") {
        // Exclude conflicting dependencies
        exclude(group = "org.ow2.asm")
    }
    // ASM for bytecode manipulation (required by dex2jar)
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Print server info when running
tasks.named<JavaExec>("run") {
    doFirst {
        println("""
            |
            |╔══════════════════════════════════════════════════════════════════╗
            |║              🧪 IReader Source Test Server                       ║
            |╠══════════════════════════════════════════════════════════════════╣
            |║                                                                  ║
            |║  Starting server...                                              ║
            |║                                                                  ║
            |║  📍 API Tester:      http://localhost:8080                       ║
            |║  📖 Visual Browser:  http://localhost:8080/browse                ║
            |║                                                                  ║
            |║  Press Ctrl+C to stop the server                                 ║
            |║                                                                  ║
            |╚══════════════════════════════════════════════════════════════════╝
            |
        """.trimMargin())
    }
}
