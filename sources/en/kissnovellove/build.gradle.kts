listOf("en").map { lang ->
  Extension(
    name = "KissNovelLove",
    versionCode = 3,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
