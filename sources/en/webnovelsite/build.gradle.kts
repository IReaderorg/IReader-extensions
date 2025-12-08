listOf("en").map { lang ->
  Extension(
    name = "WebNovelSite",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
