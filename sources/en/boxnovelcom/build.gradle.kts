listOf("en").map { lang ->
  Extension(
    name = "BoxNovel",
    versionCode = 4,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
    icon = DEFAULT_ICON
  )
}.also(::register)