listOf("en").map { lang ->
  Extension(
    name = "NovelOnline",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "Novel source based on novelsonline.net",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
