listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 10,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
