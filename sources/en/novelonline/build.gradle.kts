listOf("en").map { lang ->
  Extension(
    name = "NovelOnline",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on novelsonline.net",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register) 