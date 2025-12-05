listOf("en").map { lang ->
  Extension(
    name = "BestLightNovel",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "Novel source based on bestlightnovel.com",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
