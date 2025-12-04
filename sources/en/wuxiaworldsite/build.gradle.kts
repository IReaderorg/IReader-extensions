listOf("en").map { lang ->
  Extension(
    name = "WuxiaWorldSite",
    versionCode = 10,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
