listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 14,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
