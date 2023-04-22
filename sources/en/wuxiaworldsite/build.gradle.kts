listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 10,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
