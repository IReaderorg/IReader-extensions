listOf("en").map { lang ->
  Extension(
    name = "QidianUndergrond",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
