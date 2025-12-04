listOf("en").map { lang ->
  Extension(
    name = "QidianUndergrond",
    versionCode = 4,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON,enableJs = true,
  )
}.also(::register)
