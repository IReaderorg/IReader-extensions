listOf("en").map { lang ->
  Extension(
    name = "NovelFull",
    versionCode = 9,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
