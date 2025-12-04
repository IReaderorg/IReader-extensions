listOf("en").map { lang ->
  Extension(
    name = "NovelTop1Net",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
