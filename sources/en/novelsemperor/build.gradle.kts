listOf("en").map { lang ->
  Extension(
    name = "NovelsEmperor",
    versionCode = 2,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,
  )
}.also(::register)
