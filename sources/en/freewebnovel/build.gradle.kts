listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 11,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
