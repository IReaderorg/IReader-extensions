listOf("en").map { lang ->
  Extension(
    name = "NovelsEmperor",
    versionCode = 1,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
