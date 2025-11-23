plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.stdlib)
    compileOnly(libs.jsoup)
    compileOnly(libs.ireader.core)
    compileOnly(libs.ktor.core)
    compileOnly(libs.ktor.cio)
}
