listOf("en").map { lang ->
  Extension(
    name = "Mvlempyr",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "Novel source from mvlempyr.io",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
