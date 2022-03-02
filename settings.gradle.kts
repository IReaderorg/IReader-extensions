include(":annotations")
include(":compiler")
include(":deeplink")
include(":defaultRes")

File(rootDir, "extensions").listFiles().forEach { dir ->
  if (File(dir, "build.gradle.kts").exists()) {
    val name = ":ext-${dir.name}"
    include(name)
    project(name).projectDir = File("extensions/${dir.name}")
  }
}

File(rootDir, "src").eachDir { dir ->
  dir.eachDir { subdir ->
    val name = ":extensions:individual:${dir.name}:${subdir.name}"
    include(name)
    project(name).projectDir = File("src/${dir.name}/${subdir.name}")
  }
}

File(rootDir, "extensions").eachDir { dir ->
  dir.eachDir { subdir ->
    val name = ":extensions:individual:${dir.name}:${subdir.name}"
    include(name)
    project(name).projectDir = File("extensions/${dir.name}/${subdir.name}")
  }
}

pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
  }
  versionCatalogs {
    create("kotlinLibs") {
      from(files("./gradle/kotlin.versions.toml"))
    }
  }
}

//include(":sample-single")
//project(":sample-single").projectDir = file("samples/single-site")
//
//include(":sample-single-multilang")
//project(":sample-single-multilang").projectDir = file("samples/single-site-multilang")
//
//include(":sample-multi")
//project(":sample-multi").projectDir = file("samples/multi-site")
//
//include(":sample-multi-srcs")
//project(":sample-multi-srcs").projectDir = file("samples/multi-site-srcs")

inline fun File.eachDir(block: (File) -> Unit) {
  listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
include(":core")
