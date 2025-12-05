listOf("fr").map { lang ->
  Extension(
    name = "NovelDeGlace",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
