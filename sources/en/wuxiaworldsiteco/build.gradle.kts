listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSiteco",
    versionCode = 8,
    libVersion = "1.0",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
