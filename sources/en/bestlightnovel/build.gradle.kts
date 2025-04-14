listOf("en").map { lang ->
  Extension(
    name = "BestLightNovel",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on bestlightnovel.com",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register) 