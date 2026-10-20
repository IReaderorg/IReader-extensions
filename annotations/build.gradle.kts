plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    
    js(IR) {
        browser()
        nodejs()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
        }
    }
}
