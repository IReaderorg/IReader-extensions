listOf("en").map { lang ->
  Extension(
    name = "NovelStic",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
