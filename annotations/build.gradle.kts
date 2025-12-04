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
            // No dependencies needed for annotations
        }
    }
}
