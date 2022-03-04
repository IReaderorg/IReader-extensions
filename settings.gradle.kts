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

File(rootDir, "sources").eachDir { dir ->
  dir.eachDir { subdir ->
    val name = ":extensions:individual:${dir.name}:${subdir.name}"
    include(name)
    project(name).projectDir = File("sources/${dir.name}/${subdir.name}")
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

include(":sources")
