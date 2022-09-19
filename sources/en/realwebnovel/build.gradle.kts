listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 7,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
