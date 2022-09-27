listOf("ar").map { lang ->
  Extension(
    name = "Armtl",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
    dependencies =  { dependencyHandler: DependencyHandler, extension: Extension ->
      dependencyHandler.add("${extension.flavor}Implementation", project(Proj.multisrc))
    }
  )
}.also(::register)
