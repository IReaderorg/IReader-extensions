listOf("en").map { lang ->
  Extension(
    name = "GenesisStudio",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "Novel source from genesistudio.com",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
