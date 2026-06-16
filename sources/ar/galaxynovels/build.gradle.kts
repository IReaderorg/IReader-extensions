listOf("ar").map { lang ->
  Extension(
    name = "GalaxyNovels",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false)
}.also(::register)
