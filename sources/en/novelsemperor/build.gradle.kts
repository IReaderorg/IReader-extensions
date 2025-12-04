listOf("en").map { lang ->
  Extension(
    name = "NovelsEmperor",
    versionCode = 1,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
