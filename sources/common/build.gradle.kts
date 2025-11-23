plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.stdlib)
    compileOnly(libs.jsoup)
    compileOnly(libs.ireader.core)
}
