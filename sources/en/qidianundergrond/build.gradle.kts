listOf("en").map { lang ->
  Extension(
    name = "QidianUnderground",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
