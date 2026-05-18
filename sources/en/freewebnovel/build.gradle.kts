listOf("en").map { lang ->
  Extension(
    name = "FreeWebNovel",
    versionCode = 13,
    libVersion = "2",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)
