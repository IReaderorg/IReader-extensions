listOf("en").map { lang ->
  Extension(
    name = "DreamBigTL",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "Novel source based on dreambigtl.com",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
