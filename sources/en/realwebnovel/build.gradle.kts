listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 7,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
