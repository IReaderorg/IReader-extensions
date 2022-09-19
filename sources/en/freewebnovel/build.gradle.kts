listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 7,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
