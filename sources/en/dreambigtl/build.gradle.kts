listOf("en").map { lang ->
  Extension(
    name = "DreamBigTL",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "Novel source based on dreambigtl.com",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register) 